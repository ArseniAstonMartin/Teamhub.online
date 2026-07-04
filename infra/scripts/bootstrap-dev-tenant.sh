#!/usr/bin/env bash
set -euo pipefail

TEAMHUB_API_BASE_URL="${TEAMHUB_API_BASE_URL:-http://localhost:8080}"
MAILHOG_API_URL="${MAILHOG_API_URL:-http://localhost:8025/api/v2/messages}"
STAFF_EMAIL="${STAFF_EMAIL:-dev@teamhub.online}"
STAFF_PASSWORD="${STAFF_PASSWORD:-TeamhubDev131!}"
TENANT_NAME="${TENANT_NAME:-Dev Plumbing $(date +%Y%m%d-%H%M%S)}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin+$(date +%s)@teamhub.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-TeamhubAdmin133!}"
PRESET_KEY="${PRESET_KEY:-plumber}"
BOOTSTRAP_MODE="${BOOTSTRAP_MODE:-password}" # allowed: password|invite
MAILHOG_POLL_ATTEMPTS="${MAILHOG_POLL_ATTEMPTS:-20}"
MAILHOG_POLL_SLEEP_SECONDS="${MAILHOG_POLL_SLEEP_SECONDS:-2}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

json_get() {
  local file_path="$1"
  local key="$2"
  python3 - "$file_path" "$key" <<'PY'
import json
import pathlib
import sys

file_path = pathlib.Path(sys.argv[1])
key = sys.argv[2]
data = json.loads(file_path.read_text())

value = data.get(key)
if value in (None, ""):
    sys.exit(1)
print(value)
PY
}

extract_invite_url() {
  local file_path="$1"
  local target_email="$2"
  python3 - "$file_path" "$target_email" <<'PY'
import json
import pathlib
import re
import sys

file_path = pathlib.Path(sys.argv[1])
target_email = sys.argv[2].lower()
data = json.loads(file_path.read_text())

messages = data.get("items", [])
url_pattern = re.compile(r"https?://[^\s\"'>]+/accept-invite\?token=[^ \n\r\"'&]+")

for message in reversed(messages):
    content = message.get("Content", {}) or {}
    headers = content.get("Headers", {}) or {}
    to_values = headers.get("To", []) or []
    to_blob = " ".join(to_values).lower()
    body = (content.get("Body") or "") + "\n" + (message.get("Raw", {}).get("Data") or "")

    if target_email not in to_blob and target_email not in body.lower():
        continue

    match = url_pattern.search(body)
    if match:
        print(match.group(0))
        sys.exit(0)

sys.exit(1)
PY
}

require_cmd curl
require_cmd python3
require_cmd mktemp

if [[ "$BOOTSTRAP_MODE" != "password" && "$BOOTSTRAP_MODE" != "invite" ]]; then
  echo "Invalid BOOTSTRAP_MODE: $BOOTSTRAP_MODE (expected password or invite)" >&2
  exit 1
fi

echo "==> Logging in as staff user ($STAFF_EMAIL)"
login_payload_file="$(mktemp)"
login_status="$(
  curl -sS -o "$login_payload_file" -w "%{http_code}" \
    -X POST "$TEAMHUB_API_BASE_URL/api/v1/internal/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$STAFF_EMAIL\",\"password\":\"$STAFF_PASSWORD\"}"
)"
if [[ "$login_status" != "200" ]]; then
  echo "Staff login failed (HTTP $login_status)." >&2
  echo "Response:" >&2
  cat "$login_payload_file" >&2
  exit 1
fi

staff_access_token="$(json_get "$login_payload_file" "accessToken")"

echo "==> Provisioning tenant '$TENANT_NAME' with preset '$PRESET_KEY' for $ADMIN_EMAIL"
provision_payload_file="$(mktemp)"
provision_status="$(
  curl -sS -o "$provision_payload_file" -w "%{http_code}" \
    -X POST "$TEAMHUB_API_BASE_URL/api/v1/internal/tenants" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $staff_access_token" \
    -d "{\"name\":\"$TENANT_NAME\",\"ownerEmail\":\"$ADMIN_EMAIL\",\"presetKey\":\"$PRESET_KEY\"}"
)"
if [[ "$provision_status" != "200" ]]; then
  echo "Tenant provisioning failed (HTTP $provision_status)." >&2
  echo "Response:" >&2
  cat "$provision_payload_file" >&2
  exit 1
fi

tenant_id="$(json_get "$provision_payload_file" "tenantId")"
invite_id="$(json_get "$provision_payload_file" "inviteId")"

echo "==> Polling MailHog for invite URL (inviteId: $invite_id)"
invite_url=""
for _ in $(seq 1 "$MAILHOG_POLL_ATTEMPTS"); do
  mailhog_file="$(mktemp)"
  mailhog_status="$(curl -sS -o "$mailhog_file" -w "%{http_code}" "$MAILHOG_API_URL")"
  if [[ "$mailhog_status" == "200" ]]; then
    if invite_url="$(extract_invite_url "$mailhog_file" "$ADMIN_EMAIL" 2>/dev/null)"; then
      break
    fi
  fi
  sleep "$MAILHOG_POLL_SLEEP_SECONDS"
done

if [[ -z "$invite_url" ]]; then
  echo "Invite email not found in MailHog yet." >&2
  echo "Open http://localhost:8025 and locate the invite for $ADMIN_EMAIL." >&2
  exit 1
fi

if [[ "$BOOTSTRAP_MODE" == "invite" ]]; then
  cat <<EOF

Bootstrap complete (invite mode).
- Tenant ID: $tenant_id
- Owner email: $ADMIN_EMAIL
- Accept invite URL: $invite_url

Next: open the URL above, set the owner password, and log in at http://localhost:5173/login.
EOF
  exit 0
fi

invite_token="$(python3 - "$invite_url" <<'PY'
import sys
from urllib.parse import parse_qs, urlparse
url = sys.argv[1]
token = parse_qs(urlparse(url).query).get("token", [None])[0]
if not token:
    raise SystemExit(1)
print(token)
PY
)"

echo "==> Auto-accepting owner invite and setting password"
accept_payload_file="$(mktemp)"
accept_status="$(
  curl -sS -o "$accept_payload_file" -w "%{http_code}" \
    -X POST "$TEAMHUB_API_BASE_URL/api/v1/invites/accept" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"$invite_token\",\"password\":\"$ADMIN_PASSWORD\"}"
)"
if [[ "$accept_status" != "200" ]]; then
  echo "Invite accept failed (HTTP $accept_status)." >&2
  echo "Response:" >&2
  cat "$accept_payload_file" >&2
  exit 1
fi

cat <<EOF

Bootstrap complete (password mode).
- Tenant ID: $tenant_id
- Owner email: $ADMIN_EMAIL
- Owner password: $ADMIN_PASSWORD
- Internal console URL: http://localhost:5173/__internal/login
- Tenant login URL: http://localhost:5173/login

You can now log in as tenant Admin and continue the smoke flow.
EOF
