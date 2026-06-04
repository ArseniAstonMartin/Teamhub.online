import { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';

export type RootTabParamList = {
  Home: undefined;
  Schedule: undefined;
  Clients: undefined;
  Profile: undefined;
};

export type RootTabNavProp<T extends keyof RootTabParamList> =
  BottomTabNavigationProp<RootTabParamList, T>;
