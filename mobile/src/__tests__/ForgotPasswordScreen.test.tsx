import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import ForgotPasswordScreen from '../screens/ForgotPasswordScreen';

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('../api/authApi', () => ({
  forgotPassword: jest.fn().mockResolvedValue(undefined),
}));

describe('ForgotPasswordScreen', () => {
  it('renders email input and send reset button', () => {
    const { getByTestId } = render(<ForgotPasswordScreen />);
    expect(getByTestId('forgot-email-input')).toBeTruthy();
    expect(getByTestId('send-reset-button')).toBeTruthy();
  });

  it('shows validation error for empty email', async () => {
    const { getByTestId, getByText } = render(<ForgotPasswordScreen />);
    fireEvent.press(getByTestId('send-reset-button'));
    await waitFor(() => {
      expect(getByText('Email is required')).toBeTruthy();
    });
  });

  it('shows anti-enumeration success message after submit', async () => {
    const { getByTestId, getByText } = render(<ForgotPasswordScreen />);
    fireEvent.changeText(getByTestId('forgot-email-input'), 'test@example.com');
    fireEvent.press(getByTestId('send-reset-button'));
    await waitFor(() => {
      expect(
        getByText("If that email is registered, you'll receive a reset link shortly.")
      ).toBeTruthy();
    });
  });
});
