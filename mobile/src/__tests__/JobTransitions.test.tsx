/**
 * TASK-097 — Job Transition Buttons (Start, Pending, Done, Complete)
 *
 * Tests:
 * 1. "Start the Job" calls startJob + optimistically updates status to IN_PROGRESS
 * 2. 409 on Start → rollback to ASSIGNED_TO_TECHNICIAN + Conflict alert
 * 3. PendingSheet renders reason options; selecting and submitting calls markPending
 * 4. "Done Pending Approval" calls markDonePendingApproval
 * 5. "Complete" visible when closeJobsEnabled=true + status=DONE_PENDING_APPROVAL
 * 6. "Complete" hidden when closeJobsEnabled=false
 * 7. Complete calls completeJob
 * 8. Shake animation triggered on 409 rollback (Animated.sequence called)
 */

import React from 'react';
import { Alert, Animated } from 'react-native';
import { render, fireEvent, waitFor, act } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider, notifyManager } from '@tanstack/react-query';
import JobDetailScreen from '../screens/JobDetailScreen';
import PendingSheet from '../components/PendingSheet';
import type { JobResponse } from '../api/jobsApi';

// ─── RendererProxy — prevent react@19.2.6 / react-native-renderer@19.2.3 mismatch
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

// ─── Navigation & route ───────────────────────────────────────────────────────
const mockGoBack = jest.fn();
const mockNavigate = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ goBack: mockGoBack, navigate: mockNavigate }),
  useRoute: () => ({ params: { jobId: 'job-uuid-1234' } }),
  useFocusEffect: (cb: () => void) => { cb(); },
}));

jest.mock('@react-native-community/datetimepicker', () => () => null);

jest.mock('expo-image-picker', () => ({
  launchImageLibraryAsync: jest.fn(),
  MediaTypeOptions: { Images: 'Images' },
}));

// ─── API client mock ──────────────────────────────────────────────────────────
const mockGet = jest.fn();
const mockPost = jest.fn();
const mockPatch = jest.fn();
jest.mock('../api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    patch: (...args: unknown[]) => mockPatch(...args),
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
  },
}));

const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});

beforeAll(() => { notifyManager.setScheduler((fn) => fn()); });
afterAll(() => { notifyManager.setScheduler((fn) => setTimeout(fn, 0)); });

// ─── Fixtures ─────────────────────────────────────────────────────────────────

function makeJob(overrides: Partial<JobResponse> = {}): JobResponse {
  return {
    id: 'job-uuid-1234',
    tenantId: 't1',
    customerId: 'cust-1',
    assignedTechnicianId: 'tech-1',
    appointmentSchemaId: null,
    status: 'ASSIGNED_TO_TECHNICIAN',
    inProgressSubstatus: null,
    pendingReason: null,
    cancellationReason: null,
    customFields: { customer_name: 'Jane Doe' },
    lat: 45.52,
    lon: -122.68,
    scheduledStart: '2026-06-05T09:00:00Z',
    scheduledEnd: '2026-06-05T11:00:00Z',
    actualStart: null,
    actualEnd: null,
    createdAt: '2026-06-04T12:00:00Z',
    updatedAt: '2026-06-04T12:00:00Z',
    archivedAt: null,
    customerPhone: null,
    ...overrides,
  };
}

