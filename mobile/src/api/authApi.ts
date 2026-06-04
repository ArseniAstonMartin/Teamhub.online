import axios from 'axios';

const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'https://api.teamhub.online';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

export async function loginWithPassword(email: string, password: string): Promise<TokenResponse> {
  const { data } = await axios.post<TokenResponse>(`${BASE_URL}/api/v1/auth/login`, {
    email,
    password,
  });
  return data;
}

export async function refreshTokens(refreshToken: string): Promise<TokenResponse> {
  const { data } = await axios.post<TokenResponse>(`${BASE_URL}/api/v1/auth/refresh`, {
    refreshToken,
  });
  return data;
}

export async function logoutApi(refreshToken: string): Promise<void> {
  await axios.post(`${BASE_URL}/api/v1/auth/logout`, { refreshToken });
}

export async function forgotPassword(email: string): Promise<void> {
  await axios.post(`${BASE_URL}/api/v1/auth/forgot-password`, { email });
}
