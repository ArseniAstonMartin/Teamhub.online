/**
 * TASK-089 · Journey 3 & 4: Job detail — status transitions + notes thread
 *
 * Uses loginAs() so that the Zustand user store is populated (ADMIN role).
 * The Axios 401-interceptor bootstrap flow is used on page.goto:
 *   1. Page loads with refresh token in localStorage but no access token in memory.
 *   2. API calls return 401 (no Authorization header).
 *   3. Interceptor calls /api/v1/auth/refresh → gets fake JWT → sets user=ADMIN.
 *   4. Retried requests have Authorization header → mocks return real data.
 */
import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth';
import {
  makeJobResponse,
  makeNote,
  type JobStatus,
  type NoteResponse,
} from './helpers/fixtures';

const JOB_ID = 'test-job-id';

/**
 * Registers GET /api/v1/jobs/{id} and GET /api/v1/jobs/{id}/notes mocks that
 * correctly simulate the 401→refresh→retry bootstrap cycle that populates the
 * Zustand user state after a hard page reload.
 */
async function setupJobDetailRoutes(
  page: import('@playwright/test').Page,
  opts: {
    initialStatus?: JobStatus;
    notes?: NoteResponse[];
  } = {},
) {
  const { initialStatus = 'SUBMITTED', notes: initialNotes = [] } = opts;

  let currentStatus: JobStatus = initialStatus;
  const notes: NoteResponse[] = [...initialNotes];

  // GET /api/v1/jobs/:id — returns 401 without auth, proper data with auth
  await page.route(
    (url) => url.pathname === `/api/v1/jobs/${JOB_ID}`,
    (route) => {
      const hasAuth = !!route.request().headers()['authorization'];
      if (!hasAuth) {
        return route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ error: { code: 'UNAUTHORIZED', message: 'Unauthorized' } }),
        });
      }
      return route.fulfill({ json: makeJobResponse(JOB_ID, currentStatus) });
    },
  );

  // POST /api/v1/jobs/:id/claim — transition SUBMITTED → ASSIGNED
  await page.route(
    (url) => url.pathname === `/api/v1/jobs/${JOB_ID}/claim`,
    (route) => {
      currentStatus = 'ASSIGNED';
      return route.fulfill({ json: makeJobResponse(JOB_ID, 'ASSIGNED') });
    },
  );

  // POST /api/v1/jobs/:id/complete — transition → COMPLETED
  await page.route(
    (url) => url.pathname === `/api/v1/jobs/${JOB_ID}/complete`,
    (route) => {
      currentStatus = 'COMPLETED';
      return route.fulfill({ json: makeJobResponse(JOB_ID, 'COMPLETED') });
    },
  );

  // GET + POST /api/v1/jobs/:id/notes
  await page.route(
    (url) => url.pathname === `/api/v1/jobs/${JOB_ID}/notes`,
    async (route) => {
      const hasAuth = !!route.request().headers()['authorization'];

      if (route.request().method() === 'POST') {
        if (!hasAuth) {
          return route.fulfill({
            status: 401,
            contentType: 'application/json',
            body: JSON.stringify({ error: { code: 'UNAUTHORIZED', message: 'Unauthorized' } }),
          });
        }
        const body = await route.request().postDataJSON();
        const note = makeNote(`note-${Date.now()}`, JOB_ID, (body as { body: string }).body);
        notes.unshift(note);
        return route.fulfill({ json: note });
      }

      // GET
      if (!hasAuth) {
        return route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({ error: { code: 'UNAUTHORIZED', message: 'Unauthorized' } }),
        });
      }
      return route.fulfill({ json: [...notes] });
    },
  );

  return {
    getStatus: () => currentStatus,
    getNotes: () => [...notes],
  };
}

// ── Journey 3A: job header shows correct status badge ───────────────────────

test('job detail page shows job header with Submitted status badge', async ({ page }) => {
  await loginAs(page, 'ADMIN');
  await setupJobDetailRoutes(page, { initialStatus: 'SUBMITTED' });

  await page.goto(`/jobs/${JOB_ID}`);

  // Wait for skeleton to disappear
  await expect(page.getByTestId('job-loading-skeleton')).not.toBeVisible({ timeout: 10_000 });

  // Job header section is visible
  await expect(page.getByTestId('job-header')).toBeVisible();

  // Status badge inside header shows "Submitted"
  const headerBadge = page.getByTestId('job-header').getByRole('status', { name: 'Submitted' });
  await expect(headerBadge).toBeVisible();
});

// ── Journey 3B: Claim action transitions status to Assigned ─────────────────

test('clicking Claim updates status badge to Assigned', async ({ page }) => {
  await loginAs(page, 'ADMIN');
  await setupJobDetailRoutes(page, { initialStatus: 'SUBMITTED' });

  await page.goto(`/jobs/${JOB_ID}`);
  await expect(page.getByTestId('job-loading-skeleton')).not.toBeVisible({ timeout: 10_000 });

  // Verify initial status
  await expect(
    page.getByTestId('job-header').getByRole('status', { name: 'Submitted' }),
  ).toBeVisible();

  // Click Claim button (data-testid="action-ASSIGNED")
  await page.getByTestId('action-ASSIGNED').click();

  // Optimistic update → badge switches to Assigned immediately
  await expect(
    page.getByTestId('job-header').getByRole('status', { name: 'Assigned' }),
  ).toBeVisible({ timeout: 5_000 });
});

// ── Journey 4A: add a note — appears in thread ───────────────────────────────

test('typing and submitting a note adds it to the notes thread', async ({ page }) => {
  await loginAs(page, 'ADMIN');
  await setupJobDetailRoutes(page, { initialStatus: 'SUBMITTED', notes: [] });

  await page.goto(`/jobs/${JOB_ID}`);
  await expect(page.getByTestId('job-loading-skeleton')).not.toBeVisible({ timeout: 10_000 });

  // Notes composer should be visible for ADMIN
  const composer = page.getByTestId('note-composer');
  await expect(composer).toBeVisible();

  // Type a note
  const noteText = 'Customer requested afternoon slot — confirmed via call.';
  await composer.getByLabel('Note text').fill(noteText);

  // Submit
  await composer.getByRole('button', { name: 'Add Note' }).click();

  // Note appears in the notes list (optimistic prepend)
  await expect(page.getByText(noteText, { exact: false })).toBeVisible({ timeout: 5_000 });
});

// ── Journey 4B: empty notes state ────────────────────────────────────────────

test('shows "No notes yet" when job has no notes', async ({ page }) => {
  await loginAs(page, 'ADMIN');
  await setupJobDetailRoutes(page, { initialStatus: 'SUBMITTED', notes: [] });

  await page.goto(`/jobs/${JOB_ID}`);
  await expect(page.getByTestId('job-loading-skeleton')).not.toBeVisible({ timeout: 10_000 });

  await expect(page.getByText('No notes yet.', { exact: true })).toBeVisible({ timeout: 5_000 });
});
