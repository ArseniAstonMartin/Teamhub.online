import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { JobCreateDialog } from './JobCreateDialog';
import type { SchemaResponse } from '@/entities/schema/model/types';
import type { EvaluateAutomationsResponse } from '@/entities/automation/model/types';
import type { JobResponse } from '@/entities/job/model/types';

// ── Hoisted mocks ─────────────────────────────────────────────────────────────

const {
  mockUseSchemas,
  mockUseCreateJob,
  mockUseCreateCustomer,
  mockUseEvaluateAutomations,
  mockUseCustomers,
} = vi.hoisted(() => ({
  mockUseSchemas: vi.fn(),
  mockUseCreateJob: vi.fn(),
  mockUseCreateCustomer: vi.fn(),
  mockUseEvaluateAutomations: vi.fn(),
  mockUseCustomers: vi.fn(),
}));

vi.mock('@/entities/schema/api/schemasApi', () => ({
  useSchemas: mockUseSchemas,
}));

vi.mock('@/entities/job/api/jobsApi', () => ({
  useCreateJob: mockUseCreateJob,
}));

vi.mock('@/entities/customer/api/customersApi', () => ({
  useCustomers: mockUseCustomers,
  useCreateCustomer: mockUseCreateCustomer,
}));

vi.mock('@/entities/automation/api/automationsApi', () => ({
  useEvaluateAutomations: mockUseEvaluateAutomations,
}));

// Ensure the AddressAutocomplete fallback renders (no API key in test env)
vi.stubEnv('VITE_GOOGLE_MAPS_API_KEY', '');

// ── Factories ─────────────────────────────────────────────────────────────────

function makeSchema(overrides: Partial<SchemaResponse> = {}): SchemaResponse {
  return {
    id: 'schema-1',
    name: 'Default',
    version: 1,
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    fieldDefinitions: [
      {
        key: 'service_type',
        label: 'Service Type',
        type: 'dropdown',
        required: true,
        order: 1,
        options: ['Plumbing', 'Electrical', 'HVAC'],
        depends_on: null,
        options_by_parent: null,
        validation: null,
      },
      {
        key: 'notes',
        label: 'Notes',
        type: 'text',
        required: false,
        order: 2,
        options: null,
        depends_on: null,
        options_by_parent: null,
        validation: null,
      },
    ],
    ...overrides,
  };
}

function makeSchemaWithDependent(): SchemaResponse {
  return makeSchema({
    fieldDefinitions: [
      {
        key: 'category',
        label: 'Category',
        type: 'dropdown',
        required: false,
        order: 1,
        options: ['Residential', 'Commercial'],
        depends_on: null,
        options_by_parent: null,
        validation: null,
      },
      {
        key: 'sub_category',
        label: 'Sub-category',
        type: 'dropdown',
        required: false,
        order: 2,
        options: null,
        depends_on: 'category',
        options_by_parent: {
          Residential: ['House', 'Apartment'],
          Commercial: ['Office', 'Warehouse'],
        },
        validation: null,
      },
    ],
  });
}

function makeSchemaWithAddress(): SchemaResponse {
  return makeSchema({
    fieldDefinitions: [
      {
        key: 'address',
        label: 'Address',
        type: 'text',
        required: false,
        order: 1,
        options: null,
        depends_on: null,
        options_by_parent: null,
        validation: null,
      },
    ],
  });
}

const noopMutateAsync = vi.fn().mockResolvedValue({} as EvaluateAutomationsResponse);
const noopCreateMutate = vi.fn().mockResolvedValue({} as JobResponse);
const noopCreateCustomerMutate = vi.fn().mockResolvedValue({
  id: 'cust-new',
  tenantId: 'tenant-1',
  name: 'New Customer',
  companyName: null,
  phone: '+15035559999',
  email: null,
  addressLine: '—',
  city: '',
  state: '',
  zip: '',
  lat: null,
  lon: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  archivedAt: null,
});

const MOCK_CUSTOMER = {
  id: 'cust-1',
  tenantId: 'tenant-1',
  name: 'Alice Smith',
  companyName: 'Acme Co',
  phone: '+15035551234',
  email: 'alice@example.com',
  addressLine: '123 Main St',
  city: 'Portland',
  state: 'OR',
  zip: '97201',
  lat: 45.5,
  lon: -122.6,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  archivedAt: null,
};

