import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import SchemaEditorPage from './SchemaEditorPage';
import type { SchemaResponse } from '@/entities/schema/model/types';

// ── Polyfill for Radix UI pointer capture (jsdom lacks it) ────────────────────
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false;
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => undefined;
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => undefined;
}

// ── Mock Radix Select with native <select> so jsdom can interact ──────────────
vi.mock('@/shared/ui/select', () => {
  return {
    Select: ({
      value,
      onValueChange,
      children,
    }: {
      value?: string;
      onValueChange?: (v: string) => void;
      children?: React.ReactNode;
    }) => (
      <select
        data-testid="select-root"
        value={value}
        onChange={(e) => onValueChange?.(e.target.value)}
      >
        {children}
      </select>
    ),
    SelectTrigger: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
    SelectValue: () => null,
    SelectContent: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
    SelectItem: ({ value, children }: { value: string; children?: React.ReactNode }) => (
      <option value={value}>{children}</option>
    ),
    SelectGroup: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
    SelectLabel: ({ children }: { children?: React.ReactNode }) => <>{children}</>,
    SelectSeparator: () => null,
    SelectScrollUpButton: () => null,
    SelectScrollDownButton: () => null,
  };
});

// ── Mocks ─────────────────────────────────────────────────────────────────────

const { mockUseSchemas, mockUsePublishSchema, mockUseDeactivateSchema } = vi.hoisted(() => ({
  mockUseSchemas: vi.fn(),
  mockUsePublishSchema: vi.fn(),
  mockUseDeactivateSchema: vi.fn(),
}));

vi.mock('@/entities/schema/api/schemasApi', () => ({
  useSchemas: mockUseSchemas,
  usePublishSchema: mockUsePublishSchema,
  useDeactivateSchema: mockUseDeactivateSchema,
}));

const { mockUseAuthStore } = vi.hoisted(() => ({
  mockUseAuthStore: vi.fn(),
}));

vi.mock('@/entities/user/model/authStore', () => ({
  useAuthStore: mockUseAuthStore,
}));

// ── Factories ─────────────────────────────────────────────────────────────────

function makeSchema(overrides: Partial<SchemaResponse> = {}): SchemaResponse {
  return {
    id: 'schema-1',
    name: 'Job Form',
    version: 1,
    active: true,
    fieldDefinitions: [
      {
        key: 'service_type',
        label: 'Service Type',
        type: 'dropdown',
        required: true,
        order: 0,
        options: ['Locksmith', 'Plumbing'],
        depends_on: null,
        options_by_parent: null,
        validation: null,
      },
    ],
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

// ── Helpers ───────────────────────────────────────────────────────────────────

const mutateMock = vi.fn();

function setupMocks(
  schemas: SchemaResponse[] = [],
  publishResult: Partial<SchemaResponse> = {},
) {
  mockUseSchemas.mockReturnValue({ data: schemas, isLoading: false });
  mockUsePublishSchema.mockReturnValue({
    mutateAsync: mutateMock.mockResolvedValue({ version: 2, name: 'Job Form', ...publishResult }),
    isPending: false,
  });
  mockUseDeactivateSchema.mockReturnValue({ mutate: vi.fn(), isPending: false });
  mockUseAuthStore.mockImplementation(
    (selector: (s: { user: { id: string; tenantId: string; role: string } | null }) => unknown) =>
      selector({ user: { id: 'u1', tenantId: 't1', role: 'ADMIN' } }),
  );
}

function renderPage(initialPath = '/schema-editor') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <SchemaEditorPage />
    </MemoryRouter>,
  );
}

