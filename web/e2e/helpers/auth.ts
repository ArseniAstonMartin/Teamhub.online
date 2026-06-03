import { type Page } from '@playwright/test';

const REFRESH_TOKEN_KEY = 'teamhub_refresh_token';

/**
 * Builds a fake JWT with the given role. The payload is valid base64 so
 * parseJwtPayload (atob + JSON.parse) succeeds client-side.
 */
export function makeToken(role: string = 'ADMIN'): string {
  const header  = btoa(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({
    sub: 'user-123',
    tenant_id: 'tenant-456',
    role,
    exp: Math.floor(Date.now() / 1000) + 3600,
    iat: Math.floor(Date.now() / 1000),
  }));
  return `${header}.${payload}.fakesig`;
}

/**
 * Minimal auth gate bypass: injects a refresh token into localStorage so that
 * AuthGate passes (it allows through when refresh token exists even without an
 * in-memory access token). Also mocks the refresh endpoint so that when the
 * Axios 401-interceptor fires it gets back a valid fake JWT.
 *
 * After the refresh, the Zustand store will have user = { id, tenantId, role }.
 * All API mocks that need auth context should return 401 for requests without
 * an Authorization header, and proper data when the header is present — this
 * triggers the interceptor automatically.
 */
export async function setupAuth(page: Page, role: string = 'ADMIN'): Promise<void> {
  const fakeToken = makeToken(role);

  await page.addInitScript((key) => {
    localStorage.setItem(key, 'fake-refresh-token');
  }, REFRESH_TOKEN_KEY);

  await page.route((url) => url.pathname === '/api/v1/auth/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ accessToken: fakeToken, refreshToken: 'fake-refresh-token', expiresIn: 3600 }),
    }),
  );
}

/**
 * Full login flow via the UI. Sets the Zustand user state by going through
 * the real login form. Use when the test needs user.role to be populated
 * (e.g. job-detail action buttons, notes composer).
 *
 * After this call the page is on /schedule and:
 *   - Zustand has accessToken + user set
 *   - localStorage has the refresh token
 *   - The /api/v1/auth/refresh mock is registered for subsequent page.goto reloads
 */
export async function loginAs(page: Page, role: string = 'ADMIN'): Promise<void> {
  const fakeToken = makeToken(role);

  await page.route((url) => url.pathname === '/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken: fakeToken,
        refreshToken: 'fake-refresh-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
      }),
    }),
  );

  // Also register refresh mock so it persists after subsequent page.goto reloads
  await page.route((url) => url.pathname === '/api/v1/auth/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ accessToken: fakeToken, refreshToken: 'fake-refresh-token', expiresIn: 3600 }),
    }),
  );

  await page.goto('/login');
  await page.getByLabel('Email').fill('admin@teamhub.test');
  await page.getByLabel('Password').fill('Password1!');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.waitForURL('**/schedule**', { timeout: 10_000 });
}
