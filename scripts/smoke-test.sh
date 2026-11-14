#!/usr/bin/env bash
# End-to-end smoke test against a RUNNING stack (`make up` first). Usage: make smoke  (or scripts/smoke-test.sh)
#
# Covers the checkout saga and every compensation path (R-TEST-04, part):
#   1. happy path            -> order CONFIRMED, payment SUCCEEDED, pet SOLD, shipment created
#   2. archive rules         -> archived pet RETIRED, hidden, not orderable; sold pet cannot be archived (409)
#   3. payment declined      -> order FAILED, payment FAILED, pet AVAILABLE again
#   4. cancel during charge  -> order CANCELLED, payment REFUNDED, pet AVAILABLE again
#   5. reservation expiry    -> order CANCELLED, payment REFUNDED, pet AVAILABLE again
#   S. security (Phase 3)    -> each service rejects another customer's records (403), owner/admin allowed,
#                               shared error body, coarse gateway rules (401 without token, admin-only prefixes)
# Scenarios 3-5 restart payment/inventory with test settings; defaults are restored at the end.
# Each scenario creates its own fresh pet (as admin), so the seeded catalog is not used up.
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8089}"
COMPOSE="${COMPOSE:-docker compose}"
PASSED=0
FAILED=0

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
log() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
ok() { printf '  \033[32mPASS\033[0m %s\n' "$*"; PASSED=$((PASSED + 1)); }
bad() { printf '  \033[31mFAIL\033[0m %s\n' "$*"; FAILED=$((FAILED + 1)); }
check() { # check <description> <actual> <expected>
  if [ "$#" -ne 3 ]; then bad "$1: check called with $# arguments (script bug)"; return; fi
  if [ "$2" = "$3" ]; then ok "$1 = $2"; else bad "$1: expected $3, got $2"; fi
}

# JSON bodies are built here and always passed as ONE quoted argument (no nested escaping inside $(...)).
order_body() { printf '{"items":[{"petId":"%s","quantity":1}]}' "$1"; }
pet_body() { printf '{"name":"Smoke %s","species":"DOG","breed":"Test Breed","age":1,"gender":"MALE","price":123.45,"description":"Created by the smoke test"}' "$(date +%s)$RANDOM"; }
login_body() { printf '{"email":"%s","password":"password123"}' "$1"; }
http_code() { curl -s -m 10 -o /dev/null -w '%{http_code}' "$@"; }

login() {
  curl -sf -m 10 -X POST "$GATEWAY/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "$(login_body "$1")" | json "d['token']"
}

# create_pet <admin token> -> pet id; waits until inventory knows the pet (event delivered via the outbox)
create_pet() {
  local id
  id=$(curl -sf -m 10 -X POST "$GATEWAY/api/v1/pets" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -d "$(pet_body)" | json "d['id']")
  sleep 3
  echo "$id"
}

order_pet() { # order_pet <token> <pet id> -> order id
  curl -sf -m 10 -X POST "$GATEWAY/api/v1/orders" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -d "$(order_body "$2")" | json "d['id']"
}

order_status() { curl -sf -m 10 -H "Authorization: Bearer $1" "$GATEWAY/api/v1/orders/$2" | json "d['status']"; }
payment_status() { curl -s -m 10 -H "Authorization: Bearer $1" "$GATEWAY/api/v1/payments/order/$2" | json "d.get('status','NONE')" 2>/dev/null || echo NONE; }
pet_status() { curl -sf -m 10 "$GATEWAY/api/v1/pets/$1" | json "d['availabilityStatus']"; }

# wait_for <description> <timeout seconds> <expected> <command...>
wait_for() {
  local desc=$1 timeout=$2 expected=$3 value=""
  shift 3
  for _ in $(seq 1 "$timeout"); do
    value=$("$@" 2>/dev/null || true)
    [ "$value" = "$expected" ] && break
    sleep 1
  done
  check "$desc" "$value" "$expected"
}

restart_with() { # restart_with "VAR=value ..." services...
  local settings=$1
  shift
  echo "  (restarting $* with: ${settings:-defaults})"
  env $settings $COMPOSE up -d --no-deps --wait "$@" >/dev/null 2>&1
}

restore_defaults() {
  log "Restoring default payment/inventory settings"
  restart_with "" payment-service inventory-service
}
trap restore_defaults EXIT

ADMIN=$(login admin@petplanet.com)
USER_TOKEN=$(login customer@petplanet.com)
ok "both demo accounts log in"

log "1. Happy path"
PET=$(create_pet "$ADMIN")
ORDER=$(order_pet "$USER_TOKEN" "$PET")
wait_for "order #$ORDER status" 30 CONFIRMED order_status "$USER_TOKEN" "$ORDER"
check "payment" "$(payment_status "$USER_TOKEN" "$ORDER")" SUCCEEDED
wait_for "pet availability" 15 SOLD pet_status "$PET"
wait_for "shipment created" 15 200 http_code -H "Authorization: Bearer $USER_TOKEN" "$GATEWAY/api/v1/shipments/order/$ORDER"
SOLD_PET=$PET

log "S. Security: ownership is enforced by each owning service"
OWNER_ID=$(curl -sf -m 10 -H "Authorization: Bearer $USER_TOKEN" "$GATEWAY/api/v1/orders/$ORDER" | json "d['customerId']")
OTHER_EMAIL="smoke-$(date +%s)$RANDOM@example.com"
OTHER_TOKEN=$(curl -sf -m 10 -X POST "$GATEWAY/api/v1/auth/register" -H 'Content-Type: application/json' \
  -d "$(printf '{"email":"%s","password":"password123","firstName":"Smoke","lastName":"Other"}' "$OTHER_EMAIL")" | json "d['token']")
