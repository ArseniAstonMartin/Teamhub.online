# Teamhub — Product Requirements Document


| Field            | Value                                       |
| ---------------- | ------------------------------------------- |
| Project          | Teamhub.online                              |
| Document         | Master PRD                                  |
| Status           | MVP released (`v1.0.0`)                     |
| Version          | 1.0.0                                       |
| Date             | 2026-07-04                                  |
| Owner            | Project Lead (Human)                        |
| Repository       | https://github.com/ArseniAstonMartin/Teamhub.online.git |
| Source documents | `prd/Thoughts`, `prd/Design`, `prd/My_Spam` |


This PRD supersedes the loose notes in `Thoughts`, `Design`, and `My_Spam`. Where this PRD differs from those documents, this PRD wins. Anything still requiring a human decision is marked with a `TO-DO` block so the IDE flags it before commit.

### Terminology convention — "Tenant" vs "Team"

The terms **Tenant** and **Team** are interchangeable in this product:

- **"Tenant"** is the technical / engineering term — used in code, database schema (`tenant_id`), API paths, internal documentation, and this PRD.
- **"Team"** is the user-facing term — used in **all UI copy**, marketing site, emails, support content, and any string an end user reads.

Engineers must never expose the word "Tenant" in the UI; product managers and designers must never use "Team" in technical specs or DB design. The two refer to the same entity: a single isolated workspace inside Teamhub.

---

## 1. Executive Summary

Teamhub is a multi-tenant SaaS Field Service Management (FSM) and CRM platform for US small and mid-size field-service businesses (plumbers, electricians, cleaners, locksmiths, HVAC, handyman, roofing, solar). A single workspace lets the office (Admin + Dispatcher) take calls, create jobs with business-specific custom fields, broadcast jobs to a shared pool, and let Technicians claim and complete them from a mobile app — with photos, notes, and (post-MVP) on-site payments and phone-number masking.

The product differentiates on **deep schema customization** (each tenant defines the exact fields their jobs need — vehicle make/model/year, square footage, panel amperage, etc.) and on **operational discipline** (every job tracked end-to-end, no leakage to private phone numbers).

---

## 2. Vision and Positioning

### 2.1 What Teamhub is

A bright, professional, light-themed B2B workspace where dispatch, scheduling, customer records, technician execution, and reporting live in one product. Reference products: **Jobber, Workiz, Housecall Pro, WePro**.

### 2.2 What Teamhub is NOT

- Not a consumer marketplace (no customer self-service portal in MVP).
- Not vertical-locked (not automotive-only — automotive is just one example domain).
- Not entertainment software — visual language is closer to Jira / Microsoft / Google / Slack than to gaming or fintech.
- Not HIPAA-eligible in MVP. Tenants may **not** store Protected Health Information; the ToS prohibits PHI.

### 2.3 Primary differentiator

Per-tenant **appointment schema customization**: an admin can add, remove, reorder, and constrain fields on a job/appointment form, including dependent dropdowns (e.g. *model* depends on *make*; *year* depends on *model*).

---

## 3. Target Audience

### 3.1 Tenants (paying customers)

US-based field-service SMBs with 2–50 employees:

- Locksmiths (incl. automotive locksmiths)
- Plumbers
- Electricians
- HVAC / AC
- Appliance repair
- Handyman
- Roofing
- Solar panel installers
- Cleaning companies

### 3.2 End users (personas inside a tenant)


| Persona        | Description                                 | Primary surface | Also available on |
| -------------- | ------------------------------------------- | --------------- | ----------------- |
| **Admin**      | Business owner / operations manager         | Web app         | Mobile app        |
| **Dispatcher** | Office staff taking calls and creating jobs | Web app         | Mobile app        |
| **Technician** | Field worker executing jobs                 | Mobile app      | Web app           |

The **Primary surface** column reflects where each persona spends the vast majority of their time and which UX is optimized for them. It does **not** restrict access. Every persona can sign in on both platforms; the same JWT works on both. Mobile builds for office personas and web builds for technicians may have a reduced feature set during MVP (e.g. the schema editor lives on web only, the five-button job card lives on mobile only) but the role's data and permissions are identical across surfaces.


### 3.3 Out of scope for MVP

Customers of the tenant **are not users**; they cannot log in. They exist only as records inside a tenant's workspace. A self-service Client portal is a future expansion.

---

## 4. Scope and Phasing

The product is delivered in three phases. Phase 0 exists only as an internal milestone; the public launch is at the end of MVP.

### 4.1 Phase 0 — Internal foundation milestone (not shipped)

A vertical slice proving the platform works end-to-end before MVP feature breadth is layered on:

- Multi-tenant backend with shared DB + `tenant_id` + PostgreSQL Row-Level Security.
- JWT auth (email/password) for two roles: Dispatcher and Technician.
- Job CRUD with the default preset schema (no admin customization yet).
- Web SPA with login, job list, basic calendar view.
- Mobile app with login and job list.
- Deployed to Hetzner via Coolify.

### 4.2 MVP (v1.0) — public launch target (released as `v1.0.0`)


| Area                 | In MVP                                                                                   |
| -------------------- | ---------------------------------------------------------------------------------------- |
| Web app              | Admin + Dispatcher full workflow (schema customization, calendar, kanban, customer list) |
| Mobile app           | Technician (RN + Expo, iOS + Android via EAS, App Store + Play Store)                    |
| Job lifecycle        | Full status state machine including shared-pool claim flow                               |
| Shared pool          | Red blinking unclaimed-job card; 5-minute escalation push to admin                       |
| Customer records     | Customer entity + read-only list/detail screen on web and mobile                         |
| Schema customization | Admin can add/edit/remove fields and dependent dropdowns                                 |
| Photo capture        | Object-storage upload from mobile via backend-issued pre-signed URLs                     |
| Push notifications   | Expo Push Service                                                                        |
| Automations          | Admin-configurable field auto-fill rules                                                 |
| Subscription billing | Stripe Billing — Teamhub charges tenants Free / Starter / Team / Enterprise              |
| Audit log            | Server-side append-only table for sensitive actions (no admin UI yet)                    |
| Auth                 | Email/password + JWT, Google OAuth 2.0, Apple Sign-in                                    |
| Onboarding           | Assisted / white-glove (Teamhub team)                                                    |
| Internal admin console | Minimal Teamhub-staff-only surface for tenant provisioning and operator support        |


### 4.3 v1.1 and beyond — explicitly NOT in MVP


| Feature                                                               | Reason for exclusion / target version                                              |
| --------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| **Stripe Connect + Tap-to-Pay (on-site payment)**                     | Was in original Thoughts §3.6.1; cut from MVP to reduce risk surface. Target v1.1. |
| **Twilio SMS** ("I'm on my way" automated text)                       | Removed from MVP. Target v1.1.                                                     |
| **Twilio voice** (call recording, listening, phone-number masking)    | Target v1.2.                                                                       |
| **Live technician location** (REST POST every 60s during In Progress) | Target v1.1.                                                                       |
| **Self-service tenant signup wizard**                                 | Target v1.1. White-glove only in MVP.                                              |
| **Marketing site** at `teamhub.online` (SSR MPA)                      | Target post-launch.                                                                |
| **Real-time chat** via WebSockets                                     | Target v1.2+.                                                                      |
| **Call history** UI                                                   | Target v1.2+ (depends on Twilio voice).                                            |
| **Recurring scheduling** (e.g. weekly cleaning)                       | Target v1.2+.                                                                      |
| **Advanced dispatch algorithm** (TSP-style optimization)              | Target v1.3+.                                                                      |
| **Admin audit-log UI** ("see every click")                            | Target v1.2+. Server-side log exists in MVP.                                       |
| **Impersonation**                                                     | Target v1.2+.                                                                      |
| **Client role** (customer self-service)                               | Target v2.0+.                                                                      |
| **Manager, Accountant roles**                                         | Target v2.0+.                                                                      |
| **Inventory management**                                              | Target v2.0+.                                                                      |
| **Payout / payroll computation**                                      | Target v2.0+.                                                                      |
| **AI-assisted call answering**                                        | Target v2.0+.                                                                      |
| **B2B2C marketplace mode**                                            | Long-term, low priority.                                                           |
| **i18n translations**                                                 | English-only at MVP; i18n architecture in place but no translations shipped.       |


