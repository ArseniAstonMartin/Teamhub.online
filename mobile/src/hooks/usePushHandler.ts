import { useEffect } from 'react';
import { useNavigation } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import { handleSilentPush, handleNotificationTap } from '../notifications/pushService';
import type { RootStackNavProp } from '../navigation/types';

export function usePushHandler() {
  const navigation = useNavigation<RootStackNavProp<'MainTabs'>>();

  useEffect(() => {
    // Cold-start: process a notification tap that launched the app
    Notifications.getLastNotificationResponseAsync().then((response) => {
      if (response) {
        handleNotificationTap(response, (screen, params) =>
          navigation.navigate(screen as any, params as any),
        );
      }
    });

    const receivedSub = Notifications.addNotificationReceivedListener(handleSilentPush);

    const responseSub = Notifications.addNotificationResponseReceivedListener((response) => {
      handleNotificationTap(response, (screen, params) =>
        navigation.navigate(screen as any, params as any),
      );
    });

    return () => {
      receivedSub.remove();
      responseSub.remove();
    };
  }, [navigation]);
}
