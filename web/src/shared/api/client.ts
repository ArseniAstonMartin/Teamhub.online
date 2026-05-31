import axios, { type AxiosRequestConfig } from 'axios';
import { useAuthStore, REFRESH_TOKEN_KEY } from '@/entities/user/model/authStore';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

// ── Request interceptor ─────────────────────────────────────────────────────
// Read the access token from the Zustand store via getState() (safe to call
// outside React components — does NOT trigger re-renders).
apiClient.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState();
  if (accessToken) {
    config.headers['Authorization'] = `Bearer ${accessToken}`;
  }
  return config;
});

// ── Response interceptor — refresh-on-401 ──────────────────────────────────
let isRefreshing = false;
let refreshSubscribers: Array<(token: string) => void> = [];

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb);
}

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token));
  refreshSubscribers = [];
}

interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config as AxiosRequestConfig & {
      _retry?: boolean;
    };

    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error as unknown);
    }

    const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);

    if (!storedRefreshToken) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
      return Promise.reject(error as unknown);
    }

    if (isRefreshing) {
      // Queue concurrent requests until the refresh completes
      return new Promise((resolve) => {
        subscribeTokenRefresh((newToken) => {
          if (originalRequest.headers) {
            originalRequest.headers['Authorization'] = `Bearer ${newToken}`;
          } else {
            originalRequest.headers = { Authorization: `Bearer ${newToken}` };
          }
          resolve(apiClient(originalRequest));
        });
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const { data } = await axios.post<RefreshResponse>(
        `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}/api/v1/auth/refresh`,
        { refreshToken: storedRefreshToken },
      );

      useAuthStore.getState().setTokens(data.accessToken, data.refreshToken);
      onTokenRefreshed(data.accessToken);

      if (originalRequest.headers) {
        originalRequest.headers['Authorization'] = `Bearer ${data.accessToken}`;
      } else {
        originalRequest.headers = { Authorization: `Bearer ${data.accessToken}` };
      }
      return apiClient(originalRequest);
    } catch (refreshError) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

export default apiClient;