---

## 5. Roles, Permissions, and Access Scope

### 5.1 Role hierarchy

Admin ⊇ Dispatcher ⊇ Technician — **strictly additive**, no exceptions and no circular inheritance.

### 5.2 Per-role capabilities

#### Admin

- All Dispatcher and Technician capabilities.
- Configure appointment schema for the tenant (add/edit/remove fields, set dependencies, mark required).
- Configure automations (rule-based field auto-fill).
- Toggle the "Close Jobs" permission for the Technician role.
- Manage seats: invite, deactivate, reassign role.
- View tenant-wide reports for any time window (day/week/month/year).
- View tenant billing and change subscription tier.
- **(v1.2+)** Impersonate users; browse audit log UI.

#### Dispatcher

- All Technician capabilities.
- Create, edit, and reschedule any job in the tenant.
- See all customers in the tenant.
- Move jobs into the shared pool.
- Transition jobs between any allowed status, including out of `Canceled`.
- Clear `pending_reason`, `cancellation_reason`, `in_progress_substatus`.
- Move `Done pending approval` → `Completed` after verifying.
- Cannot permanently delete records (delete = soft-delete to `Archived`).

#### Technician

- View **only** jobs assigned to themselves or jobs in the shared pool visible to their tenant.
- Claim a job from the shared pool (transitions `Submitted` → `Assigned to Technician`).
- Transition own job to: `In Progress` (with sub-status `On the Way` or `Working`), `Pending` (with reason), `Done pending approval`, `Completed` (if Close Jobs permission is enabled).
- Set ETA (30 min / 45 min / Custom — fixed list).
- Add notes and photos to own job.
- View basic team info: other technicians' names, company details, shared equipment list.
- Cannot view other technicians' invoices, financials, or personal data.
- Cannot delete jobs.

### 5.3 Sensitive actions


| Action                       | Restriction        |
| ---------------------------- | ------------------ |
| View full payment history    | Admin only         |
| Permanently delete a record  | Admin only         |
| Soft-delete (archive)        | Dispatcher + Admin |
| Move a job out of `Canceled` | Dispatcher + Admin |
| Configure tenant schema      | Admin only         |
| Configure automations        | Admin only         |
| Toggle Close Jobs permission | Admin only         |
| Manage tenant billing        | Admin only         |


### 5.4 Forward compatibility

The permission model must be designed to accommodate **future roles** without refactor:

- `Client` (customer self-service) — v2.0+
- `Manager` (mid-tier oversight) — v2.0+
- `Accountant` (read-only financial) — v2.0+

Use a flexible role + permission model (role → permission set), not hard-coded `if (role == ADMIN)` checks scattered through the code.

### 5.5 Authorization implementation

- Spring Security with `@PreAuthorize` / `@Secured` method-level annotations.
- URL-level filters as a second layer.
- Tenant isolation enforced at the database layer via PostgreSQL Row-Level Security on `tenant_id`.
- Unauthorized cross-tenant or cross-role access must return **HTTP 403 Forbidden**, not 404 or 401.

### 5.6 Required authorization test matrix (must be in CI for every release)

Each release must include automated tests proving:

- Technician cannot read another technician's job (cross-row in same tenant) → 403.
- Technician cannot read any data from another tenant → 403.
- Technician cannot transition a job to `Completed` when Close Jobs is disabled → 403.
- Dispatcher cannot hard-delete a job → 403.
- Dispatcher cannot view another tenant's data → 403.
- Admin in tenant A cannot read tenant B → 403.

---

## 6. Authentication and Onboarding

### 6.1 Authentication methods (MVP)

- **Email + password** with BCrypt hashing via Spring Security `PasswordEncoder`.
- **Google OAuth 2.0** via Spring Security OAuth2 Client.
- **Apple Sign-in** (required by App Store guidelines once any third-party auth is offered on iOS).

### 6.2 Token model

- JWT **access token**, 15-minute lifetime, signed with rotating asymmetric key (RS256).
- JWT **refresh token**, 30-day rolling lifetime, stored server-side with revocation list.
- Mobile clients keep both tokens in OS secure storage (Keychain / Keystore via Expo SecureStore).
- Logout revokes the refresh token server-side.

### 6.3 Password reset

- Email-based reset via signed time-limited token (TTL 30 min).
- Rate-limited: 5 attempts per email per hour.

### 6.4 Tenant onboarding (MVP — assisted)

1. Prospect contacts Teamhub via marketing site or sales channel.
2. Teamhub team creates the tenant workspace in an internal admin console (out-of-app, server-side only for MVP).
3. Teamhub team selects the closest predefined schema (Appliance, AC, Electrician, Plumber, Handyman, Roofing, Solar panel, Locksmith) and customizes if needed.
4. Teamhub team creates the first Admin user and sends an invite email.
5. The Admin signs in and invites Dispatchers and Technicians by email; each invite specifies role.

**MVP requirement** — A minimal internal Teamhub admin console (a separate, super-admin-only surface, not exposed to tenants) for provisioning tenants, selecting a starter schema preset, creating the first Admin user, and triggering invite emails. The console is intentionally minimal: just enough to make assisted onboarding (§6.4) operationally repeatable. Must be auth-gated behind a Teamhub-staff-only role and protected by IP allow-list. Not exposed on `app.teamhub.online`.

### 6.5 User onboarding inside a tenant

- Admin enters invitee's email and selects role → server sends invite email with signed token.
- Invitee clicks link → sets password (or signs in with Google/Apple) → account is activated and bound to the tenant.

### 6.6 Acceptance criteria

- A new user signed in for the first time lands on a role-appropriate home screen.
- Password reset email arrives within 30 seconds.
- A revoked refresh token cannot be used to obtain a new access token (returns 401).
- Cross-tenant authentication is impossible: a JWT issued for tenant A cannot read tenant B data.

---

## 7. Multi-Tenancy

### 7.1 Model

**Shared database, shared schema, `tenant_id` column on every business table**, enforced with PostgreSQL Row-Level Security (RLS).

### 7.2 Implementation rules

- Every business-domain table has a non-null `tenant_id UUID` foreign key to `tenants(id)`.
- A Postgres session variable `app.current_tenant_id` is set per request from the JWT.
- RLS policies on each table: `USING (tenant_id = current_setting('app.current_tenant_id')::uuid)`.
- Backend uses a per-request DB session/transaction that sets the GUC before any query.
- Migrations (Flyway) enable RLS and define policies for every new table.

### 7.3 Why this model

- Cheapest to operate (one DB, one schema).
- Easiest to back up and observe.
- Comfortably covers the 500-tenant / 20,000-MAU target with significant headroom on modest hardware.
- RLS makes accidental cross-tenant leakage in application code impossible.

### 7.4 Future migration path

When Enterprise tenants demand stronger isolation, individual tenants can be moved to dedicated databases. The application's repository layer must route by tenant ID, so the data-source selection is the only change needed.

---

## 8. Appointment Schema and Customization

### 8.1 Default appointment fields (preset baseline)

All tenants start with these built-in fields. Admins can hide or extend, but cannot delete the customer name or scheduled-time fields.

**Customer info**

- Name (text, required)
- Company name (text, optional)
- Phone number (E.164, required)
- Email (email, optional)

**Address**

- Address line (free text + **Google Places Autocomplete**)
- City (text, autofilled from Places)
- State (text, autofilled from Places)
- ZIP code (text, autofilled from Places)
- Geocoded `(lat, lon)` stored as PostGIS `geography(Point, 4326)`

**Job**

- Job type (dropdown, tenant-customizable list)
- Job source (text, optional)
- Job description (multiline text, 3 rows)

**Schedule**

- Start datetime
- End datetime

### 8.2 Custom fields

Stored as `jsonb` in the `jobs` table under a `custom_fields` column.

Custom-field schema is stored per tenant in a separate `appointment_schemas` table, version-tracked:

```
appointment_schemas
  id           uuid
  tenant_id    uuid
  name         text          -- e.g. "Automotive Locksmith Job"
  version      int
  is_active    boolean
  field_definitions jsonb    -- list of FieldDefinition objects
  created_at   timestamptz
```

`FieldDefinition` object shape:

