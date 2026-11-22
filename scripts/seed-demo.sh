#!/usr/bin/env bash
# Demo data on top of the Flyway seed (2 accounts, 6 pets) against a RUNNING stack. Usage: make seed (after make up/reset)
#
# Everything goes through the public API, so every service and event stays consistent:
#   - 8 more pets (catalog -> inventory via events), 14 in total
#   - a second customer: john@petplanet.com / password123
#   - 3 orders with different progress: Jane's order DELIVERED, Jane's order OUT_FOR_DELIVERY, John's order IN_TRANSIT
# Re-running is safe: pets that already exist (by name) are skipped, and orders are only placed when the customer has none.
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8089}"
json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
post() { curl -sf -m 10 -X POST "$GATEWAY/api/v1/$1" -H 'Content-Type: application/json' ${3:+-H "Authorization: Bearer $3"} -d "$2"; }
get() { curl -sf -m 10 -H "Authorization: Bearer $2" "$GATEWAY/api/v1/$1"; }
login() { post auth/login "$(printf '{"email":"%s","password":"password123"}' "$1")" | json "d['token']"; }

wait_for() { # wait_for <description> <command...>: retries for up to 30 s until the command succeeds
  local what=$1; shift
  for _ in $(seq 1 30); do "$@" >/dev/null 2>&1 && return 0; sleep 1; done
  echo "Timed out waiting for: $what" >&2; return 1
}

echo "== Accounts"
ADMIN=$(login admin@petplanet.com)
JANE=$(login customer@petplanet.com)
if JOHN=$(login john@petplanet.com 2>/dev/null); then
  echo "  john@petplanet.com exists"
else
  JOHN=$(post auth/register '{"firstName":"John","lastName":"Walker","email":"john@petplanet.com","password":"password123"}' | json "d['token']")
  echo "  registered john@petplanet.com"
fi

echo "== Pets"
EXISTING=$(curl -sf -m 10 "$GATEWAY/api/v1/pets?size=200" | json "'|'.join(p['name'] for p in d['content'])")
add_pet() { # add_pet <name> <species> <breed> <age> <gender> <price> <description>
  if [[ "|$EXISTING|" == *"|$1|"* ]]; then echo "  $1 exists"; return; fi
  post pets "$(printf '{"name":"%s","species":"%s","breed":"%s","age":%s,"gender":"%s","price":%s,"description":"%s"}' "$@")" "$ADMIN" >/dev/null
  echo "  added $1"
}
add_pet Daisy DOG Beagle 1 FEMALE 650.00 "Cheerful, curious nose-to-the-ground explorer who gets along with everyone."
add_pet Rocky DOG Boxer 4 MALE 780.00 "Energetic and goofy family dog with a big heart and endless patience for kids."
add_pet Coco DOG Poodle 2 FEMALE 1100.00 "Clever, hypoallergenic and quick to learn new tricks."
add_pet Teddy DOG "Cavalier King Charles Spaniel" 3 MALE 1350.00 "Gentle lap dog who loves slow walks and long naps."
add_pet Nala CAT Bengal 1 FEMALE 1250.00 "Athletic, playful and wild-looking, with a spotted golden coat."
add_pet Simba CAT Persian 3 MALE 900.00 "Calm, affectionate and happiest on a sunny windowsill."
add_pet Cleo CAT Ragdoll 2 FEMALE 1050.00 "Relaxed and cuddly; goes limp with joy when picked up."
add_pet Jasper CAT "Scottish Fold" 4 MALE 820.00 "Easygoing companion with folded ears and an owl-like face."

pet_id() { curl -sf -m 10 "$GATEWAY/api/v1/pets?size=200" | json "next(p['id'] for p in d['content'] if p['name']=='$1')"; }
inventory_available() { # inventory knows the pet (catalog event delivered)
  get "inventory/pets?status=AVAILABLE&size=200" "$ADMIN" | json "'$1' in [p['petId'] for p in d['content']] or sys.exit(1)"
}
order_count() { get "orders?size=1" "$1" | json "d['totalElements']"; }

place_order() { # place_order <token> <pet name> -> order id, once the order is CONFIRMED and has a delivery
  local pet order
  pet=$(pet_id "$2")
  wait_for "inventory for $2" inventory_available "$pet"
  order=$(post orders "$(printf '{"items":[{"petId":"%s","quantity":1}]}' "$pet")" "$1" | json "d['id']")
  wait_for "order $order confirmed" bash -c "curl -sf -m 10 -H 'Authorization: Bearer $1' '$GATEWAY/api/v1/orders/$order' | grep -q '\"status\":\"CONFIRMED\"'"
  wait_for "delivery for order $order" get "deliveries/order/$order" "$ADMIN"
  echo "$order"
}
advance() { # advance <order> <status>; the note is shown to the customer, so it reads like a courier update
  local note="With the courier"
  [ "$2" = DELIVERED ] && note="Handed over at the door"
  curl -sf -m 10 -o /dev/null -X PUT "$GATEWAY/api/v1/deliveries/order/$1/status" -H "Authorization: Bearer $ADMIN" \
    -H 'Content-Type: application/json' -d "$(printf '{"status":"%s","note":"%s"}' "$2" "$note")"
}

echo "== Orders"
if [ "$(order_count "$JANE")" = "0" ]; then
  o1=$(place_order "$JANE" Daisy); advance "$o1" OUT_FOR_DELIVERY; advance "$o1" DELIVERED
  echo "  Jane: order #$o1 (Daisy) delivered"
  o2=$(place_order "$JANE" Nala); advance "$o2" OUT_FOR_DELIVERY
  echo "  Jane: order #$o2 (Nala) out for delivery"
else
  echo "  Jane already has orders"
fi
if [ "$(order_count "$JOHN")" = "0" ]; then
  o3=$(place_order "$JOHN" Rocky)
  echo "  John: order #$o3 (Rocky) in transit"
else
  echo "  John already has orders"
fi

echo "Done. Sign in with admin@petplanet.com, customer@petplanet.com or john@petplanet.com (password123)."
