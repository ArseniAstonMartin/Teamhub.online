# Teamhub.online Monorepo

This repository hosts the Teamhub.online product monorepo described in `prd/PRD.md`.
The PRD is the source of truth for product, architecture, and delivery decisions.

Note: `prd/` is a local-only working directory and is not intended to be committed.

## Repository structure

- `backend/` — Spring Boot backend services, data access, security, and migrations.
- `web/` — React SPA for Admin and Dispatcher workflows.
- `mobile/` — Expo React Native application for mobile field workflows.
- `infra/` — local infrastructure, container orchestration, and deployment-related assets.
- `.github/workflows/` — CI automation for backend, web, and mobile pipelines.
- `prd/` — local PRD and source notes (not committed; see `.gitignore`).

## Local-only paths (not committed)

Agent tooling, task tracking, and design assets stay on disk only: `AGENTS.md`, `tasks.json`, `progress.md`, `.cursor/`, `prd/`, `resources/`.

## Source of truth

- Product requirements: `prd/PRD.md`
- Monorepo layout reference: PRD §21.7

## Local preview

For a one-command tenant bootstrap and full local smoke flow across internal console, web, and mobile, follow:

- `infra/runbooks/local-dev.md`