```
{
  "key": "vehicle_make",
  "label": "Vehicle Make",
  "type": "dropdown" | "text" | "number" | "date" | "time" | "datetime"
        | "file" | "image" | "video" | "list" | "boolean",
  "required": true,
  "order": 5,
  "options": ["Ford", "Mercedes-Benz", ...],
  "depends_on": null | "other_field_key",
  "options_by_parent": { "Ford": ["F-150", "Focus"], ... },
  "validation": { "min": null, "max": null, "regex": null }
}
```

### 8.3 Dependent dropdowns

A field can declare `depends_on: "<other_field_key>"` and provide `options_by_parent` — a map from parent value to permitted child values. Example: `model` depends on `make`; `year` depends on `model`.

UI behavior:

- Until the parent is selected, the dependent field is disabled.
- When the parent changes, the dependent field's value is cleared.

### 8.4 Predefined schemas shipped with the product

The team curates and bundles JSON schema templates for popular domains:

- Appliance repair
- AC / HVAC
- Electrician
- Plumber
- Handyman
- Roofing
- Solar panel
- Locksmith (incl. automotive sub-template)

These templates seed new tenants and remain editable. Templates live in source control under `backend/src/main/resources/schema-presets/*.json` and are loaded into the DB by a Flyway migration on first deploy.

### 8.5 Make / Model / Year data source

For the automotive locksmith preset we ship the **smallest possible curated list** and rely on admin customization + a free-text fallback rather than building a big VIN database we would then have to maintain.

Default behavior:

- `make` is a dropdown with the **top 5 US-popular makes** shipped with the preset (working list: `Ford`, `Chevrolet`, `Toyota`, `Honda`, `Tesla` — finalized at preset-curation time) **plus an `Other…` option** that reveals a free-text input.
- `model` and `year` are **free-text inputs** in the shipped preset, not dropdowns. This is intentional: it avoids a maintenance burden, gets us to launch faster, and is good enough for locksmiths.
- The schema editor (§8.2) lets the Admin upgrade any of these fields to a constrained dropdown with their own options and dependencies at any time, without engineering involvement. A locksmith Admin who wants strict `Make → Model → Year` dependent dropdowns can configure them per tenant.

Rationale: simple text inputs at launch are explicitly preferred over a half-curated dropdown that locks us into ongoing data maintenance. The `Other…` fallback on `make` keeps the door open to upgrade to full dependent dropdowns later without breaking any existing job records (existing values stay valid because they were valid text values).

No third-party VIN / NHTSA integration in MVP.

### 8.6 Acceptance criteria

- Admin can add a new field of any supported type and it appears on the dispatcher's job form within one minute (no redeploy).
- Admin can configure a dependent dropdown end-to-end (e.g. `make → model`) in the schema editor without editing JSON manually.
- Removing or hiding a field does not break historical jobs that have a value for it.
- A required custom field blocks job creation if empty.
- Schema versions are append-only; editing creates a new version, old jobs keep their original version reference.

---

## 9. Job / Appointment Lifecycle

### 9.1 Status state machine

**Top-level status enum**

- `Submitted` — created by dispatcher, in shared pool, not yet claimed
- `Assigned to Technician` — claimed but not started
- `In Progress` — actively being worked
- `Pending` — paused
- `Done Pending Approval` — completed by technician but requires office approval
- `Completed` — finalized
- `Canceled` — terminal

**Sub-states / reasons (separate nullable enums)**

- `in_progress_substatus`: `On the Way`, `Working`
- `pending_reason`: `Waiting for Parts`, `Rescheduling Needed`
- `cancellation_reason`: `Too Expensive`, `Arrived Late`, `Other`

### 9.2 Allowed transitions


| From → To                            | Who can perform                                                             |
| ------------------------------------ | --------------------------------------------------------------------------- |
| Submitted → Assigned to Technician   | Technician (claim from pool) or Dispatcher/Admin (manual assign — post-MVP) |
| Assigned to Technician → In Progress | Technician (own job)                                                        |
| In Progress → Pending                | Technician                                                                  |
| In Progress → Done Pending Approval  | Technician                                                                  |
| In Progress → Completed              | Technician **only if Close Jobs is enabled**                                |
| Pending → In Progress                | Technician                                                                  |
| Done Pending Approval → Completed    | Dispatcher / Admin only                                                     |
| Any → Canceled                       | Dispatcher / Admin only                                                     |
| Canceled → Submitted                 | Dispatcher / Admin only                                                     |
| Completed → (any)                    | Admin only                                                                  |


### 9.3 Acceptance criteria

- Disallowed transitions return 409 Conflict with a machine-readable error code.
- The technician mobile UI only shows buttons for transitions allowed for that technician on that job in its current state.
- Disabling Close Jobs permission immediately hides the `Completed` action for all technicians in the tenant — no app restart required.

---

## 10. Shared-Pool Dispatching (MVP)

### 10.1 Flow

1. Dispatcher creates a job → status `Submitted` → job lands in the shared pool, scoped to the tenant.
2. The job appears on every Technician's mobile calendar **as a red blinking card** with the message `ACCEPT THE JOB`.
3. Card shows: customer address, job type, scheduled start time.
4. Customer **phone number is hidden** until the technician claims the job. (This is a design contract — in MVP we simply do not include the phone number in the unclaimed job payload; full Twilio masking lands with v1.2.)
5. First Technician to tap "Accept Job" wins → atomic DB transition (optimistic locking on the job row). Losers see a "Job already claimed" message.
6. If **no technician claims within 5 minutes**, every Admin user in the tenant receives a push notification: `JOB NOT HANDLED` (red alert styling).

### 10.2 Concurrency model

- Job table has a `version` column for optimistic locking.
- Claim endpoint: `POST /api/v1/jobs/{id}/claim` performs `UPDATE jobs SET status = 'ASSIGNED_TO_TECHNICIAN', assigned_technician_id = :uid, version = version + 1 WHERE id = :id AND status = 'SUBMITTED' AND version = :v` — single row update wins.
- If `UPDATE` returns 0 rows, return 409 Conflict.

### 10.3 The 5-minute escalation

Implemented on the eventing infrastructure defined in §22.5:

- When a `JobSubmitted` event is emitted, the `escalation-scheduler` consumer inserts a row into `delayed_jobs` with `kind = 'escalate_unclaimed_job'` and `run_at = now() + 5 min`, and adds it to the Redis sorted-set delayed-job queue.
- When the entry comes due, the dispatcher checks the current job status: if still `Submitted`, it fires a `JOB NOT HANDLED` push (red alert styling) to every admin of the tenant; if no longer `Submitted`, it skips.
- A `JobClaimed` event sets the corresponding `delayed_jobs` row to `status = 'CANCELLED'` so the escalation is suppressed cleanly without a race against the dispatcher.
- Once an admin has been notified for a given job, the row is marked `status = 'DONE'`; no re-notification is possible without a new `Submitted` transition.

### 10.4 Acceptance criteria

- Two technicians tapping Accept simultaneously: exactly one wins; the other sees a clear "job already claimed" message within 2 seconds.
- The escalation push fires within 5 minutes ± 30 seconds.
- Admin push notification text is `JOB NOT HANDLED` and includes the job ID for deep-linking.
- Customer phone is not present in the JSON payload for any unclaimed-job API response.

---

## 11. Customer Records (CRM-lite, MVP)

### 11.1 Customer entity

```
customers
  id              uuid
  tenant_id       uuid
  name            text
  company_name    text null
  phone           text                   -- E.164
  email           text null
  address_line    text
  city            text
  state           text
  zip             text
  location        geography(Point,4326)
  created_at      timestamptz
  updated_at      timestamptz
  archived_at     timestamptz null
```

### 11.2 Surfaces

- **Web (Dispatcher / Admin)**: full searchable list, customer detail page with all historical jobs, total revenue (sum of paid invoices — appears once payments land in v1.1).
- **Mobile (Technician)**: read-only list, read-only detail showing customer's name, phone (revealed only for jobs claimed by this technician), address, job history filtered to jobs this technician handled.

### 11.3 Acceptance criteria

- Creating a job auto-creates (or upserts on phone match) a customer record.
- Editing a customer's address re-geocodes via Google Maps Geocoding API.
- Archive (soft-delete) hides the customer from default lists but preserves history.

---

## 12. Schedule and Calendar

### 12.1 Web (Dispatcher / Admin)

