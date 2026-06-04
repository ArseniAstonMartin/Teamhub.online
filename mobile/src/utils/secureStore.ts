import * as SecureStore from 'expo-secure-store';

const REFRESH_TOKEN_KEY = 'refresh_token';

export const saveRefreshToken = (token: string) =>
  SecureStore.setItemAsync(REFRESH_TOKEN_KEY, token);

export const loadRefreshToken = () =>
  SecureStore.getItemAsync(REFRESH_TOKEN_KEY);

export const deleteRefreshToken = () =>
  SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
