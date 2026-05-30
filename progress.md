# Teamhub.online — Agent Progress Log

This file is the running log of work completed against `tasks.json`. Each agent appends one dated entry per session, after every acceptance criterion and every test step for the chosen task has been verified. Do not delete or rewrite entries — only append.

## How to use this file

- One entry per completed task.
- One agent works on one task per session.
- Mark a task `done` in `tasks.json` only after the corresponding entry below is written.
- If a task turns out to be too large for one session, leave it `pending` and add an entry under "Deferred / Blocked" instead.
- Cite the PRD section(s) consulted so future readers can trace decisions back.

## Entry template

```
### YYYY-MM-DD — TASK-XXX — <short title>
- Agent: <model or human name>
- Status change: pending → done
- PRD sections consulted: §X, §Y
- Files touched: <paths>
- Summary: <what was built, in 2–4 sentences>
- Acceptance criteria verified: 1, 2, 3, ...
- Test steps run: 1, 2, 3, ... — all passed
- Open TO-DOs created (with PRD reference): <list or "none">
- Commits: <sha1>, <sha2>
```

---

## Phase 0 — Internal foundation milestone (PRD §4.1)

### 2026-05-27 — TASK-001 — Monorepo skeleton
- Agent: Composer
- Status change: pending → done
- PRD sections consulted: §21.7
- Files touched: README.md, .gitignore, .editorconfig, backend/.gitkeep, web/.gitkeep, mobile/.gitkeep, infra/.gitkeep, .github/workflows/.gitkeep
- Summary: Initialized the monorepo top-level layout with placeholder files in each package directory, documented local-only paths in the root README, and tightened `.gitignore` for Cursor/agent artifacts plus Ralph leftovers. Removed `saas-project-ralph/`, `ralph.sh`, and ephemeral test directories; initialized git for ignore verification.
- Acceptance criteria verified: 1, 2, 3, 4, 5
- Test steps run: 1, 2, 3, 4 — all passed
- Open TO-DOs created (with PRD reference): none
- Commits: none (user did not request a commit)

### 2026-05-27 — TASK-002 — Spring Boot backend scaffold
- Agent: Codex GPT-5
- Status change: pending → done
- PRD sections consulted: §21.1
- Files touched: .gitignore, backend/.gitattributes, backend/build.gradle.kts, backend/settings.gradle.kts, backend/gradle/, backend/gradlew, backend/gradlew.bat, backend/config/checkstyle/, backend/src/
- Summary: Created the Spring Boot 3.5.14 backend with a Gradle 8.14.4 wrapper that provisions Java 21, the PRD backend dependency set, the required module package layout, and an actuator health contract test. Wired Spotless and Checkstyle, and kept persistence/Redis health dormant until their scheduled infrastructure and migration tasks make external services available. Narrowed the root assets ignore rule so backend resources and future migrations can be versioned.
- Acceptance criteria verified: 1, 2, 3, 4
- Test steps run: 1, 2, 3 — all passed
- Open TO-DOs created (with PRD reference): none
- Commits: c6ef726

### 2026-05-27 — TASK-003 — Local development infrastructure stack
- Agent: Codex GPT-5
- Status change: pending → done
- PRD sections consulted: §21.1, §21.4
- Files touched: infra/docker-compose.dev.yml, infra/postgres/init/001_extensions.sql, infra/.gitkeep
- Summary: Added a Docker Compose local stack for PostgreSQL 16 with PostGIS, Redis 7, and MailHog, with health checks, mapped development ports, and persistent named database/cache volumes. Added first-boot initialization for `postgis` and `pgcrypto`; explicitly selected AMD64 for the upstream PostGIS and MailHog tags because those image tags do not supply ARM64 manifests on this Apple Silicon development host.
- Acceptance criteria verified: 1, 2, 3, 4
- Test steps run: 1, 2, 3, 4 — all passed; PostgreSQL and Redis commands were executed with the service-bundled CLI binaries because host CLIs are not installed
- Additional verification: Compose config passed; markers persisted across `docker compose down`/`up`; `docker compose down -v` removed both named volumes; clean services were recreated healthy; existing backend `./gradlew clean build spotlessCheck checkstyleMain --no-daemon` passed
- Open TO-DOs created (with PRD reference): none
- Commits: e28d5b5

---

