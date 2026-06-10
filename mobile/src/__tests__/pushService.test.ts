import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import {
  registerPushToken,
  unregisterPushToken,
  handleNotificationTap,
  handleSilentPush,
} from '../notifications/pushService';

// ─── expo-notifications mock ─────────────────────────────────────────────────
jest.mock('expo-notifications', () => ({
  setNotificationHandler: jest.fn(),
  getPermissionsAsync: jest.fn().mockResolvedValue({ status: 'granted' }),
  requestPermissionsAsync: jest.fn().mockResolvedValue({ status: 'granted' }),
  getExpoPushTokenAsync: jest.fn().mockResolvedValue({ data: 'ExponentPushToken[test123]' }),
  setNotificationChannelAsync: jest.fn().mockResolvedValue(null),
  addNotificationReceivedListener: jest.fn().mockReturnValue({ remove: jest.fn() }),
  addNotificationResponseReceivedListener: jest.fn().mockReturnValue({ remove: jest.fn() }),
  getLastNotificationResponseAsync: jest.fn().mockResolvedValue(null),
  AndroidImportance: { MAX: 5 },
}));

// ─── expo-device mock ─────────────────────────────────────────────────────────
jest.mock('expo-device', () => ({ isDevice: true }));

// ─── apiClient mock ───────────────────────────────────────────────────────────
const mockPost = jest.fn().mockResolvedValue({ data: {} });
jest.mock('../api/client', () => ({
  apiClient: { post: (...args: unknown[]) => mockPost(...args) },
}));

// ─── queryClient mock ─────────────────────────────────────────────────────────
const mockInvalidateQueries = jest.fn();
jest.mock('../api/queryClient', () => ({
  queryClient: { invalidateQueries: (...args: unknown[]) => mockInvalidateQueries(...args) },
}));

// ─── secureStore mock ─────────────────────────────────────────────────────────
const mockSetItem = jest.fn().mockResolvedValue(undefined);
const mockGetItem = jest.fn().mockResolvedValue('ExponentPushToken[stored]');
jest.mock('../utils/secureStore', () => ({
  setItem: (...args: unknown[]) => mockSetItem(...args),
  getItem: (...args: unknown[]) => mockGetItem(...args),
  saveRefreshToken: jest.fn(),
  loadRefreshToken: jest.fn(),
  deleteRefreshToken: jest.fn(),
}));

beforeEach(() => {
  jest.clearAllMocks();
  (Notifications.getExpoPushTokenAsync as jest.Mock).mockResolvedValue({
    data: 'ExponentPushToken[test123]',
  });
  (Notifications.getPermissionsAsync as jest.Mock).mockResolvedValue({ status: 'granted' });
  mockPost.mockResolvedValue({ data: {} });
  mockGetItem.mockResolvedValue('ExponentPushToken[stored]');
});

describe('registerPushToken', () => {
  it('calls POST /api/v1/devices/register with token and platform', async () => {
    Object.defineProperty(Platform, 'OS', { value: 'ios', configurable: true });

    await registerPushToken();

    expect(mockPost).toHaveBeenCalledWith('/api/v1/devices/register', {
      expoToken: 'ExponentPushToken[test123]',
      platform: 'IOS',
    });
  });

  it('re-requests token and retries when backend returns DeviceNotRegistered', async () => {
    Object.defineProperty(Platform, 'OS', { value: 'android', configurable: true });

    mockPost
      .mockRejectedValueOnce({
        response: { data: { code: 'DeviceNotRegistered' } },
      })
      .mockResolvedValueOnce({ data: {} });

    (Notifications.getExpoPushTokenAsync as jest.Mock)
      .mockResolvedValueOnce({ data: 'ExponentPushToken[old]' })
      .mockResolvedValueOnce({ data: 'ExponentPushToken[new]' });

    await registerPushToken();

    expect(Notifications.getExpoPushTokenAsync).toHaveBeenCalledTimes(2);
    expect(mockPost).toHaveBeenLastCalledWith('/api/v1/devices/register', {
      expoToken: 'ExponentPushToken[new]',
      platform: 'ANDROID',
    });
  });
});

describe('unregisterPushToken', () => {
  it('calls POST /api/v1/devices/unregister with stored token', async () => {
    await unregisterPushToken();

    expect(mockPost).toHaveBeenCalledWith('/api/v1/devices/unregister', {
      expoToken: 'ExponentPushToken[stored]',
    });
  });

  it('does nothing when no token is stored', async () => {
    mockGetItem.mockResolvedValueOnce(null);

    await unregisterPushToken();

    expect(mockPost).not.toHaveBeenCalled();
  });
});

describe('handleNotificationTap', () => {
  it('navigates to JobDetail with the correct jobId', () => {
    const mockNavigate = jest.fn();
    const response = {
      notification: {
        request: {
          content: {
            data: { jobId: 'job-uuid-abc', screen: 'JobDetail' },
          },
        },
      },
    } as unknown as Notifications.NotificationResponse;

    handleNotificationTap(response, mockNavigate);

    expect(mockNavigate).toHaveBeenCalledWith('JobDetail', { jobId: 'job-uuid-abc' });
  });

  it('does not navigate when jobId is absent', () => {
    const mockNavigate = jest.fn();
    const response = {
      notification: {
        request: {
          content: {
            data: { screen: 'SharedPool' },
          },
        },
      },
    } as unknown as Notifications.NotificationResponse;

    handleNotificationTap(response, mockNavigate);

    expect(mockNavigate).not.toHaveBeenCalled();
  });
});

describe('handleSilentPush', () => {
  it('invalidates ["jobs", "shared-pool"] cache for silent push (no title/body)', () => {
    const notification = {
      request: {
        content: {
          title: null,
          body: null,
          data: { jobId: 'job-uuid-xyz', screen: 'JobDetail' },
        },
      },
    } as unknown as Notifications.Notification;

    handleSilentPush(notification);

    expect(mockInvalidateQueries).toHaveBeenCalledWith({
      queryKey: ['jobs', 'shared-pool'],
    });
  });

  it('invalidates cache when data.screen === "SharedPool"', () => {
    const notification = {
      request: {
        content: {
          title: 'New job available',
          body: 'Check the shared pool',
          data: { jobId: 'job-uuid-xyz', screen: 'SharedPool' },
        },
      },
    } as unknown as Notifications.Notification;

    handleSilentPush(notification);

    expect(mockInvalidateQueries).toHaveBeenCalledWith({
      queryKey: ['jobs', 'shared-pool'],
    });
  });

  it('does not invalidate cache for visible non-pool notifications', () => {
    const notification = {
      request: {
        content: {
          title: 'Job assigned',
          body: 'You have a new job',
          data: { jobId: 'job-uuid-xyz', screen: 'JobDetail' },
        },
      },
    } as unknown as Notifications.Notification;

    handleSilentPush(notification);

    expect(mockInvalidateQueries).not.toHaveBeenCalled();
  });
});
