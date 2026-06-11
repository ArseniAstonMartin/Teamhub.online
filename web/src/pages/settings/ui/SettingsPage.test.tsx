import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import SettingsPage from './SettingsPage';

describe('SettingsPage', () => {
  it('redirects /settings to /settings/tenant', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/settings']}>
        <Routes>
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/settings/tenant" element={<div data-testid="tenant-page" />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(container.querySelector('[data-testid="tenant-page"]')).toBeInTheDocument();
  });
});
