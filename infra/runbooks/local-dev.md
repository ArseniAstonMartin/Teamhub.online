# Local development runbook

This runbook gives a one-command path to bootstrap a local tenant and then preview the primary web and mobile MVP flows.

## 1) Start the full local stack

From repository root:

```bash
docker compose -f infra/docker-compose.dev.yml up -d
```

Start backend:

```bash
cd backend
./gradlew bootRun
```

Start web:

```bash
cd web
npm ci
npm run dev
```

Start mobile (Expo):

```bash
cd mobile
npm install
npx expo start --tunnel
```

## 2) Staff internal console access (TASK-131 / PRD §6.4)

- Internal console URL (web UI): `http://localhost:5173/__internal/login`
- Internal API base: `http://localhost:8080/api/v1/internal/**`
- Internal routes are IP allow-listed to loopback by default (`127.0.0.1`, `::1`).

### Root staff credentials (local only)

| Field | Value |
| --- | --- |
| Email | `dev@teamhub.online` |
| Password | `TeamhubDev131!` |

Manual API login check:

```bash
curl -sS -X POST http://localhost:8080/api/v1/internal/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev@teamhub.online","password":"TeamhubDev131!"}'
```

Use the returned `accessToken` as `Authorization: Bearer <token>` for `/api/v1/internal/**` calls.

## 3) Bootstrap a sample tenant (one command)

Run:

```bash
infra/scripts/bootstrap-dev-tenant.sh
```

Default behavior (`BOOTSTRAP_MODE=password`) provisions a tenant through internal API + plumber preset, fetches the owner invite from MailHog, auto-accepts the invite, and prints tenant Admin credentials for immediate login.

Optional invite-only mode:

```bash
BOOTSTRAP_MODE=invite infra/scripts/bootstrap-dev-tenant.sh
```

In invite-only mode, the script prints the accept-invite URL from MailHog instead of setting the password automatically.

Useful overrides:

```bash
TENANT_NAME="Dev Plumbing QA" \
ADMIN_EMAIL="qa-admin@teamhub.local" \
ADMIN_PASSWORD="TeamhubQa133!" \
PRESET_KEY="plumber" \
infra/scripts/bootstrap-dev-tenant.sh
```

## 4) Smoke path (web + mobile main flows)

Follow this exact order:

1. Staff provision
   - Log in at `http://localhost:5173/__internal/login` as `dev@teamhub.online`.
   - Run `infra/scripts/bootstrap-dev-tenant.sh` or create tenant manually from internal tenants page.
2. Admin login
   - Open `http://localhost:5173/login`.
   - Sign in with the tenant Admin credentials printed by bootstrap.
3. Create customer
   - In web app, open Customers and create one customer record.
4. Create job
   - Open Schedule or Jobs, create a job for that customer.
5. Kanban claim preparation
   - Move/create a job in `Submitted` so it appears in shared pool (Kanban).
6. Mobile technician login
   - Invite a technician from web Settings -> Users.
   - Accept the invite from MailHog (`http://localhost:8025`), set password.
   - Sign in on mobile app with that technician account.
   - Confirm Home screen shows shared-pool jobs and allows claim flow.

## 5) Troubleshooting

- No invite visible: open `http://localhost:8025`, confirm backend is running and SMTP host/port are default (`localhost:1025`).
- Internal API 404 from scripts/UI: request likely not from an allow-listed IP (expected behavior).
- Internal API 401: check that TEAMHUB_STAFF token is used for `/api/v1/internal/**`.

**TO-DO:** Rotate `dev@teamhub.online` credentials before any production deployment (PRD §6.4).