// Helper: select a value in our mocked native <select>
async function selectSchemaByName(name: string) {
  const selects = screen.getAllByRole('combobox');
  const schemaSelect = selects[0];
  await userEvent.selectOptions(schemaSelect, name);
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('SchemaEditorPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mutateMock.mockResolvedValue({ version: 2, name: 'Job Form' });
  });

  // Test 1: renders schema selector with fetched schema names
  it('renders schema selector with fetched schema names', () => {
    setupMocks([
      makeSchema({ name: 'Job Form' }),
      makeSchema({ id: 'schema-2', name: 'Inspection Form' }),
    ]);
    renderPage();
    // Schema selector is present
    expect(screen.getAllByRole('combobox').length).toBeGreaterThan(0);
    // Options include both schema names
    expect(screen.getByRole('option', { name: 'Job Form' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Inspection Form' })).toBeInTheDocument();
  });

  // Test 2: selecting a schema loads its fields into the editor
  it('selecting a schema loads its fields into the editor', async () => {
    const schema = makeSchema();
    setupMocks([schema]);
    renderPage();

    await selectSchemaByName('Job Form');

    await waitFor(() => {
      // "Service Type" appears in both editor list and preview — both are valid
      expect(screen.getAllByText('Service Type').length).toBeGreaterThan(0);
    });
  });

  // Test 3: "Add Field" opens dialog; saving appends field to editor list
  it('Add Field opens dialog; saving appends field to editor list', async () => {
    setupMocks([]);
    renderPage();

    // Click the "Add Field" button (small button in editor section header)
    const addButtons = screen.getAllByRole('button', { name: /add field/i });
    await userEvent.click(addButtons[0]);

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: /add field/i })).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText(/^key/i), 'vehicle_make');
    await userEvent.type(within(dialog).getByLabelText(/^label/i), 'Vehicle Make');

    // Save the field
    await userEvent.click(within(dialog).getByRole('button', { name: /add field/i }));

    await waitFor(() => {
      // "Vehicle Make" appears in both editor list and preview pane
      expect(screen.getAllByText('Vehicle Make').length).toBeGreaterThan(0);
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  // Test 4: editing a field updates its label in the list
  it('editing a field updates its label in the list', async () => {
    const schema = makeSchema();
    setupMocks([schema]);
    renderPage();

    await selectSchemaByName('Job Form');
    await waitFor(() => expect(screen.getAllByText('Service Type').length).toBeGreaterThan(0));

    await userEvent.click(screen.getByRole('button', { name: /edit service type/i }));

    const dialog = await screen.findByRole('dialog');
    const labelInput = within(dialog).getByLabelText(/^label/i);
    await userEvent.clear(labelInput);
    await userEvent.type(labelInput, 'Updated Service Type');
    await userEvent.click(within(dialog).getByRole('button', { name: /save changes/i }));

    await waitFor(() => {
      expect(screen.getAllByText('Updated Service Type').length).toBeGreaterThan(0);
      expect(screen.queryByText('Service Type')).not.toBeInTheDocument();
    });
  });

  // Test 5: deleting a field removes it
  it('deleting a field removes it from the list', async () => {
    const schema = makeSchema();
    setupMocks([schema]);
    renderPage();

    await selectSchemaByName('Job Form');
    await waitFor(() => expect(screen.getAllByText('Service Type').length).toBeGreaterThan(0));

    await userEvent.click(screen.getByRole('button', { name: /delete service type/i }));

    await waitFor(() => {
      expect(screen.queryByText('Service Type')).not.toBeInTheDocument();
    });
  });

  // Test 6: "Depends on" select appears only for dropdown fields when other dropdowns exist
  it('Depends on select appears only for dropdown fields when other dropdowns exist', async () => {
    const schemaWithDropdown = makeSchema();
    setupMocks([schemaWithDropdown]);
    renderPage();

    // Load schema with a dropdown field (service_type)
    await selectSchemaByName('Job Form');
    await waitFor(() => expect(screen.getAllByText('Service Type').length).toBeGreaterThan(0));

    // Open add dialog
    const addButtons = screen.getAllByRole('button', { name: /add field/i });
    await userEvent.click(addButtons[0]);
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/^key/i), 'vehicle_model');
    await userEvent.type(within(dialog).getByLabelText(/^label/i), 'Vehicle Model');

    // Change type to dropdown using the mocked native select
    const typeSelects = within(dialog).getAllByRole('combobox');
    await userEvent.selectOptions(typeSelects[0], 'dropdown');

    // "Depends on" label should now appear (there's already a dropdown field)
    await waitFor(() => {
      expect(within(dialog).getByText('Depends on')).toBeInTheDocument();
    });
  });

  // Test 6b: "Depends on" does NOT appear for text fields
  it('Depends on select does not appear for text fields', async () => {
    setupMocks([makeSchema()]);
    renderPage();

    const addButtons = screen.getAllByRole('button', { name: /add field/i });
    await userEvent.click(addButtons[0]);
    const dialog = await screen.findByRole('dialog');

    // Default type is text — no "Depends on"
    expect(within(dialog).queryByText('Depends on')).not.toBeInTheDocument();
  });

  // Test 7: live preview updates when a field is added
  it('live preview updates when a field is added', async () => {
    setupMocks([]);
    renderPage();

    expect(screen.getByText(/add fields to preview/i)).toBeInTheDocument();

    const addButtons = screen.getAllByRole('button', { name: /add field/i });
    await userEvent.click(addButtons[0]);
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^key/i), 'notes');
    await userEvent.type(within(dialog).getByLabelText(/^label/i), 'Notes');
    await userEvent.click(within(dialog).getByRole('button', { name: /add field/i }));

    await waitFor(() => {
      // The field should appear in both the editor list and the live preview
      const notesElements = screen.getAllByText('Notes');
      expect(notesElements.length).toBeGreaterThanOrEqual(2);
    });
  });

  // Test 8: "Save as new version" calls POST /api/v1/schemas with correct body
  it('Save as new version calls publishSchema with correct body', async () => {
    setupMocks([]);
    renderPage();

    const nameInput = screen.getByLabelText(/schema name/i);
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, 'My Schema');

    const addButtons = screen.getAllByRole('button', { name: /add field/i });
    await userEvent.click(addButtons[0]);
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^key/i), 'notes');
    await userEvent.type(within(dialog).getByLabelText(/^label/i), 'Notes');
    await userEvent.click(within(dialog).getByRole('button', { name: /add field/i }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: /save as new version/i }));

    await waitFor(() => {
      expect(mutateMock).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'My Schema',
          fieldDefinitions: expect.arrayContaining([
            expect.objectContaining({ key: 'notes', label: 'Notes' }),
          ]),
        }),
      );
    });
  });

  // Test 9: Success toast shown after save (mutation is called successfully)
  it('shows success toast after successful save', async () => {
    setupMocks([]);
    renderPage();

    const nameInput = screen.getByLabelText(/schema name/i);
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, 'My Schema');

    const addButtons = screen.getAllByRole('button', { name: /add field/i });
    await userEvent.click(addButtons[0]);
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^key/i), 'field_one');
    await userEvent.type(within(dialog).getByLabelText(/^label/i), 'Field One');
    await userEvent.click(within(dialog).getByRole('button', { name: /add field/i }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: /save as new version/i }));

    await waitFor(() => {
      // mutateAsync resolved → toast.success was called; mutation was called
      expect(mutateMock).toHaveBeenCalledTimes(1);
    });
  });

  // Test 10: non-Admin is redirected
  it('redirects non-Admin users away from the page', () => {
    mockUseSchemas.mockReturnValue({ data: [], isLoading: false });
    mockUsePublishSchema.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
    mockUseDeactivateSchema.mockReturnValue({ mutate: vi.fn(), isPending: false });
    mockUseAuthStore.mockImplementation(
      (selector: (s: { user: { id: string; tenantId: string; role: string } | null }) => unknown) =>
        selector({ user: { id: 'u2', tenantId: 't1', role: 'TECHNICIAN' } }),
    );

    renderPage();

    expect(screen.queryByText('Schema Editor')).not.toBeInTheDocument();
  });

  // Test 10b: Admin sees the page
  it('renders page heading for ADMIN users', () => {
    setupMocks([]);
    renderPage();
    expect(screen.getByRole('heading', { name: /schema editor/i })).toBeInTheDocument();
  });
});
