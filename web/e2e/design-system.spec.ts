import { test, expect } from '@playwright/test';
import path from 'path';
import fs from 'fs';

test('design system page renders all primitives', async ({ page }) => {
  await page.goto('/design-system');

  // Verify page title
  await expect(page.getByText('Teamhub Design System')).toBeVisible();

  // Verify key sections are present
  await expect(page.getByText('Typography — Manrope')).toBeVisible();
  await expect(page.getByText('Color Palette — Brand Tokens')).toBeVisible();
  await expect(page.getByText('Buttons')).toBeVisible();
  await expect(page.getByText('Badges')).toBeVisible();
  await expect(page.getByText('Card')).toBeVisible();
  await expect(page.getByText('Form Controls — Input & Select')).toBeVisible();
  await expect(page.getByText('Dialog')).toBeVisible();
  await expect(page.getByText('Toast Notifications — Sonner')).toBeVisible();

  // Save screenshot as baseline
  const screenshotDir = path.join(__dirname, 'screenshots');
  if (!fs.existsSync(screenshotDir)) {
    fs.mkdirSync(screenshotDir, { recursive: true });
  }

  await page.screenshot({
    path: path.join(screenshotDir, 'design-system-baseline.png'),
    fullPage: true,
  });
});
