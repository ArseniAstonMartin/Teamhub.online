import * as Sentry from '@sentry/react';

// TO-DO: human must provision Sentry projects per PRD §26.2 before enabling in production.
// DSN is read from VITE_SENTRY_DSN env var — no-op when the var is absent (local dev without Sentry).
export function initSentry(): void {
  const dsn = import.meta.env.VITE_SENTRY_DSN as string | undefined;
  if (!dsn) return;

  Sentry.init({
    dsn,
    environment: import.meta.env.MODE,
    release: `teamhub-web@${__APP_VERSION__}+${__GIT_SHA__}`,
    integrations: [Sentry.browserTracingIntegration(), Sentry.replayIntegration()],
    tracesSampleRate: 0.2,
    replaysSessionSampleRate: 0.05,
    replaysOnErrorSampleRate: 1.0,
    sendDefaultPii: false,
  });
}
