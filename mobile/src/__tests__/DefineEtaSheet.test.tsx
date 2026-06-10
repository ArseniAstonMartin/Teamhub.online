import React from 'react';
import { Alert } from 'react-native';
import { render, fireEvent, waitFor, act } from '@testing-library/react-native';
import DefineEtaSheet from '../components/DefineEtaSheet';
import type { JobResponse } from '../api/jobsApi';

// ─── RendererProxy ────────────────────────────────────────────────────────────
jest.mock('react-native/Libraries/ReactNative/RendererProxy', () => ({
  findNodeHandle: jest.fn(() => null),
  findHostInstance_DEPRECATED: jest.fn(() => null),
  unstable_batchedUpdates: jest.fn((fn: () => void) => fn()),
  renderElement: jest.fn(),
  dispatchCommand: jest.fn(),
  sendAccessibilityEvent: jest.fn(),
  unmountComponentAtNodeAndRemoveContainer: jest.fn(),
  isChildPublicInstance: jest.fn(),
  getNodeFromInternalInstanceHandle: jest.fn(),
  getPublicInstanceFromInternalInstanceHandle: jest.fn(),
  getPublicInstanceFromRootTag: jest.fn(),
  isProfilingRenderer: jest.fn(() => false),
}));

// ─── defineEta API mock ───────────────────────────────────────────────────────
const mockDefineEta = jest.fn();
jest.mock('../api/jobsApi', () => ({
  defineEta: (...args: unknown[]) => mockDefineEta(...args),
}));

// ─── Alert spy ────────────────────────────────────────────────────────────────
const alertSpy = jest.spyOn(Alert, 'alert');

// ─── Fixtures ─────────────────────────────────────────────────────────────────
function makeUpdatedJob(overrides: Partial<JobResponse> = {}): JobResponse {
  return {
    id: 'job-1',
    tenantId: 't1',
    customerId: 'cust-1',
    assignedTechnicianId: 'tech-1',
    appointmentSchemaId: null,
    status: 'IN_PROGRESS',
    inProgressSubstatus: 'ON_THE_WAY',
    pendingReason: null,
    cancellationReason: null,
    customFields: {},
    lat: null,
    lon: null,
    scheduledStart: null,
    scheduledEnd: new Date(Date.now() + 30 * 60_000).toISOString(),
    actualStart: new Date().toISOString(),
    actualEnd: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    archivedAt: null,
    customerPhone: null,
    ...overrides,
  };
}

// ─── Default props helper ──────────────────────────────────────────────────────
interface SheetProps {
  visible?: boolean;
  jobId?: string;
  onClose?: jest.Mock;
  onEtaDefined?: jest.Mock;
}

function renderSheet(overrides: SheetProps = {}) {
  const props = {
    visible: overrides.visible !== undefined ? overrides.visible : true,
    jobId: overrides.jobId ?? 'job-1',
    onClose: overrides.onClose ?? jest.fn(),
    onEtaDefined: overrides.onEtaDefined ?? jest.fn(),
  };
  return { ...render(<DefineEtaSheet {...props} />), props };
}

// ─── Tests ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  jest.clearAllMocks();
});

