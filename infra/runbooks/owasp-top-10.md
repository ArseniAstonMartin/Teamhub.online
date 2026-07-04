# OWASP Top 10 Pre-Launch Checklist

This runbook maps OWASP Top 10 (2021) to Teamhub MVP controls and verification commands (PRD §24.4).

## How to use before launch

1. Run each verification command in `dev` and `staging`.
2. Mark each checklist item as done only after evidence (logs, screenshots, CI links) is attached to the release ticket.
3. Any failed check blocks launch.

## A01: Broken Access Control

- [ ] **Project mitigation:** Spring Security method guards (`@PreAuthorize`) + tenant context + PostgreSQL RLS enforce tenant and role boundaries.
- [ ] **Verification command:** `cd backend && ./gradlew test --tests '*SecurityConfigIT' --tests '*Rls*' --tests '*Tenant*'`
- [ ] **Evidence to save:** CI test report showing cross-tenant/cross-role 403 coverage.

## A02: Cryptographic Failures

- [ ] **Project mitigation:** Password hashes use BCrypt, JWT is RS256, TLS terminates at Coolify reverse proxy.
- [ ] **Verification command:** `cd backend && ./gradlew test --tests '*Password*' --tests '*Jwt*'`
- [ ] **Evidence to save:** Passing auth crypto tests + deployment screenshot proving HTTPS endpoint.

## A03: Injection

- [ ] **Project mitigation:** JPA parameterized queries + Bean Validation on request DTOs + no string-built SQL in business paths.
- [ ] **Verification command:** `cd backend && ./gradlew test --tests '*IT' --tests '*Repository*'`
- [ ] **Evidence to save:** Integration test report for customer/job search and mutation endpoints.

## A04: Insecure Design

- [ ] **Project mitigation:** Threat-aware defaults in PRD (§24.4) plus mandatory code review and task-level acceptance criteria.
- [ ] **Verification command:** `rg "TO-DO\\(PRD §24\\.4\\)|TODO\\(PRD §24\\.4\\)" backend web mobile infra`
- [ ] **Evidence to save:** No unresolved security-critical TO-DO markers (or explicit risk sign-off).

## A05: Security Misconfiguration

- [ ] **Project mitigation:** Security headers (CSP, HSTS, X-Content-Type-Options, Referrer-Policy, X-Frame-Options), strict session cookie flags, CORS allowlist.
- [ ] **Verification command:** `curl -k -I https://app.teamhub.online/api/v1/openapi.json`
- [ ] **Expected headers:** `Content-Security-Policy`, `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer-when-downgrade`, `X-Frame-Options: DENY`.

## A06: Vulnerable and Outdated Components

- [ ] **Project mitigation:** Dependabot-style update cadence + CI dependency checks + pinned runtime baselines (Java 21, Spring Boot 3.2+).
- [ ] **Verification command:** `cd backend && ./gradlew dependencyUpdates`
- [ ] **Evidence to save:** Report showing no known critical dependency vulnerabilities pending.

## A07: Identification and Authentication Failures

- [ ] **Project mitigation:** Short-lived access tokens, rotating refresh tokens, server-side refresh revocation, rate limits on auth endpoints.
- [ ] **Verification command:** `cd backend && ./gradlew test --tests '*Auth*' --tests '*Refresh*' --tests '*RateLimit*'`
- [ ] **Evidence to save:** Auth lifecycle tests proving login/refresh/logout/revoke behavior.

## A08: Software and Data Integrity Failures

- [ ] **Project mitigation:** Signed/verified CI artifacts, branch protection, and webhook signature verification for Stripe callbacks.
- [ ] **Verification command:** `cd backend && ./gradlew test --tests '*BillingWebhook*'`
- [ ] **Evidence to save:** Test output confirming invalid webhook signature rejection.

## A09: Security Logging and Monitoring Failures

- [ ] **Project mitigation:** Structured logs with tenant/request context, Sentry integration, alerting for event/DLQ failures.
- [ ] **Verification command:** `cd backend && ./gradlew test --tests '*Sentry*' --tests '*Outbox*'`
- [ ] **Evidence to save:** Alert rule screenshot + sample structured security event.

## A10: Server-Side Request Forgery (SSRF)

- [ ] **Project mitigation:** Outbound calls restricted to approved integrations (Stripe, Google, Expo, storage), no arbitrary user-provided fetch targets.
- [ ] **Verification command:** `rg "RestTemplate|WebClient|HttpClient|OkHttp" backend/src/main/java`
- [ ] **Evidence to save:** Reviewed call-sites list with explicit allowlisted targets.

## Final release gate

- [ ] Lead Developer has reviewed all 10 categories and attached evidence.
- [ ] Any accepted residual risk is documented in the release notes with owner and follow-up task.