- **Calendar view**: day, week, month. Built with FullCalendar or React Big Calendar.
- **Kanban view**: columns = top-level statuses (`Submitted`, `Assigned`, `In Progress`, `Pending`, `Done Pending Approval`, `Completed`, `Canceled`).
- **List view**: paginated, filterable, sortable.
- Drag-and-drop between calendar slots (re-schedules the job).
- Drag-and-drop between kanban columns (transitions status, subject to permission/state-machine rules).

### 12.2 Mobile (Technician)

- Calendar view: today, this week.
- List view of own jobs grouped by status.
- Tapping a job opens the job detail card with action buttons (see §13).

### 12.3 Acceptance criteria

- Dragging a job to a new calendar slot persists the change within 500ms.
- An invalid drag (e.g. dropping a `Completed` job into `Submitted`) is rejected with a visual shake animation and a toast explaining why.

---

## 13. Mobile Technician Experience

### 13.1 Bottom navigation tabs (MVP)


| Tab      | MVP content                                                       |
| -------- | ----------------------------------------------------------------- |
| Home     | Dashboard: today's job count, jobs done this week, top-level KPIs |
| Schedule | Daily / weekly calendar view of own jobs + unclaimed pool jobs    |
| Clients  | Read-only customer list (jobs this tech worked, filterable)       |
| Profile  | Account info, sign-out                                            |


Out of MVP: Messages tab, Calls tab (both v1.2+).

### 13.2 Job card actions (after claim)

Exactly five primary buttons, in this order:

1. **Define ETA** — opens a sheet with options `30 minutes`, `45 minutes`, `Custom`. Selecting any value:
  - Sets job sub-status to `On the Way`.
  - **(v1.1)** sends an SMS to the customer: *"Hello, the technician is estimated to arrive in ** minutes."*
2. **Start the Job** — transitions to `In Progress / Working`, starts a visible timer in the UI (subtle, professional — no glow, no flashy animations; matches the Design doc).
3. **Pay** — opens a sheet with `Cash` and `Tap-to-Pay` options. **In MVP this button is disabled** with a tooltip "Coming in v1.1". The placeholder UI exists; the integration does not.
4. **Add Notes** — multiline text editor; appended to the job's note thread.
5. **Attach Result Image** — opens camera; multiple photos per job allowed; uploads to object storage via pre-signed URL.

### 13.3 Address tap behavior

Tapping a customer address opens the device's default navigator (Google Maps app on Android, Apple Maps / Google Maps on iOS based on user preference) with the destination pre-filled. In-app navigation is post-MVP.

### 13.4 Acceptance criteria

- All five buttons exist in the UI even when their action is disabled in MVP, so the UI doesn't reflow when capability lands.
- Notes are timestamped and attributed to the technician.
- Photo uploads happen in the background with a retry queue; the technician can leave the job card without canceling the upload.
- Offline behavior: notes typed and photos taken offline are queued and synced when connectivity returns.

---

## 14. Photo Capture and Upload

### 14.1 Storage

- **Object storage** with separate buckets per environment, key pattern `<tenant_id>/jobs/<job_id>/<uuid>.<ext>`.
- Local, dev, and MVP-production use **MinIO on Coolify**.
- Sales-production uses **Cloudflare R2**.
- All photos are private; access via short-lived signed URLs.

### 14.2 Upload path

- Mobile requests `POST /api/v1/jobs/{id}/photo-upload-url` → backend returns a pre-signed PUT URL.
- Mobile PUTs directly to the configured object store.
- Mobile then calls `POST /api/v1/jobs/{id}/photos` with the resulting key and metadata.

### 14.3 Constraints

- Max 20 MB per photo.
- Accepted MIME: `image/jpeg`, `image/png`, `image/heic`, `image/webp`.
- Server stores width, height, content-type, byte size, uploader user ID, taken-at timestamp.
- EXIF stripped before serving (privacy).

### 14.4 Acceptance criteria

- A photo upload that fails midway can be resumed (multipart upload for files > 5 MB).
- Photos appear in the web job detail view within 5 seconds of mobile upload completion.

---

## 15. Push Notifications

### 15.1 Provider

**Expo Push Service** (free, managed by Expo, abstracts FCM + APNs). Tokens are registered server-side per user.

### 15.2 Use cases in MVP


| Event                                   | Recipients                                                  | Body                                        |
| --------------------------------------- | ----------------------------------------------------------- | ------------------------------------------- |
| New unclaimed job in shared pool        | All technicians of the tenant                               | `New job: <job_type> — <address>`           |
| 5-minute unclaimed escalation           | All admins of the tenant                                    | `JOB NOT HANDLED — <job_type> at <address>` |
| Job claimed by another technician       | The technicians who *didn't* claim it (silent badge update) | (silent)                                    |
| Job approved (Done Pending → Completed) | The technician who completed it                             | `Job <id> approved`                         |


### 15.3 Acceptance criteria

- Push notifications land within 10 seconds end-to-end (server fires → device sound).
- Tapping a push opens the relevant job card in the app.
- Notifications are localized to the user's timezone for date/time strings.
- Token rotation handled gracefully (server retries with refreshed token on `DeviceNotRegistered`).

---

## 16. Automations (MVP)

### 16.1 Concept

Admin defines simple **trigger → action** rules where a job's existing field values automatically fill other fields.

### 16.2 Rule model

```
automation_rules
  id            uuid
  tenant_id     uuid
  name          text
  trigger_fields jsonb    -- ["service_type", "make", "model", "year"]
  match_rules   jsonb    -- e.g. { "service_type": "All Keys Lost", "make": "Mercedes-Benz", "model": "C-Class", "year_range": "2008-2014" }
  actions       jsonb    -- { "equipment": ["Autel Programmer", "Autel G-Box"], "description": "...", "duration_min": 30, "duration_max": 120, "difficulty": "medium", "price_usd": 500 }
  active        boolean
```

### 16.3 Engine behavior

- After any change to a trigger-field value on a draft job, the engine evaluates all active rules in priority order.
- First matching rule applies its action values to the job — but **only to fields the user has not manually edited**.
- The UI shows an "Auto-filled" badge next to each auto-populated field with an undo affordance.

### 16.4 Acceptance criteria

- Example from Thoughts §3.2.7 works end-to-end: dispatcher selects `Service: All Keys Lost`, `Make: Mercedes-Benz`, `Model: C-Class`, `Year: 2010` → equipment, description, time estimate, difficulty, and suggested price auto-fill.
- A user-edited field is not overwritten by a subsequent rule match.

---

## 17. Subscription Billing (Stripe Billing — MVP)

### 17.1 Plans


| Plan           | Price (USD / month) | Seats     | Jobs / month | Custom schema    | Advanced analytics | Twilio voice + SMS | SSO         |
| -------------- | ------------------- | --------- | ------------ | ---------------- | ------------------ | ------------------ | ----------- |
| **Free**       | $0                  | 2         | 25           | ❌ (presets only) | ❌                  | ❌                  | ❌           |
| **Starter**    | $49                 | 5         | 250          | ✅                | ❌                  | ❌                  | ❌           |
| **Team**       | $169                | 20        | 1,500        | ✅                | ✅                  | ✅ *(v1.1+)*        | ❌           |
| **Enterprise** | $349                | Unlimited | Unlimited    | ✅                | ✅                  | ✅ *(v1.1+)*        | ✅ *(v1.2+)* |


Quotas above are **confirmed** by the Project Lead and are the canonical limits enforced at the API boundary.

### 17.2 Stripe integration

- Stripe Billing — products + prices created in the Stripe dashboard, IDs pinned in env vars.
- Stripe Checkout for upgrade flow.
- Stripe Customer Portal for plan changes, cancellation, payment-method updates.
- Webhook `customer.subscription.updated` → backend updates tenant `current_plan`.
- Webhook `invoice.payment_failed` → tenant flagged, admin notified, grace period 7 days then downgrade to Free.

### 17.3 Enforcement

Quota checks happen at the API boundary:

- Seat invite rejected if seat count would exceed plan.
- Job creation rejected if monthly count would exceed plan; admin notified at 80% and 100%.
- Custom schema editor disabled on Free tier; preset selector still works.

### 17.4 Acceptance criteria

