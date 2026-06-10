import React from 'react';
import { Alert } from 'react-native';
import { render, fireEvent, waitFor, act } from '@testing-library/react-native';
import AcceptJobSheet from '../components/AcceptJobSheet';
import type { JobResponse } from '../api/jobsApi';

// ─── RendererProxy — prevent the ReactNative renderer (react-native-renderer@19.2.3)
// from being loaded in Jest where react@19.2.6 causes a version-mismatch throw.
// findNodeHandle returning null is handled gracefully by AnimatedProps in NODE_ENV=test.
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

// ─── Navigation ───────────────────────────────────────────────────────────────
const mockNavigate = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: mockNavigate, goBack: jest.fn() }),
}));

// ─── claimJob API ─────────────────────────────────────────────────────────────
const mockClaimJob = jest.fn();
jest.mock('../api/jobsApi', () => ({
  claimJob: (...args: unknown[]) => mockClaimJob(...args),
}));

// ─── Alert spy ───────────────────────────────────────────────────────────────
const alertSpy = jest.spyOn(Alert, 'alert');

// ─── Fixtures ─────────────────────────────────────────────────────────────────
function makeJob(overrides: Partial<JobResponse> = {}): JobResponse {
  return {
    id: 'job-1',
    tenantId: 't1',
    customerId: 'cust-1',
    assignedTechnicianId: null,
    appointmentSchemaId: null,
    status: 'SUBMITTED',
    inProgressSubstatus: null,
    pendingReason: null,
    cancellationReason: null,
    customFields: { serviceType: 'Plumbing' },
    lat: null,
    lon: null,
    scheduledStart: new Date().toISOString(),
    scheduledEnd: null,
    actualStart: null,
    actualEnd: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    archivedAt: null,
    customerPhone: null,
    ...overrides,
  };
}

function makeClaimedJob(base: JobResponse): JobResponse {
  return { ...base, status: 'ASSIGNED_TO_TECHNICIAN', assignedTechnicianId: 'tech-1' };
}

function make409Error() {
  const err = new Error('Conflict') as Error & { response: { status: number } };
  err.response = { status: 409 };
  return err;
}

function makeNetworkError() {
  return new Error('Network Error');
}

// ─── Default props helper ──────────────────────────────────────────────────────
interface SheetProps {
  job?: JobResponse | null;
  visible?: boolean;
  onClose?: jest.Mock;
  onClaimed?: jest.Mock;
  onJobClaimed?: jest.Mock;
}

function renderSheet(overrides: SheetProps = {}) {
  const props = {
    job: overrides.job !== undefined ? overrides.job : makeJob(),
    visible: overrides.visible !== undefined ? overrides.visible : true,
    onClose: overrides.onClose ?? jest.fn(),
    onClaimed: overrides.onClaimed ?? jest.fn(),
    onJobClaimed: overrides.onJobClaimed ?? jest.fn(),
  };
  return { ...render(<AcceptJobSheet {...props} />), props };
}

// ─── Tests ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  jest.clearAllMocks();
  jest.useRealTimers();
});