describe('DefineEtaSheet', () => {
  // ── 1. Renders "30 min" and "45 min" buttons ───────────────────────────────
  it('renders 30 min and 45 min quick buttons', () => {
    const { getByTestId } = renderSheet();
    expect(getByTestId('btn-30')).toBeTruthy();
    expect(getByTestId('btn-45')).toBeTruthy();
  });

  // ── 2. Tapping "30 min" calls defineEta(jobId, 30) ────────────────────────
  it('tapping 30 min calls defineEta with 30', async () => {
    const updated = makeUpdatedJob();
    mockDefineEta.mockResolvedValue(updated);

    const onEtaDefined = jest.fn();
    const onClose = jest.fn();
    const { getByTestId } = renderSheet({ onEtaDefined, onClose });

    await act(async () => {
      fireEvent.press(getByTestId('btn-30'));
    });

    await waitFor(() => {
      expect(mockDefineEta).toHaveBeenCalledWith('job-1', 30);
      expect(onEtaDefined).toHaveBeenCalledWith(updated);
      expect(onClose).toHaveBeenCalled();
    });
  });

  // ── 3. Tapping "45 min" calls defineEta(jobId, 45) ────────────────────────
  it('tapping 45 min calls defineEta with 45', async () => {
    const updated = makeUpdatedJob();
    mockDefineEta.mockResolvedValue(updated);

    const onEtaDefined = jest.fn();
    const { getByTestId } = renderSheet({ onEtaDefined });

    await act(async () => {
      fireEvent.press(getByTestId('btn-45'));
    });

    await waitFor(() => {
      expect(mockDefineEta).toHaveBeenCalledWith('job-1', 45);
    });
  });

  // ── 4. Shows loading spinner while request is in flight ───────────────────
  it('shows loading spinner while 30-min request is in flight', async () => {
    let resolve!: (v: JobResponse) => void;
    mockDefineEta.mockImplementation(
      () => new Promise<JobResponse>((res) => { resolve = res; }),
    );

    const { getByTestId } = renderSheet();

    await act(async () => {
      fireEvent.press(getByTestId('btn-30'));
    });

    expect(getByTestId('spinner-30')).toBeTruthy();

    // Clean up
    await act(async () => {
      resolve(makeUpdatedJob());
    });
  });

  // ── 5. On success: calls onEtaDefined and closes ──────────────────────────
  it('calls onEtaDefined with updated job and closes on success', async () => {
    const updated = makeUpdatedJob();
    mockDefineEta.mockResolvedValue(updated);

    const onEtaDefined = jest.fn();
    const onClose = jest.fn();
    const { getByTestId } = renderSheet({ onEtaDefined, onClose });

    await act(async () => {
      fireEvent.press(getByTestId('btn-45'));
    });

    await waitFor(() => {
      expect(onEtaDefined).toHaveBeenCalledWith(updated);
      expect(onClose).toHaveBeenCalled();
    });
  });

  // ── 6. On error: shows alert ───────────────────────────────────────────────
  it('shows error alert when defineEta throws', async () => {
    mockDefineEta.mockRejectedValue(new Error('Network error'));

    const { getByTestId } = renderSheet();

    await act(async () => {
      fireEvent.press(getByTestId('btn-30'));
    });

    await waitFor(() => {
      expect(alertSpy).toHaveBeenCalledWith(
        'Error',
        'Failed to set ETA. Please try again.',
      );
    });
  });

  // ── 7. Custom validation: rejects value below minimum ────────────────────
  it('rejects custom value below minimum (< 5 min)', async () => {
    const { getByTestId } = renderSheet();

    fireEvent.changeText(getByTestId('custom-input'), '3');
    await act(async () => {
      fireEvent.press(getByTestId('custom-submit'));
    });

    expect(alertSpy).toHaveBeenCalledWith(
      'Invalid ETA',
      expect.stringContaining('5'),
    );
    expect(mockDefineEta).not.toHaveBeenCalled();
  });

  // ── 8. Custom validation: rejects value above maximum ────────────────────
  it('rejects custom value above maximum (> 480 min)', async () => {
    const { getByTestId } = renderSheet();

    fireEvent.changeText(getByTestId('custom-input'), '999');
    await act(async () => {
      fireEvent.press(getByTestId('custom-submit'));
    });

    expect(alertSpy).toHaveBeenCalledWith(
      'Invalid ETA',
      expect.stringContaining('480'),
    );
    expect(mockDefineEta).not.toHaveBeenCalled();
  });

  // ── 9. Custom submit calls defineEta with parsed value ────────────────────
  it('custom submit calls defineEta with parsed custom minutes', async () => {
    const updated = makeUpdatedJob();
    mockDefineEta.mockResolvedValue(updated);

    const onEtaDefined = jest.fn();
    const { getByTestId } = renderSheet({ onEtaDefined });

    fireEvent.changeText(getByTestId('custom-input'), '60');
    await act(async () => {
      fireEvent.press(getByTestId('custom-submit'));
    });

    await waitFor(() => {
      expect(mockDefineEta).toHaveBeenCalledWith('job-1', 60);
      expect(onEtaDefined).toHaveBeenCalledWith(updated);
    });
  });

  // ── 10. Custom spinner shows while custom request is in flight ────────────
  it('shows custom spinner while custom submission is in flight', async () => {
    let resolve!: (v: JobResponse) => void;
    mockDefineEta.mockImplementation(
      () => new Promise<JobResponse>((res) => { resolve = res; }),
    );

    const { getByTestId } = renderSheet();

    fireEvent.changeText(getByTestId('custom-input'), '20');
    await act(async () => {
      fireEvent.press(getByTestId('custom-submit'));
    });

    expect(getByTestId('spinner-custom')).toBeTruthy();

    await act(async () => {
      resolve(makeUpdatedJob());
    });
  });
});
