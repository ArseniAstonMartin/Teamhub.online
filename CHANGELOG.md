# Changelog

All notable changes to Teamhub are documented in this file.

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

