import React from 'react';
import { ScrollView } from 'react-native';
import { render, fireEvent, waitFor, act } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider, notifyManager } from '@tanstack/react-query';
import HomeScreen from '../screens/HomeScreen';

// ─── Navigation ───────────────────────────────────────────────────────────────
const mockDispatch = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ dispatch: mockDispatch }),
  CommonActions: {
    navigate: (params: unknown) => ({ type: 'NAVIGATE', payload: params }),
  },
}));

// ─── Auth store ───────────────────────────────────────────────────────────────
jest.mock('../stores/authStore', () => ({
  useAuthStore: (selector: (s: { user: { id: string; tenantId: string; role: string } | null }) => unknown) =>
    selector({ user: { id: '1', tenantId: 't1', role: 'TECHNICIAN' } }),
}));

// ─── Current user (/users/me) ─────────────────────────────────────────────────
jest.mock('../api/usersApi', () => ({
  useCurrentUser: () => ({
    data: {
      id: '1',
      email: 'jane@example.com',
      name: 'Jane Smith',
      role: 'TECHNICIAN',
      tenantId: 't1',
      lastLoginAt: null,
    },
    isLoading: false,
    isError: false,
  }),
}));

// ─── API client ───────────────────────────────────────────────────────────────
const mockGet = jest.fn();
jest.mock('../api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
  },
}));

// ─── TanStack Query sync scheduler ───────────────────────────────────────────
// Without this, query state updates fire inside a setTimeout (outside act()),
// causing spurious "not wrapped in act" warnings and flaky findByText timeouts.
beforeAll(() => {
  notifyManager.setScheduler((fn) => fn());
});
afterAll(() => {
  notifyManager.setScheduler((fn) => setTimeout(fn, 0));
});

// ─── Helper ───────────────────────────────────────────────────────────────────
function renderWithQuery(ui: React.ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

function makePage(totalElements: number) {
  return Promise.resolve({
    data: { totalElements, content: [], totalPages: 1, number: 0, size: 1 },
  });
}

// ─── Tests ────────────────────────────────────────────────────────────────────
describe('HomeScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGet.mockResolvedValue({
      data: { totalElements: 0, content: [], totalPages: 0, number: 0, size: 1 },
    });
  });

  it('renders three KPI card labels', async () => {
    const { getByText } = renderWithQuery(<HomeScreen />);
    // textTransform: 'uppercase' is visual only — underlying text nodes are title-case
    expect(getByText('Today')).toBeTruthy();
    expect(getByText('This Week')).toBeTruthy();
    expect(getByText('Pending Approval')).toBeTruthy();
  });

  it('shows greeting with first name from /users/me', () => {
    const { getByText } = renderWithQuery(<HomeScreen />);
    expect(getByText('Good morning, Jane')).toBeTruthy();
  });

  it('shows loading indicators while fetching (values not yet visible)', () => {
    mockGet.mockReturnValue(new Promise(() => {}));
    const { queryByText } = renderWithQuery(<HomeScreen />);
    expect(queryByText('0')).toBeNull();
  });

  it('displays fetched count values based on request params', async () => {
    // Distinguish the three queries by params so ordering does not matter
    mockGet.mockImplementation(
      (_url: string, config: { params?: { status?: string; from?: string } }) => {
        const { status, from } = config?.params ?? {};
        if (status === 'DONE_PENDING_APPROVAL') return makePage(2);
        if (status === 'COMPLETED') return makePage(12);
        if (from) return makePage(3); // today — has `from` but no status
        return makePage(0);
      },
    );

    const { findByText } = renderWithQuery(<HomeScreen />);

    expect(await findByText('3')).toBeTruthy();
    expect(await findByText('12')).toBeTruthy();
    expect(await findByText('2')).toBeTruthy();
  });

  it('navigates to Schedule when a KPI card is pressed', async () => {
    mockGet.mockImplementation(
      (_url: string, config: { params?: { status?: string; from?: string } }) => {
        const { status, from } = config?.params ?? {};
        if (status === 'DONE_PENDING_APPROVAL') return makePage(2);
        if (status === 'COMPLETED') return makePage(12);
        if (from) return makePage(3);
        return makePage(0);
      },
    );

    const { findByText } = renderWithQuery(<HomeScreen />);
    const todayCount = await findByText('3');
    fireEvent.press(todayCount);

    expect(mockDispatch).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'NAVIGATE' }),
    );
  });

  it('pull-to-refresh triggers refetch', async () => {
    mockGet.mockResolvedValue({
      data: { totalElements: 1, content: [], totalPages: 1, number: 0, size: 1 },
    });

    const { UNSAFE_getByType } = renderWithQuery(<HomeScreen />);

    await waitFor(() => expect(mockGet).toHaveBeenCalledTimes(3));

    const callCountBefore = mockGet.mock.calls.length;

    await act(async () => {
      const scrollView = UNSAFE_getByType(ScrollView);
      scrollView.props.refreshControl.props.onRefresh();
    });

    await waitFor(() => {
      expect(mockGet.mock.calls.length).toBeGreaterThan(callCountBefore);
    });
  });
});