- Upgrading a tenant from Starter to Team in Stripe propagates to the app within 30 seconds via webhook.
- Downgrade to Free does not delete data; it only restricts further creation/editing.
- All Stripe credentials live in environment variables — never committed.

---

## 18. Reporting and Analytics (MVP — basic only)

### 18.1 Admin reports in MVP

- Jobs created / completed / canceled — day, week, month, year.
- Average time-to-claim (Submitted → Assigned).
- Average time-to-completion (Assigned → Completed).
- Per-technician: jobs handled, completion rate.
- (Revenue reports land with v1.1 once on-site payments exist.)

### 18.2 Implementation

- Materialized views in PostgreSQL refreshed every 15 minutes.
- Heavy aggregation queries cached in Redis with 5-minute TTL.
- "Advanced analytics" (cohorts, technician-vs-technician comparisons, custom date ranges) is the **Team+** feature gate.

---

## 19. Audit Log (MVP — server-side only)

### 19.1 Scope

```
audit_log
  id           uuid
  tenant_id    uuid
  actor_user_id uuid
  action       text       -- e.g. "job.claim", "job.transition", "user.invite", "schema.update"
  resource_type text
  resource_id   uuid
  before_state jsonb null
  after_state  jsonb null
  ip_address   inet
  user_agent   text
  occurred_at  timestamptz
```

### 19.2 Captured actions in MVP

- Authentication: login, login_failed, logout, password_reset, token_refresh.
- Tenant: schema.update, automation.create/update/delete, user.invite/deactivate.
- Job: create, transition, edit, archive.
- Payment (v1.1+): capture, refund.

### 19.3 Acceptance criteria

- Audit rows are append-only (no UPDATE / DELETE permitted; enforced by Postgres permissions).
- A failed login attempt is recorded with the email tried and the IP.
- No admin UI for the log in MVP — but a tenant can request an export via support (manual SQL).

---

## 20. Design and UI Direction

This section restates `prd/Design` as binding requirements; the Design doc remains the source of visual truth.

### 20.1 Mood

Clean, modern SaaS for daily operations work. Closer to Jira, Microsoft, Google, Slack than to gaming, fintech, or crypto. Reliable for daily use, but friendly and energetic — not heavy old-school CRM.

### 20.2 Color palette

- Primary: white + bright blue + indigo + cyan.
- Status: green (success / completed), orange (scheduled / warning), red (urgent / unclaimed).
- Backgrounds: very light gray or white.
- Text: dark navy.
- **Forbidden**: gradients, dark mode, glow effects, neon, entertainment-style visuals.

### 20.3 Typography

- **Manrope**, medium and semibold weights.
- Clear hierarchy: page title > section title > label > data.

### 20.4 Layout primitives

- Generous spacing, breathable layouts.
- Rounded corners, soft borders, light shadows, consistent padding.
- Bento Grid for dashboards.
- Modular components: cards, tables, schedules, status badges, sidebars, search bars, tabs, buttons, forms, map/status panels.

### 20.5 Interaction polish

- Smooth, subtle animations on action (no bouncing, no spinning, no flash).
- Confirm-before-quit on unsaved changes (per Thoughts §3.0.4).
- Skeleton loaders for data fetching.
- Toast notifications for non-blocking errors and successes.

### 20.6 Component library decision

Component library: **shadcn/ui** (Radix UI primitives styled with Tailwind, copied into the codebase). Chosen so every primitive lives in our repo and is stylable to match the Manrope-driven brand exactly, with no external dependency that could shift styling out from under us. Pair with `lucide-react` for icons.

---

## 21. Technology Stack (locked)

### 21.1 Backend

- Java 21 LTS
- Spring Boot 3.2+
- Spring Web, Spring Data JPA, Spring Security, Spring Validation
- Hibernate 6.4+
- PostgreSQL 16+ with **PostGIS** extension and **Row-Level Security**
- Flyway for migrations
- Redis (cache + delayed-job queue for the 5-min escalation)
- Lombok, MapStruct
- SpringDoc OpenAPI 3 (required, not optional — partner integrations and contract testing depend on it)
- Spring Boot Actuator + Micrometer
- JUnit 5, Mockito, Testcontainers, MockMvc
- SLF4J + Logback (JSON layout for production)
- Gradle for build
- SonarQube + Checkstyle + Spotless

**Removed from earlier Thoughts**: no Python, no FastAPI, no Ruff, no Black.

### 21.2 Frontend (web SPA at `app.teamhub.online`)

- TypeScript 5.3+ (strict mode)
- React 18+
- Vite 5+
- TanStack Query 5
- Zustand (client state for ephemeral UI)
- React Router DOM 6
- Axios
- Tailwind CSS 3.4
- React Hook Form 7 + Zod 3.22
- shadcn/ui + lucide-react (confirmed — see §20.6)
- ESLint + Prettier
- Vitest + React Testing Library + Playwright (e2e)

**Architecture**: Feature-Sliced Design — layers `app`, `pages`, `widgets`, `features`, `entities`, `shared`.

### 21.3 Mobile

- Expo SDK (latest stable)
- React Native
- TypeScript
- React Navigation
- TanStack Query 5
- Zustand
- Expo SecureStore for tokens
- Expo Notifications + Expo Push Service
- Expo ImagePicker / Camera
- EAS Build + EAS Submit for App Store and Play Store

### 21.4 Infrastructure

- **Hetzner cloud VMs** (US — Hillsboro region) provisioned via **Coolify**
- Docker + Docker Compose
- PostgreSQL 16 with PostGIS
- Redis 7
- **MinIO on Coolify** for local, dev, and MVP-production object storage; **Cloudflare R2** for sales-production. Object-store access keys are held only by the backend; mobile uses backend-issued pre-signed URLs.
- Reverse proxy: Caddy or Traefik (managed by Coolify)
- TLS via Let's Encrypt (auto-renewed by Coolify)

### 21.5 Observability

- Prometheus (metrics from Spring Actuator + Micrometer)
- Grafana for dashboards
- Loki for logs + Grafana Alloy for log shipping
- **Sentry** for crash and error reporting across backend, web SPA, and mobile app. Source maps uploaded on every release build.

### 21.6 CI / CD

- GitHub Actions
- Pipeline stages: lint → test → SonarQube → build Docker image → push to registry → deploy via Coolify webhook.
- Mandatory checks before merge to `main`: lint passes, unit + integration tests pass, ≥70% coverage on business logic, no Sonar blocker / critical issues.
- Two environments minimum: **dev** and **prod**. Staging environment added when needed.

### 21.7 Monorepo layout

```
saas-from-0/
├── backend/           # Spring Boot
├── web/               # React SPA
├── mobile/            # Expo React Native
├── prd/               # this doc and source notes
├── infra/             # Docker, Coolify manifests
├── .github/workflows/
└── README.md
```

---

## 22. Data Model (high-level)

Only the principal entities are listed. The full DDL is owned by Flyway migrations.

```
tenants            (id, name, plan, created_at, current_period_end, stripe_customer_id)
users              (id, tenant_id, email, password_hash, role, name, is_active)
appointment_schemas (id, tenant_id, name, version, is_active, field_definitions jsonb)
jobs               (id, tenant_id, schema_id, schema_version, customer_id,
                    status, in_progress_substatus, pending_reason, cancellation_reason,
                    assigned_technician_id, scheduled_start, scheduled_end,
                    custom_fields jsonb, location geography, version int)
customers          (id, tenant_id, name, phone, email, address_line, city, state, zip, location)
job_notes          (id, job_id, author_user_id, body, created_at)
job_photos         (id, job_id, s3_key, content_type, size_bytes, uploaded_by, taken_at)
job_status_history (id, job_id, from_status, to_status, actor_user_id, at)
automation_rules   (id, tenant_id, name, trigger_fields jsonb, match_rules jsonb, actions jsonb, active)
audit_log          (id, tenant_id, actor_user_id, action, resource_type, resource_id, before, after, ip, ua, at)
device_tokens      (id, user_id, expo_token, platform, last_seen_at)
stripe_subscriptions (id, tenant_id, stripe_subscription_id, plan, status, current_period_end)

-- eventing infrastructure (see §22.5)
outbox_events      (id, tenant_id, event_type, payload jsonb, aggregate_type, aggregate_id,
                    created_at, processed_at null, attempt_count int default 0)
processed_events   (event_id uuid, consumer_group text, processed_at timestamptz,
                    PRIMARY KEY (event_id, consumer_group))
delayed_jobs       (id, tenant_id, kind, payload jsonb, run_at, status, attempt_count)
```

