// Optional backend client (shared/api/API.md). Every call degrades gracefully:
// when no server is configured or it is unreachable, the app keeps working
// fully offline from local state.
//
// Auth model: access tokens expire after 1h. On a 401 the client transparently
// refreshes with the stored refresh token and retries once; if that is not
// possible it raises a clear SIGN_IN_REQUIRED error (never a vague message).

export const DEFAULT_API_URL = 'https://medtech-review-hub-api.dazoarmando.workers.dev';

export type ApiErrorReason = 'SIGN_IN_REQUIRED' | 'NETWORK' | 'HTTP' | 'NO_URL';

export class ApiUnavailableError extends Error {
  reason: ApiErrorReason;
  constructor(message = 'Backend unavailable', reason: ApiErrorReason = 'HTTP') {
    super(message);
    this.name = 'ApiUnavailableError';
    this.reason = reason;
  }
}

export class AiUnavailableError extends Error {
  constructor() {
    super('AI features are not configured on this server');
    this.name = 'AiUnavailableError';
  }
}

interface StoredAuth {
  email?: string;
  token?: string | null;
  refreshToken?: string | null;
}

let baseUrl = '';
let token: string | null = null;
let refreshToken: string | null = null;

function loadAuthFromStorage(): void {
  if (typeof window === 'undefined') return;
  try {
    const saved = window.localStorage.getItem('mtrh-auth');
    const parsed = saved ? (JSON.parse(saved) as StoredAuth) : null;
    token = parsed?.token ?? null;
    refreshToken = parsed?.refreshToken ?? null;
  } catch {
    token = null;
    refreshToken = null;
  }
}

/** Persists tokens (merge with whatever profile info is already stored). */
export function saveAuth(auth: { token: string; refreshToken?: string | null }): void {
  token = auth.token;
  if (auth.refreshToken !== undefined) refreshToken = auth.refreshToken;
  if (typeof window === 'undefined') return;
  try {
    const prev = JSON.parse(window.localStorage.getItem('mtrh-auth') ?? '{}') as StoredAuth;
    window.localStorage.setItem(
      'mtrh-auth',
      JSON.stringify({
        ...prev,
        token: auth.token,
        refreshToken: auth.refreshToken !== undefined ? auth.refreshToken : prev.refreshToken ?? null,
      }),
    );
  } catch {
    /* storage unavailable */
  }
}

export function signOutLocally(): void {
  token = null;
  refreshToken = null;
  try {
    window.localStorage.removeItem('mtrh-auth');
  } catch {
    /* ignore */
  }
}

export function isSignedIn(): boolean {
  if (!token && typeof window !== 'undefined') loadAuthFromStorage();
  return Boolean(token);
}

export function configureApi(url: string, accessToken?: string | null): void {
  baseUrl = (url || '').replace(/\/$/, '');
  if (accessToken !== undefined) {
    token = accessToken;
    if (!accessToken) refreshToken = null;
  } else {
    loadAuthFromStorage();
  }
}

export function apiConfigured(): boolean {
  return Boolean(baseUrl);
}

async function refreshAccess(): Promise<boolean> {
  if (!baseUrl || !refreshToken) return false;
  try {
    const res = await fetch(`${baseUrl}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    const data = (await res.json()) as { accessToken?: string; refreshToken?: string };
    if (!data.accessToken) return false;
    saveAuth({ token: data.accessToken, refreshToken: data.refreshToken ?? refreshToken });
    return true;
  } catch {
    return false;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  if (!baseUrl) baseUrl = DEFAULT_API_URL; // always have a server to talk to
  if (!token) loadAuthFromStorage();

  const doFetch = () =>
    fetch(`${baseUrl}/api/v1${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(init?.headers ?? {}),
      },
    });

  let res: Response;
  try {
    res = await doFetch();
  } catch {
    throw new ApiUnavailableError('Could not reach the server', 'NETWORK');
  }

  // transparent token refresh on expiry
  if (res.status === 401 && (await refreshAccess())) {
    try {
      res = await doFetch();
    } catch {
      throw new ApiUnavailableError('Could not reach the server', 'NETWORK');
    }
  }
  if (res.status === 401) {
    throw new ApiUnavailableError('Sign in required', 'SIGN_IN_REQUIRED');
  }

  if (res.status === 503) {
    const body = await res.json().catch(() => null);
    if (body?.error?.code === 'AI_UNAVAILABLE') throw new AiUnavailableError();
    throw new ApiUnavailableError('Service unavailable', 'HTTP');
  }
  if (!res.ok) throw new ApiUnavailableError(`HTTP ${res.status}`, 'HTTP');
  return (await res.json()) as T;
}

/** Friendly, truthful message for any API failure (used across AI UIs). */
export function friendlyApiError(e: unknown): string {
  if (e instanceof AiUnavailableError) {
    return 'AI is not configured on this server — everything else keeps working offline.';
  }
  if (e instanceof ApiUnavailableError) {
    if (e.reason === 'SIGN_IN_REQUIRED') {
      return 'Please sign in (Login page) to use AI tools — it takes a moment and also syncs your study data.';
    }
    if (e.reason === 'NETWORK') {
      return 'Could not reach the server. Check your connection and try again.';
    }
    return `The server had a problem (${e.message}). Please try again.`;
  }
  return 'Something went wrong. Please try again.';
}

export interface AuthResponse {
  user: { id: string; email: string; displayName: string };
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export const api = {
  health: () => request<{ status: string; version: string }>('/health'),
  register: async (email: string, password: string, displayName: string) => {
    const res = await request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, displayName }),
    });
    saveAuth({ token: res.accessToken, refreshToken: res.refreshToken });
    return res;
  },
  login: async (email: string, password: string) => {
    const res = await request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    saveAuth({ token: res.accessToken, refreshToken: res.refreshToken });
    return res;
  },
  me: () => request<{ user: unknown; profile: unknown }>('/me'),
  analyticsOverview: () => request<Record<string, unknown>>('/analytics/overview'),
  aiSummarize: (text: string, mode: string, pdfId?: string, page?: number) =>
    request<{ result: unknown; citations: unknown[]; warnings: string[] }>('/ai/summarize', {
      method: 'POST',
      body: JSON.stringify({ text, mode, pdfId, page }),
    }),
  aiExplain: (text: string, style: string) =>
    request<{ result: unknown; citations: unknown[]; warnings: string[] }>('/ai/explain', {
      method: 'POST',
      body: JSON.stringify({ text, style }),
    }),
  aiFlashcards: (text: string) =>
    request<{ result: unknown; citations: unknown[]; warnings: string[] }>('/ai/flashcards', {
      method: 'POST',
      body: JSON.stringify({ text }),
    }),
  aiQuiz: (text: string) =>
    request<{ result: unknown; citations: unknown[]; warnings: string[] }>('/ai/quiz', {
      method: 'POST',
      body: JSON.stringify({ text }),
    }),
  aiKeyterms: (text: string) =>
    request<{ result: unknown; citations: unknown[]; warnings: string[] }>('/ai/keyterms', {
      method: 'POST',
      body: JSON.stringify({ text }),
    }),
};
