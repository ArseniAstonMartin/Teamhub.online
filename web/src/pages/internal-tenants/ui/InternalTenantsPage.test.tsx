import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import InternalTenantsPage from './InternalTenantsPage';

const { mockMutateAsync, mockTenants, mockPresets } = vi.hoisted(() => ({
  mockMutateAsync: vi.fn(),
  mockTenants: vi.fn(),
  mockPresets: vi.fn(),
}));

vi.mock('@/entities/staff/api/internalApi', () => ({
  useInternalTenants: () => mockTenants(),
  useSchemaPresets: () => mockPresets(),
  useProvisionTenant: () => ({
    mutateAsync: mockMutateAsync,
    isPending: false,
  }),
}));

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

describe('InternalTenantsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockTenants.mockReturnValue({
      data: {
        content: [
          {
            id: 't-1',
            name: 'Acme Plumbing',
            plan: 'FREE',
            createdAt: '2026-06-01T00:00:00Z',
            userCount: 1,
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      },
      isLoading: false,
    });
    mockPresets.mockReturnValue({
      data: [{ presetKey: 'plumber', schemaName: 'Plumber' }],
    });
  });

  it('lists tenants and shows create form', () => {
    render(
      <MemoryRouter>
        <InternalTenantsPage />
      </MemoryRouter>,
    );
    expect(screen.getByRole('heading', { level: 1, name: 'Tenants' })).toBeInTheDocument();
    expect(screen.getByText('Acme Plumbing')).toBeInTheDocument();
    expect(screen.getByLabelText(/tenant name/i)).toBeInTheDocument();
  });

  it('shows invite success after provisioning', async () => {
    mockMutateAsync.mockResolvedValueOnce({
      tenantId: 'new-tenant',
      inviteId: 'invite-1',
      ownerEmail: 'owner@example.com',
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <InternalTenantsPage />
      </MemoryRouter>,
    );

    await user.type(screen.getByLabelText(/tenant name/i), 'New Co');
    await user.type(screen.getByLabelText(/owner email/i), 'owner@example.com');
    await user.click(screen.getByRole('button', { name: /create tenant/i }));

    expect(mockMutateAsync).toHaveBeenCalledWith({
      name: 'New Co',
      ownerEmail: 'owner@example.com',
      presetKey: undefined,
    });
    expect(await screen.findByTestId('provision-success')).toBeInTheDocument();
    expect(screen.getByText(/owner@example.com/)).toBeInTheDocument();
  });
});
