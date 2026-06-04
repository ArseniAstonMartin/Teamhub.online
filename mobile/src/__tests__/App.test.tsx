import React from 'react';
import { render } from '@testing-library/react-native';
import App from '../../App';

// Sentry requires native modules; mock the entire package for unit tests.
jest.mock('@sentry/react-native', () => ({
  init: jest.fn(),
  wrap: (component: React.ComponentType) => component,
  mobileReplayIntegration: jest.fn(() => ({})),
  feedbackIntegration: jest.fn(() => ({})),
}));

jest.mock('expo-constants', () => ({
  expoConfig: { version: '1.0.0' },
}));

jest.mock('expo-status-bar', () => ({
  StatusBar: () => null,
}));

jest.mock('@react-navigation/native', () => ({
  NavigationContainer: ({ children }: { children: React.ReactNode }) => children,
  useNavigation: () => ({}),
}));

jest.mock('@react-navigation/bottom-tabs', () => ({
  createBottomTabNavigator: () => ({
    Navigator: ({ children }: { children: React.ReactNode }) => children,
    Screen: () => null,
  }),
}));

jest.mock('@expo/vector-icons', () => ({
  Ionicons: () => null,
}));

describe('App', () => {
  it('renders without crashing', () => {
    expect(() => render(<App />)).not.toThrow();
  });
});
