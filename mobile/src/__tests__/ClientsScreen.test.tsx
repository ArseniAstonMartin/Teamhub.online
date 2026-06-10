import React from 'react';
import { render, fireEvent, waitFor, act } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider, notifyManager } from '@tanstack/react-query';
import ClientsScreen from '../screens/ClientsScreen';

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

// ─── Navigation ───────────────────────────────────────────────────────────────
const mockNavigate = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: mockNavigate }),
  useRoute: () => ({ params: {} }),
}));

// ─── customersApi mock ────────────────────────────────────────────────────────
const mockUseCustomers = jest.fn();
jest.mock('../api/customersApi', () => ({
  useCustomers: (...args: unknown[]) => mockUseCustomers(...args),
}));

// ─── TanStack Query sync scheduler ───────────────────────────────────────────
beforeAll(() => { notifyManager.setScheduler((fn) => fn()); });
afterAll(() => { notifyManager.setScheduler((fn) => setTimeout(fn, 0)); });

// ─── Fixtures ─────────────────────────────────────────────────────────────────
const CUSTOMER_A = {
  id: 'cust-1',
  name: 'Acme Corp',
  addressLine: '123 Main St',
  city: 'Dallas',
  state: 'TX',
  updatedAt: '2026-06-02T10:00:00Z',
};

const CUSTOMER_B = {
  id: 'cust-2',
  name: "Bob's Plumbing",
  addressLine: '456 Oak Ave',
  city: 'Austin',
  state: 'TX',
  updatedAt: '2026-05-28T08:00:00Z',
};

function makePageResult(customers = [CUSTOMER_A, CUSTOMER_B]) {
  return {
    data: {
      pages: [
        {
          content: customers,
          totalPages: 1,
          totalElements: customers.length,
          number: 0,
        },
      ],
      pageParams: [0],
    },
    isLoading: false,
    isFetchingNextPage: false,
    hasNextPage: false,
    fetchNextPage: jest.fn(),
    refetch: jest.fn(),
    isRefetching: false,
  };
}

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ClientsScreen />
    </QueryClientProvider>,
  );
}

// ─── Tests ────────────────────────────────────────────────────────────────────
describe('ClientsScreen', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
    mockUseCustomers.mockReturnValue(makePageResult());
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  // 1. Renders customer list
  it('renders customer rows from useCustomers', async () => {
    const { getByText } = renderScreen();
    await waitFor(() => getByText('Acme Corp'));
    expect(getByText("Bob's Plumbing")).toBeTruthy();
  });

  // 2. Search input triggers useCustomers with new search term after debounce
  it('calls useCustomers with search term after 300 ms debounce', async () => {
    const { getByTestId } = renderScreen();
    const input = getByTestId('search-input');

    fireEvent.changeText(input, 'Acme');
    // Before debounce, still called with ''
    expect(mockUseCustomers).toHaveBeenLastCalledWith('');

    act(() => {
      jest.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(mockUseCustomers).toHaveBeenCalledWith('Acme');
    });
  });

  // 3. Tapping a row navigates to CustomerDetail
  it('navigates to CustomerDetail when a customer row is tapped', async () => {
    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('customer-row-cust-1'));

    fireEvent.press(getByTestId('customer-row-cust-1'));

    expect(mockNavigate).toHaveBeenCalledWith('CustomerDetail', { customerId: 'cust-1' });
  });

  // 4. Shows empty state when list is empty
  it('shows empty state when no customers', async () => {
    mockUseCustomers.mockReturnValue(makePageResult([]));
    const { getByTestId } = renderScreen();
    await waitFor(() => getByTestId('empty-state'));
    expect(getByTestId('empty-state')).toBeTruthy();
  });

  // 5. Shows skeleton rows during loading
  it('shows skeleton rows while loading', () => {
    mockUseCustomers.mockReturnValue({
      data: undefined,
      isLoading: true,
      isFetchingNextPage: false,
      hasNextPage: false,
      fetchNextPage: jest.fn(),
      refetch: jest.fn(),
      isRefetching: false,
    });
    const { getAllByTestId } = renderScreen();
    expect(getAllByTestId('skeleton-row')).toHaveLength(5);
  });
});
