# Local development runbook

## Staff internal console (TASK-131)

The internal admin API is available at `http://localhost:8080/api/v1/internal/**` and is IP-restricted to loopback by default (`127.0.0.1`, `::1`).

### Root staff credentials (local only)

| Field    | Value              |
|----------|--------------------|
| Email    | `dev@teamhub.online` |
| Password | `TeamhubDev131!`   |

Obtain a staff JWT:

```bash
curl -s -X POST http://localhost:8080/api/v1/internal/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"dev@teamhub.online","password":"TeamhubDev131!"}'
```

Use the returned `accessToken` as `Authorization: Bearer <token>` on other `/api/v1/internal/**` routes.

**TO-DO:** Rotate `dev@teamhub.online` credentials before any production deployment (PRD §6.4).
