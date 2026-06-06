import React from 'react';
import { Alert, Linking, Platform } from 'react-native';
import { render, fireEvent, waitFor, act } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider, notifyManager } from '@tanstack/react-query';
import JobDetailScreen from '../screens/JobDetailScreen';
import type { JobResponse, JobNoteResponse } from '../api/jobsApi';
import * as openMapsModule from '../utils/openMaps';

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

// ─── DateTimePicker — stub (native only) ─────────────────────────────────────
jest.mock('@react-native-community/datetimepicker', () => () => null);

// ─── expo-image-picker ────────────────────────────────────────────────────────
const mockLaunchImageLibrary = jest.fn();
jest.mock('expo-image-picker', () => ({
  launchImageLibraryAsync: (...args: unknown[]) => mockLaunchImageLibrary(...args),
  MediaTypeOptions: { Images: 'Images' },
}));

// ─── API client ───────────────────────────────────────────────────────────────
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

// ─── Alert & Linking spies ────────────────────────────────────────────────────
const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
const openURLSpy = jest.spyOn(Linking, 'openURL').mockResolvedValue(undefined as unknown as boolean);
const openMapsSpy = jest.spyOn(openMapsModule, 'openMaps').mockResolvedValue(undefined);
const copyAddressSpy = jest.spyOn(openMapsModule, 'copyAddress').mockImplementation(() => {});

// ─── TanStack Query sync scheduler ───────────────────────────────────────────
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
    customFields: { customer_name: 'Jane Doe', address: '123 Main St', city: 'Portland', state: 'OR' },
    lat: 45.52,
    lon: -122.68,
    scheduledStart: '2026-06-05T09:00:00Z',
    scheduledEnd: '2026-06-05T11:00:00Z',
    actualStart: null,
    actualEnd: null,
    createdAt: '2026-06-04T12:00:00Z',
    updatedAt: '2026-06-04T12:00:00Z',
    archivedAt: null,
    customerPhone: '+15033334444',
    ...overrides,
  };
}

function makeNote(overrides: Partial<JobNoteResponse> = {}): JobNoteResponse {
  return {
    id: 'note-1',
    jobId: 'job-uuid-1234',
    authorUserId: 'tech-1',
    body: 'Arrived on site',
    createdAt: '2026-06-05T10:00:00Z',
    ...overrides,
  };
}

// ─── Render helper ────────────────────────────────────────────────────────────
function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <JobDetailScreen />
    </QueryClientProvider>,
  );
}

