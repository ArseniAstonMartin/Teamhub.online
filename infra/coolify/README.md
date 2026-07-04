# Teamhub Coolify Deployment Runbook (Hetzner Hillsboro)

This document defines the MVP deployment topology for Teamhub on Hetzner VMs managed by Coolify, aligned with PRD §21.4 and backup requirements in PRD §24.3.

## 1) Topology at a glance

- Region: Hetzner US (Hillsboro).
- Deployment orchestrator: Coolify.
- Runtime: Docker images per service.
- TLS: Let's Encrypt certificates managed by Coolify.

## 2) VM and service placement

Use separate VMs for application and stateful services to reduce blast radius and make rollback/recovery simpler.

| VM | Primary responsibility | Services on VM |
| --- | --- | --- |
| `vm-app-01` | Stateless application workloads | `backend` (Spring Boot), `web` (SPA static host), optional `landing` site |
| `vm-data-01` | Stateful data plane | PostgreSQL 16 + PostGIS, Redis 7 |
| `vm-edge-01` | North-south traffic entry | Reverse proxy (Coolify-managed Caddy/Traefik), TLS termination, routing to app services |

Notes:
- Keep PostgreSQL and Redis private (no public internet exposure).
- Only `vm-edge-01` should expose 80/443 to the internet.
- Inter-VM traffic should be restricted to required ports only (internal network / firewall allow-list).

## 3) Networking and DNS

Minimum DNS records:
- `app.teamhub.online` -> public IP of `vm-edge-01`
- `api.teamhub.online` -> public IP of `vm-edge-01`
- `teamhub.online` -> public IP of `vm-edge-01` (when marketing/landing is enabled)

Minimum firewall posture:
- Public inbound on `vm-edge-01`: TCP 80, 443.
- Private inbound on `vm-app-01`: app ports from `vm-edge-01` only.
- Private inbound on `vm-data-01`: PostgreSQL and Redis ports from `vm-app-01` only.
- SSH access should be restricted to operator/admin IPs.

## 4) Coolify deployment wiring

### 4.1 Git + webhook trigger

Coolify should redeploy from the selected branch/tag on webhook events.

- Coolify webhook URL:
  - `TO-DO(PRD §26.2): create Coolify app and paste deploy webhook URL here`
  - Example placeholder: `https://coolify.example.com/api/v1/deploy/webhook/<project-token>`
- GitHub Actions or repository webhook should call this URL after image publish or after merge policy passes.

### 4.2 Required environment variables

Do not commit real values. All values must be provisioned in Coolify/secret storage.

Core application:
- `SPRING_PROFILES_ACTIVE`
- `SERVER_PORT`
- `APP_BASE_URL`
- `WEB_BASE_URL`

Database:
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`

Redis:
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`

JWT / auth:
- `JWT_PRIVATE_KEY_PEM`
- `JWT_PUBLIC_KEY_PEM`
- `JWT_KEY_ID`

Object storage (MinIO for local/dev/MVP-prod; R2 for sales-prod per PRD §21.4):
- `S3_ENDPOINT`
- `S3_REGION`
- `S3_BUCKET`
- `S3_ACCESS_KEY_ID`
- `S3_SECRET_ACCESS_KEY`

Observability:
- `SENTRY_DSN`
- `OTEL_SERVICE_NAME` (if used)

Integrations (provisioning tracked by PRD §26.2):
- `TO-DO(PRD §26.2): STRIPE_SECRET_KEY`
- `TO-DO(PRD §26.2): STRIPE_WEBHOOK_SECRET`
- `TO-DO(PRD §26.2): GOOGLE_MAPS_API_KEY`
- `TO-DO(PRD §26.2): EXPO_ACCESS_TOKEN`
- `TO-DO(PRD §26.2): HETZNER_API_TOKEN`

## 5) TLS via Let's Encrypt

1. Point DNS records to `vm-edge-01`.
2. In Coolify, enable HTTPS/SSL for each public domain.
3. Confirm certificate issuance and auto-renew is enabled.
4. Validate:
   - `https://app.teamhub.online/health` (or equivalent web endpoint)
   - `https://api.teamhub.online/actuator/health`

If issuance fails:
- Verify DNS propagation and A records.
- Ensure 80/443 are reachable publicly on `vm-edge-01`.
- Retry certificate issuance in Coolify after DNS fix.

## 6) Rollback procedure (< 5 minutes target)

Goal: redeploy previous known-good Docker image tag quickly after a bad release.

Preconditions:
- Every deployment uses immutable image tags (e.g. commit SHA).
- The previous stable tag is recorded in release notes / deployment history.

Steps:
1. Open Coolify project -> affected service (`backend`, `web`, or both).
2. Select previous known-good image tag from deployment history.
3. Trigger redeploy for that tag.
4. Watch logs until health checks are green.
5. Validate external endpoints:
   - `GET /actuator/health` for backend.
   - Web app load + critical page smoke test.
6. Announce rollback completion and capture incident notes.

Expected timing:
- Tag selection and redeploy trigger: ~1 minute.
- Container restart + health checks: ~2-3 minutes.
- Smoke validation + communication: ~1 minute.

## 7) Backup and retention procedure

PRD §24.3 requirements:
- Daily PostgreSQL backups retained 30 days.
- Weekly backups retained 90 days.
- Restore drill at least once per quarter.

Recommended operational flow:
1. Nightly full PostgreSQL dump from `vm-data-01` to encrypted object storage.
2. Retention policy:
   - Keep daily snapshots for 30 days.
   - Promote one snapshot per week and retain for 90 days.
3. Verify backup job success daily (alert on failure).
4. Quarterly restore drill:
   - Restore latest backup into isolated restore environment.
   - Run schema and row-count sanity checks.
   - Record restore duration and any issues.

`TO-DO(PRD §26.2): choose final backup target/provider account (Hetzner storage box, S3-compatible bucket, or equivalent) and wire Coolify/cron credentials.`

## 8) Security and credential handling

- Never commit credentials, tokens, private keys, or webhook secrets.
- Keep all secrets in Coolify environment management or approved secrets manager.
- Rotate leaked/compromised secrets immediately.
- Restrict database and Redis to private networking only.

## 9) Operator checklist before first production deploy

- `TO-DO(PRD §26.2): Hetzner Cloud project provisioned in Hillsboro`.
- `TO-DO(PRD §26.2): Coolify installed and reachable by operators`.
- `TO-DO(PRD §26.2): GitHub Actions secrets configured for deploy pipeline`.
- `TO-DO(PRD §26.2): Stripe, Google, Expo, Sentry, Cloudflare R2 accounts provisioned`.
- DNS records and TLS validated.
- Rollback dry-run completed once with a non-production release.
