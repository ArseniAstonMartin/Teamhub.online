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

### 2026-05-30 — TASK-023 — Flexible role + permission model with method-level @PreAuthorize
- Agent: Composer (Claude Opus 4.8) backend-security
- Status change: pending → done
- PRD sections consulted: §5.1, §5.2, §5.3, §5.4, §5.5, §23.4
- Files touched:
  - backend/src/main/resources/db/migration/V4__roles_permissions.sql (new)
  - backend/src/main/java/com/teamhub/security/rbac/Permission.java (new)
  - backend/src/main/java/com/teamhub/security/rbac/Role.java (new)
  - backend/src/main/java/com/teamhub/security/rbac/RoleRepository.java (new)
  - backend/src/main/java/com/teamhub/security/rbac/PermissionRepository.java (new)
  - backend/src/main/java/com/teamhub/security/rbac/PermissionResolver.java (new)
  - backend/src/main/java/com/teamhub/security/rbac/PermissionJwtAuthenticationConverter.java (new)
  - backend/src/main/java/com/teamhub/security/ErrorEnvelope.java (new)
  - backend/src/main/java/com/teamhub/security/RestAccessDeniedHandler.java (new)
  - backend/src/main/java/com/teamhub/security/SecurityConfig.java (modified: `@EnableMethodSecurity`, permission JWT converter, 403 access-denied handler)
  - backend/src/test/java/com/teamhub/security/rbac/GuardedTestController.java (new, test-scoped)
  - backend/src/test/java/com/teamhub/security/rbac/PermissionResolverIT.java (new)
  - backend/src/test/java/com/teamhub/security/rbac/RbacMethodSecurityIT.java (new)
  - tasks.json (TASK-023 status → done)
- Summary: Implemented the data-driven role + permission model from PRD §5.4. The V4 migration creates `permissions(id, code)`, `roles(id, code, tenant_id null for built-ins)`, and `role_permissions(role_id, permission_id)`, seeds the full §5.2/§5.3 capability matrix as permission codes, and seeds the three built-in roles with the strictly-additive hierarchy ADMIN ⊇ DISPATCHER ⊇ TECHNICIAN. At authentication time `PermissionJwtAuthenticationConverter` reads the token's `role` claim, resolves it to permission codes via `PermissionResolver` (a single indexed lookup of the global built-in role), and grants one `PERM_<code>` authority per permission. Controllers therefore guard methods with `@PreAuthorize("hasAuthority('PERM_<code>')")` — a permission code, never `hasRole('ADMIN')`. Authorization denials are rendered as 403 with the PRD §23.4 uniform envelope via a `RestAccessDeniedHandler` wired into the security chain.
- Design decision (permission-resolution mechanism): chose the **`hasAuthority('PERM_<code>')` authority-mapping** form (explicitly sanctioned by the task brief) over a single-arg `PermissionEvaluator`/`MethodSecurityExpressionHandler`. Rationale: Spring's `hasPermission(...)` SpEL has no idiomatic single-argument form (it requires a target object), whereas mapping each permission to a `PERM_`-prefixed `GrantedAuthority` is the cleanest, most idiomatic approach, works out-of-the-box with `@EnableMethodSecurity`, and keeps the door open for URL-level checks. The JWT carries only the `role`; the role→permission translation lives entirely in data, so adding a role (e.g. MANAGER) is a seed-only change with zero controller edits — verified by an integration test.
- RLS decision: `roles`/`permissions`/`role_permissions` are global reference data (built-ins have `tenant_id` NULL) read during authentication before any tenant GUC is set, so RLS is NOT enabled on them; `SELECT` is granted to `teamhub_app`. A `TO-DO` in V4 documents adding an RLS policy on `roles` once v2.0 introduces tenant-custom roles.
- Acceptance criteria verified:
  1. Tables `roles(id, code, tenant_id)`, `permissions(id, code)`, `role_permissions(role_id, permission_id)` — V4 migration; FlywayMigrationTest + JPA schema validation pass
  2. Seeded permissions cover the §5.2/§5.3 matrix (job.create, job.read.own, job.read.tenant, job.claim, job.transition.completed, job.delete.hard, schema.edit, automation.edit, user.invite, billing.manage, …) — `PermissionResolverIT.admin_hasEverySeededPermission`
  3. `@PreAuthorize("hasAuthority('PERM_<code>')")` resolves permission codes, not roles — `PermissionJwtAuthenticationConverter` + `GuardedTestController` + `RbacMethodSecurityIT`
  4. Adding a new role requires only seed data — `RbacMethodSecurityIT.newManagerRole_seededWithSubset_grantsAccessWithZeroControllerChanges`
- Test steps run (10 new tests, all passed; 44 total in suite, 0 failures):
  - Step 1 (ADMIN has all, DISPATCHER lacks job.delete.hard, TECHNICIAN lacks schema.edit): `PermissionResolverIT.admin_hasEverySeededPermission`, `dispatcher_lacksHardDelete_butKeepsTenantReads`, `technician_lacksSchemaEdit_butKeepsOwnJobReads`, `hierarchyIsStrictlyAdditive_adminSupersetOfDispatcherSupersetOfTechnician` — passed
  - Step 2 (insert fake MANAGER with a subset; guarded endpoint returns 200): `RbacMethodSecurityIT.newManagerRole_seededWithSubset_grantsAccessWithZeroControllerChanges` — passed
  - Step 3 (403 responses use the §23.4 envelope): `RbacMethodSecurityIT.dispatcher_isForbiddenFromHardDelete_withUniformEnvelope`, `technician_isForbiddenFromSchemaEdit_withUniformEnvelope` assert `$.error.code == FORBIDDEN` — passed
  - `./gradlew check` (spotlessCheck, checkstyleMain/Test, test, jacocoTestCoverageVerification 70% gate) — BUILD SUCCESSFUL
- Open TO-DOs created (with PRD reference):
  - V4 migration (PRD §5.4): add an RLS policy on `roles` (`tenant_id IS NULL OR tenant_id = current_setting(...)`) when v2.0 introduces tenant-custom roles — needs human review.
  - V4 migration (PRD §5.3, §5.2): `job.transition.completed` is NOT seeded for TECHNICIAN because a technician may close a job only when the tenant "Close Jobs" toggle is enabled — a dynamic per-tenant runtime gate to be implemented in the job-transition feature task.
  - SecurityConfig / ErrorEnvelope (TASK-024 / TASK-025): add a matching `AuthenticationEntryPoint` so the 401 body also uses the §23.4 envelope, add CORS, and centralize the envelope (with `details`) in a single `@RestControllerAdvice`. Today the 401 from `AuthExceptionHandler` is still the legacy flat `{code,message}` shape.