// ─── Tests ────────────────────────────────────────────────────────────────────
describe('JobDetailScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGet.mockImplementation((url: string) => {
      if (url === '/api/v1/jobs/job-uuid-1234') {
        return Promise.resolve({ data: makeJob() });
      }
      if (url === '/api/v1/jobs/job-uuid-1234/notes') {
        return Promise.resolve({ data: [] });
      }
      if (url === '/api/v1/tenant/settings') {
        return Promise.resolve({ data: { closeJobsEnabled: true } });
      }
      return Promise.reject(new Error(`Unmocked GET ${url}`));
    });
  });

  // 1. Renders job header with status badge
  it('renders job header with status badge', async () => {
    const { getByText } = renderScreen();
    await waitFor(() => getByText(/JOB-UUID/i));
    expect(getByText(/Assigned/i)).toBeTruthy();
  });

  // 2. Phone number shown and tappable
  it('shows phone number and calls Linking.openURL on tap', async () => {
    const { getByLabelText } = renderScreen();
    await waitFor(() => getByLabelText('Call +15033334444'));
    fireEvent.press(getByLabelText('Call +15033334444'));
    expect(openURLSpy).toHaveBeenCalledWith('tel:+15033334444');
  });

  // 3. Start the Job button calls startJob API
  it('calls POST /jobs/{id}/start when Start the Job is tapped', async () => {
    mockPost.mockResolvedValue({ data: makeJob({ status: 'ASSIGNED_TO_TECHNICIAN' }) });

    const { getByLabelText } = renderScreen();
    await waitFor(() => getByLabelText('Start the Job'));

    await act(async () => {
      fireEvent.press(getByLabelText('Start the Job'));
    });

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/api/v1/jobs/job-uuid-1234/start', {});
    });
  });

  // 4. Pay button present but disabled; tap shows coming-soon alert
  it('Pay button is present and shows coming-soon alert when tapped', async () => {
    const { getByLabelText } = renderScreen();
    await waitFor(() => getByLabelText('Pay'));

    const payBtn = getByLabelText('Pay');
    fireEvent.press(payBtn);

    expect(alertSpy).toHaveBeenCalledWith(
      'Coming in v1.1',
      'In-app payment is coming in the next release.',
    );
  });

  // 5. Add Notes navigates to AddNote screen
  it('Add Notes button navigates to AddNote screen', async () => {
    const { getByLabelText } = renderScreen();
    await waitFor(() => getByLabelText('Add Notes'));

    fireEvent.press(getByLabelText('Add Notes'));
    expect(mockNavigate).toHaveBeenCalledWith('AddNote', { jobId: 'job-uuid-1234' });
  });

  // 6. In-progress timer shown when status === 'IN_PROGRESS'
  it('shows elapsed timer when status is IN_PROGRESS', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/api/v1/jobs/job-uuid-1234') {
        return Promise.resolve({
          data: makeJob({ status: 'IN_PROGRESS', actualStart: new Date(Date.now() - 90_000).toISOString() }),
        });
      }
      if (url === '/api/v1/jobs/job-uuid-1234/notes') {
        return Promise.resolve({ data: [] });
      }
      if (url === '/api/v1/tenant/settings') {
        return Promise.resolve({ data: { closeJobsEnabled: true } });
      }
      return Promise.reject(new Error(`Unmocked GET ${url}`));
    });

    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('elapsed-timer'));
    const timerText = getByTestId('elapsed-timer').props.children;
    expect(typeof timerText).toBe('string');
    expect(timerText).toMatch(/^\d{2}:\d{2}:\d{2}$/);
  });

  // 7. Timer NOT shown when status === 'ASSIGNED_TO_TECHNICIAN'
  it('does NOT show elapsed timer when status is ASSIGNED_TO_TECHNICIAN', async () => {
    const { queryByTestId } = renderScreen();
    await waitFor(() => queryByTestId('elapsed-timer') === null);
    expect(queryByTestId('elapsed-timer')).toBeNull();
  });

  // 8. Notes list renders from API response
  it('renders notes list from API response', async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === '/api/v1/jobs/job-uuid-1234') {
        return Promise.resolve({ data: makeJob() });
      }
      if (url === '/api/v1/jobs/job-uuid-1234/notes') {
        return Promise.resolve({
          data: [
            makeNote({ id: 'n1', body: 'Arrived on site' }),
            makeNote({ id: 'n2', body: 'Parts needed' }),
          ],
        });
      }
      if (url === '/api/v1/tenant/settings') {
        return Promise.resolve({ data: { closeJobsEnabled: true } });
      }
      return Promise.reject(new Error(`Unmocked GET ${url}`));
    });

    const { getByText } = renderScreen();
    await waitFor(() => getByText('Arrived on site'));
    expect(getByText('Parts needed')).toBeTruthy();
  });

  // 9. Address tap calls openMaps
  it('calls openMaps when address row is tapped', async () => {
    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('address-row'));

    await act(async () => {
      fireEvent.press(getByTestId('address-row'));
    });

    expect(openMapsSpy).toHaveBeenCalledWith(
      expect.objectContaining({ lat: 45.52, lon: -122.68, label: 'Job site' }),
    );
  });

  // 10. Address long-press shows copy option on Android and copies address
  it('shows copy Alert on Android when address row is long-pressed', async () => {
    Object.defineProperty(Platform, 'OS', { get: () => 'android' });

    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('address-row'));

    fireEvent(getByTestId('address-row'), 'longPress');

    expect(alertSpy).toHaveBeenCalledWith(
      'Address',
      expect.stringContaining('123 Main St'),
      expect.arrayContaining([expect.objectContaining({ text: 'Copy' })]),
    );

    // Invoke the Copy button's onPress callback to verify copyAddress is wired up
    const alertArgs = alertSpy.mock.calls[alertSpy.mock.calls.length - 1];
    const buttons: { text: string; onPress?: () => void }[] = alertArgs[2] as { text: string; onPress?: () => void }[];
    const copyBtn = buttons.find((b) => b.text === 'Copy');
    copyBtn?.onPress?.();
    expect(copyAddressSpy).toHaveBeenCalledWith(expect.stringContaining('123 Main St'));

    // restore
    Object.defineProperty(Platform, 'OS', { get: () => 'ios' });
  });
});
