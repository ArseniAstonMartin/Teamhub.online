import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { apiClient } from '../api/client';
import { queryClient } from '../api/queryClient';
import { setItem, getItem } from '../utils/secureStore';

// Configure foreground notification display behavior
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

export async function registerPushToken(): Promise<void> {
  if (!Device.isDevice) return;

  const { status: existingStatus } = await Notifications.getPermissionsAsync();
  let finalStatus = existingStatus;
  if (existingStatus !== 'granted') {
    const { status } = await Notifications.requestPermissionsAsync();
    finalStatus = status;
  }
  if (finalStatus !== 'granted') return;

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync('default', {
      name: 'Default',
      importance: Notifications.AndroidImportance.MAX,
    });
  }

  const tokenData = await Notifications.getExpoPushTokenAsync();
  const expoToken = tokenData.data;
  const platform = Platform.OS === 'ios' ? 'IOS' : 'ANDROID';

  try {
    await apiClient.post('/api/v1/devices/register', { expoToken, platform });
    await setItem('expoPushToken', expoToken);
  } catch (err: unknown) {
    const axiosErr = err as { response?: { data?: { code?: string } } };
    if (axiosErr?.response?.data?.code === 'DeviceNotRegistered') {
      const newToken = await Notifications.getExpoPushTokenAsync();
      await apiClient.post('/api/v1/devices/register', { expoToken: newToken.data, platform });
      await setItem('expoPushToken', newToken.data);
    }
  }
}

export async function unregisterPushToken(): Promise<void> {
  const expoToken = await getItem('expoPushToken');
  if (!expoToken) return;
  try {
    await apiClient.post('/api/v1/devices/unregister', { expoToken });
  } catch {
    // best-effort
  }
}

export function handleNotificationTap(
  response: Notifications.NotificationResponse,
  navigate: (screen: string, params: object) => void,
): void {
  const data = response.notification.request.content.data as {
    jobId?: string;
    screen?: string;
  };
  if (data?.jobId) {
    navigate('JobDetail', { jobId: data.jobId });
  }
}

export function handleSilentPush(notification: Notifications.Notification): void {
  const data = notification.request.content.data as { screen?: string };
  const isSilent =
    !notification.request.content.title && !notification.request.content.body;
  if (isSilent || data?.screen === 'SharedPool') {
    queryClient.invalidateQueries({ queryKey: ['jobs', 'shared-pool'] });
  }
}
