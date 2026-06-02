# Teamhub Backend

Spring Boot 3.2+ REST API for the Teamhub field-service management platform.

## Tech Stack

- **Java 21** (virtual threads enabled)
- **Spring Boot 3.2+** — Web, Security, Data JPA, Actuator, Validation
- **PostgreSQL** — primary database with Flyway migrations
- **Redis** — caching and session storage
- **Gradle (Kotlin DSL)** — build tool with dependency locking
- **MapStruct** — compile-time DTO mapping
- **SpringDoc / OpenAPI 3** — auto-generated API docs at `/swagger-ui.html`
- **Sentry** — error monitoring and release tracking (PRD §26.2)
- **Testcontainers** — integration tests against real PostgreSQL

## Local Setup

### Prerequisites

- JDK 21 (`sdk use java 21` via SDKMAN, or Homebrew `brew install openjdk@21`)
- Docker Desktop (needed by Testcontainers and optional local Postgres/Redis)
- `psql` client (optional, for inspecting the DB)

### 1 — Copy and fill environment variables

```bash
cp .env.example .env
# Open .env and fill in the values described in each comment.
# At minimum, set DB_URL, DB_USER, DB_PASSWORD, and REDIS_URL.
# Leave JWT_PRIVATE_KEY_PATH / JWT_PUBLIC_KEY_PATH blank for local dev
# (an ephemeral RS256 key pair is generated at startup).
```

Variables are loaded automatically by Spring Boot from the `.env` file when
running with `bootRun`. For tests, Testcontainers spins up its own containers
so no external DB or Redis is required.

### 2 — Start the app

```bash
./gradlew bootRun
# Health check:
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

### 3 — API docs

Open `http://localhost:8080/swagger-ui.html` in your browser.

### 4 — Run the full test suite

```bash
./gradlew clean test
# Coverage report at build/reports/jacoco/test/html/index.html
```

### 5 — Linting and formatting

```bash
./gradlew spotlessCheck checkstyleMain  # check
./gradlew spotlessApply                 # auto-fix formatting
```

## Environment Variables

| Variable | Description | Default (dev) |
|----------|-------------|---------------|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/teamhub` |
| `DB_USER` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | `postgres` |
| `REDIS_URL` | Redis connection URL | `redis://localhost:6379` |
| `JWT_PRIVATE_KEY_PATH` | RS256 private key PEM path | _(ephemeral)_ |
| `JWT_PUBLIC_KEY_PATH` | RS256 public key PEM path | _(ephemeral)_ |
| `GOOGLE_OAUTH_CLIENT_ID` | Google OAuth 2.0 client ID | — |
| `GOOGLE_OAUTH_CLIENT_SECRET` | Google OAuth 2.0 client secret | — |
| `APPLE_TEAM_ID` | Apple Developer Team ID | — |
| `APPLE_KEY_ID` | Apple Sign-In key ID | — |
| `APPLE_PRIVATE_KEY_PATH` | Path to Apple `.p8` private key | — |
| `STRIPE_SECRET_KEY` | Stripe secret API key | — |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret | — |
| `OBJECT_STORAGE_ENDPOINT` | S3-compatible endpoint URL | — |
| `OBJECT_STORAGE_BUCKET` | Object storage bucket name | — |
| `OBJECT_STORAGE_REGION` | Object storage region | — |
| `OBJECT_STORAGE_ACCESS_KEY_ID` | Object storage access key | — |
| `OBJECT_STORAGE_SECRET_ACCESS_KEY` | Object storage secret key | — |
| `SENTRY_DSN` | Sentry DSN for error monitoring | — |
| `GOOGLE_MAPS_API_KEY` | Google Maps Geocoding API key | — |

See `.env.example` for the full list with inline documentation.
> **TO-DO:** Provision Sentry, Stripe, Google, Apple, and storage accounts per PRD §26.2 before deploying.

## Package Layout

```
com.teamhub/
├── auth/         # Authentication & OAuth flows
├── tenancy/      # Multi-tenant isolation
├── jobs/         # Job / work-order management
├── customers/    # Customer & contact management
├── schemas/      # Dynamic form schemas
├── billing/      # Stripe billing integration
├── eventing/     # Domain event publishing
├── audit/        # Audit log
└── shared/       # Cross-cutting concerns (security, config, utils)
```

## Database Migrations

Flyway runs automatically at startup. Migration scripts live in
`src/main/resources/db/migration/`. Naming convention: `V<version>__<description>.sql`.

## Local Development — Object Storage

Teamhub uses [MinIO](https://min.io/) as an S3-compatible object store for local development
(photo uploads, multipart uploads — PRD TASK-049/050).

### Start MinIO

```bash
# From the repo root — starts MinIO and creates the `teamhub` bucket automatically.
docker compose -f infra/docker-compose.dev.yml up -d minio minio-init
```

| Service | URL | Credentials |
|---------|-----|-------------|
| S3 API endpoint | http://localhost:9000 | — |
| MinIO Console UI | http://localhost:9001 | `minioadmin` / `minioadmin` |

### Environment variables

| Variable | Description | Dev default |
|----------|-------------|-------------|
| `MINIO_ENDPOINT` | S3-compatible endpoint URL | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` | Access key ID | `minioadmin` |
| `MINIO_SECRET_KEY` | Secret access key | `minioadmin` |
| `MINIO_BUCKET` | Bucket name | `teamhub` |
| `MINIO_REGION` | AWS region identifier | `us-east-1` |

> **Production:** override all five vars with real credentials (AWS S3, Cloudflare R2, etc.).
> Do **not** commit production keys; use environment-specific secrets management (PRD §26.2).