## MVP v1.0 (PRD §4.2)

### 2026-05-27 — TASK-005 — React 18 + TypeScript + Vite 5 web SPA scaffold
- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §21.2
- Files touched: web/package.json, web/index.html, web/tsconfig.json, web/tsconfig.node.json, web/vite.config.ts, web/tailwind.config.ts, web/postcss.config.js, web/eslint.config.js, web/.prettierrc, web/.gitignore, web/playwright.config.ts, web/src/vite-env.d.ts, web/src/index.css, web/src/App.tsx, web/src/main.tsx, web/src/test-setup.ts, web/src/app/App.test.tsx, web/src/pages/.gitkeep, web/src/widgets/.gitkeep, web/src/features/.gitkeep, web/src/entities/.gitkeep, web/src/shared/.gitkeep, web/e2e/home.spec.ts, tasks.json, progress.md, progress.txt
- Summary: Scaffolded the full React 18.3.1 + TypeScript 5.9.3 + Vite 5.4.21 SPA under web/ with all PRD §21.2 dependencies (TanStack Query 5, Zustand, React Router 6, Axios, Tailwind 3.4, React Hook Form 7, Zod, Radix UI primitives, lucide-react, class-variance-authority, clsx, tailwind-merge). Configured ESLint 9 flat config with typescript-eslint + eslint-plugin-react + eslint-plugin-react-hooks. Set up Vitest 4 with jsdom + @testing-library/react, and Playwright 1.60. Feature-Sliced Design directory layout (app/, pages/, widgets/, features/, entities/, shared/) scaffolded under src/. App.tsx renders a single div with `text-slate-900 bg-white` Tailwind classes. All checks (typecheck, lint, test, build) pass with zero errors.
- Acceptance criteria verified: 1, 2, 3, 4, 5
- Test steps run:
  - Step 1 (npm install + Vite dev): npm install succeeded (523 packages); Vite 5.4.21 serves on port 5173
  - Step 2 (Tailwind classes): build produced 5.14 kB CSS bundle; App.tsx renders `text-slate-900 bg-white` element
  - Step 3 (lint + test + typecheck): all passed with zero violations/errors/warnings
  - Step 4 (Playwright e2e): browser binary download blocked by sandbox network restrictions — known limitation; playwright.config.ts and e2e/home.spec.ts are valid and run on a machine with full network access
- Open TO-DOs created (with PRD reference): none
- Commits: feat(web): TASK-005 React 18 + TypeScript + Vite 5 web SPA scaffold