for path in orders/$ORDER payments/order/$ORDER shipments/order/$ORDER notifications/order/$ORDER \
            deliveries/order/$ORDER fulfillment/order/$ORDER; do
  # owner first (also waits until the asynchronous record exists), then another customer, then admin
  wait_for "owner GET /$path" 15 200 http_code -H "Authorization: Bearer $USER_TOKEN" "$GATEWAY/api/v1/$path"
  check "other customer GET /$path" "$(http_code -H "Authorization: Bearer $OTHER_TOKEN" "$GATEWAY/api/v1/$path")" 403
  check "admin GET /$path" "$(http_code -H "Authorization: Bearer $ADMIN" "$GATEWAY/api/v1/$path")" 200
done
check "other customer lists owner's notifications" \
  "$(http_code -H "Authorization: Bearer $OTHER_TOKEN" "$GATEWAY/api/v1/notifications/customer/$OWNER_ID")" 403
check "owner lists own notifications" \
  "$(http_code -H "Authorization: Bearer $USER_TOKEN" "$GATEWAY/api/v1/notifications/customer/$OWNER_ID")" 200
check "no token (HTTP)" "$(http_code "$GATEWAY/api/v1/orders")" 401
check "401 body uses shared error shape" \
  "$(curl -s -m 10 "$GATEWAY/api/v1/orders" | json "sorted(k for k in d)")" \
  "['correlationId', 'error', 'message', 'path', 'status', 'timestamp']"
check "403 body from a service uses shared error shape" \
  "$(curl -s -m 10 -H "Authorization: Bearer $OTHER_TOKEN" "$GATEWAY/api/v1/payments/order/$ORDER" | json "d['status'], d['error'], bool(d['correlationId'])")" \
  "403 Forbidden True"
check "user on admin-only /users (HTTP)" "$(http_code -H "Authorization: Bearer $USER_TOKEN" "$GATEWAY/api/v1/users")" 403
check "user marks delivered (HTTP)" "$(http_code -X PUT -H "Authorization: Bearer $USER_TOKEN" "$GATEWAY/api/v1/deliveries/order/$ORDER/delivered")" 403
check "unknown order payment (HTTP)" "$(http_code -H "Authorization: Bearer $ADMIN" "$GATEWAY/api/v1/payments/order/999999999")" 404
check "own profile from token (HTTP)" "$(http_code -H "Authorization: Bearer $OTHER_TOKEN" "$GATEWAY/api/v1/users/me")" 200

log "2. Archive rules"
PET=$(create_pet "$ADMIN")
check "archive available pet (HTTP)" "$(http_code -X DELETE -H "Authorization: Bearer $ADMIN" "$GATEWAY/api/v1/pets/$PET")" 204
check "archived pet availability" "$(pet_status "$PET")" RETIRED
check "archived pet hidden from default listing" \
  "$(curl -sf -m 10 "$GATEWAY/api/v1/pets?size=200" | json "str(any(p['id']=='$PET' for p in d['content']))")" False
BODY=$(order_body "$PET")
check "ordering archived pet (HTTP)" "$(http_code -X POST "$GATEWAY/api/v1/orders" -H "Authorization: Bearer $USER_TOKEN" -H 'Content-Type: application/json' -d "$BODY")" 409
check "archive sold pet (HTTP)" "$(http_code -X DELETE -H "Authorization: Bearer $ADMIN" "$GATEWAY/api/v1/pets/$SOLD_PET")" 409
check "malformed order JSON (HTTP)" "$(http_code -X POST "$GATEWAY/api/v1/orders" -H "Authorization: Bearer $USER_TOKEN" -H 'Content-Type: application/json' -d '{"items":["oops"]}')" 400

log "3. Payment declined"
restart_with "PAYMENT_SIMULATION_RESULT=FAILURE" payment-service
PET=$(create_pet "$ADMIN")
ORDER=$(order_pet "$USER_TOKEN" "$PET")
wait_for "order #$ORDER status" 30 FAILED order_status "$USER_TOKEN" "$ORDER"
check "payment" "$(payment_status "$USER_TOKEN" "$ORDER")" FAILED
wait_for "pet released" 15 AVAILABLE pet_status "$PET"

log "4. Customer cancels while the charge is in progress"
restart_with "PAYMENT_SIMULATION_DELAY=PT8S" payment-service
PET=$(create_pet "$ADMIN")
ORDER=$(order_pet "$USER_TOKEN" "$PET")
wait_for "order #$ORDER status before cancel" 15 PAYMENT_PENDING order_status "$USER_TOKEN" "$ORDER"
check "cancel (HTTP)" "$(http_code -X PATCH -H "Authorization: Bearer $USER_TOKEN" "$GATEWAY/api/v1/orders/$ORDER/cancel")" 200
wait_for "payment refunded after the slow charge" 30 REFUNDED payment_status "$USER_TOKEN" "$ORDER"
check "order status" "$(order_status "$USER_TOKEN" "$ORDER")" CANCELLED
wait_for "pet released" 15 AVAILABLE pet_status "$PET"

log "5. Reservation expires while the charge is in progress"
restart_with "INVENTORY_RESERVATION_TIMEOUT=PT5S INVENTORY_EXPIRATION_CHECK_DELAY_MS=1000" inventory-service
restart_with "PAYMENT_SIMULATION_DELAY=PT15S" payment-service
PET=$(create_pet "$ADMIN")
ORDER=$(order_pet "$USER_TOKEN" "$PET")
wait_for "order #$ORDER cancelled by expiry" 20 CANCELLED order_status "$USER_TOKEN" "$ORDER"
wait_for "payment refunded after the slow charge" 40 REFUNDED payment_status "$USER_TOKEN" "$ORDER"
wait_for "pet released" 15 AVAILABLE pet_status "$PET"

log "Result: $PASSED passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
