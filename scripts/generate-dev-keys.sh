#!/usr/bin/env bash
# Generates local development secrets into .env (R-INF-08): no keys or tokens are committed to git.
#
#   JWT_PRIVATE_KEY              RSA-2048 private key (PKCS#8, base64, one line) - given to user-service only
#   JWT_PUBLIC_KEY               matching public key (X.509, base64, one line)  - given to every service
#   USER_SERVICE_INTERNAL_TOKEN  shared gateway -> user-service token (random hex)
#
# Creates .env from .env.example if it does not exist. Existing values are kept unless --force is given
# (rotating the key pair invalidates every token already issued; users simply log in again).
#
# Usage: scripts/generate-dev-keys.sh [--force]
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE=.env
FORCE=false
[[ "${1:-}" == "--force" ]] && FORCE=true

command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }

if [[ ! -f "$ENV_FILE" ]]; then
  cp .env.example "$ENV_FILE"
  echo "Created $ENV_FILE from .env.example"
fi

current() { grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true; }

# Replace KEY=... in .env (or append it), without sed -i portability problems.
set_value() {
  local key="$1" value="$2" tmp
  tmp="$(mktemp)"
  awk -v key="$key" -v value="$value" '
    BEGIN { done = 0 }
    $0 ~ "^" key "=" { if (!done) { print key "=" value; done = 1 } ; next }
    { print }
    END { if (!done) print key "=" value }
  ' "$ENV_FILE" > "$tmp"
  mv "$tmp" "$ENV_FILE"
}

if $FORCE || [[ -z "$(current JWT_PRIVATE_KEY)" || -z "$(current JWT_PUBLIC_KEY)" ]]; then
  pem="$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null)"
  private_key="$(printf '%s\n' "$pem" | grep -v -- '-----' | tr -d '\n')"
  public_key="$(printf '%s\n' "$pem" | openssl pkey -pubout 2>/dev/null | grep -v -- '-----' | tr -d '\n')"
  set_value JWT_PRIVATE_KEY "$private_key"
  set_value JWT_PUBLIC_KEY "$public_key"
  echo "Generated a new RSA key pair (JWT_PRIVATE_KEY / JWT_PUBLIC_KEY) in $ENV_FILE"
else
  echo "Keeping the existing JWT key pair in $ENV_FILE (use --force to rotate)"
fi

if $FORCE || [[ -z "$(current USER_SERVICE_INTERNAL_TOKEN)" ]]; then
  set_value USER_SERVICE_INTERNAL_TOKEN "$(openssl rand -hex 32)"
  echo "Generated USER_SERVICE_INTERNAL_TOKEN in $ENV_FILE"
else
  echo "Keeping the existing USER_SERVICE_INTERNAL_TOKEN in $ENV_FILE (use --force to rotate)"
fi
