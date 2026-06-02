# Teamhub Web

React 18 + TypeScript SPA for the Teamhub field-service management platform.
Hosted at `app.teamhub.online`.

## Tech Stack

- **React 18** + **TypeScript** (strict mode)
- **Vite 5** — bundler and dev server
- **TanStack Query 5** — server state management
- **Zustand 5** — client state management
- **React Router 6** — client-side routing
- **Axios** — HTTP client
- **Tailwind CSS 3.4** + **shadcn/ui** — styling and component library
- **React Hook Form 7** + **Zod** — forms and validation
- **Sentry** (`@sentry/react`) — error monitoring and release tracking (PRD §26.2)
- **Vitest** — unit tests
- **Playwright** — end-to-end tests

## Local Setup

### Prerequisites

- Node.js 20+ (`nvm use 20` or Homebrew `brew install node@20`)
- npm 10+

### 1 — Copy and fill environment variables

```bash
cp .env.example .env
# Open .env and fill in the values described in each comment.
# At minimum, set VITE_API_BASE_URL to point at the running backend.
```

Only variables prefixed with `VITE_` are embedded into the client bundle.

### 2 — Install dependencies

```bash
npm ci
```

### 3 — Start the dev server

```bash
npm run dev
# Opens at http://localhost:5173
```

### 4 — Run checks

```bash
npm run typecheck   # TypeScript strict check
npm run lint        # ESLint
npm test            # Vitest unit tests
npm run test:e2e    # Playwright end-to-end (requires a running backend)
```

### 5 — Production build

```bash
npm run build
npm run preview     # Preview the built output locally
```

## Environment Variables

| Variable | Description | Default (dev) |
|----------|-------------|---------------|
| `VITE_API_BASE_URL` | Backend base URL | `http://localhost:8080` |
| `VITE_SENTRY_DSN` | Sentry DSN for error monitoring | — |
| `VITE_GOOGLE_MAPS_API_KEY` | Google Maps JavaScript API key | — |

See `.env.example` for inline documentation.
> **TO-DO:** Provision Sentry and Google Maps API key per PRD §26.2 before deploying.

## Folder Structure (Feature-Sliced Design)

```
web/src/
├── app/          # App-level setup (router, providers, sentry.ts)
├── pages/        # Route-level page components
├── widgets/      # Composite UI blocks (sidebars, navbars, dashboards)
├── features/     # Feature slices (jobs, customers, invoices, auth …)
├── entities/     # Domain entity components and models
└── shared/       # Shared UI, utils, API client, hooks
```
