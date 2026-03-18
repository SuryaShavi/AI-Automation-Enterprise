import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import { apiClient } from '../../api/client';
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserProfile,
} from '../../api/contracts';
import { endpoints } from '../../api/endpoints';
import { AUTH_STORAGE_KEYS } from '../../config/api';

interface SessionContextValue {
  user: UserProfile | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  setUser: (user: UserProfile | null) => void;
}

const SessionContext = createContext<SessionContextValue | undefined>(undefined);

function clearStoredSession() {
  localStorage.removeItem(AUTH_STORAGE_KEYS.accessToken);
  localStorage.removeItem(AUTH_STORAGE_KEYS.refreshToken);
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  async function refreshUser() {
    const accessToken = localStorage.getItem(AUTH_STORAGE_KEYS.accessToken);
    if (!accessToken) {
      setUser(null);
      return;
    }

    try {
      const envelope = await apiClient.request<UserProfile>(endpoints.auth.me);
      setUser(envelope.data);
    } catch {
      clearStoredSession();
      setUser(null);
    }
  }

  async function login(credentials: LoginRequest) {
    const envelope = await apiClient.request<LoginResponse>(endpoints.auth.login, {
      method: 'POST',
      body: credentials,
      skipAuth: true,
    });

    localStorage.setItem(AUTH_STORAGE_KEYS.accessToken, envelope.data.accessToken);
    localStorage.setItem(AUTH_STORAGE_KEYS.refreshToken, envelope.data.refreshToken);
    setUser(envelope.data.user);
  }

  async function register(payload: RegisterRequest) {
    const envelope = await apiClient.request<LoginResponse>(endpoints.auth.register, {
      method: 'POST',
      body: payload,
      skipAuth: true,
    });

    localStorage.setItem(AUTH_STORAGE_KEYS.accessToken, envelope.data.accessToken);
    localStorage.setItem(AUTH_STORAGE_KEYS.refreshToken, envelope.data.refreshToken);
    setUser(envelope.data.user);
  }

  async function logout() {
    const refreshToken = localStorage.getItem(AUTH_STORAGE_KEYS.refreshToken);

    try {
      if (refreshToken) {
        await apiClient.request<{ status: string }>(endpoints.auth.logout, {
          method: 'POST',
          body: { refreshToken },
        });
      }
    } catch {
      // Clear local session even if the revoke request fails.
    } finally {
      clearStoredSession();
      setUser(null);
    }
  }

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        await refreshUser();
      } finally {
        if (active) {
          setIsLoading(false);
        }
      }
    }

    void bootstrap();

    return () => {
      active = false;
    };
  }, []);

  return (
    <SessionContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated: user !== null,
        login,
        register,
        logout,
        refreshUser,
        setUser,
      }}
    >
      {children}
    </SessionContext.Provider>
  );
}

export function useSession() {
  const context = useContext(SessionContext);

  if (!context) {
    throw new Error('useSession must be used within a SessionProvider');
  }

  return context;
}