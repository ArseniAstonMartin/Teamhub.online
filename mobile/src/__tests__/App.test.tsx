import React from 'react';
import { render } from '@testing-library/react-native';
import App from '../../App';

// Sentry requires native modules; mock the entire package for unit tests.
// jest.mock is hoisted by Babel before imports, so App.tsx receives these mocks.
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

describe('App', () => {
  it('renders without crashing', () => {
    const { toJSON } = render(<App />);
    expect(toJSON()).toBeTruthy();
  });
});
