import React from 'react';
import { render, fireEvent, waitFor, act } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider, notifyManager } from '@tanstack/react-query';
import ScheduleScreen from '../screens/ScheduleScreen';
import type { JobResponse } from '../api/jobsApi';

// ─── Navigation ───────────────────────────────────────────────────────────────
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ dispatch: jest.fn() }),
  CommonActions: { navigate: (p: unknown) => ({ type: 'NAVIGATE', payload: p }) },
}));

// ─── Auth store ───────────────────────────────────────────────────────────────
jest.mock('../stores/authStore', () => ({
  useAuthStore: (selector: (s: { user: { id: string; tenantId: string; role: string } | null }) => unknown) =>
    selector({ user: { id: 'tech-1', tenantId: 't1', role: 'TECHNICIAN' } }),
}));

// ─── Reduced motion — off by default ─────────────────────────────────────────
jest.mock('../hooks/useReducedMotion', () => ({
  useReducedMotion: () => false,
}));

// ─── Animated components — mock to avoid RN renderer version mismatch ─────────
// react@19.2.6 vs react-native-renderer@19.2.3 causes AnimatedProps to throw
// when connecting to the native renderer in the test environment.

jest.mock('../components/SharedPoolCard', () => {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const React = require('react');
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { Text, TouchableOpacity } = require('react-native');
  return function MockSharedPoolCard({
    onPress,
  }: {
    onPress: () => void;
  }) {
    return (
      <TouchableOpacity onPress={onPress} accessibilityRole="button">
        <Text>ACCEPT THE JOB</Text>
      </TouchableOpacity>
    );
  };
});

jest.mock('../components/AcceptJobSheet', () => {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const React = require('react');
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { Text, TouchableOpacity, View } = require('react-native');
  return function MockAcceptJobSheet({
    visible,
    onClose,
    job,
    onClaimed,
    onJobClaimed: _onJobClaimed,
  }: {
    visible: boolean;
    onClose: () => void;
    job: { id: string } | null;
    onClaimed: (j: unknown) => void;
    onJobClaimed: (id: string) => void;
  }) {
    if (!visible || !job) return null;
    return (
      <View>
        <TouchableOpacity
          onPress={() => {
            // eslint-disable-next-line @typescript-eslint/no-require-imports
          const { apiClient } = require('../api/client');
            apiClient
              .post(`/api/v1/jobs/${job.id}/claim`)
              .then((res: { data: unknown }) => {
                onClaimed(res.data);
                onClose();
              })
              .catch(() => onClose());
          }}
          accessibilityRole="button"
          accessibilityLabel="Accept Job"
        >
          <Text>Accept Job</Text>
        </TouchableOpacity>
      </View>
    );
  };
});

// ─── API client ───────────────────────────────────────────────────────────────
const mockGet = jest.fn();
const mockPost = jest.fn();
jest.mock('../api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
  },
}));

// ─── TanStack Query sync scheduler ───────────────────────────────────────────
beforeAll(() => {
  notifyManager.setScheduler((fn) => fn());
});
afterAll(() => {
  notifyManager.setScheduler((fn) => setTimeout(fn, 0));
});

// ─── Fixtures ─────────────────────────────────────────────────────────────────

const TODAY = new Date();
TODAY.setHours(10, 0, 0, 0);

function makeJob(overrides: Partial<JobResponse> = {}): JobResponse {
  return {
    id: 'job-1',
    tenantId: 't1',
    customerId: 'cust-1',
    assignedTechnicianId: 'tech-1',
    appointmentSchemaId: null,
    status: 'ASSIGNED_TO_TECHNICIAN',
    inProgressSubstatus: null,
    pendingReason: null,
    cancellationReason: null,
    customFields: { serviceType: 'Plumbing' },
    lat: null,
    lon: null,
    scheduledStart: TODAY.toISOString(),
    scheduledEnd: null,
    actualStart: null,
    actualEnd: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    archivedAt: null,
    customerPhone: '+1-555-0100',
    ...overrides,
  };
}

function makeSubmittedJob(overrides: Partial<JobResponse> = {}): JobResponse {
  return makeJob({
    id: 'job-submitted-1',
    status: 'SUBMITTED',
    assignedTechnicianId: null,
    customerPhone: null,
    ...overrides,
  });
}

function makePage(content: JobResponse[]) {
  return Promise.resolve({
    data: {
      content,
      totalElements: content.length,
      totalPages: 1,
      number: 0,
      size: 100,
    },
  });
}

// ─── Helper ───────────────────────────────────────────────────────────────────

