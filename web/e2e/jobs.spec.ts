/**
 * TASK-089 · Journey 2: Jobs list + Create Job
 *
 * Auth is bootstrapped by injecting a refresh token into localStorage
 * (no UI login required for these tests — AuthGate passes when refresh
 * token is present, even without an in-memory access token).
 *
 * Data is seeded via page.route() mocks (no real backend).
 */
import { test, expect } from '@playwright/test';
import { setupAuth } from './helpers/auth';
import {
  makeJobDto,
  makeJobsPage,
  makeSchema,
} from './helpers/fixtures';

// ── Shared job fixture ───────────────────────────────────────────────────────

const JOB_ID = 'job-abc123';

// ── Journey 2A: jobs list page loads ────────────────────────────────────────

test('jobs page renders heading and New Job button', async ({ page }) => {
  await setupAuth(page);

  await page.route((url) => url.pathname === '/api/v1/jobs', (route) =>
    route.fulfill({ json: makeJobsPage([]) }),
  );

  await page.goto('/jobs');

  await expect(page.getByRole('heading', { name: 'Jobs', exact: true })).toBeVisible();
  await expect(page.getByTestId('new-job-btn')).toBeVisible();
});

// ── Journey 2B: empty state ──────────────────────────────────────────────────

test('shows empty state when there are no jobs', async ({ page }) => {
  await setupAuth(page);

  await page.route((url) => url.pathname === '/api/v1/jobs', (route) =>
    route.fulfill({ json: makeJobsPage([]) }),
  );

  await page.goto('/jobs');

  await expect(
    page.getByText('No jobs found', { exact: false }),
  ).toBeVisible({ timeout: 5_000 });
});

// ── Journey 2C: open Create Job dialog ──────────────────────────────────────

test('opens New Job dialog when button is clicked', async ({ page }) => {
  await setupAuth(page);

  await page.route((url) => url.pathname === '/api/v1/jobs', (route) =>
    route.fulfill({ json: makeJobsPage([]) }),
  );
  await page.route((url) => url.pathname === '/api/v1/schemas', (route) =>
    route.fulfill({ json: [makeSchema()] }),
  );
  // Automation evaluate fires on every field change (debounced 300 ms)
  await page.route((url) => url.pathname === '/api/v1/automations/evaluate', (route) =>
    route.fulfill({ json: { suggested_patch: {}, auto_filled_fields: [] } }),
  );

  await page.goto('/jobs');
  await page.getByTestId('new-job-btn').click();

  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'New Job', exact: true })).toBeVisible();
});

// ── Journey 2D: create a job and see it in the list ─────────────────────────

test('creates a new job and it appears in the list', async ({ page }) => {
  await setupAuth(page);

  const newJob = makeJobDto(JOB_ID, 'SUBMITTED');
  let jobCreated = false;

  // Before creation: empty list; after: list with the new job
  await page.route((url) => url.pathname === '/api/v1/jobs', (route) => {
    if (route.request().method() === 'POST') {
      jobCreated = true;
      return route.fulfill({
        status: 201,
        json: { ...newJob, customFields: {} },
      });
    }
    return route.fulfill({
      json: makeJobsPage(jobCreated ? [newJob] : []),
    });
  });

  await page.route((url) => url.pathname === '/api/v1/schemas', (route) =>
    route.fulfill({ json: [makeSchema()] }),
  );
  await page.route((url) => url.pathname === '/api/v1/automations/evaluate', (route) =>
    route.fulfill({ json: { suggested_patch: {}, auto_filled_fields: [] } }),
  );

  await page.goto('/jobs');

  // Open create dialog
  await page.getByTestId('new-job-btn').click();
  await expect(page.getByRole('dialog')).toBeVisible();

  // Fill required text field ("Service Type")
  await page.getByLabel('Service Type').fill('Plumbing repair');

  // Submit
  await page.getByRole('button', { name: 'Create Job' }).click();

  // Dialog should close
  await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 8_000 });

  // The job row appears in the table
  await expect(page.getByRole('status', { name: 'Submitted' }).first()).toBeVisible({ timeout: 5_000 });
  await expect(page.getByText('Acme Corp')).toBeVisible();
});
