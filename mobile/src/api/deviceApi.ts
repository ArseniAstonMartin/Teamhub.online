import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { apiClient } from './client';

export interface DeviceRegistration {
  id: string;
  expoToken: string;
  platform: string;
  lastSeenAt: string;
}

export async function registerPushToken(accessToken: string): Promise<void> {
  try {
    const { status } = await Notifications.requestPermissionsAsync();
    if (status !== 'granted') return;
    const tokenData = await Notifications.getExpoPushTokenAsync();
    await apiClient.post<DeviceRegistration>(
      '/api/v1/devices/register',
      {
        expoToken: tokenData.data,
        platform: Platform.OS === 'ios' ? 'IOS' : 'ANDROID',
      },
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );
  } catch {
    // Non-fatal — push registration failure should not block login
  }
}

export async function unregisterPushToken(): Promise<void> {
  try {
    const tokenData = await Notifications.getExpoPushTokenAsync();
    await apiClient.post('/api/v1/devices/unregister', {
      expoToken: tokenData.data,
    });
  } catch {
    // Non-fatal
  }
}
