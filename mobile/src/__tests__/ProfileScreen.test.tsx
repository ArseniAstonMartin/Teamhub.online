import React from 'react';
import { Alert } from 'react-native';
import { render, fireEvent, act, waitFor } from '@testing-library/react-native';
import ProfileScreen from '../screens/ProfileScreen';

// ─── expo-constants ───────────────────────────────────────────────────────────
jest.mock('expo-constants', () => ({
  expoConfig: { version: '1.0.0' },
}));

// ─── expo-device ──────────────────────────────────────────────────────────────
jest.mock('expo-device', () => ({
  modelName: 'iPhone 15',
}));

// ─── expo-application ─────────────────────────────────────────────────────────
jest.mock('expo-application', () => ({}));

// ─── Auth store ───────────────────────────────────────────────────────────────
const mockLogout = jest.fn().mockResolvedValue(undefined);
const mockUser = {
  id: 'e5f6a7b8-0000-0000-0000-000000000000',
  tenantId: 'a1b2c3d4-0000-0000-0000-000000000000',
  role: 'TECHNICIAN' as const,
};

jest.mock('../stores/authStore', () => {
  const useAuthStoreMock = Object.assign(
    jest.fn(
      (selector: (s: { user: typeof mockUser; logout: jest.Mock }) => unknown) =>
        selector({ user: mockUser, logout: mockLogout }),
    ),
    {
      getState: jest.fn(() => ({ user: mockUser, logout: mockLogout })),
    },
  );
  return { useAuthStore: useAuthStoreMock };
});

// ─── API client ───────────────────────────────────────────────────────────────
const mockPost = jest.fn().mockResolvedValue({});
jest.mock('../api/client', () => ({
  apiClient: {
    post: (...args: unknown[]) => mockPost(...args),
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
  },
}));

// ─── Secure store ─────────────────────────────────────────────────────────────
const mockLoadRefreshToken = jest.fn().mockResolvedValue('test-refresh-token');
jest.mock('../utils/secureStore', () => ({
  loadRefreshToken: () => mockLoadRefreshToken(),
  saveRefreshToken: jest.fn(),
  deleteRefreshToken: jest.fn(),
  setItem: jest.fn(),
  getItem: jest.fn(),
}));

// ─── Setup / Teardown ─────────────────────────────────────────────────────────
beforeEach(() => {
  jest.useFakeTimers();
  jest.clearAllMocks();
  mockLogout.mockResolvedValue(undefined);
  mockPost.mockResolvedValue({});
  mockLoadRefreshToken.mockResolvedValue('test-refresh-token');
});

afterEach(() => {
  jest.useRealTimers();
});

// ─── Tests ────────────────────────────────────────────────────────────────────
describe('ProfileScreen', () => {
  it('renders role, truncated tenantId, and truncated userId from auth store', () => {
    const { getByText } = render(<ProfileScreen />);

    expect(getByText('TECHNICIAN')).toBeTruthy();
    expect(getByText('a1b2c3d4…')).toBeTruthy();
    expect(getByText('e5f6a7b8…')).toBeTruthy();
  });

  it('renders app version and device name', () => {
    const { getByText } = render(<ProfileScreen />);

    expect(getByText('1.0.0')).toBeTruthy();
    expect(getByText('iPhone 15')).toBeTruthy();
  });

  it('tapping Sign Out shows a confirmation alert', () => {
    const alertSpy = jest.spyOn(Alert, 'alert');
    const { getByTestId } = render(<ProfileScreen />);

    fireEvent.press(getByTestId('sign-out-button'));

    expect(alertSpy).toHaveBeenCalledWith(
      'Sign out',
      'Are you sure you want to sign out?',
      expect.arrayContaining([
        expect.objectContaining({ text: 'Cancel' }),
        expect.objectContaining({ text: 'Sign Out' }),
      ]),
    );
  });

  it('confirming sign-out calls POST /api/v1/auth/logout then authStore.logout()', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert');
    const { getByTestId } = render(<ProfileScreen />);

    fireEvent.press(getByTestId('sign-out-button'));

    // Extract the "Sign Out" confirm button's onPress from the alert call
    const [, , buttons] = alertSpy.mock.calls[0] as [
      string,
      string,
      { text: string; onPress?: () => Promise<void> }[],
    ];
    const confirmButton = buttons.find((b) => b.text === 'Sign Out');
    expect(confirmButton).toBeDefined();

    await act(async () => {
      await confirmButton!.onPress?.();
    });

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/api/v1/auth/logout', {
        refreshToken: 'test-refresh-token',
      });
      expect(mockLogout).toHaveBeenCalled();
    });

    // Ensure logout is called after (or alongside) the server revocation
    const postOrder = mockPost.mock.invocationCallOrder[0];
    const logoutOrder = mockLogout.mock.invocationCallOrder[0];
    expect(logoutOrder).toBeGreaterThanOrEqual(postOrder);
  });
});
