import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  api, ApiUnavailableError, AiUnavailableError, configureApi, friendlyApiError,
  isSignedIn, saveAuth, signOutLocally,
} from '../api';

const fetchMock = vi.fn();

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
  signOutLocally();
  configureApi('https://example.test', null);
});

describe('API auth + error truthfulness (regression guard)', () => {
  it('maps 401 to a clear SIGN_IN_REQUIRED error (never "no backend configured")', async () => {
    vi.stubGlobal('fetch', fetchMock);
    configureApi('https://example.test', 'expired-token');
    fetchMock.mockResolvedValue(jsonResponse(401, { error: { code: 'UNAUTHORIZED' } }));

    await expect(api.aiKeyterms('some text that is long enough')).rejects.toMatchObject({
      reason: 'SIGN_IN_REQUIRED',
    });
    const msg = await api.aiKeyterms('some text that is long enough').catch((e) => friendlyApiError(e));
    expect(msg).toContain('sign in');
    expect(msg).not.toContain('backend configured');
  });

  it('transparently refreshes an expired token and retries once', async () => {
    vi.stubGlobal('fetch', fetchMock);
    saveAuth({ token: 'expired', refreshToken: 'refresh-me' });
    configureApi('https://example.test');

    let call = 0;
    fetchMock.mockImplementation((url: string, init?: RequestInit) => {
      call += 1;
      if (url.includes('/auth/refresh')) {
        expect(JSON.parse(String(init?.body)).refreshToken).toBe('refresh-me');
        return Promise.resolve(jsonResponse(200, { accessToken: 'fresh', refreshToken: 'refresh-me' }));
      }
      const auth = String((init?.headers as Record<string, string>)?.Authorization ?? '');
      if (auth.includes('expired')) {
        return Promise.resolve(jsonResponse(401, { error: { code: 'UNAUTHORIZED' } }));
      }
      return Promise.resolve(jsonResponse(200, { result: 'ok', citations: [], warnings: [] }));
    });

    const res = await api.aiKeyterms('some text that is long enough');
    expect((res as { result: string }).result).toBe('ok');
    expect(call).toBe(3); // fail -> refresh -> retry success
    expect(isSignedIn()).toBe(true);
  });

  it('maps network failures to NETWORK and 503 AI_UNAVAILABLE distinctly', async () => {
    vi.stubGlobal('fetch', fetchMock);
    configureApi('https://example.test', 'valid');

    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(api.health()).rejects.toMatchObject({ reason: 'NETWORK' });

    fetchMock.mockResolvedValue(jsonResponse(503, { error: { code: 'AI_UNAVAILABLE' } }));
    await expect(api.aiKeyterms('some text that is long enough')).rejects.toBeInstanceOf(AiUnavailableError);
  });

  it('auto-falls back to the default API URL when none is configured', async () => {
    vi.stubGlobal('fetch', fetchMock);
    configureApi('');
    fetchMock.mockResolvedValue(jsonResponse(200, { status: 'ok' }));
    const res = await api.health();
    expect((res as { status: string }).status).toBe('ok');
    expect(String(fetchMock.mock.calls[0][0])).toContain('medtech-review-hub-api');
  });

  it('friendlyApiError tells the truth for every failure kind', () => {
    expect(friendlyApiError(new AiUnavailableError())).toContain('AI is not configured');
    expect(friendlyApiError(new ApiUnavailableError('x', 'SIGN_IN_REQUIRED'))).toContain('sign in');
    expect(friendlyApiError(new ApiUnavailableError('x', 'NETWORK'))).toContain('connection');
    expect(friendlyApiError(new ApiUnavailableError('HTTP 500', 'HTTP'))).toContain('HTTP 500');
  });
});
