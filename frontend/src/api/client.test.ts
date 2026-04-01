import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiClient } from './client';
import { endpoints } from './endpoints';
import { AUTH_STORAGE_KEYS } from '../config/api';

function envelopeResponse<T>(data: T, status = 200) {
  return new Response(
    JSON.stringify({
      timestamp: new Date().toISOString(),
      traceId: 'test-trace',
      data,
      error: null,
    }),
    {
      status,
      headers: { 'Content-Type': 'application/json' },
    },
  );
}

describe('ApiClient', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    localStorage.clear();
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('adds auth and idempotency headers for JSON requests', async () => {
    localStorage.setItem(AUTH_STORAGE_KEYS.accessToken, 'access-token-1');
    fetchMock.mockResolvedValueOnce(envelopeResponse({ status: 'ok' }));

    const client = new ApiClient();
    await client.request('/tasks', {
      method: 'POST',
      idempotencyKey: 'task-123',
      body: { title: 'Prepare quarterly report' },
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);

    const [, requestInit] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(requestInit.headers);

    expect(headers.get('Authorization')).toBe('Bearer access-token-1');
    expect(headers.get('Idempotency-Key')).toBe('task-123');
    expect(headers.get('Content-Type')).toContain('application/json');
    expect(requestInit.body).toBe(JSON.stringify({ title: 'Prepare quarterly report' }));
  });

  it('refreshes once after a 401 and retries the original request', async () => {
    localStorage.setItem(AUTH_STORAGE_KEYS.accessToken, 'expired-access');
    localStorage.setItem(AUTH_STORAGE_KEYS.refreshToken, 'refresh-token-1');

    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: null, error: null }), { status: 401, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(envelopeResponse({
        accessToken: 'fresh-access-token',
        refreshToken: 'fresh-refresh-token',
        accessTokenExpiresAt: '2026-04-01T12:00:00Z',
        refreshTokenExpiresAt: '2026-04-02T12:00:00Z',
        user: {
          id: 'user-1',
          email: 'user@company.com',
          firstName: 'Test',
          lastName: 'User',
          roles: ['EMPLOYEE'],
          preferences: {},
          twoFactorEnabled: false,
          lastLoginAt: '2026-04-01T10:00:00Z',
        },
      }))
      .mockResolvedValueOnce(envelopeResponse({ message: 'retried-successfully' }));

    const client = new ApiClient();
    const result = await client.request<{ message: string }>(endpoints.auth.me);

    expect(result.data.message).toBe('retried-successfully');
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.accessToken)).toBe('fresh-access-token');
    expect(localStorage.getItem(AUTH_STORAGE_KEYS.refreshToken)).toBe('fresh-refresh-token');
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(String(fetchMock.mock.calls[1]?.[0])).toContain(endpoints.auth.refresh);
  });
});
