/**
 * TASK-089 · Journey 1: Login flow
 *
 * Tests run against the production preview build (no live backend).
 * All API calls are intercepted with page.route() handlers.
 */
import { test, expect } from '@playwright/test';
import { makeToken } from './helpers/auth';

// ── Journey 1A: login page renders ──────────────────────────────────────────

test('login page renders sign-in form', async ({ page }) => {
  await page.goto('/login');

  await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible();
  await expect(page.getByLabel('Email')).toBeVisible();
  await expect(page.getByLabel('Password')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
});

// ── Journey 1B: successful login redirects to /schedule ──────────────────────

test('successful login redirects to /schedule', async ({ page }) => {
  const fakeToken = makeToken('ADMIN');

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

  await page.goto('/login');
  await page.getByLabel('Email').fill('admin@teamhub.test');
  await page.getByLabel('Password').fill('Password1!');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();

  await page.waitForURL('**/schedule**', { timeout: 10_000 });
  expect(page.url()).toContain('/schedule');
});

// ── Journey 1C: wrong credentials shows error toast ─────────────────────────

test('invalid credentials show an error notification', async ({ page }) => {
  // Use 400 (Bad Request) rather than 401 so the Axios refresh interceptor
  // does not intercept the error and redirect to /login before the toast renders.
  // Real backends commonly return 400/422 for wrong credentials, reserving 401
  // for "session expired / unauthenticated" cases.
  await page.route((url) => url.pathname === '/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: JSON.stringify({
        error: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' },
      }),
    }),
  );

  await page.goto('/login');
  await page.getByLabel('Email').fill('wrong@test.com');
  await page.getByLabel('Password').fill('badpassword');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();

  // Sonner renders toasts as `li[role="status"]` in a `ol[aria-label="Notifications"]`
  await expect(
    page.getByText('Invalid email or password', { exact: false }),
  ).toBeVisible({ timeout: 5_000 });

  // URL must NOT change to /schedule
  expect(page.url()).toContain('/login');
});