Every business table has `tenant_id`, an RLS policy, and indexes on `(tenant_id, ...)` for predicate locality. The eventing tables (`outbox_events`, `processed_events`, `delayed_jobs`) also carry `tenant_id` so the same RLS guarantees apply to events and scheduled work.

---

## 22.5 Internal Eventing and Asynchronous Processing

### 22.5.0 Decision

**No Kafka in MVP.** Internal events and asynchronous work are built on the infrastructure already in the stack (PostgreSQL + Redis) using a three-layer model:

1. **Spring `ApplicationEventPublisher`** for trivial in-process fan-out.
2. **Transactional Outbox + Redis Streams** for durable, decoupled, replayable cross-handler events.
3. **Redis sorted-set delayed-job queue** for time-based scheduling.

This decision is reviewed when sustained event throughput exceeds **1,000 events/sec** or when the modular monolith is split into multiple independently-deployed services. Neither condition is plausible before v2.0+ at the 500-tenant / 20,000-MAU target (§24.2).

### 22.5.1 Why not Kafka at our scale

Sizing estimate at the §24.2 target:

- 500 tenants × ~50 jobs/day average = ~25,000 jobs/day, concentrated in a ~10-hour US business day → ~0.7 jobs/sec average.
- 10× burst headroom = ~7 jobs/sec.
- Each `JobSubmitted` fans out to ~4–5 internal handlers → sustained peak event rate **~30 events/sec**.

Kafka starts paying off at thousands of events/sec sustained and across multiple separately-owned services. At 30 events/sec inside a modular monolith, Kafka would add:

- A new piece of infra to operate on Hetzner (KRaft mode, partition planning, monitoring, retention tuning, schema registry).
- ~4–8 GB of additional RAM for a usable single-broker setup.
- A whole new failure-mode surface (consumer lag, partition rebalancing, broker recovery).
- Slower development velocity at the time the team most needs to ship.

Both Redis Streams and a Postgres outbox give us the same delivery properties Kafka would (durability, fan-out, replay, ordering per stream, at-least-once consumer groups) with zero new operational components.

### 22.5.2 Layer 1 — In-process events (Spring `ApplicationEventPublisher`)

Used when the handler is in the same JVM, can tolerate being lost on a crash, and does not need replay.

- Producer: `applicationEventPublisher.publishEvent(new SomethingHappened(...))`.
- Consumer: `@TransactionalEventListener(phase = AFTER_COMMIT)` so it only fires when the originating DB transaction has actually committed.
- For non-trivial work, the listener method is annotated `@Async` so it doesn't block the originating request.
- Use cases: invalidate a Redis cache entry, refresh a counter, write a structured log marker.

### 22.5.3 Layer 2 — Transactional Outbox + Redis Streams (the workhorse)

Every event that has at least one of these properties uses this layer:

- Must survive an application crash.
- Has multiple independent consumers (fan-out).
- Crosses module boundaries inside the monolith.
- Is interesting enough to want to replay later for debugging or rebuilding a read model.

#### Producer flow

In a single DB transaction with the business write:

```
BEGIN;
  INSERT INTO jobs (...);
  INSERT INTO outbox_events (
      id, tenant_id, event_type, payload, aggregate_type, aggregate_id, created_at
  ) VALUES (
      gen_random_uuid(), :tenantId, 'JobSubmitted',
      :payloadJson, 'Job', :jobId, now()
  );
COMMIT;
```

Atomicity is guaranteed by the database — either both rows commit or neither does. No event can ever be emitted for a business write that didn't happen, and no business write can ever silently fail to emit its event.

#### Outbox Relay

A single leader-elected component (leadership claimed via a Redis lock with TTL) does:

- `LISTEN outbox_inserted` on Postgres for low-latency wake-ups, falling back to a 200 ms polling loop.
- For each unprocessed row: `XADD events:<event_type> * id <uuid> tenant_id <uuid> payload <json>`.
- `UPDATE outbox_events SET processed_at = now() WHERE id = :id` after successful XADD.

Only one relay runs at a time to preserve per-stream ordering of events for the same aggregate.

#### Consumers

Each consumer is a Redis Streams consumer group on the relevant stream. Spring Boot worker beans poll with `XREADGROUP` and ACK with `XACK` after successful processing.

- Consumer groups are independent: adding a new consumer is one new bean and one new group name; producers and existing consumers don't change.
- Failed messages stay in the Pending Entries List (PEL) and are claimed by another consumer instance after a visibility timeout (`XPENDING` + `XCLAIM`).
- After N attempts (default 5) a message is moved to a dead-letter stream `events:<event_type>:dlq` and an admin alert fires.

### 22.5.4 Layer 3 — Delayed-job queue (Redis sorted set)

Time-based scheduling (e.g. "fire the `JOB_NOT_HANDLED` escalation 5 minutes from now") uses a Redis sorted set:

- `ZADD delayed_jobs <run_at_unix_ms> <job_payload_id>`.
- The payload itself lives in the `delayed_jobs` table (Postgres) keyed by `<job_payload_id>` — Redis only stores the schedule, Postgres stores the truth and survives a Redis flush.
- A single dispatcher worker runs `ZRANGEBYSCORE delayed_jobs 0 <now> LIMIT 0 100` every 5 seconds, atomically removes due entries with `ZREM`, marks the Postgres row `status = 'RUNNING'`, executes the handler, then marks `status = 'DONE'` (or `FAILED` with retry).
- Cancellation: setting `status = 'CANCELLED'` in Postgres before run-time makes the dispatcher skip the entry. Used by the escalation handler: if the job is no longer `Submitted` at run-time, the escalation push is suppressed.

### 22.5.5 Event catalog (MVP)

| Event | Producer | Consumer groups | Notes |
|---|---|---|---|
| `JobSubmitted` | JobService on create | `quota-checker`, `push-fan-out-technicians`, `escalation-scheduler`, `audit-logger` | Triggers shared-pool fan-out and the 5-min escalation timer |
| `JobClaimed` | JobService on claim | `silent-push-other-techs`, `audit-logger`, `escalation-canceler` | Cancels the pending escalation delayed-job for this job |
| `JobStatusChanged` | JobService on transition | `push-actor`, `audit-logger`, `analytics-aggregator` | Carries from / to status + actor |
| `JobCompleted` | JobService on transition to `Completed` | `audit-logger`, `analytics-aggregator` | Reserved for future revenue handlers in v1.1 |
| `QuotaThresholdReached` | `quota-checker` consumer (cascading event) | `email-admin`, `in-app-banner` | Fires at 80% and 100% of the plan's monthly job quota |
| `SubscriptionUpdated` | Stripe webhook handler | `tenant-plan-updater`, `audit-logger` | Triggered by `customer.subscription.updated` from Stripe |
| `UserInvited` | UserService on invite | `email-sender`, `audit-logger` | Carries the signed invite token |
| `PhotoUploaded` | JobPhotoService on confirm | `web-realtime-notifier`, `audit-logger` | v1.0 web side polls; this event positions us for WebSocket push in v1.2 |

The full payload schema for each event lives in `backend/src/main/java/.../events/payloads/` as Java records with Jackson annotations. Schemas are append-only: fields may be added, never removed or renamed, without a new event type.

### 22.5.6 Delivery guarantees and idempotency

- **At-least-once delivery**, never exactly-once. Consumers must be idempotent.
- Every consumer uses the `processed_events (event_id, consumer_group)` table as a de-dup guard. The first action in any handler is an `INSERT … ON CONFLICT DO NOTHING` against `processed_events`; if zero rows were inserted, the event has already been handled and the consumer ACKs immediately.
- This is enforced by a base class (`AbstractOutboxConsumer`) all consumers must extend; the base class wraps the user-supplied `handle(event)` method in the de-dup check.
- The de-dup table is partitioned by month and old partitions are dropped after the corresponding events have aged out of the Redis Stream (default 30 days retention).

### 22.5.7 Walk-through — the user's example end-to-end

Dispatcher submits a job at quota 79% / 250 plan limit / current count 198:

