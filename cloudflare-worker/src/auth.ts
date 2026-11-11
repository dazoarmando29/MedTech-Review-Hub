// Auth primitives for the Cloudflare Worker backend:
// PBKDF2-SHA256 password hashing and HS256 JWTs via Web Crypto.

export interface Env {
  DB: D1Database;
  JWT_SECRET?: string;
  GEMINI_API_KEY?: string;
  GEMINI_MODEL?: string;
  AUTH_PBKDF2_ITERATIONS?: string;
  AI_DAILY_TOKEN_BUDGET?: string;
  AI_DAILY_TOKEN_BUDGET_ANON?: string;
  ALLOWED_ORIGINS?: string;
  PDFS?: R2Bucket;
}

export class HttpError extends Error {
  status: number;
  code: string;
  details?: unknown;
  constructor(status: number, code: string, message: string, details?: unknown) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function uid(prefix = ''): string {
  const b = crypto.getRandomValues(new Uint8Array(16));
  const hex = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
  return `${prefix}${hex}`;
}

function b64url(bytes: ArrayBuffer | Uint8Array): string {
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = '';
  for (const b of arr) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function b64urlEncode(text: string): string {
  return b64url(new TextEncoder().encode(text));
}

function b64urlDecode(text: string): string {
  const pad = text.replace(/-/g, '+').replace(/_/g, '/');
  const bin = atob(pad + '='.repeat((4 - (pad.length % 4)) % 4));
  return new TextDecoder().decode(Uint8Array.from(bin, (c) => c.charCodeAt(0)));
}

// ---------- passwords ----------

const enc = new TextEncoder();

export async function hashPassword(password: string, iterations: number): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', hash: 'SHA-256', salt, iterations },
    key,
    256,
  );
  return `pbkdf2$${iterations}$${b64url(salt)}$${b64url(bits)}`;
}

export async function verifyPassword(password: string, stored: string): Promise<boolean> {
  const [algo, iterStr, saltB64, hashB64] = stored.split('$');
  if (algo !== 'pbkdf2') return false;
  const iterations = Number(iterStr);
  const salt = Uint8Array.from(atob(saltB64.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations }, key, 256);
  return b64url(bits) === hashB64;
}

const COMMON_PASSWORDS = new Set([
  'password', 'password1', 'password123', '12345678', '123456789', '1234567890',
  'qwertyuiop', 'letmein123', 'iloveyou1', 'admin12345', 'welcome123',
]);

export function validatePassword(password: string): string | null {
  if (password.length < 10) return 'Password must be at least 10 characters.';
  if (COMMON_PASSWORDS.has(password.toLowerCase())) return 'Password is too common.';
  return null;
}

// ---------- JWT (HS256) ----------

async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign', 'verify']);
}

export interface TokenClaims {
  sub: string;
  email: string;
  role: string;
  exp: number;
  typ: 'access';
}

export async function signAccessToken(
  secret: string,
  user: { id: string; email: string; role: string },
  ttlSeconds = 3600,
): Promise<string> {
  const header = b64urlEncode(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = b64urlEncode(
    JSON.stringify({
      sub: user.id,
      email: user.email,
      role: user.role,
      exp: Math.floor(Date.now() / 1000) + ttlSeconds,
      typ: 'access',
    }),
  );
  const key = await hmacKey(secret);
  const sig = await crypto.subtle.sign('HMAC', key, enc.encode(`${header}.${payload}`));
  return `${header}.${payload}.${b64url(sig)}`;
}

export async function verifyAccessToken(secret: string, token: string): Promise<TokenClaims> {
  const parts = token.split('.');
  if (parts.length !== 3) throw new HttpError(401, 'UNAUTHORIZED', 'Malformed token');
  const key = await hmacKey(secret);
  const valid = await crypto.subtle.verify('HMAC', key, Uint8Array.from(atob(parts[2].replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0)), enc.encode(`${parts[0]}.${parts[1]}`));
  if (!valid) throw new HttpError(401, 'UNAUTHORIZED', 'Invalid token signature');
  const claims = JSON.parse(b64urlDecode(parts[1])) as TokenClaims;
  if (claims.exp * 1000 < Date.now()) throw new HttpError(401, 'UNAUTHORIZED', 'Token expired');
  return claims;
}

export function newRefreshToken(): string {
  return uid('rt_');
}

export async function hashToken(token: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', enc.encode(token));
  return b64url(digest);
}

// ---------- request helpers ----------

export async function readJson<T>(request: Request): Promise<T> {
  try {
    return (await request.json()) as T;
  } catch {
    throw new HttpError(400, 'VALIDATION_ERROR', 'Invalid JSON body');
  }
}

export function requireFields<T extends Record<string, unknown>>(body: T, fields: string[]): void {
  for (const f of fields) {
    const v = body[f];
    if (v === undefined || v === null || (typeof v === 'string' && v.trim() === '')) {
      throw new HttpError(400, 'VALIDATION_ERROR', `Missing field: ${f}`);
    }
  }
}

export function errorResponse(err: unknown): Response {
  const e = err instanceof HttpError ? err : null;
  const status = e ? e.status : 500;
  const body = {
    error: {
      code: e ? e.code : 'INTERNAL',
      message: e ? e.message : 'Internal server error',
      details: e?.details ?? {},
    },
  };
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

export function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
}
