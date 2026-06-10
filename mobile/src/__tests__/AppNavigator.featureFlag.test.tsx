import React from 'react';
import { render } from '@testing-library/react-native';
import AppNavigator from '../navigation/AppNavigator';
import * as featureFlagsModule from '../config/featureFlags';

// ─── Navigation mocks ─────────────────────────────────────────────────────────

jest.mock('@react-navigation/native', () => ({
  NavigationContainer: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
}));

jest.mock('@react-navigation/native-stack', () => ({
  createNativeStackNavigator: () => ({
    Navigator: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    Screen: ({ component: Component }: { component: React.ComponentType }) => <Component />,
  }),
}));

// Tab.Screen renders its `name` as a testID so tests can query for tab presence.
// `require` is used inside the factory to avoid the jest hoisting out-of-scope restriction.
jest.mock('@react-navigation/bottom-tabs', () => {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const React = require('react');
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { Text: RNText } = require('react-native');
  return {
    createBottomTabNavigator: () => ({
      Navigator: ({ children }: { children: React.ReactNode }) =>
        React.createElement(React.Fragment, null, children),
      Screen: ({ name }: { name: string }) =>
        React.createElement(RNText, { testID: `tab-${name}` }, name),
    }),
  };
});

// ─── Native / Expo mocks ──────────────────────────────────────────────────────

jest.mock('@expo/vector-icons', () => ({ Ionicons: () => null }));

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('expo-notifications', () => ({
  setNotificationHandler: jest.fn(),
  requestPermissionsAsync: jest.fn().mockResolvedValue({ status: 'denied' }),
  getPermissionsAsync: jest.fn().mockResolvedValue({ status: 'denied' }),
  getExpoPushTokenAsync: jest.fn().mockResolvedValue({ data: 'ExponentPushToken[test]' }),
  addNotificationReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
  addNotificationResponseReceivedListener: jest.fn(() => ({ remove: jest.fn() })),
  getLastNotificationResponseAsync: jest.fn().mockResolvedValue(null),
  AndroidImportance: { MAX: 5 },
}));

jest.mock('axios', () => ({
  post: jest.fn().mockRejectedValue(new Error('no refresh token')),
}));

// ─── App mocks ────────────────────────────────────────────────────────────────

jest.mock('../stores/authStore', () => ({
  useAuthStore: jest.fn().mockReturnValue({
    accessToken: 'test-access-token',
    setTokens: jest.fn(),
  }),
}));

jest.mock('../utils/secureStore', () => ({
  loadRefreshToken: jest.fn().mockResolvedValue(null),
  deleteRefreshToken: jest.fn(),
}));

jest.mock('../hooks/usePushHandler', () => ({
  usePushHandler: jest.fn(),
}));

jest.mock('../navigation/AuthNavigator', () => () => null);

// Screen mocks — render null so tests stay focused on tab structure.
jest.mock('../screens/HomeScreen', () => () => null);
jest.mock('../screens/ScheduleScreen', () => () => null);
jest.mock('../screens/ClientsScreen', () => () => null);
jest.mock('../screens/ProfileScreen', () => () => null);
jest.mock('../screens/JobDetailScreen', () => () => null);
jest.mock('../screens/AddNoteScreen', () => () => null);
jest.mock('../screens/AttachImageScreen', () => () => null);
jest.mock('../screens/CustomerDetailScreen', () => () => null);
jest.mock('../screens/MessagesScreen', () => () => null);
jest.mock('../screens/CallsScreen', () => () => null);

// Feature flags — mocked as mutable so each describe block can flip the flag.
jest.mock('../config/featureFlags', () => ({
  featureFlags: { v1_2_preview: false },
}));

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('AppNavigator feature flag — v1_2_preview = false (default)', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    (featureFlagsModule.featureFlags as { v1_2_preview: boolean }).v1_2_preview = false;
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('does not render the Messages tab', () => {
    const { queryByTestId } = render(<AppNavigator />);
    expect(queryByTestId('tab-Messages')).toBeNull();
  });

  it('does not render the Calls tab', () => {
    const { queryByTestId } = render(<AppNavigator />);
    expect(queryByTestId('tab-Calls')).toBeNull();
  });
});

describe('AppNavigator feature flag — v1_2_preview = true (mocked)', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    (featureFlagsModule.featureFlags as { v1_2_preview: boolean }).v1_2_preview = true;
  });

  afterEach(() => {
    jest.useRealTimers();
    (featureFlagsModule.featureFlags as { v1_2_preview: boolean }).v1_2_preview = false;
  });

  it('renders the Messages tab', () => {
    const { getByTestId } = render(<AppNavigator />);
    expect(getByTestId('tab-Messages')).toBeTruthy();
  });

  it('renders the Calls tab', () => {
    const { getByTestId } = render(<AppNavigator />);
    expect(getByTestId('tab-Calls')).toBeTruthy();
  });
});
