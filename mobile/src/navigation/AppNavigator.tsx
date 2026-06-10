import React, { useEffect } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Ionicons } from '@expo/vector-icons';
import axios from 'axios';

import { colors } from '../theme/colors';
import type { RootStackParamList, RootTabParamList } from './types';
import HomeScreen from '../screens/HomeScreen';
import ScheduleScreen from '../screens/ScheduleScreen';
import ClientsScreen from '../screens/ClientsScreen';
import ProfileScreen from '../screens/ProfileScreen';
import JobDetailScreen from '../screens/JobDetailScreen';
import AddNoteScreen from '../screens/AddNoteScreen';
import AttachImageScreen from '../screens/AttachImageScreen';
import AuthNavigator from './AuthNavigator';
import { useAuthStore } from '../stores/authStore';
import { loadRefreshToken, deleteRefreshToken } from '../utils/secureStore';
import { usePushHandler } from '../hooks/usePushHandler';

const Tab = createBottomTabNavigator<RootTabParamList>();
const Stack = createNativeStackNavigator<RootStackParamList>();

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

const TAB_ICONS: Record<keyof RootTabParamList, { active: IoniconName; inactive: IoniconName }> = {
  Home: { active: 'home', inactive: 'home-outline' },
  Schedule: { active: 'calendar', inactive: 'calendar-outline' },
  Clients: { active: 'people', inactive: 'people-outline' },
  Profile: { active: 'person', inactive: 'person-outline' },
};

const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'https://api.teamhub.online';

function MainTabs() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        tabBarActiveTintColor: colors.tabActive,
        tabBarInactiveTintColor: colors.tabInactive,
        tabBarStyle: {
          backgroundColor: colors.background,
          borderTopColor: colors.border,
        },
        headerStyle: { backgroundColor: colors.background },
        headerTintColor: colors.text,
        headerShadowVisible: false,
        tabBarIcon: ({ focused, color, size }) => {
          const icons = TAB_ICONS[route.name as keyof RootTabParamList];
          const iconName = focused ? icons.active : icons.inactive;
          return <Ionicons name={iconName} size={size} color={color} />;
        },
      })}
    >
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen name="Schedule" component={ScheduleScreen} />
      <Tab.Screen name="Clients" component={ClientsScreen} />
      <Tab.Screen name="Profile" component={ProfileScreen} />
    </Tab.Navigator>
  );
}

function AuthenticatedStack() {
  usePushHandler();

  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="MainTabs" component={MainTabs} />
      <Stack.Screen
        name="JobDetail"
        component={JobDetailScreen}
        options={{ headerShown: false, presentation: 'card' }}
      />
      <Stack.Screen
        name="AddNote"
        component={AddNoteScreen}
        options={{ headerShown: false, presentation: 'modal' }}
      />
      <Stack.Screen
        name="AttachImage"
        component={AttachImageScreen}
        options={{ headerShown: false, presentation: 'modal' }}
      />
    </Stack.Navigator>
  );
}

export default function AppNavigator() {
  const { accessToken, setTokens } = useAuthStore();

  useEffect(() => {
    loadRefreshToken().then((rt) => {
      if (!rt) return;
      axios
        .post(`${BASE_URL}/api/v1/auth/refresh`, { refreshToken: rt })
        .then(({ data }) => setTokens(data.accessToken, data.refreshToken))
        .catch(() => deleteRefreshToken());
    });
  }, [setTokens]);

  return (
    <NavigationContainer>
      {accessToken ? <AuthenticatedStack /> : <AuthNavigator />}
    </NavigationContainer>
  );
}