describe('AcceptJobSheet', () => {
  // ── 1. Spinner while claim is in flight ────────────────────────────────────
  it('shows spinner while claim request is pending', async () => {
    let resolvePromise!: (v: JobResponse) => void;
    mockClaimJob.mockImplementation(
      () => new Promise<JobResponse>((res) => { resolvePromise = res; }),
    );

    const { getByLabelText, getByTestId } = renderSheet();

    await act(async () => {
      fireEvent.press(getByLabelText('Accept Job'));
    });

    expect(getByTestId('accept-spinner')).toBeTruthy();

    await act(async () => {
      resolvePromise(makeClaimedJob(makeJob()));
    });
  });

  // ── 2. Double-tap debounce ─────────────────────────────────────────────────
  it('ignores a second tap while claim is in flight (debounce)', async () => {
    let resolveFirst!: (v: JobResponse) => void;
    mockClaimJob.mockImplementationOnce(
      () => new Promise<JobResponse>((res) => { resolveFirst = res; }),
    );

    const { getByLabelText } = renderSheet();

    await act(async () => {
      fireEvent.press(getByLabelText('Accept Job'));
    });

    // Second tap while first is still pending
    await act(async () => {
      fireEvent.press(getByLabelText('Accept Job'));
    });

    await act(async () => {
      resolveFirst(makeClaimedJob(makeJob()));
    });

    expect(mockClaimJob).toHaveBeenCalledTimes(1);
  });

  // ── 3. 200: navigate and call onClaimed ────────────────────────────────────
  it('on 200: navigates to JobDetail and calls onClaimed', async () => {
    const job = makeJob();
    const claimed = makeClaimedJob(job);
    mockClaimJob.mockResolvedValue(claimed);

    const onClaimed = jest.fn();
    const { getByLabelText } = renderSheet({ job, onClaimed });

    await act(async () => {
      fireEvent.press(getByLabelText('Accept Job'));
    });

    await waitFor(() => {
      expect(onClaimed).toHaveBeenCalledWith(claimed);
      expect(mockNavigate).toHaveBeenCalledWith('JobDetail', { jobId: claimed.id });
    });
  });

  // ── 4. 409: shows "already taken" alert and calls onJobClaimed ─────────────
  it('on 409: shows alert and calls onJobClaimed', async () => {
    const job = makeJob();
    mockClaimJob.mockRejectedValue(make409Error());

    const onJobClaimed = jest.fn();
    const onClaimed = jest.fn();
    const { getByLabelText } = renderSheet({ job, onJobClaimed, onClaimed });

    await act(async () => {
      fireEvent.press(getByLabelText('Accept Job'));
    });

    await waitFor(() => {
      expect(onJobClaimed).toHaveBeenCalledWith(job.id);
      expect(onClaimed).not.toHaveBeenCalled();
      expect(mockNavigate).not.toHaveBeenCalled();
      expect(alertSpy).toHaveBeenCalledWith(
        'Already Claimed',
        expect.stringContaining('already taken'),
        expect.any(Array),
      );
    });
  });

  // ── 5. Network error after 3 retries: shows "Please try again" ─────────────
  it('after 3 failed attempts shows connection error alert', async () => {
    jest.useFakeTimers();
    mockClaimJob.mockRejectedValue(makeNetworkError());

    const onClaimed = jest.fn();
    const onJobClaimed = jest.fn();
    const { getByLabelText } = renderSheet({ onClaimed, onJobClaimed });

    act(() => {
      fireEvent.press(getByLabelText('Accept Job'));
    });

    // Advance through 500ms backoff (attempt 1→2) and 1000ms backoff (attempt 2→3)
    await act(async () => {
      jest.advanceTimersByTime(500);
    });
    await act(async () => {
      jest.advanceTimersByTime(1000);
    });
    // After third attempt there's no more backoff — let promises flush
    await act(async () => {
      await Promise.resolve();
    });

    await waitFor(() => {
      expect(mockClaimJob).toHaveBeenCalledTimes(3);
      expect(alertSpy).toHaveBeenCalledWith(
        'Connection Error',
        expect.stringContaining('Connection error'),
        expect.any(Array),
      );
      expect(onClaimed).not.toHaveBeenCalled();
      expect(onJobClaimed).not.toHaveBeenCalled();
    });

    jest.useRealTimers();
  });

  // ── 6. Retry: first fails, second succeeds ─────────────────────────────────
  it('retries on network error and navigates on second success', async () => {
    jest.useFakeTimers();
    const job = makeJob();
    const claimed = makeClaimedJob(job);

    mockClaimJob
      .mockRejectedValueOnce(makeNetworkError())
      .mockResolvedValueOnce(claimed);

    const onClaimed = jest.fn();
    const { getByLabelText } = renderSheet({ job, onClaimed });

    act(() => {
      fireEvent.press(getByLabelText('Accept Job'));
    });

    // First attempt fails → 500ms backoff before second attempt
    await act(async () => {
      jest.advanceTimersByTime(500);
    });
    // Let the second attempt (and its promise) resolve
    await act(async () => {
      await Promise.resolve();
    });

    await waitFor(() => {
      expect(mockClaimJob).toHaveBeenCalledTimes(2);
      expect(onClaimed).toHaveBeenCalledWith(claimed);
      expect(mockNavigate).toHaveBeenCalledWith('JobDetail', { jobId: claimed.id });
    });

    jest.useRealTimers();
  });
});
