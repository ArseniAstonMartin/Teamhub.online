import React from 'react';
import { render, fireEvent, waitFor, act } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider, notifyManager } from '@tanstack/react-query';
import CustomerDetailScreen from '../screens/CustomerDetailScreen';
import * as openMapsModule from '../utils/openMaps';

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

// ─── Navigation & route ───────────────────────────────────────────────────────
const mockGoBack = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ goBack: mockGoBack }),
  useRoute: () => ({ params: { customerId: 'cust-1' } }),
}));

// ─── customersApi mock ────────────────────────────────────────────────────────
const mockUseCustomer = jest.fn();
jest.mock('../api/customersApi', () => ({
  useCustomer: (...args: unknown[]) => mockUseCustomer(...args),
}));

// ─── jobsApi mock ─────────────────────────────────────────────────────────────
const mockUseJobsByCustomer = jest.fn();
jest.mock('../api/jobsApi', () => ({
  useJobsByCustomer: (...args: unknown[]) => mockUseJobsByCustomer(...args),
}));

// ─── openMaps spy ─────────────────────────────────────────────────────────────
const openMapsSpy = jest.spyOn(openMapsModule, 'openMaps').mockResolvedValue(undefined);

// ─── TanStack Query sync scheduler ───────────────────────────────────────────
beforeAll(() => { notifyManager.setScheduler((fn) => fn()); });
afterAll(() => { notifyManager.setScheduler((fn) => setTimeout(fn, 0)); });

// ─── Fixtures ─────────────────────────────────────────────────────────────────
const CUSTOMER = {
  id: 'cust-1',
  name: 'Acme Corp',
  phone: '***-***-1234',
  email: 'bob@acme.com',
  addressLine: '123 Main St',
  city: 'Dallas',
  state: 'TX',
  lat: 32.77,
  lon: -96.80,
  updatedAt: '2026-06-02T10:00:00Z',
};

const JOB_A = {
  id: 'aaaaaaaa-0000-0000-0000-000000000001',
  status: 'IN_PROGRESS',
  scheduledStart: '2026-06-01T09:00:00Z',
  createdAt: '2026-06-01T08:00:00Z',
};

const JOB_B = {
  id: 'bbbbbbbb-0000-0000-0000-000000000002',
  status: 'COMPLETED',
  scheduledStart: '2026-05-28T09:00:00Z',
  createdAt: '2026-05-28T08:00:00Z',
};

function makeCustomerResult(customer = CUSTOMER) {
  return { data: customer, isLoading: false, isError: false };
}

function makeJobsResult(jobs = [JOB_A, JOB_B]) {
  return {
    data: { content: jobs, totalPages: 1, totalElements: jobs.length, number: 0 },
    isLoading: false,
  };
}

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <CustomerDetailScreen />
    </QueryClientProvider>,
  );
}

// ─── Tests ────────────────────────────────────────────────────────────────────
describe('CustomerDetailScreen', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
    mockUseCustomer.mockReturnValue(makeCustomerResult());
    mockUseJobsByCustomer.mockReturnValue(makeJobsResult());
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  // 1. Renders customer name, masked phone, email
  it('renders customer name, phone, and email', async () => {
    const { getByText, getByTestId } = renderScreen();
    await waitFor(() => getByText('Acme Corp'));
    expect(getByTestId('phone-value').props.children).toBe('***-***-1234');
    expect(getByTestId('email-value').props.children).toBe('bob@acme.com');
  });

  // 2. Address tap calls openMaps
  it('calls openMaps when address row is tapped', async () => {
    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('address-row'));

    await act(async () => {
      fireEvent.press(getByTestId('address-row'));
    });

    expect(openMapsSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        lat: 32.77,
        lon: -96.80,
        label: 'Acme Corp',
      }),
    );
  });

  // 3. Job history list renders job numbers
  it('renders job history with short job IDs', async () => {
    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId(`job-row-${JOB_A.id}`));
    expect(getByTestId(`job-row-${JOB_B.id}`)).toBeTruthy();
  });

  // 4. Shows "No jobs yet" when job list is empty
  it('shows empty jobs message when no jobs', async () => {
    mockUseJobsByCustomer.mockReturnValue(makeJobsResult([]));
    const { getByText } = renderScreen();
    await waitFor(() => getByText('No jobs yet'));
  });
});