```
1. POST /api/v1/jobs                                            (web, <100ms)
2. JobService.create():
     BEGIN
       INSERT INTO jobs (..., status='SUBMITTED', tenant_id=T)
       INSERT INTO outbox_events (type='JobSubmitted',
                                  payload={job_id, tenant_id, ...})
     COMMIT
3. 201 Created returned to client.
─────────────────────────────────────────────────────────────────
4. OutboxRelay XADDs to events:JobSubmitted.
5. Three consumer groups process in parallel:

   (a) quota-checker
       - SELECT count(*) FROM jobs WHERE tenant_id=T
         AND created_at >= current_period_start;  -- returns 199
       - plan_limit=250, 80% threshold = 200; 199 < 200 → no-op this time.
       - (When the next job submission lands and count hits 200, this
         consumer publishes QuotaThresholdReached(80) to a SECOND outbox
         row inside its own DB tx, which the relay fans out to
         email-admin + in-app-banner consumers.)

   (b) push-fan-out-technicians
       - SELECT expo_push_token FROM device_tokens dt
         JOIN users u ON dt.user_id = u.id
         WHERE u.tenant_id=T AND u.role='TECHNICIAN' AND u.is_active;
       - Batch Expo push (100 tokens per request).
       - Retry transient failures, mark DeviceNotRegistered tokens stale.

   (c) escalation-scheduler
       - INSERT INTO delayed_jobs (kind='escalate_unclaimed_job',
                                   payload={job_id}, run_at=now()+5min)
       - ZADD delayed_jobs <run_at_ms> <delayed_job_id>

6. Five minutes later, the delayed-job dispatcher pops the entry:
   - SELECT status FROM jobs WHERE id=<job_id>
   - IF still 'SUBMITTED' → push JOB_NOT_HANDLED to all admins of T.
   - ELSE → skip (a JobClaimed event would already have set the
            delayed_jobs row to status='CANCELLED').
```

Every handler runs independently. Adding a fifth handler ("post to a Slack incoming webhook" in v1.2) is one new consumer group; producers and existing consumers don't change.

### 22.5.8 Migration path if Kafka ever becomes necessary

When sustained throughput crosses ~1,000 events/sec or the monolith is split:

- The `outbox_events` table stays as-is.
- Replace the Outbox Relay's `XADD` with a Kafka produce call.
- Replace each consumer's Redis Streams client with a Kafka consumer; consumer-group names map 1:1 to Kafka groups.
- Event schemas, idempotency keys, and `processed_events` de-dup logic all carry over unchanged.

The eventing architecture is broker-agnostic by construction; Redis Streams is simply the broker for v1.0–v1.x.

### 22.5.9 Acceptance criteria

- A `JobSubmitted` event is always produced when (and only when) a job row is committed; verified by a Testcontainers test that crashes the app between the business write and the Redis XADD and asserts the outbox relay catches up on restart.
- Two identical event deliveries to the same consumer result in exactly one logical effect (verified by integration test that double-fires an event and asserts only one push, one email, one row in the side effect).
- A 5-minute escalation does not fire if the job was claimed within the window (verified by integration test).
- Median event end-to-end latency (producer commit → consumer ACK) is < 1 second under nominal load.
- Dead-letter stream entries trigger an alert in Sentry / Grafana.
- The relay survives a single-process crash and resumes from the last unprocessed outbox row on restart with no duplicates beyond at-least-once semantics.

---

## 23. API Conventions

### 23.1 Style

- REST over HTTPS.
- JSON request and response bodies.
- HTTP verbs: GET (read), POST (create / actions), PUT (full replace), PATCH (partial update), DELETE (soft-delete).
- Status codes used correctly (200 / 201 / 204 / 400 / 401 / 403 / 404 / 409 / 422 / 429 / 500).
- Idempotency keys on POST endpoints that create money-related resources (relevant from v1.1 onward).

### 23.2 Versioning

URI-prefixed: `/api/v1/...`. A new major version → `/api/v2/...`. Minor changes are backward-compatible additions.

### 23.3 Pagination

- Cursor-based pagination preferred for high-cardinality lists.
- Page/size also supported via Spring's `Pageable` for simpler lists.
- Sorting via `?sort=<field>,<asc|desc>`.
- Filtering via Spring Data JPA Specifications or QueryDSL.

### 23.4 Errors

Uniform error envelope:

```json
{
  "error": {
    "code": "JOB_ALREADY_CLAIMED",
    "message": "This job has already been claimed by another technician.",
    "details": { "job_id": "..." }
  }
}
```

### 23.5 Documentation

SpringDoc OpenAPI 3 generates `/api/v1/openapi.json` and a Swagger UI on dev / staging. Production exposes the spec but not the UI.

---

## 24. Non-Functional Requirements

### 24.1 Performance

- API response time: p95 < 200 ms for read endpoints, < 500 ms for write endpoints.
- Cold-start mobile app: < 3 s on a mid-range Android phone.
- DB queries: profiled, indexed, `@EntityGraph` / `FETCH JOIN` used to avoid N+1 problems.

### 24.2 Scalability

- Realistic target: **500 tenants (Teams) and 20,000 monthly active users** without architectural change. The product must not become unstable or noticeably slow under that load.
- Headroom: the architecture (shared DB + RLS on Postgres 16, Hetzner cloud VMs, stateless app instances) can be scaled vertically well past that envelope; horizontal scaling (read replicas, multiple app instances behind a reverse proxy) is added when CPU is sustained > 60% or DB latency p95 > 50 ms.
- Capacity planning is reviewed each time the active tenant count crosses a 100-tenant threshold.

### 24.3 Availability

- Target 99.5% uptime for MVP, 99.9% from v1.2 onward.
- Daily Postgres backups retained 30 days; weekly retained 90 days.
- Restore-from-backup drill at least once per quarter.

### 24.4 Security

- All traffic over HTTPS, TLS 1.2+.
- Passwords stored via BCrypt cost factor 12.
- JWT signed RS256 with rotating keys.
- All external integration credentials in env vars or a secrets manager — **never committed**. CI must scan for leaked secrets (gitleaks).
- OWASP Top 10 reviewed before launch.
- Rate limiting on auth endpoints (5 / min / IP) and job-claim endpoint (10 / min / user).
- CSP headers, CSRF protection on cookie-based session paths if any.

### 24.5 Compliance

- **CCPA** (California Consumer Privacy Act) — data export and delete-on-request endpoints available before public launch.
- **GDPR** — not in scope for MVP (US market only); architecture is GDPR-compatible (no design choices that block it).
- **HIPAA** — **explicitly out of scope**. The ToS prohibits storing Protected Health Information (PHI). Teamhub is positioned for general field-service operations, not healthcare workflows.
- **PCI DSS** — out of scope in MVP since on-site payments are deferred to v1.1; Stripe handles all card data with SAQ-A scope when we add it.

### 24.6 Logging

- Structured JSON logs (SLF4J + Logback JSON encoder).
- Every log line carries `tenant_id`, `user_id`, `request_id`.
- PII (emails, phone numbers) redacted from logs.

### 24.7 Accessibility

- WCAG 2.1 AA targeted for web.
- All interactive elements keyboard-navigable.
- Color is never the only signal (status badges have text + icon, not only color — critical for the red unclaimed-job indicator).

---

## 25. Development Process and Quality Gates

### 25.1 Incremental delivery

This is the **single most important process rule**, learned the hard way: **do not attempt to build the whole product at once**.

- Each feature is split into focused tasks by page, feature, entity, integration, or workflow.
- **The frontend is never a single task.** Pages, widgets, features, entities, shared utilities, API clients, state, forms — each lives in its own task.
- Each task includes a verification step before being marked done.

### 25.2 Definition of Done (per task)

A task is Done when **all** of the following hold:

- Code merged to `main` via PR with at least one human reviewer.
- Unit + integration tests pass.
- Coverage on the affected business logic ≥ 70%.
- No Sonar blocker or critical issues introduced.
- For user-facing flows (auth, scheduling, push notifications, payments, technician actions): a **human** has manually verified the flow in the dev environment.
- Documentation updated (this PRD, README, or API docs) if behavior or contract changed.

### 25.3 Agent / AI rules

