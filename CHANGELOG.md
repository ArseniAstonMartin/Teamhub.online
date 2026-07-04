# Changelog

All notable changes to Teamhub are documented in this file.

## [v1.0.0] - 2026-07-04

MVP release milestone aligned with PRD §4.2.

### Added
- Tenant-facing MVP capabilities across schema customization, automations, shared-pool claim and escalation flow, Stripe billing integration, audit logging, push notifications, MinIO-backed photo upload path, and internal admin onboarding console.
- Consolidated operational runbooks for deployment, local bootstrap, and security hardening required for release readiness.

### Verification Notes
- CI and human walkthrough requirements still require Lead Developer execution/sign-off on the target release commit per PRD §25.2.
- `tasks.json` prerequisite check for TASK-122 identifies remaining non-deferred pending items (`TASK-022`, `TASK-104`, `TASK-105`, `TASK-106`, `TASK-109`, `TASK-110`, `TASK-111`, `TASK-112`, `TASK-113`) that must be resolved for strict AC-1 compliance.

## [v0.1.0] - 2026-07-04

Phase 0 internal foundation milestone aligned with PRD §4.1.

### Added
- Multi-tenant backend baseline with PostgreSQL RLS tenant isolation and JWT authentication.
- Job CRUD on the preset schema, including state transitions required for internal end-to-end flow.
- Web SPA foundation with login, jobs list, and calendar view.
- Mobile app foundation with login and jobs list for technicians.
- Hetzner + Coolify deployment runbook and local dev/bootstrap runbooks for repeatable operator setup.

### Verification Notes
- Dependency status check confirms all required Phase 0 tasks are marked `done` in `tasks.json`.
- Human verification remains required for dev-environment smoke path (`/login` sign-in, jobs list, calendar) and mobile device walkthrough per PRD §25.2.

