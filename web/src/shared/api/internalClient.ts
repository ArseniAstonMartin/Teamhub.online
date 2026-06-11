import axios from 'axios';
import { useStaffAuthStore } from '@/entities/staff/model/staffAuthStore';

const internalClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

internalClient.interceptors.request.use((config) => {
  const { accessToken } = useStaffAuthStore.getState();
  if (accessToken) {
    config.headers['Authorization'] = `Bearer ${accessToken}`;
  }
  return config;
});

internalClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      useStaffAuthStore.getState().logout();
      const redirect = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.href = `/__internal/login?redirect=${redirect}`;
    }
    return Promise.reject(error as unknown);
  },
);

export default internalClient;