function setupDefaultMocks() {
  mockUseSchemas.mockReturnValue({ data: [makeSchema()] });
  mockUseCreateJob.mockReturnValue({
    mutateAsync: noopCreateMutate,
    isPending: false,
  });
  mockUseCreateCustomer.mockReturnValue({
    mutateAsync: noopCreateCustomerMutate,
    isPending: false,
  });
  mockUseEvaluateAutomations.mockReturnValue({
    mutateAsync: noopMutateAsync,
  });
  mockUseCustomers.mockReturnValue({
    data: {
      content: [MOCK_CUSTOMER],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 8,
    },
  });
}

async function fillCoreJobFields(mode: 'existing' | 'new' = 'existing') {
  if (mode === 'existing') {
    await userEvent.type(screen.getByLabelText(/^name/i), 'Alice');
    const suggestion = await screen.findByRole(
      'option',
      { name: 'Alice Smith - alice@example.com - Acme Co - +15035551234' },
      { timeout: 1000 },
    );
    await userEvent.click(suggestion);
  } else {
    await userEvent.type(screen.getByLabelText(/^name/i), 'New Customer');
    await userEvent.type(screen.getByLabelText(/phone number/i), '+15035559999');
    await userEvent.type(screen.getByLabelText(/^address$/i), '123 Main St, Portland, OR');
  }

  await userEvent.type(screen.getByLabelText(/scheduled start/i), '2026-06-15T09:00');
}

// ── Render helper ─────────────────────────────────────────────────────────────