- Plans, task lists, and code changes generated by AI agents are reviewed by the Lead Developer (Human) before implementation continues.
- If an agent reaches a step that requires human setup (credentials, external accounts, Stripe / Twilio / Firebase / Expo / Google Cloud configuration), the agent **stops** and asks. **No placeholders, no mocks for required production integrations, no `paste_your_token_here`.**
- Stripe, push notifications, Twilio, and Apple Sign-in cannot be merged without Lead Developer review.
- All `TO-DO` markers in code must be resolved or explicitly deferred (with ticket reference) before a release branch is cut.

### 25.4 Branching

- Trunk-based development on `main`.
- Short-lived feature branches (≤ 3 days).
- Release tags semver: `v0.x.y` during MVP build, `v1.0.0` at public launch.

---

## 26. Open Items and TO-DO

All product / technical decisions from earlier drafts have been resolved (see §A and §B in the original audit). The items remaining below require either **engineering build work** that is in MVP scope but not yet a coded task, or **human setup** of external accounts and legal artifacts.

### 26.1 Engineering build items still to be tasked (in-MVP scope)

- Build the minimal internal Teamhub admin console for tenant provisioning and operator support (§6.4). Implemented as Teamhub-staff-only routes inside the existing web SPA, IP allow-listed.
- Curate and bundle the eight predefined schema presets (Appliance, AC, Electrician, Plumber, Handyman, Roofing, Solar panel, Locksmith) under `backend/src/main/resources/schema-presets/*.json` (§8.4).
- Finalize the top-5 makes list for the automotive locksmith preset (§8.5). Working list: Ford, Chevrolet, Toyota, Honda, Tesla — confirm before bundling.

### 26.2 External account provisioning (must be done before respective feature build)

- Hetzner Cloud (Hillsboro region) + Coolify
- GitHub Actions (runners + secrets storage)
- Stripe (account, products, prices, webhook endpoint, Customer Portal config)
- Google Cloud (OAuth client, Maps Geocoding + Places API keys)
- Apple Developer (Sign in with Apple, App Store Connect)
- Google Play Console
- Expo (organization, push credentials, EAS Build, EAS Submit)
- Sentry (backend, web, mobile projects)
- Cloudflare (R2 account, sales-production bucket, object-store-only access key)
- Twilio — **reserve account name `teamhub`** now; full provisioning deferred to v1.1

### 26.3 Legal and business setup (before public launch)

- Trademark and DBA registration for "Teamhub" in target US states.
- Terms of Service — must include the PHI / HIPAA prohibition (§24.5), seat / quota enforcement, and acceptable-use clauses.
- Privacy Policy — CCPA-compliant, includes data-export and data-deletion procedure.
- Data Processing Addendum (DPA) for tenants that ask for one.
- All three legal artifacts reviewed by a licensed US attorney before launch.

### 26.4 Deferred (post-launch)

- Marketing site at `teamhub.online` — SSR MPA. Stack and content decided in a post-launch sprint.

---

## 27. Out of Scope (explicit)

The following are **not** built in MVP and are documented here so they cannot quietly creep in:

- On-site / tap-to-pay technician payment (deferred to v1.1).
- Twilio SMS automations (deferred to v1.1).
- Twilio voice, call recording, phone-number masking (deferred to v1.2).
- Real-time chat (deferred to v1.2).
- Live technician location streaming (deferred to v1.1).
- Self-service tenant signup (deferred to v1.1; assisted onboarding only at MVP).
- Marketing site SSR MPA (deferred to post-launch).
- Impersonation feature (deferred to v1.2).
- Admin audit-log UI (deferred to v1.2; server-side log exists in MVP).
- Recurring scheduling (deferred to v1.2).
- Advanced dispatch algorithm / TSP optimization (deferred to v1.3).
- Inventory management (deferred to v2.0).
- Payroll computation (deferred to v2.0).
- Client / Manager / Accountant roles (deferred to v2.0).
- AI-assisted call answering (deferred to v2.0).
- B2B2C marketplace mode (long-term, low priority).
- Spanish (or any non-English) translations (post-launch; i18n architecture in place).
- HIPAA-eligible deployment (out of scope indefinitely without explicit business decision).

---

## 28. Risks and Mitigations


| Risk                                                                         | Severity | Mitigation                                                                                            |
| ---------------------------------------------------------------------------- | -------- | ----------------------------------------------------------------------------------------------------- |
| Schema customization complexity is bigger than estimated                     | High     | Ship hand-curated preset schemas first; let customization be a follow-up sprint within MVP if needed. |
| Shared-pool race conditions cause double-claim                               | High     | Optimistic locking + DB-level atomic transition (§10.2); load-test with 50 concurrent claims.         |
| Apple Sign-in App Store rejection if Google sign-in is offered without Apple | Medium   | Apple Sign-in is in MVP — both required.                                                              |
| Expo Push delivery delay during traffic spikes                               | Medium   | Add a fallback alert path (admin still sees red banner in web app when escalation fires).             |
| Tenant outgrows the shared-DB RLS model                                      | Medium   | Hybrid migration path documented in §7.4; Enterprise tier customers get dedicated DB when needed.     |
| Stripe webhook drift (we lose a state transition)                            | Medium   | Daily reconciliation job calls Stripe API to verify subscription status for active tenants.           |
| Mobile photo uploads on bad rural connectivity                               | Medium   | Offline queue with retry (§13.4 acceptance criteria).                                                 |
| AI agent regenerating placeholders (`paste_your_token_here`)                 | High     | CI secret scan + Lead Developer review gate for all integration code (§25.3).                         |
| White-glove onboarding caps growth                                           | Medium   | Self-service signup is the top-priority v1.1 item.                                                    |


---

## 29. Future Roadmap (post-MVP)

### 29.1 v1.1 (3–6 months after launch)

- Stripe Connect + Stripe Tap to Pay (iOS + Android) for on-site payment.
- Twilio SMS for customer notifications ("On the way", "Job complete", custom templates).
- Live technician location (REST POST / 60s; visualized on dispatcher map).
- Self-service tenant signup wizard.
- Revenue analytics in dashboards.

### 29.2 v1.2

- Twilio voice: call recording, phone-number masking, listen-in for admins.
- Admin audit-log UI.
- Impersonation.
- Real-time chat (WebSockets).
- Recurring scheduling.

### 29.3 v1.3+

- Advanced dispatch algorithm (TSP-style optimization, skill + tool matching).
- In-app navigation (instead of handing off to Google Maps).
- Manager and Accountant roles.

### 29.4 v2.0+

- Client role (customer self-service portal).
- Inventory management.
- Payroll computation.
- AI-assisted call answering.
- B2B2C marketplace mode.
- Spanish localization.

---

## 30. Glossary

- **Tenant** — the technical / engineering term for a paying business workspace on Teamhub. Tenants are isolated from each other (shared DB + tenant_id + RLS). Used in code, DB, API, and PRD only.
- **Team** — the user-facing label for the exact same thing. Always used in UI copy, marketing, emails, and support content. **"Tenant" and "Team" are interchangeable** — they refer to the same entity.
- **Workspace** — informal synonym for Team; avoid in finalized UI copy in favor of "Team" for consistency.
- **Job / Appointment** — a unit of work that a dispatcher creates and a technician executes.
- **Shared pool** — the bucket of `Submitted` jobs that any technician in the tenant can claim.
- **Schema** — the per-tenant definition of which custom fields a job has.
- **Schema preset** — a built-in starter schema for a common business domain (Locksmith, Plumber, etc.).
- **Close Jobs permission** — a tenant-level toggle controlling whether technicians can move jobs directly to `Completed` or must go through `Done Pending Approval`.
- **Escalation push** — the `JOB NOT HANDLED` notification fired to admins when a job sits in the shared pool unclaimed for 5 minutes.
- **Outbox / Transactional Outbox** — the pattern of writing an event row in the same DB transaction as the business row to guarantee atomic event emission. See §22.5.
- **Outbox Relay** — the leader-elected component that ships rows from `outbox_events` into Redis Streams.
- **Consumer group** — a named subscription on a Redis Stream; each event is delivered to each group exactly once (at-least-once semantics with PEL retry).
- **Delayed job** — a unit of work scheduled to run at a future timestamp; backed by `delayed_jobs` (Postgres) + a Redis sorted set for cheap due-time polling.
- **At-least-once delivery** — the delivery guarantee chosen for all outbox events; consumers must be idempotent via the `processed_events` de-dup table.

---

*End of PRD.*