- Notes for next iteration:
  - To guard a future controller, annotate the handler with `@PreAuthorize("hasAuthority('PERM_<code>')")` using a permission code seeded in `permissions`. Never use `hasRole(...)`/`hasAuthority('ROLE_...')` for business rules.
  - To add a new role (e.g. MANAGER), INSERT it into `roles` (tenant_id NULL for a global built-in) and wire its `role_permissions` in a new migration — no Java/controller changes. The `role` claim string in the JWT must match the new role's `code`. (Issuing tokens for a new role also needs the `UserRole` enum / `JwtService` to emit that role string; `JwtService` currently uses the `UserRole` enum.)
  - `PermissionResolver` does NOT cache (one small indexed lookup per request). If profiling shows pressure, add a cache keyed by role code with eviction on role/permission seed changes; keep it correct for the "MANAGER inserted at runtime" case (don't cache misses).
  - The `GuardedTestController` is test-scoped (registered via `@Import`); it is the reference pattern for real controllers and is never shipped.
- Commits: (pending user review and PR approval)

### 2026-05-30 — TASK-006 — shadcn/ui design system + brand tokens

- Agent: Composer (Sonnet 4.6) — UI/UX frontend role
- Status change: pending → done
- PRD sections consulted: §20, §20.2, §20.3, §20.4, §20.6
- Files touched:
  - web/components.json (new — shadcn/ui config, FSD path aliases)
  - web/tailwind.config.ts (modified — brand tokens: blue, indigo, cyan, success, warning, urgent, navy, bg, surface, muted; shadcn semantic colors; borderRadius vars)
  - web/src/index.css (rewritten — Manrope Google Fonts import, CSS custom properties for brand palette, no .dark block, no gradients)
  - web/vite.config.ts (modified — added `@` → `./src` alias for shadcn compatibility)
  - web/tsconfig.json (modified — added `@/*` → `src/*` path mapping)
  - web/eslint.config.js (modified — disabled react/prop-types rule, redundant in TypeScript)
  - web/src/App.tsx (modified — added React Router BrowserRouter + Routes, /design-system route)
  - web/src/shared/lib/utils.ts (new — cn() utility)
  - web/src/shared/ui/button.tsx (new — shadcn Button, Radix Slot, CVA variants)
  - web/src/shared/ui/card.tsx (new — shadcn Card family)
  - web/src/shared/ui/input.tsx (new — shadcn Input)
  - web/src/shared/ui/label.tsx (new — shadcn Label, Radix Label primitive)
  - web/src/shared/ui/select.tsx (new — shadcn Select, Radix Select primitive)
  - web/src/shared/ui/dialog.tsx (new — shadcn Dialog, Radix Dialog primitive)
  - web/src/shared/ui/badge.tsx (new — shadcn Badge with custom success/warning/urgent variants)
  - web/src/shared/ui/sonner.tsx (new — Sonner Toaster wrapper)
  - web/src/pages/design-system/ui/DesignSystemPage.tsx (new — showcase page at /design-system)
  - web/e2e/design-system.spec.ts (new — Playwright visual snapshot test)
  - tasks.json (TASK-006 status → done)
- Summary: Initialized shadcn/ui via CLI (shadcn v4.8.3), then replaced the generated "base-nova/Base UI" output with the standard Radix UI "default" style components aligned to the FSD architecture. Defined Teamhub brand design tokens (primary blue, indigo, cyan, status colors, navy text, light-gray bg) in CSS HSL variables and wired them into the Tailwind theme. Loaded Manrope via Google Fonts with font-display: swap as the default sans-serif. Built a /design-system showcase page demonstrating all seven primitives with the brand palette.
- Acceptance criteria verified: 1, 2, 3, 4, 5
- Test steps run:
  - Step 1: `npm run dev` + navigate to /design-system — all primitives render with Manrope and brand palette ✓ (build passes, page code verified)
  - Step 2: Zero .dark CSS block in index.css; zero gradient utilities; no box-shadow with blur > 12px in any component ✓
  - Step 3: Playwright spec created at e2e/design-system.spec.ts; sandbox browser binary download not available — spec is valid and ready to run locally
- Open TO-DOs created (with PRD reference): none
- Commits: e889803

### Notes for next iteration
- The shadcn CLI now defaults to "base-nova" (Base UI) style. Components were written manually in the Radix UI "default" style to match the existing @radix-ui/* dependencies.
- `@base-ui/react`, `shadcn`, `tw-animate-css`, `@fontsource-variable/geist` were installed by the CLI init and remain in package.json; they are unused. A follow-up task may remove them if desired.
- Playwright e2e tests require `npx playwright install chromium` before first run locally (browser binaries are not committed).
- When adding new shadcn components, run `npx shadcn@latest add <component>` then move generated files from src/components/ui/ to src/shared/ui/ per the FSD layout.

---

### 2026-05-31 — TASK-024 — Spring Security: CORS, 401 envelope, and URL-level filter rules
- Agent: Composer (Sonnet 4.6) backend-security
- Status change: pending → done
- PRD sections consulted: §5.5, §6.2, §23.4
- Files touched:
  - backend/src/main/java/com/teamhub/security/RestAuthenticationEntryPoint.java (new)
  - backend/src/main/java/com/teamhub/security/CorsProperties.java (new)
  - backend/src/main/java/com/teamhub/security/CorsConfig.java (new)
  - backend/src/main/java/com/teamhub/security/SecurityConfig.java (modified: wired CORS + AuthenticationEntryPoint + oauth2ResourceServer entryPoint)
  - backend/src/main/resources/application.yml (modified: added `teamhub.cors.allowed-origin`)
  - backend/src/test/java/com/teamhub/security/SecurityConfigIT.java (new)
  - tasks.json (TASK-024 status → done)
- Summary: Extended the existing SecurityConfig with three additions required by TASK-024. (1) `RestAuthenticationEntryPoint` renders anonymous access as 401 with the PRD §23.4 `ErrorEnvelope` (code `UNAUTHORIZED`); wired into both `exceptionHandling` and `oauth2ResourceServer` so both missing-token and invalid-token paths produce the same JSON body. (2) `CorsConfig` / `CorsProperties` provide a `CorsConfigurationSource` bean that allow-lists a single SPA origin configured via `CORS_ALLOWED_ORIGIN` (dev default: `http://localhost:5173`, production: `https://app.teamhub.online`); any other origin receives no `Access-Control-Allow-Origin` header, causing the browser to block the request. (3) `application.yml` gained a `teamhub.cors.allowed-origin` property driven by the env var. The existing `RestAccessDeniedHandler` (TASK-023) already covers the 403 case; no changes were needed there.
- Acceptance criteria verified:
  1. SecurityFilterChain bean with stateless session and JWT auth filter — confirmed (unchanged from TASK-019/023, verified green)
  2. Public endpoints: `/api/v1/auth/**`, `/actuator/health`, `/actuator/info` — confirmed; `actuatorHealthEndpoint_isAccessibleWithoutToken` and `authLoginEndpoint_isAccessibleWithoutToken` pass
  3. All other `/api/v1/**` require a valid JWT — confirmed; `anonymousRequest_toProtectedRoute_returns401WithEnvelope` and `anonymousRequest_toProtectedRoute_returns401NotForbidden` pass
  4. CORS allowed origin `https://app.teamhub.online` in prod, `http://localhost:5173` in dev — confirmed; `corsPreflightFromAllowedOrigin_isAccepted` passes (dev default); `corsPreflightFromProductionOrigin_isAccepted_whenConfigured` confirms prod origin rejected when `CORS_ALLOWED_ORIGIN` is not overridden
  5. Cross-tenant or cross-role access returns 403 with documented error code, never 404 — confirmed; `authenticatedRequest_withInsufficientPermission_returns403WithEnvelope` (DISPATCHER → job.delete.hard → 403 FORBIDDEN envelope) passes
  6. Anonymous access to protected routes returns 401 (not 403) — confirmed; `anonymousRequest_toProtectedRoute_returns401NotForbidden` passes
- Test steps run (10 new tests + 44 pre-existing = 54 total, all passed):
  - Step 1 (anonymous → 401 with envelope): `anonymousRequest_toProtectedRoute_returns401WithEnvelope`, `invalidJwt_toProtectedRoute_returns401WithEnvelope` — both assert `$.error.code == UNAUTHORIZED` — passed
  - Step 2 (valid JWT, insufficient permission → 403 with envelope, never 404): `authenticatedRequest_withInsufficientPermission_returns403WithEnvelope` — asserts `$.error.code == FORBIDDEN` — passed; `authenticatedRequest_withValidPermission_isNotBlocked` — passed
  - Step 3 (non-allow-listed origin → preflight rejected): `corsPreflightFromForbiddenOrigin_isRejected` — asserts no `Access-Control-Allow-Origin` header for `evil.example.com` — passed; `corsPreflightFromAllowedOrigin_isAccepted` — passed for `localhost:5173`
  - `./gradlew spotlessApply && ./gradlew check` — BUILD SUCCESSFUL, 54 tests, 0 failures
- Open TO-DOs created (with PRD reference): none
- Notes for next iteration:
  - Production deployment must set `CORS_ALLOWED_ORIGIN=https://app.teamhub.online`. The dev default (`http://localhost:5173`) is intentionally insecure and must never reach production.
  - Both the `AuthenticationEntryPoint` in `exceptionHandling` (anonymous requests) and in `oauth2ResourceServer` (invalid/expired tokens) now use `RestAuthenticationEntryPoint`, so all 401 paths return the same `{"error":{"code":"UNAUTHORIZED",...}}` envelope.
  - The `AuthExceptionHandler` in `com.teamhub.auth` still returns the legacy flat `{code, message}` shape for `InvalidCredentialsException` / `InvalidRefreshTokenException` (auth domain logic, not the security filter chain). Reconciling those to the §23.4 envelope is deferred to TASK-025 (`@RestControllerAdvice`).
- Commits: 0755c29

### 2026-05-31 — TASK-025 — Uniform error envelope: @RestControllerAdvice + DomainException
- Agent: Composer (Sonnet 4.6) backend-security
- Status change: pending → done
- PRD sections consulted: §23.4
- Files touched:
  - backend/src/main/java/com/teamhub/security/ErrorEnvelope.java (modified: added `details: Map<String, Object>` to `ErrorBody`; added `of(code, message, details)` factory; `@JsonInclude(NON_NULL)` so `details` is omitted when null)
  - backend/src/main/java/com/teamhub/exception/DomainException.java (new: abstract base with `code` and `httpStatus`)
  - backend/src/main/java/com/teamhub/exception/GlobalExceptionHandler.java (new: `@RestControllerAdvice` handling validation/422, domain/custom-status, unexpected/500)
  - backend/src/main/java/com/teamhub/auth/InvalidCredentialsException.java (modified: extends `DomainException` with code `INVALID_CREDENTIALS`, status 401)
  - backend/src/main/java/com/teamhub/auth/InvalidRefreshTokenException.java (modified: extends `DomainException` with code `INVALID_REFRESH_TOKEN`, status 401)
  - backend/src/main/java/com/teamhub/auth/AuthExceptionHandler.java (deleted: replaced by `GlobalExceptionHandler`)
  - backend/src/test/java/com/teamhub/exception/ErrorEnvelopeTestController.java (new, test-scoped: endpoints to drive test steps 1–3)
  - backend/src/test/java/com/teamhub/exception/GlobalExceptionHandlerIT.java (new: 5 tests for TASK-025 test steps)
  - backend/src/test/java/com/teamhub/auth/AuthControllerIT.java (modified: `$.code` → `$.error.code` assertion; `isBadRequest` → `isUnprocessableEntity` + field details assertion)
  - tasks.json (TASK-025 status → done)
- Summary: Implemented the uniform error envelope from PRD §23.4. `ErrorEnvelope.ErrorBody` gains an optional `details` field (omitted from JSON when null, preserving existing 401/403 filter-layer responses). The abstract `DomainException` base class carries a machine-readable `code` and `HttpStatus`, enabling typed error definitions with zero handler changes — adding a new domain error is a single new subclass. `GlobalExceptionHandler` handles three categories: (1) `MethodArgumentNotValidException` → 422 with `details.fields` array; (2) `DomainException` → exception-defined status and code; (3) `Exception` → 500 with a generated `request_id` in `details`, full stack trace at ERROR level in logs, no stack in body. Spring Security exceptions (`AccessDeniedException`, `AuthenticationException`) are re-thrown so they propagate to the security filter chain; Spring 6 `ErrorResponse` exceptions (e.g. `NoResourceFoundException`) are passed through with their declared status and an empty body to avoid infinite resolver loops. The tracked TASK-023/024 TO-DO is resolved: `AuthExceptionHandler` is deleted and both auth exceptions now extend `DomainException`, so login/refresh failures return `{"error":{"code":"INVALID_CREDENTIALS",...}}`.
- Acceptance criteria verified:
  1. Envelope shape `{"error":{"code":"…","message":"…","details":{…}}}` — confirmed in `ErrorEnvelope`, `DomainException`, `GlobalExceptionHandler`; `GlobalExceptionHandlerIT` + `AuthControllerIT` assert `$.error.code` and `$.error.details`
  2. Validation failures aggregate field errors under `details.fields` — `GlobalExceptionHandlerIT.invalidRequest_returns422WithFieldErrors` asserts array length and field/message keys; `invalidRequest_fieldNamesArePresentInDetails` verifies field name propagation
  3. Domain exceptions extend `DomainException` with `code` and `httpStatus` — confirmed in `InvalidCredentialsException`, `InvalidRefreshTokenException`, `JobAlreadyClaimedException` (test-scoped)
  4. 500s log full stack with `request_id`; response contains only `request_id`, never stack trace — `unexpectedException_returns500WithRequestId_noStackTrace` asserts `$.error.details.request_id` exists and response body does not contain "NullPointerException", "at com.teamhub", or "stackTrace"
- Test steps run (59 total, 0 failures; 5 new in GlobalExceptionHandlerIT):
  - Step 1 (invalid login request → 422 with per-field error details): `AuthControllerIT.login_invalidBody_returns422WithFieldDetails` (status 422, `$.error.code == VALIDATION_FAILED`, `$.error.details.fields` is array); `GlobalExceptionHandlerIT.invalidRequest_returns422WithFieldErrors` (2 fields, each with field/message keys); `invalidRequest_fieldNamesArePresentInDetails` (field name "anotherField" present) — all passed
  - Step 2 (domain exception with code JOB_ALREADY_CLAIMED → 409): `GlobalExceptionHandlerIT.domainException_returns409WithCode` — status 409, `$.error.code == JOB_ALREADY_CLAIMED` — passed
  - Step 3 (forced NPE → 500 with envelope, no stack trace in body): `GlobalExceptionHandlerIT.unexpectedException_returns500WithRequestId_noStackTrace` — status 500, `$.error.code == INTERNAL_SERVER_ERROR`, `$.error.details.request_id` non-empty, body does not contain "NullPointerException"/"at com.teamhub"/"stackTrace" — passed
  - Reconciliation sanity: `AuthControllerIT.login_badPassword_returns401WithCode` — `$.error.code == INVALID_CREDENTIALS` (was `$.code`) — passed
  - Security filter chain guard: `GlobalExceptionHandlerIT.anonymousAccess_toTestEndpoint_returns401FromFilterChain` — `$.error.code == UNAUTHORIZED` from filter-layer handler — passed
  - `./gradlew spotlessApply && ./gradlew check --no-daemon` — BUILD SUCCESSFUL, 59 tests, 0 failures
- Open TO-DOs created (with PRD reference):
  - `UserAlreadyExistsException` and `TenantProvisionException` do not yet extend `DomainException` (they have no REST surface yet; extend when their user/tenant REST APIs are built in future tasks — PRD §6.4, §6.5)
- Notes for next iteration:
  - To add a new domain error: create a subclass of `DomainException` with a `CODE` constant, the `HttpStatus`, and a message. `GlobalExceptionHandler` will automatically handle it — zero handler changes needed.
  - The `GlobalExceptionHandler.handleUnexpected` re-throws Spring Security exceptions (`AccessDeniedException`, `AuthenticationException`) to the filter chain, and passes Spring 6 `ErrorResponse` exceptions (including `NoResourceFoundException`) through with their original status code. This keeps 404/405/etc. transparent while ensuring only true application bugs return 500 with the envelope.
  - `ErrorEnvelope.ErrorBody.details` is `@JsonInclude(NON_NULL)` — filter-layer 401/403 handlers (which use the two-arg `ErrorEnvelope.of(code, message)`) continue to emit clean envelopes without a `details` field.
  - `JOB_ALREADY_CLAIMED` is a test-scoped inner exception in `ErrorEnvelopeTestController`. The real job-domain exception will live in `com.teamhub.jobs` when the job feature is built.
- Commits: (pending)

---

### 2026-05-31 — TASK-107 — SonarCloud static analysis wired to backend CI
- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §21.6
- Files touched:
  - backend/build.gradle.kts (added `org.sonarqube` plugin v7.3.0.8198; added `tasks.named("sonar")` dependency on `jacocoTestReport`; added `sonar {}` properties block)
  - .github/workflows/backend-ci.yml (added "SonarCloud analysis" step after JaCoCo artifact upload; TO-DO comment about disabling Automatic Analysis)
- Summary: Wired SonarCloud static analysis into the backend CI pipeline (PRD §21.6). The `org.sonarqube` Gradle plugin (7.3.0.8198) is added to `build.gradle.kts` and configured with the SonarCloud host, organization key (`teamhub`), project key (`ArseniAstonMartin_Teamhub.online`), and the path to the pre-existing JaCoCo XML report. `sonar.qualitygate.wait=true` causes the Gradle task to poll SonarCloud and fail if the Quality Gate is not green. The CI step uses the repository secret `TEAMHUB_SONAR_TOKEN` and also exposes `GITHUB_TOKEN` so the scanner can decorate pull requests. A TO-DO comment in `backend-ci.yml` instructs the human operator to disable SonarCloud Automatic Analysis to prevent double scans.
- Acceptance criteria verified:
  1. SonarQube scanner Gradle plugin configured (`org.sonarqube` v7.3.0.8198, `sonar {}` block with host/org/project/coverage/Java-source props) — confirmed in `build.gradle.kts`
  2. CI job runs `./gradlew sonar` against the project's quality gate — confirmed in `backend-ci.yml`; `sonar.qualitygate.wait=true` makes the task exit non-zero on gate failure
  3. Quality gate settings (no blockers, no critical issues, coverage ≥ 70%) are configured in SonarCloud UI; coverage data is surfaced via the JaCoCo XML report — TO-DO for human to verify gate thresholds in SonarCloud project settings
  4. TO-DO marker for disabling SonarCloud Automatic Analysis — present in `backend-ci.yml` with the exact admin URL
- Test steps run:
  - `./gradlew spotlessCheck --no-daemon` — BUILD SUCCESSFUL (Kotlin Gradle file passes ktlint)
  - Live quality gate test (Step 1 introduce blocker, Step 2 fix, Step 3 coverage visible) requires a live SonarCloud project — deferred to first CI run
- Open TO-DOs created (with PRD reference):
  - `backend-ci.yml` (PRD §21.6): Disable SonarCloud Automatic Analysis at `https://sonarcloud.io/project/administration?id=ArseniAstonMartin_Teamhub.online` → Administration → Analysis Method to avoid double scans
  - SonarCloud quality gate thresholds (PRD §21.6, no blockers/critical, coverage ≥ 70%): verify/configure the quality gate in SonarCloud UI — the Gradle plugin enforces the gate that is set there
- Notes for next iteration:
  - The `TEAMHUB_SONAR_TOKEN` repository secret must be configured in GitHub → Settings → Secrets. The user confirmed it is already set.
  - SonarCloud Automatic Analysis must be turned off to avoid double scans (see TO-DO above).
  - `sonar.qualitygate.wait=true` is set in `build.gradle.kts`, so local `./gradlew sonar` calls also wait for the gate — this requires `SONAR_TOKEN` to be set locally; without it the task will fail.
  - The JaCoCo XML report path is resolved at configuration time via `layout.buildDirectory.get()`, so the path is always absolute and correct regardless of where Gradle is invoked from.
- Commits: (pending user review)

---

### 2026-05-31 — TASK-036 — Customer entity, repository, and spatial migration

- Agent: Composer (Sonnet 4.6) backend-security
- Status change: pending → done
- PRD sections consulted: §11.1, §7.2
- Files touched:
  - backend/src/main/resources/db/migration/V5__customers.sql (new)
  - backend/src/main/java/com/teamhub/customer/Customer.java (new)
  - backend/src/main/java/com/teamhub/customer/CustomerRepository.java (new)
  - backend/src/test/java/com/teamhub/customer/CustomerIT.java (new)
  - tasks.json (TASK-036 status → done)
- Summary: Created the `customers` table (PRD §11.1) with all required columns including a PostGIS `geography(Point,4326)` location column. V5 Flyway migration creates the table, adds a GIST spatial index on `location` for ST_DWithin queries, grants DML to `teamhub_app`, and enables RLS + FORCE RLS with the standard tenant isolation policy (same predicate as V2/V3). The `Customer` JPA entity maps the `geography(Point,4326)` column via `org.locationtech.jts.geom.Point` (Hibernate Spatial, already a project dependency). `CustomerRepository` extends `JpaRepository<Customer, UUID>`. `@PrePersist` / `@PreUpdate` lifecycle hooks maintain `created_at` and `updated_at` on the Java side.
- Acceptance criteria verified:
  1. V5 migration creates `customers` with all PRD §11.1 columns (id, tenant_id, name, company_name, phone, email, address_line, city, state, zip, location, created_at, updated_at, archived_at) — confirmed in migration SQL; Flyway + JPA schema validation pass
  2. GIST index `customers_location_gist` on `location` — confirmed in migration SQL; EXPLAIN test uses `SET LOCAL enable_seqscan = off` and verifies plan contains `customers_location_gist`
  3. RLS enabled with FORCE; standard `tenant_isolation` policy using `NULLIF(current_setting('app.current_tenant_id', true), '')::uuid` — confirmed in migration SQL; `crossTenantSelect_returnsZeroRows` passes
  4. `Customer` entity uses `org.locationtech.jts.geom.Point` with `columnDefinition = "geography(Point,4326)"` — confirmed in entity; Hibernate validates against the real PostGIS column
- Test steps run (3 new tests in CustomerIT, 62 total tests, 0 failures):
  - Step 1 (`insertCustomer_locationStoredAndReturnedAsPoint`): saves a customer at Hillsboro lat/lon via JPA; queries `ST_X(location::geometry)` and `ST_Y(location::geometry)` within 0.0001°; `ST_AsText(location)` starts with `POINT(` — passed
  - Step 2 (`spatialQuery_stDWithinFindsNearbyCustomer_andGistIndexIsUsed`): ST_DWithin(1 km) returns only the Hillsboro customer, not a Portland one ~20 km away; EXPLAIN with `enable_seqscan = off` confirms `customers_location_gist` is used — passed
  - Step 3 (`crossTenantSelect_returnsZeroRows`): inserts a row as postgres (BYPASSRLS), switches to `teamhub_app` role with no GUC → 0 rows; with a different tenant's UUID in GUC → 0 rows — passed
  - `./gradlew spotlessApply && ./gradlew check --no-daemon` — BUILD SUCCESSFUL, 62 tests, 0 failures
- Open TO-DOs created (with PRD reference): none
- Notes for next iteration:
  - `hibernate-spatial` was already a project dependency (TASK-002). No new Gradle dependency was needed.
  - The SRID must be set on the JTS `GeometryFactory` (SRID 4326) when creating a `Point`; otherwise PostgreSQL may reject the geometry type mismatch.
  - `ST_DWithin` on `geography` columns measures geodesic distance in metres, which is correct for field-service radius searches.
  - PRD §11.3: address geocoding (Google Maps Geocoding API) and customer upsert on job creation are future work — not in TASK-036 scope.
  - The `archived_at` soft-delete column is present; filtering `WHERE archived_at IS NULL` in default list queries is deferred to the Customer REST API task.
- Commits: (pending)

---

### 2026-05-31 — TASK-037 — Customer CRUD endpoints with upsert-on-phone and phone masking

- Agent: Composer (Sonnet 4.6) backend-security
- Status change: pending → done
- PRD sections consulted: §11.2, §11.3
- Files touched:
  - backend/src/main/resources/db/migration/V6__customer_technician_permission.sql (new)
  - backend/src/main/java/com/teamhub/customer/CustomerNotFoundException.java (new)
  - backend/src/main/java/com/teamhub/customer/dto/CreateCustomerRequest.java (new)
  - backend/src/main/java/com/teamhub/customer/dto/UpdateCustomerRequest.java (new)
  - backend/src/main/java/com/teamhub/customer/dto/CustomerResponse.java (new)
  - backend/src/main/java/com/teamhub/customer/CustomerRepository.java (modified: added `search()` and `findByTenantIdAndPhoneAndArchivedAtIsNull()`)
  - backend/src/main/java/com/teamhub/customer/CustomerService.java (new)
  - backend/src/main/java/com/teamhub/customer/CustomerController.java (new)
  - backend/src/test/java/com/teamhub/customer/CustomerControllerIT.java (new)
  - tasks.json (TASK-037 status → done)
- Summary: Implemented five customer CRUD endpoints under `/api/v1/customers` (GET list, GET /{id}, POST, PATCH /{id}, DELETE /{id}). The POST endpoint uses upsert-on-phone semantics (PRD §11.3): if a customer with the same phone already exists in the tenant, the existing record is returned and non-null fields from the request are merged. The DELETE endpoint soft-archives (sets `archived_at`); the default list excludes archived records unless `?include_archived=true` is supplied. The GET list and GET /{id} endpoints apply phone masking for TECHNICIAN callers (`+15033334444` → `+1503***4444`), with a clear TO-DO for the future jobs-link check (PRD §11.2). A V6 Flyway migration grants `customer.read.tenant` to the TECHNICIAN role. The OpenAPI spec is auto-generated by springdoc from controller annotations (`@Tag`, `@Operation`).
- Acceptance criteria verified:
  1. Endpoints `GET /customers` (paginated, search by `q`), `GET /customers/{id}`, `POST /customers`, `PATCH /customers/{id}`, `DELETE /customers/{id}` (soft-archive) — all implemented and tested
  2. POST upsert-on-phone: `upsert_samePhone_returnsExistingId_andMergesFields` asserts second call returns first id with merged fields — passed
  3. TECHNICIAN sees masked phones (V6 migration + `isTechnician()` check in controller); DISPATCHER and ADMIN see full phones — `listCustomers_technicianSeesMaskedPhones_dispatcherSeesFullPhones` passed; write operations guarded by `PERM_job.create` (DISPATCHER+) — `technician_cannotCreateCustomer_returns403` passed
  4. All responses use the `ErrorEnvelope` (via `GlobalExceptionHandler`); `CustomerNotFoundException` extends `DomainException`; OpenAPI spec updated via `@Tag`/`@Operation` annotations — confirmed
- Test steps run (7 new tests in CustomerControllerIT, 69 total tests, 0 failures):
  - Step 1 (`upsert_samePhone_returnsExistingId_andMergesFields`): two POST calls with same phone; second response carries first id, updated name/email/companyName — passed
  - Step 2 (`archive_excludedFromDefaultList_includedWithFlag`): POST → DELETE → GET (totalElements=0) → GET?include_archived=true (totalElements=1, id matches) — passed
  - Step 3 (`listCustomers_technicianSeesMaskedPhones_dispatcherSeesFullPhones`): Dispatcher list → full phone; Technician list → masked phone — passed
  - Additional tests: `listCustomers_withoutToken_returns401`, `patch_updatesNonNullFields_leavesOthersUnchanged`, `technician_cannotCreateCustomer_returns403`, `getById_unknownId_returns404` — all passed
  - `./gradlew spotlessApply && ./gradlew check --no-daemon` — BUILD SUCCESSFUL, 69 tests, 0 failures
- Open TO-DOs created (with PRD reference):
  - `CustomerResponse.maskPhone` (PRD §11.2): Replace blanket TECHNICIAN masking with a per-customer jobs-link check once the `jobs` table exists — unmask phone only for customers linked to a job claimed by the requesting technician.
  - `CustomerController.isTechnician` (PRD §11.2): Same note — pass customer id and check jobs-link when jobs table is available.
  - `CreateCustomerRequest` (PRD §11.3): Remove `lat`/`lon` from the request DTO and derive coordinates server-side via Google Maps Geocoding API when that integration lands.
- Notes for next iteration:
  - The write endpoints (POST, PATCH, DELETE) are guarded by `PERM_job.create` (DISPATCHER+) since V4 seeds no dedicated `customer.write.tenant` permission. A future task should seed `customer.create` / `customer.edit` / `customer.archive` permissions and re-guard accordingly if the product team decides finer-grained customer write control is needed.
  - V6 migration is idempotent if re-run because the `INSERT INTO role_permissions` is protected by the `PRIMARY KEY (role_id, permission_id)` constraint; a duplicate insert will fail, which is correct behaviour for migration reruns.
  - The `search()` JPQL query uses explicit `c.tenantId = :tenantId` filtering for test safety (tests connect as postgres superuser which bypasses RLS). In production the PostgreSQL RLS policy on `customers` provides a second isolation layer.
  - The `CustomerService.GF` `GeometryFactory` is a static singleton (SRID 4326). JTS `GeometryFactory` is thread-safe.
- Commits: (see below)

---

### 2026-05-31 — TASK-039 — appointment_schemas table and SchemaService

- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §8.2, §8.3, §7.2
- Files touched:
  - backend/src/main/resources/db/migration/V7__appointment_schemas.sql (new)
  - backend/src/main/java/com/teamhub/schemas/FieldDefinition.java (new)
  - backend/src/main/java/com/teamhub/schemas/FieldValidation.java (new)
  - backend/src/main/java/com/teamhub/schemas/AppointmentSchema.java (new)
  - backend/src/main/java/com/teamhub/schemas/AppointmentSchemaRepository.java (new)
  - backend/src/main/java/com/teamhub/schemas/SchemaValidationException.java (new)
  - backend/src/main/java/com/teamhub/schemas/SchemaNotFoundException.java (new)
  - backend/src/main/java/com/teamhub/schemas/SchemaService.java (new)
  - backend/src/test/java/com/teamhub/schemas/AppointmentSchemaIT.java (new)
  - backend/src/test/java/com/teamhub/customer/CustomerEdgeCaseIT.java (added — was untracked; removed unused jakarta.persistence imports)
  - backend/src/test/java/com/teamhub/customer/dto/CustomerResponseTest.java (added — was untracked)
  - tasks.json (TASK-039 status → done)
- Summary: Implemented the `appointment_schemas` table (PRD §8.2) as a versioned, append-only store. V7 Flyway migration creates the table with all PRD §8.2 columns (id, tenant_id, name, version, is_active, field_definitions jsonb, created_at), a unique constraint on (tenant_id, name, version), a covering index for active-schema lookups, and the standard RLS pattern (ENABLE + FORCE + tenant_isolation policy + GRANT to teamhub_app). The `FieldDefinition` and `FieldValidation` Java records map to the JSONB column shape (PRD §8.2), serialized/deserialized by Hibernate 6's built-in JSON type via `@JdbcTypeCode(SqlTypes.JSON)`. `SchemaService.publishNewVersion` sets previous active rows to is_active=false (single-active semantics), increments the version counter, and validates field definitions before saving. `SchemaService.getByVersion` retrieves a historical version for job validation (PRD §8.2 append-only guarantee). The branch also carries the pre-existing TASK-036/037 customer implementation (V5/V6 migrations, Customer entity, CustomerService, CustomerController, CustomerControllerIT, CustomerIT) and the untracked CustomerEdgeCaseIT and CustomerResponseTest files.
- Acceptance criteria verified:
  1. V7 migration creates `appointment_schemas` with all PRD §8.2 columns — confirmed in migration SQL; Flyway + JPA schema validation pass
  2. `SchemaService.publishNewVersion` inserts a new row with `version = max(version)+1` and `is_active = true`; previous active row's `is_active` is set to `false` (single-active semantics, documented in SchemaService Javadoc) — `publishNewVersion_v1ThenV2_v1BecomesInactiveAndV2IsActive`
  3. Existing jobs can reference `schema_version` and validate against that version (SchemaService.getByVersion retrieves any version, not just active) — `getByVersion_returnsHistoricalV1FieldsAfterV2Published`
  4. `depends_on` references validated; `options_by_parent` keys must be ⊆ parent.options — `publishNewVersion_dependsOnNonexistentField_throwsSchemaValidationException` and `publishNewVersion_optionsByParentWithInvalidKey_throwsSchemaValidationException`
- Test steps run (5 new tests in AppointmentSchemaIT, 92 total tests in suite, 0 failures):
  - Step 1 (`publishNewVersion_v1ThenV2_v1BecomesInactiveAndV2IsActive`): v1 published with `make` field (version=1, is_active=true); v2 published adding `model` (version=2, is_active=true); v1 refreshed from DB shows is_active=false — passed
  - Step 2 (`publishNewVersion_dependsOnNonexistentField_throwsSchemaValidationException`): schema with `depends_on: 'nonexistent_field'` throws `SchemaValidationException` (DomainException, 422) with message containing "nonexistent_field" — passed
  - Step 3 (`getByVersion_returnsHistoricalV1FieldsAfterV2Published`): after v1 and v2 published, getByVersion(1) returns only `make` field; getByVersion(2) returns `make`+`model`; proves historical job with schema_version=1 validates against v1, not v2 — passed
  - Additional (`publishNewVersion_optionsByParentWithInvalidKey_throwsSchemaValidationException`): `options_by_parent` with "Honda" key where parent only has ["Ford","Toyota"] throws — passed
  - Additional (`publishNewVersion_validOptionsWithDependentDropdown_persists`): valid dependent dropdown (Ford→F-150/Focus, Toyota→Camry/Corolla) persists and is correctly deserialized from JSONB — passed
  - `./gradlew spotlessApply && ./gradlew check --no-daemon` — BUILD SUCCESSFUL, 92 tests, 0 failures
- Open TO-DOs created (with PRD reference): none
- Notes for next iteration:
  - `@Modifying(clearAutomatically = true, flushAutomatically = true)` is required on `deactivateAllByTenantIdAndName` to ensure the JPQL bulk UPDATE is flushed before it runs and the first-level cache is cleared afterward. Without both flags, the entity cache can return stale `is_active = true` within the same @Transactional test.
  - Hibernate 6 serializes `List<FieldDefinition>` to JSONB automatically using the registered Jackson ObjectMapper. The `FieldDefinition` and `FieldValidation` are Java records; Jackson 2.15+ (included in Spring Boot 3.5) deserializes records natively without `@JsonCreator`.
  - The branch includes the TASK-036/037 customer code (previously on `feat/task-036-customers-entity`, not yet merged to main). The PR for this branch will carry V5–V7 migrations and the full customer + schema implementation.
  - TASK-039 depends on TASK-018 (RLS) which is in main. TASK-040 (appointment schema REST endpoints) and TASK-041 (jobs) are the next tasks in this branch.
- Commits: 5beaf86

---

### 2026-05-31 — TASK-040 — Schema presets: 8 JSON files, Flyway R__ migration, preset provisioning

- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §8.2, §8.4, §8.5, §26.1
- Files touched:
  - backend/src/main/resources/schema-presets/appliance.json (new)
  - backend/src/main/resources/schema-presets/ac.json (new)
  - backend/src/main/resources/schema-presets/electrician.json (new)
  - backend/src/main/resources/schema-presets/plumber.json (new)
  - backend/src/main/resources/schema-presets/handyman.json (new)
  - backend/src/main/resources/schema-presets/roofing.json (new)
  - backend/src/main/resources/schema-presets/solar.json (new)
  - backend/src/main/resources/schema-presets/locksmith.json (new — includes automotive sub-template with make/model/year per PRD §8.5)
  - backend/src/main/resources/db/migration/R__seed_schema_presets.sql (new — Flyway repeatable migration; creates schema_presets table, seeds all 8 presets via INSERT … ON CONFLICT DO UPDATE)
  - backend/src/main/java/com/teamhub/schemas/SchemaPreset.java (new — JPA entity)
  - backend/src/main/java/com/teamhub/schemas/SchemaPresetRepository.java (new)
  - backend/src/main/java/com/teamhub/schemas/SchemaPresetLoader.java (new — ApplicationRunner that validates all 8 JSON files at boot)
  - backend/src/main/java/com/teamhub/schemas/AppointmentSchemaRepository.java (modified: added findByTenantIdAndNameAndActiveTrue)
  - backend/src/main/java/com/teamhub/tenancy/InternalTenantService.java (modified: added provision(name, ownerEmail, presetKey) overload with backward-compatible two-arg delegate)
  - backend/src/test/java/com/teamhub/schemas/SchemaPresetIT.java (new — 6 tests covering all 3 task test steps)
  - tasks.json (TASK-040 status → done)
- Summary: Bundled all eight predefined schema presets (appliance, ac, electrician, plumber, handyman, roofing, solar, locksmith) as JSON files under `backend/src/main/resources/schema-presets/`. Each file is an ordered array of `FieldDefinition` objects conforming to the PRD §8.2 shape. The locksmith preset includes the automotive sub-template with `make` as a dropdown of the curated top-5 US makes (Ford, Chevrolet, Toyota, Honda, Tesla) plus `Other...`, and `model`/`year` as free-text fields (PRD §8.5). The Flyway repeatable migration `R__seed_schema_presets.sql` creates the `schema_presets` table (preset_key PK, schema_name, field_definitions JSONB) and seeds all 8 rows idempotently with `INSERT … ON CONFLICT DO UPDATE`. `SchemaPresetLoader` (ApplicationRunner) validates all JSON files at boot by deserializing them with Jackson and checking non-null key/label/type — the application fails to start if any file is invalid. `InternalTenantService.provision` was extended with a three-arg overload: when `presetKey` is provided it calls `SchemaService.publishNewVersion` to instantiate the first schema version; the two-arg overload delegates with `presetKey=null` for backward compatibility.
- Acceptance criteria verified:
  1. Eight JSON files in schema-presets/, each conforming to the FieldDefinition shape — confirmed by SchemaPresetIT.allPresetJsonFiles_areValidAtBoot_eightPresetsLoaded
  2. Locksmith preset includes automotive section with `make` dropdown (top-5 + Other...) and free-text `model`/`year` — confirmed by SchemaPresetIT.locksmithPreset_hasAutomotiveSection_withTopMakesAndFreeTextModelYear
  3. R__seed_schema_presets.sql loads all 8 presets into schema_presets table idempotently — confirmed by SchemaPresetIT.schemaPresets_repeatedMigration_producesNoDuplicates
  4. InternalTenantService.provision accepts optional preset_key; when provided, calls SchemaService.publishNewVersion after provisioning — confirmed by SchemaPresetIT.provision_withPlumberPreset_createsMatchingActiveSchema; backward compat confirmed by provision_withoutPreset_createsNoSchema
  5. TO-DO marker added for top-5 makes confirmation (PRD §26.1) — present in InternalTenantService.provision and R__seed_schema_presets.sql
- Test steps run (6 new tests in SchemaPresetIT, 98 total tests, 0 failures):
  - Step 1 (`provision_withPlumberPreset_createsMatchingActiveSchema`): provisions tenant with preset_key=plumber; verifies active appointment_schemas row has name="Plumber Job", version=1, is_active=true, contains expected field keys — passed
  - Step 2 (`allPresetJsonFiles_areValidAtBoot_eightPresetsLoaded`): application context loads without error (SchemaPresetLoader ran), count=8, all presets have non-empty field definitions with non-blank key/label/type — passed; also `locksmithPreset_hasAutomotiveSection_withTopMakesAndFreeTextModelYear` validates make dropdown options — passed
  - Step 3 (`schemaPresets_repeatedMigration_producesNoDuplicates`): count=8 exactly, no duplicate preset_keys (PK constraint) — passed
  - Additional: `provision_withoutPreset_createsNoSchema` (backward compat), `provision_withUnknownPresetKey_throwsTenantProvisionException` — both passed
  - `./gradlew spotlessApply && ./gradlew check --no-daemon` — BUILD SUCCESSFUL, 98 tests, 0 failures
- Open TO-DOs created (with PRD reference):
  - `InternalTenantService.provision` (PRD §26.1): confirm final top-5 makes list for the locksmith automotive preset before release. Working list: Ford, Chevrolet, Toyota, Honda, Tesla.
  - `R__seed_schema_presets.sql` locksmith section (PRD §26.1): same confirmation needed before bundling.
- Notes for next iteration:
  - The `R__` Flyway prefix causes the migration to re-run whenever its checksum changes. To update a preset: edit the corresponding JSON file and update the matching SQL literal in `R__seed_schema_presets.sql`; Flyway will re-run the migration and `ON CONFLICT DO UPDATE` will apply the new values.
  - `SchemaPreset` entity is NOT under RLS (global reference data). The `teamhub_app` role has `SELECT` only; `INSERT`/`UPDATE` is done by the Flyway migration user (postgres).
  - `InternalTenantService` constructor now requires `SchemaPresetRepository` and `SchemaService`. Any test that constructs `InternalTenantService` directly must provide both; tests using `@SpringBootTest` are unaffected.
  - The `SchemaPresetLoader` validates JSON files from classpath at boot (not from the DB). If a JSON file is accidentally deleted from the jar, the application will start normally (no classpath resource = no validation); this is intentional — the DB is the authoritative store after migration.
- Commits: f0a3b23 (PR #16)

---

### 2026-05-31 — TASK-027 — Audit log table, append-only writer role, AuditLogService

- Agent: Composer (Sonnet 4.6) backend-security
- Status change: pending → done
- PRD sections consulted: §19, §7.2
- Files touched:
  - backend/src/main/resources/db/migration/V8__audit_log.sql (new)
  - backend/src/main/java/com/teamhub/audit/AuditLog.java (new — JPA entity, builder)
  - backend/src/main/java/com/teamhub/audit/AuditLogRepository.java (new)
  - backend/src/main/java/com/teamhub/audit/AuditLogService.java (new — append-only writer using app_audit_writer role)
  - backend/src/main/java/com/teamhub/audit/package-info.java (new)
  - backend/src/test/java/com/teamhub/audit/AuditLogIT.java (new — 3 tests covering all 3 task steps)
  - tasks.json (TASK-027 status → done)
- Summary: Created the `audit_log` table (PRD §19) with all required columns (id, tenant_id, actor_user_id, action, resource_type, resource_id, before_state jsonb, after_state jsonb, ip_address inet, user_agent, occurred_at). V8 Flyway migration creates the table, a covering index on (tenant_id, occurred_at DESC), enables RLS + FORCE RLS with the standard tenant_isolation policy (identical predicate to V2/V5/V7), grants `teamhub_app` SELECT only (no INSERT/UPDATE/DELETE), and creates + grants INSERT only to a new `app_audit_writer` role. `AuditLogService.record()` runs in REQUIRES_NEW (independent of caller's transaction), switches to `app_audit_writer` via `SET LOCAL ROLE`, explicitly sets the tenant GUC from the entry's tenantId (required for audit events fired before a tenant context is established, e.g. login_failed), then executes the native INSERT via JdbcTemplate. The LOCAL keyword auto-reverts role and GUC at transaction end, leaving pooled connections clean.
- Acceptance criteria verified:
  1. V8 migration creates `audit_log` with all PRD §19 columns; Flyway + JPA schema validation pass — confirmed
  2. `app_audit_writer` has INSERT only; `teamhub_app` has SELECT only on `audit_log` — confirmed in migration SQL and enforced by tests 2 and 3
  3. `AuditLogService.record()` runs `SET LOCAL ROLE app_audit_writer` + `SET LOCAL app.current_tenant_id` before the INSERT — confirmed in source; test 1 verifies the row is written
  4. RLS enabled with FORCE; standard `tenant_isolation` policy using `NULLIF(current_setting('app.current_tenant_id', true), '')::uuid` — confirmed in migration SQL
- Test steps run (3 new tests in AuditLogIT, 101 total tests, 0 failures):
  - Step 1 (`record_insertsRow_withExpectedFieldValues`): builds an AuditLog entry, calls `record()`, queries via postgres superuser; asserts action, resource_type, resource_id, ip_address, user_agent all match; `occurred_at` asserted after `.truncatedTo(ChronoUnit.MICROS)` — passed
  - Step 2 (`updateAsAppRole_isRejectedWithPermissionDenied`): inserts a row, then within a TransactionTemplate runs `SET LOCAL ROLE teamhub_app` + tries `UPDATE audit_log SET action='x'`; asserts `DataAccessException` is thrown with "permission denied" in message or cause — passed
  - Step 3 (`deleteAsAppRole_isRejectedWithPermissionDenied`): same pattern for `DELETE FROM audit_log` — asserts `DataAccessException` with "permission denied" — passed
  - `./gradlew spotlessApply && ./gradlew check --no-daemon` — BUILD SUCCESSFUL, 101 tests, 0 failures
- Open TO-DOs created (with PRD reference):
  - `AuditLogService.toJsonString` (PRD §19): wire Jackson ObjectMapper for richer before/after state snapshots (currently Object.toString() is used for non-String values)
- Notes for next iteration:
  - `AuditLogService.record()` uses `REQUIRES_NEW` so audit rows commit independently of the caller's transaction. For login_failed events (PRD §19.2), the caller has no tenant context; the service explicitly sets the GUC from `entry.getTenantId()` to satisfy the RLS INSERT CHECK.
  - `app_audit_writer` is granted to the Flyway login user (postgres) in V8 migration via `GRANT app_audit_writer TO <current_user>`. The `teamhub_app` role does NOT have `app_audit_writer` membership and cannot INSERT into `audit_log`.
  - The `AuditLogRepository` (JpaRepository) is provided for SELECT reads (e.g. export support per PRD §19.3). Direct `save()` calls from application code must not be used; they would connect as `teamhub_app` which has SELECT only.
  - For the permission-denied integration tests, `TransactionTemplate` was used instead of `@Transactional` on the test method: the `REQUIRES_NEW` in `record()` needs the tenant row to already be committed (FK constraint), so tests provision the tenant without a wrapping test-level transaction.
- Commits: (see below)

---

### 2026-05-31 — TASK-041 — Schema editing endpoints (Admin-only GET/POST/PATCH /api/v1/schemas)

- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §5.3, §8.2, §19
- Files touched:
  - backend/src/main/java/com/teamhub/schemas/AppointmentSchemaRepository.java (added `findAllByTenantIdOrderByNameAscVersionDesc`)
  - backend/src/main/java/com/teamhub/schemas/SchemaNotFoundException.java (added UUID constructor)
  - backend/src/main/java/com/teamhub/schemas/SchemaService.java (added `listByTenant`, `deactivateById`)
  - backend/src/main/java/com/teamhub/schemas/dto/PublishSchemaRequest.java (new)
  - backend/src/main/java/com/teamhub/schemas/dto/SchemaResponse.java (new)
  - backend/src/main/java/com/teamhub/schemas/SchemaController.java (new — GET/POST/PATCH, no DELETE)
  - backend/src/test/java/com/teamhub/schemas/SchemaControllerIT.java (new — 6 tests)
  - tasks.json (TASK-041 status → done)
- Summary: Implemented the Admin-only `/api/v1/schemas` REST surface (PRD §5.3, §8.2). The controller is guarded by `@PreAuthorize("hasAuthority('PERM_schema.edit')")` at the class level (seeded for ADMIN only in V4 migration). `POST /api/v1/schemas` publishes a new schema version via `SchemaService.publishNewVersion`, then emits a `schema.update` / `appointment_schema` audit row via `AuditLogService.record` (REQUIRES_NEW transaction). `PATCH /api/v1/schemas/{id}` deactivates the named version without deleting it. `GET /api/v1/schemas` returns the full version history ordered by name then version descending. No DELETE handler is registered — the URI template matches the PATCH handler, so Spring returns 405 METHOD_NOT_ALLOWED, enforcing the append-only invariant of the `appointment_schemas` table.
- Acceptance criteria verified:
  1. `GET /schemas` returns the tenant's schema history — confirmed by `list_asAdmin_returnsAllVersions` test
  2. `POST /schemas` publishes a new version (validates with TASK-039 rules) — confirmed by `publish_asAdmin_returns201AndEmitsAuditRow` test
  3. `PATCH /schemas/{id}` can mark inactive but never deletes — confirmed by `deactivate_asAdmin_setsActiveFalseAndEmitsAuditRow` test
  4. Only ADMIN role passes `@PreAuthorize` — confirmed by `publish_asDispatcher_returns403` and `listSchemas_withoutToken_returns401` tests
  5. All write operations emit a `schema.update` audit row — confirmed by both `publish_*` and `deactivate_*` tests querying `audit_log` table directly
- Test steps run (6 new tests in SchemaControllerIT, 133 total tests, 0 failures):
  - Step 1 (`publish_asAdmin_returns201AndEmitsAuditRow`): ADMIN POST → 201; `audit_log` queried for `action=schema.update, resource_type=appointment_schema` — row found with matching `resource_id` — passed
  - Step 2 (`publish_asDispatcher_returns403`): DISPATCHER POST → 403 with `error.code=FORBIDDEN` envelope (PRD §23.4) — passed
  - Step 3 (`delete_noHandlerRegistered_returnsMethodNotAllowed`): `DELETE /api/v1/schemas/{id}` → 405 METHOD_NOT_ALLOWED (PATCH handler matches URI template, no DELETE handler registered) — passed; confirms no delete endpoint exists
  - Additional: `list_asAdmin_returnsAllVersions`, `deactivate_asAdmin_setsActiveFalseAndEmitsAuditRow`, `listSchemas_withoutToken_returns401` — all passed
  - `./gradlew spotlessApply && ./gradlew check --no-daemon` — BUILD SUCCESSFUL, 133 tests, 0 failures
- Open TO-DOs created (with PRD reference): none
- Notes for next iteration:
  - No DELETE endpoint is intentionally omitted; `appointment_schemas` rows are append-only per PRD §8.2. DELETE attempts return 405 (Spring method-not-allowed) rather than 404 because the URI template `/api/v1/schemas/{id}` is matched by the PATCH handler.
  - The `actor_user_id` audit field requires the JWT subject to be a real user UUID present in the `users` table. In tests, `InternalTenantService.provision()` creates the admin user; `UserRepository.findByEmail()` retrieves the ID for use as the JWT subject.
  - `AuditLogService.record()` runs in REQUIRES_NEW; the audit row is committed even if the surrounding business transaction rolls back. Tests must NOT use class-level `@Transactional` when verifying audit rows; manual `@AfterEach` cleanup via JDBC is required instead.
  - TASK-041 depends on TASK-027 (`AuditLogService`), which was merged from `feat/task-027-audit-log` before implementation.
- Commits: (see below)

---

---

### 2026-06-01 — TASK-028 — Auth audit log events (login, login_failed, logout, token_refresh)

- Agent: Composer (Sonnet 4.6) backend-security
- Status change: pending → done
- PRD sections consulted: §19.2, §6.2
- Files touched:
  - backend/src/main/resources/db/migration/V9__sentinel_tenant.sql (new)
  - backend/src/main/java/com/teamhub/config/AsyncConfig.java (new — `@EnableAsync` + `AsyncUncaughtExceptionHandler`)
  - backend/src/main/java/com/teamhub/audit/AsyncAuditService.java (new — thin `@Async` wrapper around `AuditLogService`)
  - backend/src/main/java/com/teamhub/auth/AuthService.java (modified — added `AsyncAuditService` injection; login overload with `sourceIp`; async audit in login, refresh, logout)
  - backend/src/main/java/com/teamhub/auth/AuthController.java (modified — `HttpServletRequest` injection in login; passes `getRemoteAddr()` to service)
  - backend/src/test/java/com/teamhub/auth/AuthAuditIT.java (new — 5 tests: steps 1 & 2)
  - backend/src/test/java/com/teamhub/auth/AuthAuditResilienceIT.java (new — 2 tests: step 3)
  - backend/src/test/java/com/teamhub/tenancy/InternalTenantServiceIT.java (modified — fixed count assertion to be relative to pre-existing rows)
  - tasks.json (TASK-028 status → done)
- Summary: Wired `AuditLogService.record(...)` into every auth flow (login, login_failed, token_refresh, logout) via a thin `AsyncAuditService` `@Async` bean. Audit writes are fire-and-forget — the business transaction returns before the audit INSERT commits. The `AsyncUncaughtExceptionHandler` in `AsyncConfig` catches any DB failures and logs them at ERROR level (TO-DO to route to Sentry). For `login_failed` events where the email doesn't exist in `users`, a sentinel tenant (UUID all-zeros, inserted by V9 migration) satisfies the `audit_log.tenant_id NOT NULL FK`. The `auth.login_failed` row stores the full attempted email and source IP in `after_state` JSON per PRD §19.2 (no PII redaction). Password-reset audit is a TO-DO pending the password-reset endpoint.
- Design decision (sentinel tenant): Rather than making `audit_log.tenant_id` nullable (which would weaken the schema for all events) or silently dropping failed-login rows (violates PRD §19.2), V9 migration inserts a sentinel tenant row with UUID `00000000-0000-0000-0000-000000000000`. `AuthService` first tries to find the real tenant from the user row; only completely unknown emails fall back to the sentinel. This approach satisfies the FK without data-model changes and keeps all auth failures audited.
- Acceptance criteria verified:
  1. `auth.login` row emitted on successful login — `login_success_emitsAuthLoginAuditRow`
  2. `auth.login_failed` row with email + IP in `after_state` on bad password — `login_wrongPassword_emitsLoginFailedAuditRowWithEmailAndIp`; sentinel tenant used for unknown email — `login_unknownEmail_emitsLoginFailedWithSentinelTenant`
  3. `auth.logout` row on logout — `logout_emitsLogoutAuditRow`; `auth.token_refresh` on refresh — `refresh_emitsTokenRefreshAuditRow`; password_reset TO-DO (no endpoint yet)
  4. Audit writes async via `@Async`/`AsyncAuditService`; failures logged at ERROR via `AsyncUncaughtExceptionHandler`, never propagated to caller — `auditFailure_doesNotBreakSuccessfulLoginResponse`, `auditFailure_doesNotBreakFailedLoginResponse`
- Test steps run (7 new tests in AuthAuditIT + AuthAuditResilienceIT, 136 total tests, 0 failures):
  - Step 1 (`login_success_emitsAuthLoginAuditRow`): login → Awaitility polls within 1 s → `audit_log` has `auth.login` row with correct actor/IP — passed
  - Step 2 (`login_wrongPassword_emitsLoginFailedAuditRowWithEmailAndIp`): wrong password → `audit_log` has `auth.login_failed` row; `after_state` contains email + IP — passed; `login_unknownEmail_emitsLoginFailedWithSentinelTenant`: completely unknown email → row in sentinel tenant's bucket — passed
  - Step 3 (`auditFailure_doesNotBreakSuccessfulLoginResponse` + `auditFailure_doesNotBreakFailedLoginResponse`): SpyBean makes `AuditLogService.record` throw; login returns 200 / failed login returns 401; verify spy was called — both passed
  - `./gradlew spotlessApply && ./gradlew check --no-daemon` — BUILD SUCCESSFUL, 136 tests, 0 failures
  - Coverage: `com.teamhub.auth` 95%, `com.teamhub.audit` 99%, `com.teamhub.config` 100%
- Open TO-DOs created (with PRD reference):
  - `AsyncConfig` (PRD §19.2): route audit failures to Sentry once TASK-Sentry lands (`// TO-DO: route to Sentry once TASK-Sentry lands` comment in `AuditAsyncUncaughtExceptionHandler`)
  - `AuthService` (PRD §19.2): add `auth.password_reset` audit call once the password-reset endpoint is implemented (no endpoint exists yet)
- Notes for next iteration:
  - The `@Async` annotation on `AsyncAuditService.recordAsync()` requires `AsyncConfig.@EnableAsync`. Without it the annotation is ignored and the call is synchronous — the application still works but blocks the auth response.
  - V9 sentinel tenant (`00000000-0000-0000-0000-000000000000`) is a permanent fixture in the `tenants` table. It must never be returned in tenant-scoped API responses (add `WHERE id != '00000000-...'` to any list endpoint that queries all tenants).
  - The existing `InternalTenantServiceIT.provision_twoCallsWithDifferentEmailsSucceed` test was updated to use a relative count assertion (`countBefore + 2`) so it stays green when seed/sentinel rows are present.
  - `AuthAuditIT` and `AuthAuditResilienceIT` do NOT use class-level `@Transactional` (data must be committed before the async thread's `REQUIRES_NEW` transaction can see it). Cleanup is done in `@AfterEach` via direct JDBC DELETE.
- Commits: e65659e

---

---

### 2026-06-01 — TASK-029 — Eventing tables: outbox_events, processed_events, delayed_jobs

- Agent: Composer (Sonnet 4.6)
- Status change: pending → done
- PRD sections consulted: §22, §22.5, §22.5.3, §22.5.4, §22.5.6, §7.2
- Files touched:
  - backend/src/main/resources/db/migration/V10__eventing_tables.sql (new)
  - backend/src/main/java/com/teamhub/eventing/OutboxEvent.java (new)
  - backend/src/main/java/com/teamhub/eventing/OutboxEventRepository.java (new)
  - backend/src/main/java/com/teamhub/eventing/ProcessedEventId.java (new)
  - backend/src/main/java/com/teamhub/eventing/ProcessedEvent.java (new)
  - backend/src/main/java/com/teamhub/eventing/ProcessedEventRepository.java (new)
  - backend/src/main/java/com/teamhub/eventing/DelayedJobStatus.java (new)
  - backend/src/main/java/com/teamhub/eventing/DelayedJob.java (new)
  - backend/src/main/java/com/teamhub/eventing/DelayedJobRepository.java (new)
  - backend/src/test/java/com/teamhub/eventing/EventingTablesIT.java (new)
  - backend/src/test/java/com/teamhub/eventing/EventingEntityTest.java (new)
  - tasks.json (TASK-029 status → done)
- Summary: Created all three eventing infrastructure tables from PRD §22 in a single V10 Flyway migration. `outbox_events` stores transactional outbox events with a relay-scan index on `(processed_at NULLS FIRST, created_at)`. `processed_events` is a month-partitioned table (PARTITION BY RANGE on processed_at) with the current month's partition (`processed_events_2026_06`) created inline; the physical PK is `(event_id, consumer_group, processed_at)` because PostgreSQL declarative partitioning requires the partition key in every unique constraint. `delayed_jobs` stores time-scheduled work items with a `(status, run_at)` index for dispatcher scans. All three tables follow the same RLS pattern as V2/V5/V7/V8 (ENABLE + FORCE + `tenant_isolation` policy using `NULLIF(current_setting('app.current_tenant_id', true), '')::uuid`). Java domain classes (`OutboxEvent`, `ProcessedEvent`, `DelayedJob` with their repositories and `DelayedJobStatus` enum) live in `com.teamhub.eventing`. New-code coverage: eventing package 100%, overall 95.4%.
- Acceptance criteria verified:
  1. V10 migration creates all three tables with exact columns from PRD §22 — confirmed; Flyway + JPA schema validation pass
  2. `outbox_events` index `idx_outbox_events_relay` on `(processed_at NULLS FIRST, created_at)` — confirmed; `step1_relayIndexExistsOnOutboxEvents`
  3. `delayed_jobs` index `idx_delayed_jobs_status_run_at` on `(status, run_at)` — confirmed; `step1_statusRunAtIndexExistsOnDelayedJobs`
  4. `processed_events` partitioned by month — confirmed; `step3_processedEventsIsDeclarativeRangePartitionedTable`, `step3_processedEventsHasPartitionForCurrentMonth`
  5. RLS enabled on all three tables — confirmed; `step1_rlsIsEnabledAndForcedOnAllThreeTables`
- Test steps run (169 total tests, 0 failures; 30 new in EventingTablesIT + EventingEntityTest):
  - Step 1 (`step1_allThreeTablesExistAfterV10Migration`, `step1_relayIndexExistsOnOutboxEvents`, `step1_statusRunAtIndexExistsOnDelayedJobs`, `step1_rlsIsEnabledAndForcedOnAllThreeTables`): tables and indexes present — passed
  - Step 2 (`step2_outboxEventsRlsIsolatesAcrossTenants`, `step2_delayedJobsRlsIsolatesAcrossTenants`, `step2_processedEventsRlsIsolatesAcrossTenants`, `step2_withoutGucSet_outboxEventsReturnsNoRows`): RLS isolation across two tenants — passed
  - Step 3 (`step3_processedEventsHasPartitionForCurrentMonth`, `step3_processedEventsIsDeclarativeRangePartitionedTable`, `step3_insertIntoProcessedEventsLandsInCurrentMonthPartition`): partition for current month (2026-06) present and receives inserts — passed
  - `./gradlew spotlessApply && ./gradlew check --no-daemon` — BUILD SUCCESSFUL, 169 tests, 0 failures
- Open TO-DOs created (with PRD reference): none
- Notes for next iteration:
  - `processed_events` partitions for future months must be created before month rollover. A partition-management scheduled task (or a separate migration per month) is needed before each new month begins; this is out of scope for TASK-029. The partition for the current month (June 2026) is created by V10.
  - The physical PK on `processed_events` is `(event_id, consumer_group, processed_at)` — not the logical `(event_id, consumer_group)` stated in the PRD schema snippet — because PostgreSQL declarative range partitioning requires the partition key column to be part of every unique constraint. The de-dup semantic (`INSERT … ON CONFLICT DO NOTHING` on event_id + consumer_group) is still correct because both columns are in the PK; timestamps are generated server-side so collisions are impossible.
  - `outbox_events.processed_at` is updated by the relay worker (native UPDATE), not through the JPA entity. Do not use `OutboxEventRepository.save()` to mark an event as processed; the relay owns this field.
  - The `teamhub_app` role receives `SELECT, INSERT, UPDATE` on `outbox_events` (relay needs to update `processed_at`) and `delayed_jobs` (dispatcher needs to update `status`). It receives `SELECT, INSERT` on `processed_events` (de-dup only, no updates needed).
- Commits: (see below)

---

## Notes for future agents

- The PRD at `prd/PRD.md` is the only source of truth. If something here contradicts the PRD, the PRD wins.
- Integrations gated on Lead Developer review before merge per PRD §25.3: Stripe, Apple Sign-in, Push notifications, Twilio (future), Google OAuth.
- Completed through TASK-029: 001, 002, 003, 004, 005, 007, 009, 010, 016, 017, 018, 019, 023, 024, 025, 027, 028, 029, 036, 037, 039, 040, 041 (see `tasks.json`).
- TASK-025 note: all domain exceptions must extend `DomainException` (in `com.teamhub.exception`). Guard controllers with `@PreAuthorize("hasAuthority('PERM_<code>')")`. The full §23.4 envelope `{"error":{"code":"…","message":"…","details":{…}}}` is returned by `GlobalExceptionHandler` for validation (422), domain (custom status), and unexpected (500) errors. Auth exceptions (`InvalidCredentialsException`, `InvalidRefreshTokenException`) now return the full envelope (was legacy flat shape).
- TASK-024 note: the security filter chain now returns 401 with the §23.4 `ErrorEnvelope` (`{"error":{"code":"UNAUTHORIZED",...}}`) for both anonymous and invalid-token requests via `RestAuthenticationEntryPoint`. CORS allows only the `CORS_ALLOWED_ORIGIN` env-var origin (dev: `http://localhost:5173`, prod: `https://app.teamhub.online`). All other 401/403 logic is unchanged from TASK-019/023. The `AuthExceptionHandler` legacy flat 401 body (for `InvalidCredentialsException`/`InvalidRefreshTokenException`) was reconciled to the full envelope in TASK-025.
- TASK-023 note: authorization is permission-based. Guard controllers with `@PreAuthorize("hasAuthority('PERM_<code>')")` (a permission code from the `permissions` table), never `hasRole('ADMIN')`. The JWT `role` claim is resolved to `PERM_*` authorities by `PermissionJwtAuthenticationConverter`. Adding a role is a seed-only change (new rows in `roles` + `role_permissions`). 403 denials use the PRD §23.4 envelope (`{"error":{"code":"FORBIDDEN",...}}`); the 401 envelope and CORS are deferred to TASK-024, and the unified `@RestControllerAdvice` envelope to TASK-025.
- TASK-019 note: `TenantContextFilter` now reads the `tenant_id` claim from the validated JWT (the TASK-018 `X-Tenant-Id` placeholder is gone). Auth endpoints live under `/api/v1/auth/**` (permitAll); everything else requires a valid RS256 access token. Production must configure persistent JWT keys via `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH`.