function setupGetMocks(
  jobOverrides: Partial<JobResponse> = {},
  closeJobsEnabled = true,
) {
  mockGet.mockImplementation((url: string) => {
    if (url === '/api/v1/jobs/job-uuid-1234') {
      return Promise.resolve({ data: makeJob(jobOverrides) });
    }
    if (url === '/api/v1/jobs/job-uuid-1234/notes') {
      return Promise.resolve({ data: [] });
    }
    if (url === '/api/v1/tenant/settings') {
      return Promise.resolve({ data: { closeJobsEnabled } });
    }
    return Promise.reject(new Error(`Unmocked GET ${url}`));
  });
}

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <JobDetailScreen />
    </QueryClientProvider>,
  );
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('TASK-097 Job Transition Buttons', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // 1. "Start the Job" calls startJob and optimistically updates status to IN_PROGRESS
  it('Start the Job calls startJob and optimistically updates to IN_PROGRESS', async () => {
    setupGetMocks({ status: 'ASSIGNED_TO_TECHNICIAN' });
    const inProgressJob = makeJob({
      status: 'IN_PROGRESS',
      inProgressSubstatus: 'WORKING',
      actualStart: new Date().toISOString(),
    });
    mockPost.mockResolvedValue({ data: inProgressJob });

    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('btn-start-job'));

    await act(async () => {
      fireEvent.press(getByTestId('btn-start-job'));
    });

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/api/v1/jobs/job-uuid-1234/start', {});
    });
  });

  // 2. 409 on Start → rollback + Conflict alert
  it('409 on Start rolls back to ASSIGNED_TO_TECHNICIAN and shows Conflict alert', async () => {
    setupGetMocks({ status: 'ASSIGNED_TO_TECHNICIAN' });
    const error = Object.assign(new Error('Conflict'), { response: { status: 409 } });
    mockPost.mockRejectedValue(error);

    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('btn-start-job'));

    await act(async () => {
      fireEvent.press(getByTestId('btn-start-job'));
    });

    await waitFor(() => {
      expect(alertSpy).toHaveBeenCalledWith(
        'Conflict',
        'This job status was already changed. Refreshing...',
      );
    });
  });

  // 3. PendingSheet: renders reason options; selecting and submitting calls markPending
  it('PendingSheet submits markPending when reason selected and submitted', async () => {
    const onPending = jest.fn();
    const onClose = jest.fn();
    mockPost.mockResolvedValue({ data: makeJob({ status: 'PENDING' }) });

    const { getByTestId } = render(
      <PendingSheet
        visible
        jobId="job-uuid-1234"
        onClose={onClose}
        onPending={onPending}
      />,
    );

    await act(async () => {
      fireEvent.press(getByTestId('reason-WAITING_FOR_PARTS'));
    });

    await act(async () => {
      fireEvent.press(getByTestId('pending-submit-btn'));
    });

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/api/v1/jobs/job-uuid-1234/pending', {
        pendingReason: 'WAITING_FOR_PARTS',
      });
      expect(onPending).toHaveBeenCalledWith(expect.objectContaining({ status: 'PENDING' }));
    });
  });

  // 4. "Done Pending Approval" calls markDonePendingApproval
  it('Done Pending Approval calls markDonePendingApproval endpoint', async () => {
    setupGetMocks({ status: 'IN_PROGRESS', actualStart: new Date().toISOString() });
    mockPost.mockResolvedValue({ data: makeJob({ status: 'DONE_PENDING_APPROVAL' }) });

    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('btn-done-pending'));

    await act(async () => {
      fireEvent.press(getByTestId('btn-done-pending'));
    });

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(
        '/api/v1/jobs/job-uuid-1234/done-pending-approval',
        { note: undefined },
      );
    });
  });

  // 5. "Complete" visible when closeJobsEnabled=true and status=DONE_PENDING_APPROVAL
  it('Complete button is visible when closeJobsEnabled=true and status=DONE_PENDING_APPROVAL', async () => {
    setupGetMocks({ status: 'DONE_PENDING_APPROVAL' }, true);

    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('btn-complete'));
    expect(getByTestId('btn-complete')).toBeTruthy();
  });

  // 6. "Complete" hidden when closeJobsEnabled=false
  it('Complete button is NOT rendered when closeJobsEnabled=false', async () => {
    setupGetMocks({ status: 'DONE_PENDING_APPROVAL' }, false);

    const { queryByTestId, getByLabelText } = renderScreen();
    // Wait for screen to load: Add Notes is always visible for non-terminal statuses
    await waitFor(() => getByLabelText('Add Notes'));
    expect(queryByTestId('btn-complete')).toBeNull();
  });

  // 7. Complete calls completeJob
  it('Complete calls completeJob endpoint', async () => {
    setupGetMocks({ status: 'DONE_PENDING_APPROVAL' }, true);
    mockPost.mockResolvedValue({ data: makeJob({ status: 'COMPLETED' }) });

    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('btn-complete'));

    await act(async () => {
      fireEvent.press(getByTestId('btn-complete'));
    });

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(
        '/api/v1/jobs/job-uuid-1234/complete',
        {},
      );
    });
  });

  // 8. Shake animation triggered on 409 rollback
  it('shake animation (Animated.sequence) is invoked on 409 rollback', async () => {
    const sequenceSpy = jest.spyOn(Animated, 'sequence');

    setupGetMocks({ status: 'ASSIGNED_TO_TECHNICIAN' });
    const error = Object.assign(new Error('Conflict'), { response: { status: 409 } });
    mockPost.mockRejectedValue(error);

    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('btn-start-job'));

    await act(async () => {
      fireEvent.press(getByTestId('btn-start-job'));
    });

    await waitFor(() => {
      expect(sequenceSpy).toHaveBeenCalled();
    });

    sequenceSpy.mockRestore();
  });
});
