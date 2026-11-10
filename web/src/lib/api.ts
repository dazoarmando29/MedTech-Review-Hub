// Optional backend client (shared/api/API.md). Every call degrades gracefully:
// when no server is configured or it is unreachable, the app keeps working
// fully offline from local state.

export class ApiUnavailableError extends Error {
  constructor(message = 'Backend unavailable') {
    super(message);
    this.name = 'ApiUnavailableError';
  }
}

export class AiUnavailableError extends Error {
  constructor() {
    super('AI features are not configured on this server');
    this.name = 'AiUnavailableError';
  }
}

let baseUrl = '';
let token: string | null = null;

export function configureApi(url: string, accessToken?: string | null): void {
  baseUrl = url.replace(/\/$/, '');
  if (accessToken !== undefined) {
    token = accessToken;
  } else if (typeof window !== 'undefined') {
    try {
      const saved = window.localStorage.getItem('mtrh-auth');
      token = saved ? (JSON.parse(saved) as { token?: string }).token ?? null : null;
    } catch {
      token = null;
    }
  }
}

export function apiConfigured(): boolean {
  return Boolean(baseUrl);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  if (!baseUrl) throw new ApiUnavailableError('No server configured');
  try {
    const res = await fetch(`${baseUrl}/api/v1${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(init?.headers ?? {}),
      },
    });
    if (res.status === 503) {
      const body = await res.json().catch(() => null);
      if (body?.error?.code === 'AI_UNAVAILABLE') throw new AiUnavailableError();
      throw new ApiUnavailableError();
    }
    if (!res.ok) throw new ApiUnavailableError(`HTTP ${res.status}`);
    return (await res.json()) as T;
  } catch (e) {
    if (e instanceof AiUnavailableError || e instanceof ApiUnavailableError) throw e;
    throw new ApiUnavailableError(String(e));
  }
}

export interface AuthResponse {
  user: { id: string; email: string; displayName: string };
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export const api = {
  health: () => request<{ status: string; version: string }>('/health'),
  register: (email: string, password: string, displayName: string) =>
    request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, displayName }),
    }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
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
  aiKeyterms: (text: string) =>
    request<{ result: unknown; citations: unknown[]; warnings: string[] }>('/ai/keyterms', {
      method: 'POST',
      body: JSON.stringify({ text }),
    }),
};
