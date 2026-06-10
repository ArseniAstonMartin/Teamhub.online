import React from 'react';
import { render, fireEvent, waitFor, act } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider, notifyManager } from '@tanstack/react-query';
import NetInfo from '@react-native-community/netinfo';
import AddNoteScreen from '../screens/AddNoteScreen';

// ─── RendererProxy — prevent react@19 / react-native-renderer mismatch
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

// ─── Navigation & route ────────────────────────────────────────────────────────
const mockGoBack = jest.fn();
const mockNavigate = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ goBack: mockGoBack, navigate: mockNavigate }),
  useRoute: () => ({ params: { jobId: 'job-123' } }),
}));

// ─── expo-sqlite — override global mock with a capturable instance ─────────────
const mockDbRunSync = jest.fn();
const mockDbExecSync = jest.fn();
const mockDbGetAllSync = jest.fn().mockReturnValue([]);

jest.mock('expo-sqlite', () => ({
  openDatabaseSync: () => ({
    execSync: mockDbExecSync,
    runSync: mockDbRunSync,
    getAllSync: mockDbGetAllSync,
  }),
}));

// ─── API client ────────────────────────────────────────────────────────────────
const mockPost = jest.fn();
jest.mock('../api/client', () => ({
  apiClient: {
    get: jest.fn(),
    post: (...args: [string, unknown]) => mockPost(...args),
    patch: jest.fn(),
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
  },
}));

// ─── TanStack Query sync scheduler ────────────────────────────────────────────
beforeAll(() => { notifyManager.setScheduler((fn) => fn()); });
afterAll(() => { notifyManager.setScheduler((fn) => setTimeout(fn, 0)); });

beforeEach(() => {
  jest.clearAllMocks();
  mockDbGetAllSync.mockReturnValue([]);
});

function renderScreen() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AddNoteScreen />
    </QueryClientProvider>,
  );
}

describe('AddNoteScreen', () => {
  it('renders multiline text input and Save button', () => {
    const { getByTestId, getByText } = renderScreen();
    expect(getByTestId('note-input')).toBeTruthy();
    expect(getByText('Save')).toBeTruthy();
  });

  it('character count updates as user types', () => {
    const { getByTestId, getByText } = renderScreen();
    const input = getByTestId('note-input');
    fireEvent.changeText(input, 'Hello world');
    expect(getByText('11 / 4000 characters')).toBeTruthy();
  });

  it('Save while online calls addNote and navigates back', async () => {
    (NetInfo.fetch as jest.Mock).mockResolvedValue({ isConnected: true });
    mockPost.mockResolvedValue({
      data: {
        id: 'note-server-1',
        jobId: 'job-123',
        authorUserId: 'user-1',
        body: 'Checked the wiring',
        createdAt: new Date().toISOString(),
      },
    });

    const { getByTestId, getByText } = renderScreen();
    fireEvent.changeText(getByTestId('note-input'), 'Checked the wiring');

    await act(async () => {
      fireEvent.press(getByText('Save'));
    });

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith(
        '/api/v1/jobs/job-123/notes',
        { body: 'Checked the wiring' },
      );
      expect(mockGoBack).toHaveBeenCalled();
    });
  });

  it('Save while offline calls enqueueNote and navigates back', async () => {
    (NetInfo.fetch as jest.Mock).mockResolvedValue({ isConnected: false });

    const { getByTestId, getByText } = renderScreen();
    fireEvent.changeText(getByTestId('note-input'), 'Offline note');

    await act(async () => {
      fireEvent.press(getByText('Save'));
    });

    await waitFor(() => {
      expect(mockPost).not.toHaveBeenCalled();
      expect(mockDbRunSync).toHaveBeenCalledWith(
        'INSERT INTO offline_notes (id, job_id, body, created_at, synced) VALUES (?, ?, ?, ?, 0)',
        expect.arrayContaining(['job-123', 'Offline note']),
      );
      expect(mockGoBack).toHaveBeenCalled();
    });
  });
});
