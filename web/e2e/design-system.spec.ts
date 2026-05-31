import { test, expect } from '@playwright/test';

test('design system page renders all primitives', async ({ page }, testInfo) => {
  await page.goto('/design-system');

  // Page title (h1)
  await expect(page.getByRole('heading', { name: 'Teamhub Design System', exact: true })).toBeVisible();

  // Section headings (h2) — role-scoped to avoid strict-mode violations from
  // demo content that shares words with heading text (e.g. "Open Dialog" button,
  // "Card Title" h3 in the typography section, etc.)
  await expect(page.getByRole('heading', { name: 'Typography — Manrope', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Color Palette — Brand Tokens', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Buttons', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Badges', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Card', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Form Controls — Input & Select', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Dialog', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Toast Notifications — Sonner', exact: true })).toBeVisible();

  // Save screenshot as baseline — use Playwright's testInfo.outputPath() which
  // works in both CJS and ESM (no __dirname needed) and auto-creates the directory.
  await page.screenshot({
    path: testInfo.outputPath('design-system-baseline.png'),
    fullPage: true,
  });
});
