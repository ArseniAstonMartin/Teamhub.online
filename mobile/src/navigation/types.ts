import { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';

export type RootStackParamList = {
  MainTabs: undefined;
  JobDetail: { jobId: string };
  // TO-DO: implement full MapView screen in a future task
  MapView: { jobId: string };
};

export type RootTabParamList = {
  Home: undefined;
  Schedule: undefined;
  Clients: undefined;
  Profile: undefined;
};

export type RootTabNavProp<T extends keyof RootTabParamList> =
  BottomTabNavigationProp<RootTabParamList, T>;

export type RootStackNavProp<T extends keyof RootStackParamList> =
  NativeStackNavigationProp<RootStackParamList, T>;

export type AuthStackParamList = {
  Login: undefined;
  ForgotPassword: undefined;
};

export type AuthStackNavProp<T extends keyof AuthStackParamList> =
  NativeStackNavigationProp<AuthStackParamList, T>;
