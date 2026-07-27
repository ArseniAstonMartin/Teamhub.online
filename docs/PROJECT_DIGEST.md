# Teamhub.online — выжимка проекта

Краткий обзор продукта, стека и практического опыта по итогам MVP (`v1.0.0`). Источники: `prd/PRD.md`, `tasks.json` (134 задачи, ~123 done), `infra/coolify/`, `CHANGELOG.md`.

---

## Что это

**Teamhub** — multi-tenant SaaS для field service (сантехники, электрики, HVAC, locksmith, roofing, solar и т.п.): офис создаёт и диспетчеризует jobs, техники забирают их из shared pool в мобильном приложении.

**Дифференциатор:** глубокая кастомизация схемы appointment per tenant (custom fields, dependent dropdowns) + end-to-end operational discipline (статусная машина, claim race, escalation).

**Поверхности:**
- Web SPA (`app.teamhub.online`) — Admin / Dispatcher
- Mobile (Expo RN, iOS + Android) — Technician
- Internal staff console — white-glove provisioning tenants
- Marketing landing — `teamhub.online`

**Релиз:** Phase 0 (`v0.1.0`) → MVP (`v1.0.0`).

---

## Чему научился / с чем поработал

### Продукт и домен FSM/CRM
- Multi-tenant модель: shared DB + `tenant_id` + PostgreSQL **RLS**
- Роли Admin ⊇ Dispatcher ⊇ Technician, permissions matrix, authz-тесты в CI
- Job lifecycle (Submitted → Assigned → In Progress → … → Completed/Canceled)
- Shared-pool claim с optimistic locking и 5-min escalation push
- Per-tenant schema editor + 8 industry presets
- Automations (trigger → auto-fill)
- CRM-lite customers, calendar/kanban, reports
- Stripe Billing (Free / Starter / Team / Enterprise) + quotas + webhooks

### Backend
- Java 21, Spring Boot 3, Spring Security, JPA/Hibernate, Flyway
- JWT RS256 (access + refresh revocation), Google OAuth, BCrypt
- PostGIS geography, Redis (cache + Streams + delayed jobs)
- Transactional Outbox → Redis Streams consumers (без Kafka на MVP)
- MinIO / pre-signed photo uploads, Expo Push
- Actuator + Micrometer → Prometheus metrics
- Structured JSON logs (tenant_id, user_id, request_id)
- Testcontainers, JUnit, Spotless/Checkstyle, Sonar, OpenAPI

### Frontend web
- React 18, TypeScript, Vite, Feature-Sliced Design
- TanStack Query, Zustand, React Router, RHF + Zod
- shadcn/ui + Tailwind, React Big Calendar, Playwright/Vitest

### Mobile
- Expo + React Native, SecureStore, Notifications, Camera/ImagePicker
- EAS Build profiles, offline queues for notes/photos

### DevOps / production (то, что реально доводил руками)
- Деплой на **DigitalOcean Droplet** через **Coolify** (Docker, TLS/Let’s Encrypt, Traefik)
- Backend на `api.teamhub.online`, Grafana на `grafana.teamhub.online`
- Observability на том же droplet: **Prometheus + Grafana + Loki + Promtail**
  - scrape `/actuator/prometheus` (Basic auth)
  - backend logs в named volume → Promtail → Loki
  - provisioned dashboard *Teamhub Backend Overview*
  - crash/redeploy log persistence для observability-контейнеров
- GitHub Actions CI (backend / web / mobile), secret hygiene, OWASP runbook
- Локальный стек: Postgres+PostGIS, Redis, MailHog, bootstrap tenant script

### Интеграции
- Stripe Checkout / Customer Portal / webhooks / reconciliation
- Google Maps Geocoding + Places
- Sentry (backend / web / mobile)
- Expo Push Service
- Object storage: MinIO (MVP) → Cloudflare R2 (sales-prod path)

---

## Архитектура одной картинкой

```
Web SPA ──┐
Mobile ───┼──► Spring Boot API ──► PostgreSQL (+ RLS, PostGIS)
Staff ────┘         │              Redis (cache / Streams / ZSET)
                    ├── MinIO (photos)
                    ├── Stripe / Expo / Google / Sentry
                    └── Actuator metrics + JSON logs
                              │
              Coolify on DigitalOcean Droplet
                              │
              Prometheus ◄── metrics
              Promtail ──► Loki ◄── logs
              Grafana (dashboards, IP allow-list planned)
```

---

## Объём работы по tasks.json

| Категория        | Кол-во | Фокус |
|------------------|--------|--------|
| functional       | 41     | tenants, jobs, schema, automations, invites, reports, mobile flows |
| infrastructure   | 33     | monorepo, CI, eventing, Coolify/observability, releases |
| ui               | 33     | web pages + mobile screens |
| security         | 15     | RLS, JWT, OAuth, rate limits, staff API, CCPA |
| integration      | 12     | Stripe, Maps, MinIO, Expo Push, OpenAPI |

**Статус на момент выжимки:** ~123 done / 9 pending / 2 deferred.

Оставшееся (не блокирует «что уже умею», но честно по трекеру): Apple Sign-in, backups/restore drill, load-test claim, WCAG polish, часть eventing/crash-survival tests, финальная закрывалка TASK-104 в трекере (стек observability в `infra/coolify/observability/` уже лежит и деплоился на DO).

---

## Ключевые инженерные решения

1. **RLS как последний рубеж изоляции** — баг в app-коде не должен утечь в чужой tenant.
2. **Outbox + Redis Streams вместо Kafka** — масштаб MVP (~30 events/sec peak estimate) не оправдывает брокер.
3. **Schema as data (`jsonb` + versioned definitions)** — кастомизация без редеплоя.
4. **Optimistic locking на claim** — корректный winner при гонке техников.
5. **Coolify + single Droplet** — быстрый path от monorepo до prod с observability рядом с API.

---

## Для резюме / портфолио (коротко)

Спроектировал и довёл до MVP multi-tenant FSM/CRM SaaS: Spring Boot + Postgres RLS + React/Expo monorepo, eventing на outbox/Redis, Stripe billing, shared-pool dispatch. Задеплоил на DigitalOcean Droplet через Coolify, поднял Grafana/Loki/Prometheus для метрик и логов в проде.
