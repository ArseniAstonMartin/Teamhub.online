import React from 'react';
import { Alert, Linking } from 'react-native';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider, notifyManager } from '@tanstack/react-query';
import JobDetailScreen from '../screens/JobDetailScreen';
import type { JobResponse, JobNoteResponse } from '../api/jobsApi';

// ─── Navigation & route ───────────────────────────────────────────────────────
const mockGoBack = jest.fn();
const mockNavigate = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ goBack: mockGoBack, navigate: mockNavigate }),
  useRoute: () => ({ params: { jobId: 'job-uuid-1234' } }),
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
    // Keep the job status as ASSIGNED_TO_TECHNICIAN after start to avoid timer teardown issues
    mockPost.mockResolvedValue({ data: makeJob({ status: 'ASSIGNED_TO_TECHNICIAN' }) });

    const { getByLabelText } = renderScreen();
    await waitFor(() => getByLabelText('Start the Job'));

    fireEvent.press(getByLabelText('Start the Job'));

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

  // 5. Add Notes opens inline note composer
  it('Add Notes button opens the note input', async () => {
    const { getByLabelText, getByTestId } = renderScreen();
    await waitFor(() => getByLabelText('Add Notes'));

    fireEvent.press(getByLabelText('Add Notes'));
    expect(getByTestId('note-input')).toBeTruthy();
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
      return Promise.reject(new Error(`Unmocked GET ${url}`));
    });

    const { getByText } = renderScreen();
    await waitFor(() => getByText('Arrived on site'));
    expect(getByText('Parts needed')).toBeTruthy();
  });
});
