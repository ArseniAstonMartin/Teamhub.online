# Teamhub Coolify Deployment Runbook (DigitalOcean)

This runbook defines the production deployment baseline for Teamhub on a single DigitalOcean Droplet managed by Coolify.

`Deprecated:` the previous Hetzner-specific version of this runbook is replaced by this DigitalOcean-first variant.

## 1) Scope for current rollout

- Provider: DigitalOcean.
- Placement: backend + observability run in the same Droplet and same Coolify project.
- Observability stack: Prometheus + Grafana + Loki + promtail.
- Prometheus scrape target: `http://teamhub-backend:8080/actuator/prometheus`.
- Log persistence: backend logs are written to a named Docker volume so logs survive container restart/redeploy.

## 2) Deployment topology (single Droplet)

- `teamhub-backend` service (Spring Boot, private project network).
- `teamhub-observability` stack from `infra/coolify/observability/docker-compose.yaml`.
- Public ingress:
  - `api.teamhub.online` -> backend via Coolify TLS.
  - `grafana.teamhub.online` -> Grafana via Coolify TLS + IP allow-list middleware.
- Stateful data persisted through Docker named volumes on the Droplet.

## 3) Observability config files in this repo

- Stack file: `infra/coolify/observability/docker-compose.yaml`
- Prometheus config: generated at container startup from `PROMETHEUS_ACTUATOR_USERNAME` / `PROMETHEUS_ACTUATOR_PASSWORD`.
- Loki config: `infra/coolify/observability/loki/loki-config.yaml`
- promtail config: `infra/coolify/observability/promtail/promtail-config.yaml`
- Grafana provisioning:
  - `infra/coolify/observability/grafana/provisioning/datasources/datasources.yaml`
  - `infra/coolify/observability/grafana/provisioning/dashboards/dashboards.yaml`
  - `infra/coolify/observability/grafana/dashboards/teamhub-overview.json`

## 4) Coolify environment variables (no secrets in git)

### 4.1 Backend service (`teamhub-backend`)

Required for Prometheus scrape auth and persistent logs:

- `ACTUATOR_USERNAME`
- `ACTUATOR_PASSWORD`
- `LOGGING_FILE_NAME=/var/log/teamhub/backend/application.log`

`TO-DO(PRD §26.2):` in Coolify, add a persistent volume to backend:

- Volume name: `teamhub_backend_logs`
- Mount path: `/var/log/teamhub/backend`

### 4.2 Observability stack (`teamhub-observability`)

Required:

- `PROMETHEUS_ACTUATOR_USERNAME` (must equal backend `ACTUATOR_USERNAME`)
- `PROMETHEUS_ACTUATOR_PASSWORD` (must equal backend `ACTUATOR_PASSWORD`)
- `GRAFANA_DOMAIN` (for example `grafana.teamhub.online`)
- `TO-DO(PRD §26.2): GRAFANA_ALLOWED_IPS` (comma-separated CIDRs, example `203.0.113.10/32,198.51.100.7/32`)
- `GRAFANA_ADMIN_USER`
- `GRAFANA_ADMIN_PASSWORD`

## 5) Grafana IP restriction (access layer requirement)

Grafana is restricted at the Coolify/Traefik ingress layer using an IP allow-list middleware in the compose labels.

Operational guidance:

1. Set `GRAFANA_ALLOWED_IPS` in Coolify with explicit CIDRs.
2. Redeploy the observability stack.
3. Verify:
   - Allowed IP can open `https://grafana.teamhub.online/login`.
   - Non-allowed IP receives `403 Forbidden`.

`TO-DO(PRD §26.2):` final allowed office/VPN/public egress IP list must be approved by operators before production cutover.

## 6) Rollout steps (minimal)

1. Deploy/update backend service with:
   - `ACTUATOR_USERNAME`
   - `ACTUATOR_PASSWORD`
   - `LOGGING_FILE_NAME`
   - volume `teamhub_backend_logs` mounted at `/var/log/teamhub/backend`
2. In the same Coolify project, create Docker Compose resource from:
   - `infra/coolify/observability/docker-compose.yaml`
3. Set observability env vars listed in section 4.2.
4. Attach domain `grafana.teamhub.online` to Grafana service.
5. Redeploy observability stack.

## 7) Verification checklist

Backend metrics:

- From Prometheus container:
  - `wget -qO- --user="$PROMETHEUS_ACTUATOR_USERNAME" --password="$PROMETHEUS_ACTUATOR_PASSWORD" http://teamhub-backend:8080/actuator/prometheus | head -n 5`
- In Prometheus UI (`/targets`): target `teamhub-backend` is `UP`.

Persistent logs:

- Restart backend container and confirm `/var/log/teamhub/backend/application.log` still exists.
- Redeploy backend and confirm log file remains in `teamhub_backend_logs` volume.

Dashboards:

- Open Grafana, confirm the provisioned `Teamhub Backend Overview` dashboard loads.
- Validate panels render (`up`, JVM memory, request rate).

## 8) Backup and security notes

- Keep Coolify/database/redis secrets out of git.
- Restrict SSH and any non-public ports at the DigitalOcean Droplet firewall.
- Keep Grafana behind IP allow-list (section 5).
- `TO-DO(PRD §26.2):` select final backup target (DigitalOcean Spaces or other S3-compatible storage) and wire retention/restore drill process.
