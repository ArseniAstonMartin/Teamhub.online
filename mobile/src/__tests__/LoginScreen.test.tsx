import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import LoginScreen from '../screens/LoginScreen';

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('expo-notifications', () => ({
  requestPermissionsAsync: jest.fn().mockResolvedValue({ status: 'denied' }),
  getExpoPushTokenAsync: jest.fn().mockResolvedValue({ data: 'ExponentPushToken[test]' }),
}));

jest.mock('expo-apple-authentication', () => ({
  isAvailableAsync: jest.fn().mockResolvedValue(false),
  AppleAuthenticationButton: () => null,
  AppleAuthenticationButtonType: { SIGN_IN: 'SIGN_IN' },
  AppleAuthenticationButtonStyle: { BLACK: 'BLACK' },
}));

jest.mock('expo-web-browser', () => ({
  maybeCompleteAuthSession: jest.fn(),
  openAuthSessionAsync: jest.fn().mockResolvedValue({ type: 'cancel' }),
}));

jest.mock('expo-linking', () => ({
  createURL: jest.fn().mockReturnValue('teamhub://auth/callback'),
}));

jest.mock('../api/authApi', () => ({
  loginWithPassword: jest.fn().mockRejectedValue(new Error('Unauthorized')),
}));

jest.mock('../api/deviceApi', () => ({
  registerPushToken: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../api/client', () => ({
  apiClient: {
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
    post: jest.fn(),
  },
}));

jest.mock('../stores/authStore', () => ({
  useAuthStore: () => ({
    accessToken: null,
    user: null,
    setTokens: jest.fn().mockResolvedValue(undefined),
    logout: jest.fn().mockResolvedValue(undefined),
  }),
}));

describe('LoginScreen', () => {
  it('renders email input, password input, and sign-in button', () => {
    const { getByTestId } = render(<LoginScreen />);
    expect(getByTestId('email-input')).toBeTruthy();
    expect(getByTestId('password-input')).toBeTruthy();
    expect(getByTestId('sign-in-button')).toBeTruthy();
  });

  it('renders Google sign-in button', () => {
    const { getByTestId } = render(<LoginScreen />);
    expect(getByTestId('google-sign-in-button')).toBeTruthy();
  });

  it('renders forgot password link', () => {
    const { getByTestId } = render(<LoginScreen />);
    expect(getByTestId('forgot-password-link')).toBeTruthy();
  });

  it('shows validation errors when submitting empty form', async () => {
    const { getByTestId, getByText } = render(<LoginScreen />);
    fireEvent.press(getByTestId('sign-in-button'));
    await waitFor(() => {
      expect(getByText('Email is required')).toBeTruthy();
      expect(getByText('Password is required')).toBeTruthy();
    });
  });

  it('shows invalid email error for bad email format', async () => {
    const { getByTestId, getByText } = render(<LoginScreen />);
    fireEvent.changeText(getByTestId('email-input'), 'not-an-email');
    fireEvent.changeText(getByTestId('password-input'), 'password123');
    fireEvent.press(getByTestId('sign-in-button'));
    await waitFor(() => {
      expect(getByText('Enter a valid email address')).toBeTruthy();
    });
  });

  it('shows loading state on submit with valid credentials', async () => {
    let resolveLogin: () => void;
    const authApi = jest.requireMock('../api/authApi') as { loginWithPassword: jest.Mock };
    authApi.loginWithPassword.mockImplementation(
      () => new Promise((_resolve, reject) => { resolveLogin = () => reject(new Error('err')); })
    );

    const { getByTestId } = render(<LoginScreen />);
    fireEvent.changeText(getByTestId('email-input'), 'test@example.com');
    fireEvent.changeText(getByTestId('password-input'), 'password123');
    fireEvent.press(getByTestId('sign-in-button'));

    // Button is disabled while loading
    await waitFor(() => {
      expect(getByTestId('sign-in-button').props.accessibilityState?.disabled ?? false).toBe(true);
    });

    // Settle the promise so no dangling state updates after teardown
    resolveLogin!();
    await waitFor(() => {
      expect(getByTestId('sign-in-button').props.accessibilityState?.disabled ?? false).toBe(false);
    });
  });
});
