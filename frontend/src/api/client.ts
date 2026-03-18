import { API_BASE_URL, AUTH_STORAGE_KEYS } from '../config/api';
import { endpoints } from './endpoints';
import type { ApiEnvelope, LoginResponse } from './contracts';

type HttpMethod = 'GET' | 'POST' | 'PATCH' | 'DELETE';

function isRawBody(body: unknown): body is BodyInit {
  return body instanceof FormData
    || body instanceof Blob
    || body instanceof URLSearchParams
    || typeof body === 'string'
    || body instanceof ArrayBuffer
    || ArrayBuffer.isView(body);
}

interface RequestOptions extends Omit<RequestInit, 'method' | 'body'> {
  method?: HttpMethod;
  body?: BodyInit | Record<string, unknown>;
  idempotencyKey?: string;
  skipAuth?: boolean;
}

export class ApiClient {
  private refreshPromise: Promise<string | null> | null = null;

  async request<T>(path: string, options: RequestOptions = {}): Promise<ApiEnvelope<T>> {
    const response = await this.performRequest<T>(path, options);
    if (response.status !== 401 || options.skipAuth) {
      return this.parseEnvelope<T>(response);
    }

    const refreshedToken = await this.refreshAccessToken();
    if (!refreshedToken) {
      throw new Error('Authentication expired');
    }

    const retryResponse = await this.performRequest<T>(path, options);
    return this.parseEnvelope<T>(retryResponse);
  }

  private async performRequest<T>(path: string, options: RequestOptions): Promise<Response> {
    const headers = new Headers(options.headers);
    const isFormData = options.body instanceof FormData;

    if (options.body !== undefined && !isFormData && !isRawBody(options.body) && options.body !== null) {
      headers.set('Content-Type', 'application/json');
    }

    if (!options.skipAuth) {
      const accessToken = localStorage.getItem(AUTH_STORAGE_KEYS.accessToken);
      if (accessToken) {
        headers.set('Authorization', `Bearer ${accessToken}`);
      }
    }

    if (options.idempotencyKey) {
      headers.set('Idempotency-Key', options.idempotencyKey);
    }

    const requestBody: BodyInit | null = options.body === undefined
      ? null
      : isRawBody(options.body)
        ? (options.body as BodyInit)
        : isFormData
          ? (options.body as unknown as BodyInit)
          : (JSON.stringify(options.body) as string);

    return fetch(`${API_BASE_URL}${path}`, {
      ...options,
      method: options.method ?? 'GET',
      headers,
      body: requestBody,
    });
  }

  private async parseEnvelope<T>(response: Response): Promise<ApiEnvelope<T>> {
    const envelope = (await response.json()) as ApiEnvelope<T>;
    if (!response.ok || envelope.error) {
      throw new Error(envelope.error?.message ?? `Request failed with ${response.status}`);
    }
    return envelope;
  }

  private async refreshAccessToken(): Promise<string | null> {
    if (!this.refreshPromise) {
      this.refreshPromise = this.fetchNewAccessToken().finally(() => {
        this.refreshPromise = null;
      });
    }

    return this.refreshPromise;
  }

  private async fetchNewAccessToken(): Promise<string | null> {
    const refreshToken = localStorage.getItem(AUTH_STORAGE_KEYS.refreshToken);
    if (!refreshToken) {
      return null;
    }

    const response = await this.performRequest<LoginResponse>(endpoints.auth.refresh, {
      method: 'POST',
      body: { refreshToken },
      skipAuth: true,
    }) as Response;

    if (!response.ok) {
      localStorage.removeItem(AUTH_STORAGE_KEYS.accessToken);
      localStorage.removeItem(AUTH_STORAGE_KEYS.refreshToken);
      return null;
    }

    const envelope = (await response.json()) as ApiEnvelope<LoginResponse>;
    localStorage.setItem(AUTH_STORAGE_KEYS.accessToken, envelope.data.accessToken);
    localStorage.setItem(AUTH_STORAGE_KEYS.refreshToken, envelope.data.refreshToken);
    return envelope.data.accessToken;
  }
}

export const apiClient = new ApiClient();