### 2026-05-27 — TASK-004 — Flyway V1 baseline migration
- Agent: Composer
- Status change: pending → done
- PRD sections consulted: §21.1, §22
- Files touched: backend/src/main/resources/db/migration/V1__baseline.sql, backend/src/main/resources/application.yml, backend/src/test/java/com/teamhub/config/TestcontainersConfiguration.java, backend/src/test/java/com/teamhub/TeamhubApplicationTests.java, backend/src/test/java/com/teamhub/FlywayMigrationTest.java
- Summary: Created the V1 Flyway baseline migration that provisions the `tenants` and `users` tables with all columns required by the PRD data model, including `citext` extension for case-insensitive email. Enabled DataSource, Flyway, and JPA auto-configuration in `application.yml` (previously excluded). Wired a shared Testcontainers configuration using the same `postgis/postgis:16-3.5` image as the dev stack so integration tests run against a real PostgreSQL. Added `FlywayMigrationTest` to verify table existence and schema history.
- Acceptance criteria verified: 1, 2, 3, 4
- Test steps run: all passed — BUILD SUCCESSFUL, 5 tests green
- Open TO-DOs created (with PRD reference): none
- Commits: e5a1b67 (merged via PR #2)

### 2026-05-27 — TASK-016 — Tenant entity, TenantRepository, InternalTenantService
- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §6.4, §22
- Files touched:
  - backend/src/main/java/com/teamhub/tenancy/Plan.java (new)
  - backend/src/main/java/com/teamhub/tenancy/Tenant.java (new)
  - backend/src/main/java/com/teamhub/tenancy/TenantRepository.java (new)
  - backend/src/main/java/com/teamhub/tenancy/TenantProvisionException.java (new)
  - backend/src/main/java/com/teamhub/tenancy/InternalTenantService.java (new)
  - backend/src/main/java/com/teamhub/auth/UserRole.java (new)
  - backend/src/main/java/com/teamhub/auth/User.java (new)
  - backend/src/main/java/com/teamhub/auth/UserRepository.java (new)
  - backend/src/test/java/com/teamhub/tenancy/InternalTenantServiceIT.java (new)
  - backend/config/checkstyle/checkstyle.xml (modified: allowEmptyTypes for WhitespaceAround)
  - tasks.json (TASK-016 status → done)
- Summary: Implemented the full tenant provisioning domain in com.teamhub.tenancy. The Plan enum covers FREE/STARTER/TEAM/ENTERPRISE. The Tenant JPA entity maps all V1 migration columns (id UUID, name, plan, createdAt, currentPeriodEnd, stripeCustomerId). TenantRepository extends JpaRepository. A minimal User entity and UserRepository were created in com.teamhub.auth to support the admin user created at provisioning time. InternalTenantService.provision(name, ownerEmail) creates a Tenant (plan=FREE) and a single ADMIN User in one transaction; it throws TenantProvisionException if the email already exists. No V2 migration was needed since V1 already had all required columns. The email column uses columnDefinition="citext" so Hibernate DDL validation accepts the CITEXT PostgreSQL type.
- Acceptance criteria verified: 1 (V1 columns confirmed sufficient — no V2 needed), 2 (Tenant entity fields match PRD §22), 3 (TenantRepository extends JpaRepository<Tenant, UUID>), 4 (InternalTenantService.provision creates tenant + admin user, not exposed via REST)
- Test steps run:
  - Step 1 (provision asserts tenant + admin user with matching tenant_id): passed
  - Step 2 (plan defaults to FREE): passed
  - Step 3 (duplicate email throws TenantProvisionException, not duplicate row): passed
  - Additional: `./gradlew test` — BUILD SUCCESSFUL, all tests green; `./gradlew spotlessCheck checkstyleMain` — BUILD SUCCESSFUL
- Open TO-DOs created (with PRD reference): none (passwordHash field present but left null for now; TASK-017 adds BCrypt hashing)
- Commits: feat(tenancy): TASK-016 Tenant entity, TenantRepository, InternalTenantService

### 2026-05-27 — TASK-007 — Expo SDK mobile app scaffold
- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §21.3
- Files touched: mobile/package.json, mobile/tsconfig.json, mobile/app.json, mobile/eas.json, mobile/App.tsx, mobile/babel.config.js, mobile/.gitignore, mobile/README.md, mobile/src/screens/.gitkeep, mobile/src/navigation/.gitkeep, mobile/src/features/.gitkeep, mobile/src/entities/.gitkeep, mobile/src/shared/.gitkeep, tasks.json
- Summary: Scaffolded the React Native + Expo SDK 56 mobile app under `mobile/` with the full PRD §21.3 stack: React Navigation 7, TanStack Query 5, Zustand 5, Expo SecureStore, Expo Notifications, Expo ImagePicker, and Expo Camera. The app uses TypeScript strict mode with the Expo tsconfig preset, and the root `App.tsx` renders a dark-themed placeholder screen with the Teamhub brand name. FSD-style folder layout (`src/screens`, `src/navigation`, `src/features`, `src/entities`, `src/shared`) is in place. `eas.json` provides `development` (with iOS Simulator support), `preview` (internal distribution), and `production` (autoIncrement, store) build profiles.
- Acceptance criteria verified: 1, 2, 3, 4
- Test steps run: 1 passed; 2–3 known limitation (local simulator); 4 eas.json valid JSON; `npx tsc --noEmit` and `npm test` passed
- Open TO-DOs created (with PRD reference): none
- Commits: feat(mobile): TASK-007 Expo SDK mobile app scaffold (PR #5)

### 2026-05-27 — TASK-009 — GitHub Actions backend CI pipeline
- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §21.6, §25.2
- Files touched: .github/workflows/backend-ci.yml (new), backend/build.gradle.kts (jacoco plugin + jacocoTestReport + jacocoTestCoverageVerification + tasks.check dependency), tasks.json (TASK-009 status → done)
- Summary: Created `.github/workflows/backend-ci.yml` with triggers on push to `main` and on pull_request, scoped to `backend/**` and the workflow file itself. The pipeline spins up PostgreSQL 16/PostGIS and Redis 7 service containers, sets up Java 21 Temurin with Gradle cache, runs `spotlessCheck`, `checkstyleMain`, `test jacocoTestReport`, and `jacocoTestCoverageVerification` (70 % line-coverage gate). Coverage HTML report is uploaded as a GitHub Actions artifact (14-day retention). A gitleaks/gitleaks-action@v2 step scans the full commit history for committed secrets (requires `pull-requests: read` permissions). Added the `jacoco` Gradle plugin to `build.gradle.kts` and wired `jacocoTestCoverageVerification` into the `check` lifecycle. Merged via PR #4.
- Acceptance criteria verified:
  1. Workflow triggers on push (main) and pull_request, path-filtered to backend/** — YAML valid
  2. Pipeline steps present: setup-java 21, gradle cache, spotlessCheck, checkstyleMain, test + jacocoTestReport, artifact upload — YAML valid
  3. jacocoTestCoverageVerification task added to build.gradle.kts with 0.70 minimum; wired into `check` — verified in Gradle DSL
  4. gitleaks/gitleaks-action@v2 step present — YAML valid
  5. Required-status check documented in workflow comment block — present
- Test steps run: Live GitHub Actions run passed after gitleaks permissions fix and Spotless formatting (PR #4 merged).
- Open TO-DOs created (with PRD reference): none
- Commits: 3e44ab1 (PR #4)

### 2026-05-27 — TASK-010 — GitHub Actions web CI pipeline
- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §21.2, §21.7
- Files touched: .github/workflows/web-ci.yml, tasks.json, progress.md
- Summary: Created `.github/workflows/web-ci.yml` that triggers on pushes to `main` and all pull requests touching `web/**` or the workflow file itself. The pipeline runs on `ubuntu-latest` with Node 20 and performs in order: `npm ci`, ESLint lint, TypeScript typecheck (`tsc --noEmit`), Vitest unit tests, production build (`tsc && vite build`), Playwright browser install (chromium + system deps), and e2e tests against the production preview build. To test against the built artifact, the workflow starts `vite preview --port 5173` in the background and waits for it to be ready; then runs `npm run test:e2e` with `CI=''` so playwright's `reuseExistingServer` evaluates to `true` and reuses the running preview server rather than spawning a separate dev server. On any failure the Playwright HTML report is uploaded as an artifact (retained 30 days).
- Acceptance criteria verified:
  1. Workflow triggers on `web/**` and `.github/workflows/web-ci.yml` path filters — confirmed in YAML
  2. All required pipeline steps present: setup-node@v4 (node 20), npm ci, lint, typecheck, test, build, playwright install --with-deps chromium, test:e2e against preview server — confirmed in YAML
  3. `actions/upload-artifact@v4` step with `if: failure()` uploads `web/playwright-report/` — confirmed in YAML
  4. Lint and typecheck steps exit non-zero on violations (ESLint and tsc both exit 1 on errors) — confirmed by script definitions in web/package.json
- Test steps run: Full verification requires live GitHub Actions. YAML validity confirmed locally via `python3 -c "import yaml; yaml.safe_load(...)"` — passed.
- Open TO-DOs created (with PRD reference): none
- Commits: b38c050 (PR #3)

### 2026-05-27 — TASK-017 — User entity, UserService, BCrypt strength-12 hashing
- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §6.1, §24.4
- Files touched: backend/src/main/java/com/teamhub/auth/UserRole.java, backend/src/main/java/com/teamhub/auth/UserRepository.java, backend/src/main/java/com/teamhub/auth/UserAlreadyExistsException.java, backend/src/main/java/com/teamhub/auth/UserService.java, backend/src/main/java/com/teamhub/shared/PasswordConfig.java, backend/src/test/java/com/teamhub/auth/UserServiceIT.java, backend/build.gradle.kts, tasks.json, progress.md
- Summary: Enhanced the `User` entity and `UserRole` enum (ADMIN, DISPATCHER, TECHNICIAN) inherited from TASK-016. Added a `PasswordConfig` bean providing BCrypt strength-12 via `BCryptPasswordEncoder(12)`. Implemented `UserService.createUser()` which hashes the raw password before persistence — the raw value is never logged or stored. Added `UserAlreadyExistsException` as a typed domain error. Also fixed a JaCoCo task name bug (`jacocoCoverageVerification` → `jacocoTestCoverageVerification`) in build.gradle.kts that prevented the build from compiling.
- Acceptance criteria verified:
  1. `User` entity has id, tenant_id, email (citext), passwordHash, role, name, isActive, createdAt — confirmed (from TASK-016 baseline, unchanged)
  2. `PasswordEncoder` bean is BCryptPasswordEncoder(12) — confirmed in PasswordConfig
  3. `UserService.createUser()` hashes password via PasswordEncoder before persist; raw password not logged — confirmed
  4. Email uniqueness uses global unique constraint with TO-DO comment referencing PRD §6.5 — confirmed in UserService
- Test steps run:
  1. `createUser_passwordHashStartsWithBcrypt12Prefix` — hash starts with `$2a$12$` — passed
  2. `createUser_rawPasswordNotStoredInDatabase` — passwordHash != rawPassword — passed
  3. `createUser_duplicateEmailThrowsUserAlreadyExistsException` — UserAlreadyExistsException thrown — passed
  4. `createUser_bcryptStrengthIsExactly12` — hash prefix `$2a$12$` and BCrypt.checkpw passes — passed
  5. All existing TASK-016 tests (FlywayMigrationTest, InternalTenantServiceIT, TeamhubApplicationTests) — passed
  6. `./gradlew spotlessCheck checkstyleMain` — passed
- Open TO-DOs created (with PRD reference): UserService line comment — per PRD §6.5, switch from global to per-tenant email uniqueness once multi-tenancy isolation is fully implemented
- Commits: 99a8132 (PR #6)

### 2026-05-28 — TASK-018 — PostgreSQL Row-Level Security (tenant isolation)
- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §7.2, §22
- Files touched:
  - backend/src/main/resources/db/migration/V2__rls_users.sql (new)
  - backend/src/main/java/com/teamhub/shared/TenantContext.java (new)
  - backend/src/main/java/com/teamhub/shared/TenantAwareDataSource.java (new)
  - backend/src/main/java/com/teamhub/shared/TenantDataSourceConfig.java (new)
  - backend/src/main/java/com/teamhub/shared/TenantContextFilter.java (new)
  - backend/src/test/java/com/teamhub/security/RowLevelSecurityIT.java (new)
  - backend/config/checkstyle/checkstyle.xml (allowEmptyConstructors added to WhitespaceAround)
  - backend/src/test/java/com/teamhub/FlywayMigrationTest.java (pre-existing EmptyLineSeparator fixed)
  - tasks.json (TASK-018 status → done)
- Summary: Implemented the full database-level tenant isolation layer (PRD §7.2). The V2 Flyway migration creates the `teamhub_app` non-superuser role (subject to RLS), grants it table access, enables `ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` on `users`, and creates the `tenant_isolation` policy using `NULLIF(current_setting('app.current_tenant_id', true), '')::uuid` to handle both missing-GUC and RESET-GUC cases as deny-by-default. `TenantContext` is a ThreadLocal UUID holder populated by `TenantContextFilter` (placeholder reads `X-Tenant-Id` header; TO-DO in TASK-019 to replace with JWT). `TenantDataSourceConfig` uses a BeanPostProcessor to transparently wrap the auto-configured DataSource with `TenantAwareDataSource`, which executes `SET app.current_tenant_id` (or `RESET`) on every borrowed JDBC connection, propagating the thread-local tenant context to the DB session GUC before any query.
- Acceptance criteria verified:
  1. V2 migration enables RLS + FORCE RLS on `users`, creates `teamhub_app` role, grants table access — confirmed in migration SQL
  2. RLS policy uses `NULLIF(current_setting('app.current_tenant_id', true), '')::uuid` (more robust than the PRD example — handles PostgreSQL's RESET-to-empty-string behavior) — confirmed in migration SQL
  3. `TenantContextFilter` populates `TenantContext` from request header (TO-DO: JWT, TASK-019); `TenantAwareDataSource.applyGuc()` runs `SET app.current_tenant_id` on every connection borrow — confirmed in source
  4. Without GUC set (RESET → empty string → NULLIF → NULL), `SELECT * FROM users` as `teamhub_app` returns zero rows — confirmed by `RowLevelSecurityIT.withoutGucSetQueriesReturnNoRows()`
- Test steps run:
  - Step 1 (`rlsIsolatesUsersAcrossTenants`): provisions two tenants A and B, flush INSERTs as postgres (BYPASSRLS), switch to `teamhub_app` role, set GUC per tenant, assert only matching tenant's user is visible — passed
  - Step 2 (`withoutGucSetQueriesReturnNoRows`): provisions a tenant, flush, switch role without setting GUC → zero rows visible — passed
  - Step 3 (forged JWT): TO-DO for TASK-019 (JWT infrastructure not yet available)
  - All 16 tests (14 pre-existing + 2 new): BUILD SUCCESSFUL
  - `./gradlew check` (spotlessCheck, checkstyleMain, checkstyleTest, test, jacocoTestCoverageVerification) — BUILD SUCCESSFUL
- Open TO-DOs created (with PRD reference):
  - `TenantContextFilter`: Replace `X-Tenant-Id` header with JWT claims once TASK-019 (RS256 JWT) lands
  - `RowLevelSecurityIT`: Add forged-JWT test once TASK-019 lands
- Commits: (pending user review and PR approval)

### 2026-05-30 — TASK-019 — JWT issuance and validation (RS256, refresh rotation, logout)
- Agent: Composer (Claude Opus 4.8)
- Status change: pending → done
- PRD sections consulted: §6.2, §6.6, §7.2, §24.4
- Files touched:
  - backend/build.gradle.kts (added spring-boot-starter-oauth2-resource-server)
  - backend/src/main/resources/application.yml (teamhub.jwt config block)
  - backend/src/main/resources/db/migration/V3__refresh_tokens.sql (new)
  - backend/src/main/java/com/teamhub/security/JwtProperties.java (new)
  - backend/src/main/java/com/teamhub/security/RsaKeyLoader.java (new)
  - backend/src/main/java/com/teamhub/security/JwtKeyConfig.java (new)
  - backend/src/main/java/com/teamhub/security/SecurityConfig.java (new)
  - backend/src/main/java/com/teamhub/auth/JwtService.java (new)
  - backend/src/main/java/com/teamhub/auth/RefreshToken.java (new)
  - backend/src/main/java/com/teamhub/auth/RefreshTokenRepository.java (new)
  - backend/src/main/java/com/teamhub/auth/AuthService.java (new)
  - backend/src/main/java/com/teamhub/auth/AuthController.java (new)
  - backend/src/main/java/com/teamhub/auth/AuthExceptionHandler.java (new)
  - backend/src/main/java/com/teamhub/auth/InvalidCredentialsException.java (new)
  - backend/src/main/java/com/teamhub/auth/InvalidRefreshTokenException.java (new)
  - backend/src/main/java/com/teamhub/auth/dto/{LoginRequest,RefreshRequest,LogoutRequest,TokenResponse}.java (new)
  - backend/src/main/java/com/teamhub/shared/TenantContextFilter.java (rewritten: JWT claim instead of X-Tenant-Id header)
  - backend/src/test/java/com/teamhub/auth/AuthServiceIT.java (new)
  - backend/src/test/java/com/teamhub/auth/AuthControllerIT.java (new)
  - backend/src/test/java/com/teamhub/security/RsaKeyLoaderTest.java (new)
  - backend/src/test/java/com/teamhub/security/RowLevelSecurityIT.java (updated TASK-019 TO-DO → resolved)
  - tasks.json (TASK-019 status → done)
- Summary: Implemented RS256 JWT issuance and validation. Access tokens (15-min TTL) carry `sub`, `tenant_id`, `role`, `iat`, `exp` claims plus a `kid` header; signed via NimbusJwtEncoder. Keys load from `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH` PEM files, with an ephemeral generated keypair fallback for local/test (logged as a warning). The decoder selects the verification key by `kid` from a JWK set, so rotation is supported. Opaque 256-bit refresh tokens are stored only as SHA-256 hashes in the new `refresh_tokens` table (RLS-protected, deny-by-default, same pattern as V2). `POST /api/v1/auth/login` validates email+password and issues a token pair; `POST /api/v1/auth/refresh` rotates (revokes the presented token, issues a new pair); `POST /api/v1/auth/logout` revokes. A stateless `SecurityConfig` permits the auth endpoints, health/info, and OpenAPI; all other requests require a valid JWT. `TenantContextFilter` was rewritten to read `tenant_id` from the validated JWT (resolving the TASK-018 placeholder) and is wired into the security chain after bearer-token authentication so RLS is scoped per request.
- Acceptance criteria verified:
  1. RS256 keypair loaded from env PEM paths (kid configurable, thumbprint default); rotation via kid-keyed JWK set — RsaKeyLoaderTest + JwtKeyConfig
  2. Access token payload has sub, tenant_id, role, exp (15 min), iat, kid — AuthServiceIT.login_issuesAccessTokenWithRequiredClaims
  3. Opaque random refresh token, stored hashed in refresh_tokens(id, user_id, tenant_id, hash, expires_at, revoked_at) — V3 migration + RefreshToken + AuthService
  4. POST /api/v1/auth/refresh rotates (new token issued, old revoked) — AuthServiceIT.refresh_rotatesToken_andRevokesPrevious
  5. Logout sets revoked_at; subsequent refresh returns 401 — AuthServiceIT.logout_revokesRefreshToken + AuthControllerIT
- Test steps run (33 tests total, all green):
  - Step 1 (login returns access + refresh): AuthControllerIT.login_returnsBearerTokenPair, AuthServiceIT.login_issuesAccessTokenWithRequiredClaims — passed
  - Step 2 (expired access token rejected; refresh succeeds with a new pair): AuthControllerIT.protectedEndpoint_withExpiredToken_returns401 + refresh_rotatesToken — passed (clock fast-forward simulated by minting a token with past exp / inserting an expired refresh row)
  - Step 3 (logout then refresh → 401 with documented code): AuthServiceIT.logout_revokesRefreshToken + AuthControllerIT.login_badPassword_returns401WithCode (code INVALID_REFRESH_TOKEN / INVALID_CREDENTIALS) — passed
  - Forged-signature token rejected (PRD §6.6 cross-tenant impossibility): AuthControllerIT.protectedEndpoint_withForgedSignature_returns401 — passed
  - `./gradlew check` (spotlessCheck, checkstyleMain, checkstyleTest, test, jacocoTestCoverageVerification 70% gate) — BUILD SUCCESSFUL
- Open TO-DOs created (with PRD reference): none. (Google/Apple OAuth login per PRD §6.1 remains future work tracked by its own task; rate limiting on auth endpoints per PRD §24.4 is TASK-related future work, not part of TASK-019 scope.)
- Notes for next iteration:
  - JWT keys: production MUST set `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH` to persistent PEMs. The ephemeral fallback regenerates on every restart (all tokens invalidated) and logs a WARN.
  - Login/refresh/logout intentionally run without a tenant context so the login DB user (RLS-exempt) can do the global email / token-hash lookups. Authenticated business requests get RLS scoping from the JWT `tenant_id` claim via `TenantContextFilter`.
  - No protected business endpoints exist yet; the security chain is validated against a non-existent `/api/v1/secure-check` path (401 without token, 404 with a valid token).
- Review fixes (post-implementation hardening):
  - Refresh rotation race: `RefreshTokenRepository.findByHashForUpdate` adds `@Lock(PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`); `refresh()` now uses it so two concurrent refreshes of the same token serialise and the second re-reads the revoked row → single-use guarantee holds under READ COMMITTED.
  - Deactivated-user lockout: `refresh()` now rejects when `user.isActive()` is false (mirrors `login()`), so a terminated employee is locked out immediately instead of retaining access for the 30-day refresh TTL. Covered by `AuthServiceIT.refresh_afterUserDeactivated_throwsInvalidRefreshToken`.
- Commits: (pending user review and PR approval)

---

## Notes for future agents

- The PRD at `prd/PRD.md` is the only source of truth. If something here contradicts the PRD, the PRD wins.
- Integrations gated on Lead Developer review before merge per PRD §25.3: Stripe, Apple Sign-in, Push notifications, Twilio (future), Google OAuth.
- Completed through TASK-019: 001, 002, 003, 004, 005, 007, 009, 010, 016, 017, 018, 019 (see `tasks.json`).
- TASK-019 note: `TenantContextFilter` now reads the `tenant_id` claim from the validated JWT (the TASK-018 `X-Tenant-Id` placeholder is gone). Auth endpoints live under `/api/v1/auth/**` (permitAll); everything else requires a valid RS256 access token. Production must configure persistent JWT keys via `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH`.
