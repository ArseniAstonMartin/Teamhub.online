import axios, { create as axiosCreate } from 'axios';
import { useAuthStore } from '../stores/authStore';
import { loadRefreshToken } from '../utils/secureStore';

const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'https://api.teamhub.online';

export const apiClient = axiosCreate({ baseURL: BASE_URL });

// Attach Bearer token
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Refresh on 401
let isRefreshing = false;
let queue: ((token: string) => void)[] = [];

apiClient.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config;
    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error);
    }
    original._retry = true;

    if (isRefreshing) {
      return new Promise((resolve) => {
        queue.push((token) => {
          original.headers.Authorization = `Bearer ${token}`;
          resolve(apiClient(original));
        });
      });
    }

    isRefreshing = true;
    try {
      const refreshToken = await loadRefreshToken();
      if (!refreshToken) throw new Error('No refresh token');
      const { data } = await axios.post(`${BASE_URL}/api/v1/auth/refresh`, { refreshToken });
      await useAuthStore.getState().setTokens(data.accessToken, data.refreshToken);
      queue.forEach((cb) => cb(data.accessToken));
      queue = [];
      original.headers.Authorization = `Bearer ${data.accessToken}`;
      return apiClient(original);
    } catch {
      await useAuthStore.getState().logout();
      return Promise.reject(error);
    } finally {
      isRefreshing = false;
    }
  }
);
