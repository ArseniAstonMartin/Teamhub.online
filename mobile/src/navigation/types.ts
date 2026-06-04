import { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';

export type RootTabParamList = {
  Home: undefined;
  Schedule: undefined;
  Clients: undefined;
  Profile: undefined;
};

export type RootTabNavProp<T extends keyof RootTabParamList> =
  BottomTabNavigationProp<RootTabParamList, T>;

export type AuthStackParamList = {
  Login: undefined;
  ForgotPassword: undefined;
};

export type AuthStackNavProp<T extends keyof AuthStackParamList> =
  NativeStackNavigationProp<AuthStackParamList, T>;
