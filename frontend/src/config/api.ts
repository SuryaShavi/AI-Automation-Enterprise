export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

export const AUTH_STORAGE_KEYS = {
  accessToken: 'aieap.access-token',
  refreshToken: 'aieap.refresh-token',
} as const;