function renderDialog(open = true, onClose = vi.fn()) {
  return render(<JobCreateDialog open={open} onClose={onClose} />);
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('JobCreateDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupDefaultMocks();
  });

  // ── AC1: Form fields rendered from active schema ───────────────────────────

  it('renders form fields from the mocked active schema sorted by order', () => {
    renderDialog();
    expect(screen.getByLabelText(/^name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/phone number/i)).toBeInTheDocument();
    expect(screen.getByText('Scheduled start')).toBeInTheDocument();
    expect(screen.getByText('Service Type')).toBeInTheDocument();
    expect(screen.getByText('Notes')).toBeInTheDocument();
  });

  it('shows customer suggestions while typing name', async () => {
    renderDialog();
    await userEvent.type(screen.getByLabelText(/^name/i), 'Alice');
    await waitFor(
      () => {
        expect(screen.getByTestId('customer-suggestions')).toBeInTheDocument();
        expect(
          screen.getByRole('option', {
            name: 'Alice Smith - alice@example.com - Acme Co - +15035551234',
          }),
        ).toBeInTheDocument();
      },
      { timeout: 1000 },
    );
  });

  it('renders a dropdown for dropdown-type fields', () => {
    renderDialog();
    expect(screen.getByRole('combobox', { name: 'Service Type' })).toBeInTheDocument();
  });

  it('renders a text input for text-type fields', () => {
    renderDialog();
    const input = screen.getByLabelText(/notes/i);
    expect(input).toHaveAttribute('type', 'text');
  });

  it('shows "required" indicator (*) for required fields', () => {
    renderDialog();
    // The Service Type label should have an asterisk child
    const label = screen.getByText('Service Type');
    expect(label.closest('div')?.textContent).toContain('*');
  });

  // ── AC1: Required field validation ────────────────────────────────────────

  it('shows validation error when required field is empty on submit', async () => {
    renderDialog();
    await userEvent.click(screen.getByRole('button', { name: /create job/i }));
    await waitFor(() => {
      expect(screen.getByText(/name is required/i)).toBeInTheDocument();
      expect(screen.getByText(/phone number is required/i)).toBeInTheDocument();
      expect(screen.getByText(/address is required/i)).toBeInTheDocument();
      expect(screen.getByText(/scheduled start is required/i)).toBeInTheDocument();
      expect(screen.getByText(/service type is required/i)).toBeInTheDocument();
    });
  });

  it('does not call createJob when required field is missing', async () => {
    const mutate = vi.fn().mockResolvedValue({});
    mockUseCreateJob.mockReturnValue({ mutateAsync: mutate, isPending: false });
    renderDialog();
    await userEvent.click(screen.getByRole('button', { name: /create job/i }));
    await waitFor(() => {
      expect(mutate).not.toHaveBeenCalled();
    });
  });

  // ── AC2: Dependent dropdowns ──────────────────────────────────────────────

  it('dependent dropdown is disabled until parent is set', () => {
    mockUseSchemas.mockReturnValue({ data: [makeSchemaWithDependent()] });
    renderDialog();
    const subCombo = screen.getByRole('combobox', { name: 'Sub-category' });
    expect(subCombo).toBeDisabled();
  });

  it('dependent dropdown enables when parent is set', async () => {
    mockUseSchemas.mockReturnValue({ data: [makeSchemaWithDependent()] });
    renderDialog();

    // Use exact label text to avoid matching "Sub-category" which also contains "category"
    const categoryCombo = screen.getByRole('combobox', { name: 'Category' });
    await userEvent.click(categoryCombo);
    const option = await screen.findByRole('option', { name: 'Residential' });
    await userEvent.click(option);

    await waitFor(() => {
      const subCombo = screen.getByRole('combobox', { name: 'Sub-category' });
      expect(subCombo).not.toBeDisabled();
    });
  });

  // ── AC3: AddressAutocomplete fallback ─────────────────────────────────────

  it('AddressAutocomplete renders plain input fallback when API key not set', () => {
    mockUseSchemas.mockReturnValue({ data: [makeSchemaWithAddress()] });
    renderDialog();
    expect(screen.getAllByTestId('address-autocomplete-fallback').length).toBeGreaterThanOrEqual(1);
  });

  // ── AC4: Valid form submission ─────────────────────────────────────────────

  it('submitting valid form with new customer creates customer then job', async () => {
    const createCustomer = vi.fn().mockResolvedValue({ id: 'cust-new' });
    const createJob = vi.fn().mockResolvedValue({ id: 'new-job-1' });
    mockUseCreateCustomer.mockReturnValue({ mutateAsync: createCustomer, isPending: false });
    mockUseCreateJob.mockReturnValue({ mutateAsync: createJob, isPending: false });
    renderDialog();

    await fillCoreJobFields('new');

    const combo = screen.getByRole('combobox', { name: 'Service Type' });
    await userEvent.click(combo);
    await userEvent.click(await screen.findByRole('option', { name: 'Plumbing' }));
    await userEvent.click(screen.getByRole('button', { name: /create job/i }));

    await waitFor(() => {
      expect(createCustomer).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'New Customer',
          phone: '+15035559999',
          addressLine: '123 Main St, Portland, OR',
          address: '123 Main St, Portland, OR',
        }),
      );
      expect(createJob).toHaveBeenCalledWith(
        expect.objectContaining({
          customerId: 'cust-new',
          customFields: expect.objectContaining({ service_type: 'Plumbing' }),
        }),
      );
    });
  });

  it('submitting valid form calls POST /api/v1/jobs with correct body', async () => {
    const mutate = vi.fn().mockResolvedValue({ id: 'new-job-1' });
    mockUseCreateJob.mockReturnValue({ mutateAsync: mutate, isPending: false });
    renderDialog();

    await fillCoreJobFields();

    // Open the Service Type dropdown and pick Plumbing
    const combo = screen.getByRole('combobox', { name: 'Service Type' });
    await userEvent.click(combo);
    const plumbing = await screen.findByRole('option', { name: 'Plumbing' });
    await userEvent.click(plumbing);

    await userEvent.click(screen.getByRole('button', { name: /create job/i }));

    await waitFor(() => {
      expect(mutate).toHaveBeenCalledWith(
        expect.objectContaining({
          customerId: 'cust-1',
          scheduledStart: expect.any(String) as string,
          customFields: expect.objectContaining({ service_type: 'Plumbing' }),
          appointmentSchemaId: 'schema-1',
        }),
      );
    });
  });

  // ── AC4: Success closes dialog and invalidates query ─────────────────────

  it('success closes dialog', async () => {
    const mutate = vi.fn().mockResolvedValue({ id: 'new-job-1' });
    mockUseCreateJob.mockReturnValue({ mutateAsync: mutate, isPending: false });
    const onClose = vi.fn();
    render(<JobCreateDialog open={true} onClose={onClose} />);

    await fillCoreJobFields();

    const combo = screen.getByRole('combobox', { name: 'Service Type' });
    await userEvent.click(combo);
    const plumbing = await screen.findByRole('option', { name: 'Plumbing' });
    await userEvent.click(plumbing);

    await userEvent.click(screen.getByRole('button', { name: /create job/i }));

    await waitFor(() => {
      expect(onClose).toHaveBeenCalled();
    });
  });

  // ── AC5: Automation evaluate called on field change ────────────────────────

  it('automation evaluate is called after field change (debounced)', async () => {
    const evaluateMock = vi.fn().mockResolvedValue({
      suggested_patch: {},
      auto_filled_fields: [],
    });
    mockUseEvaluateAutomations.mockReturnValue({ mutateAsync: evaluateMock });

    renderDialog();

    const notesInput = screen.getByLabelText(/notes/i);
    await userEvent.type(notesInput, 'test note');

    await waitFor(
      () => {
        expect(evaluateMock).toHaveBeenCalled();
      },
      { timeout: 1000 },
    );
  });

  // ── AC5: Auto-fill badge shown and undo works ─────────────────────────────

  it('shows auto-fill badge when automation returns auto_filled_fields', async () => {
    // Auto-fill "notes" when user types something in the notes field but the
    // automation fills a *different* field (service_type) that hasn't been touched
    const evaluateMock = vi.fn().mockResolvedValue({
      suggested_patch: { service_type: 'Plumbing' },
      auto_filled_fields: [
        {
          field_key: 'service_type',
          value: 'Plumbing',
          rule_id: 'rule-1',
          rule_name: 'My Rule',
        },
      ],
    });
    mockUseEvaluateAutomations.mockReturnValue({ mutateAsync: evaluateMock });

    renderDialog();

    // User types in the notes field, which is NOT the field being auto-filled
    const notesInput = screen.getByLabelText(/notes/i);
    await userEvent.type(notesInput, 'trigger');

    await waitFor(
      () => {
        // The auto-fill badge should appear on service_type label area
        expect(screen.getByText(/auto-filled by my rule/i)).toBeInTheDocument();
      },
      { timeout: 1000 },
    );
  });

  it('undo auto-fill clears the value and badge', async () => {
    const evaluateMock = vi.fn().mockResolvedValue({
      suggested_patch: { service_type: 'Plumbing' },
      auto_filled_fields: [
        {
          field_key: 'service_type',
          value: 'Plumbing',
          rule_id: 'rule-1',
          rule_name: 'My Rule',
        },
      ],
    });
    mockUseEvaluateAutomations.mockReturnValue({ mutateAsync: evaluateMock });

    renderDialog();

    // User types in notes; automation auto-fills service_type
    const notesInput = screen.getByLabelText(/notes/i);
    await userEvent.type(notesInput, 'trigger');

    // Wait for badge to appear on service_type
    const badge = await screen.findByText(/auto-filled by my rule/i, {}, { timeout: 1000 });
    expect(badge).toBeInTheDocument();

    // Click the undo button (×) inside the badge span
    const undoBtn = within(badge.closest('span')!).getByRole('button');
    await userEvent.click(undoBtn);

    await waitFor(() => {
      expect(screen.queryByText(/auto-filled by my rule/i)).not.toBeInTheDocument();
    });
  });

  // ── Cancel button ─────────────────────────────────────────────────────────

  it('clicking Cancel calls onClose', async () => {
    const onClose = vi.fn();
    render(<JobCreateDialog open={true} onClose={onClose} />);
    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });

  // ── No active schema ──────────────────────────────────────────────────────

  it('shows message when no active schema is available', () => {
    mockUseSchemas.mockReturnValue({ data: [] });
    renderDialog();
    expect(screen.getByText(/no active schema found/i)).toBeInTheDocument();
  });
});