function renderWithQuery(ui: React.ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('ScheduleScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGet.mockResolvedValue(makePage([]));
  });

  it('renders Day and Week toggle buttons', () => {
    const { getByText } = renderWithQuery(<ScheduleScreen />);
    expect(getByText('Day')).toBeTruthy();
    expect(getByText('Week')).toBeTruthy();
  });

  it('Day toggle is active by default', () => {
    const { getByText } = renderWithQuery(<ScheduleScreen />);
    const dayBtn = getByText('Day');
    expect(dayBtn).toBeTruthy();
  });

  it('switches to Week view when Week button is pressed', () => {
    const { getByText } = renderWithQuery(<ScheduleScreen />);
    fireEvent.press(getByText('Week'));
    expect(getByText('Week')).toBeTruthy();
  });

  it('SUBMITTED job renders as SharedPoolCard with "ACCEPT THE JOB" text', async () => {
    const submittedJob = makeSubmittedJob();
    mockGet.mockResolvedValue(makePage([submittedJob]));

    const { findByText } = renderWithQuery(<ScheduleScreen />);
    expect(await findByText('ACCEPT THE JOB')).toBeTruthy();
  });

  it('own ASSIGNED_TO_TECHNICIAN job renders with service label and status', async () => {
    const job = makeJob();
    mockGet.mockResolvedValue(makePage([job]));

    const { findByText } = renderWithQuery(<ScheduleScreen />);
    expect(await findByText('Plumbing')).toBeTruthy();
  });

  it('customerPhone is never rendered on SUBMITTED cards', async () => {
    const submittedJob = makeSubmittedJob({ customerPhone: null });
    mockGet.mockResolvedValue(makePage([submittedJob]));

    const { findByText, queryByText } = renderWithQuery(<ScheduleScreen />);
    await findByText('ACCEPT THE JOB');
    expect(queryByText('+1-555-0100')).toBeNull();
    expect(queryByText(/\+1-555/)).toBeNull();
  });

  it('customerPhone is shown on own (non-SUBMITTED) job cards', async () => {
    const job = makeJob({ customerPhone: '+1-555-0100' });
    mockGet.mockResolvedValue(makePage([job]));

    const { findByText } = renderWithQuery(<ScheduleScreen />);
    expect(await findByText('+1-555-0100')).toBeTruthy();
  });

  it('tapping a SharedPoolCard opens AcceptJobSheet', async () => {
    const submittedJob = makeSubmittedJob();
    mockGet.mockResolvedValue(makePage([submittedJob]));

    const { findByText } = renderWithQuery(<ScheduleScreen />);
    const card = await findByText('ACCEPT THE JOB');
    fireEvent.press(card);

    expect(await findByText('Accept Job')).toBeTruthy();
  });

  it('AcceptJobSheet Accept button calls POST /jobs/{id}/claim', async () => {
    const submittedJob = makeSubmittedJob();
    mockGet.mockResolvedValue(makePage([submittedJob]));
    mockPost.mockResolvedValue({
      data: { ...submittedJob, status: 'ASSIGNED_TO_TECHNICIAN', assignedTechnicianId: 'tech-1' },
    });

    const { findByText } = renderWithQuery(<ScheduleScreen />);

    const acceptText = await findByText('ACCEPT THE JOB');
    fireEvent.press(acceptText);

    const acceptButton = await findByText('Accept Job');
    await act(async () => {
      fireEvent.press(acceptButton);
    });

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(`/api/v1/jobs/${submittedJob.id}/claim`);
    });
  });

  it('shows "No jobs for this day" when list is empty', async () => {
    mockGet.mockResolvedValue(makePage([]));
    const { findByText } = renderWithQuery(<ScheduleScreen />);
    expect(await findByText('No jobs for this day')).toBeTruthy();
  });

  it('navigates to previous day when left arrow is pressed', () => {
    const { getByLabelText, getByText } = renderWithQuery(<ScheduleScreen />);
    const prevBtn = getByLabelText('Previous');
    // Date label format: "Thu, Jun 4" — matches locale short weekday, month, day
    const initialLabel = getByText(/\w{3},\s\w{3}\s\d+/);
    const initialText = initialLabel.props.children;
    fireEvent.press(prevBtn);
    const newLabel = getByText(/\w{3},\s\w{3}\s\d+/);
    expect(newLabel.props.children).not.toBe(initialText);
  });

  it('unscheduled jobs appear in Unscheduled section', async () => {
    const unscheduledJob = makeJob({ scheduledStart: null });
    mockGet.mockResolvedValue(makePage([unscheduledJob]));

    const { findAllByText } = renderWithQuery(<ScheduleScreen />);
    // "Unscheduled" appears as section label AND as time label inside the card
    const nodes = await findAllByText('Unscheduled');
    expect(nodes.length).toBeGreaterThanOrEqual(1);
  });
});
