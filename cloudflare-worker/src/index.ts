// MedTech Review Hub — Cloudflare Worker backend (shared/api/API.md).
// Entry point: routing, CORS, auth middleware, rate limiting, auth routes.

import {
  errorResponse, hashPassword, hashToken, HttpError, json, newRefreshToken,
  readJson, requireFields, uid, validatePassword, verifyPassword,
  verifyAccessToken, signAccessToken, type Env,
} from './auth';
import { handleContent } from './content';
import { runAiTool } from './gemini';
import * as study from './study';

const PREFIX = '/api/v1';

// Best-effort per-isolate rate limiting (swap for Durable Objects / Redis at scale).
const buckets = new Map<string, { count: number; resetAt: number }>();
function rateLimit(key: string, limit: number, windowMs = 60_000): void {
  const now = Date.now();
  const b = buckets.get(key);
  if (!b || b.resetAt < now) {
    buckets.set(key, { count: 1, resetAt: now + windowMs });
    return;
  }
  b.count++;
  if (b.count > limit) throw new HttpError(429, 'RATE_LIMITED', 'Too many requests. Slow down.');
}

function corsHeaders(env: Env, request: Request): Record<string, string> {
  const origin = request.headers.get('Origin') ?? '';
  const allowed = (env.ALLOWED_ORIGINS ?? '*').split(',').map((s) => s.trim());
  const allow = allowed.includes('*') ? (origin || '*') : allowed.includes(origin) ? origin : (allowed[0] ?? '');
  return {
    'Access-Control-Allow-Origin': allow,
    'Access-Control-Allow-Methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type,Authorization',
    'Access-Control-Max-Age': '86400',
    'Vary': 'Origin',
  };
}

async function requireAuth(env: Env, request: Request): Promise<study.UserRow & { claims: { sub: string; role: string } }> {
  const header = request.headers.get('Authorization') ?? '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) throw new HttpError(401, 'UNAUTHORIZED', 'Missing bearer token');
  const secret = env.JWT_SECRET;
  if (!secret) throw new HttpError(500, 'INTERNAL', 'JWT_SECRET is not configured');
  const claims = await verifyAccessToken(secret, token);
  const user = await env.DB.prepare('SELECT * FROM users WHERE id = ?').bind(claims.sub).first<study.UserRow>();
  if (!user) throw new HttpError(401, 'UNAUTHORIZED', 'User no longer exists');
  return { ...user, claims };
}

// ---------- auth routes ----------

async function register(env: Env, request: Request): Promise<Response> {
  const body = await readJson<{ email: string; password: string; displayName?: string }>(request);
  requireFields(body as unknown as Record<string, unknown>, ['email', 'password']);
  const email = String(body.email).trim().toLowerCase();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) throw new HttpError(400, 'VALIDATION_ERROR', 'Invalid email');
  const passwordIssue = validatePassword(body.password);
  if (passwordIssue) throw new HttpError(400, 'VALIDATION_ERROR', passwordIssue);

  const existing = await env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(email).first();
  if (existing) throw new HttpError(409, 'CONFLICT', 'An account with this email already exists');

  const iterations = Number(env.AUTH_PBKDF2_ITERATIONS ?? 100000);
  const id = uid('usr_');
  const createdAt = new Date().toISOString();
  await env.DB.prepare('INSERT INTO users (id, email, password_hash, display_name, role, created_at) VALUES (?, ?, ?, ?, ?, ?)')
    .bind(id, email, await hashPassword(body.password, iterations), (body.displayName ?? email.split('@')[0]).slice(0, 120), 'USER', createdAt)
    .run();
  return issueTokens(env, { id, email, role: 'USER' }, 201);
}

async function login(env: Env, request: Request): Promise<Response> {
  const body = await readJson<{ email: string; password: string }>(request);
  requireFields(body as unknown as Record<string, unknown>, ['email', 'password']);
  const email = String(body.email).trim().toLowerCase();
  const user = await env.DB.prepare('SELECT * FROM users WHERE email = ?').bind(email).first<study.UserRow>();
  // Constant-ish work on failure to avoid trivially distinguishing unknown emails.
  const stored = user?.password_hash ?? 'pbkdf2$100000$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';
  const ok = await verifyPassword(body.password, stored);
  if (!user || !ok) throw new HttpError(401, 'UNAUTHORIZED', 'Invalid email or password');
  return issueTokens(env, { id: user.id, email: user.email, role: user.role }, 200);
}

async function issueTokens(
  env: Env,
  user: { id: string; email: string; role: string },
  status: number,
): Promise<Response> {
  const secret = env.JWT_SECRET;
  if (!secret) throw new HttpError(500, 'INTERNAL', 'JWT_SECRET is not configured');
  const accessToken = await signAccessToken(secret, user);
  const refreshToken = newRefreshToken();
  const expiresAt = new Date(Date.now() + 30 * 86400000).toISOString();
  await env.DB.prepare('INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, revoked) VALUES (?, ?, ?, ?, 0)')
    .bind(uid('rtid_'), user.id, await hashToken(refreshToken), expiresAt)
    .run();
  return json({ user: { id: user.id, email: user.email, displayName: '' }, accessToken, refreshToken, expiresIn: 3600 }, status);
}

async function refresh(env: Env, request: Request): Promise<Response> {
  const body = await readJson<{ refreshToken: string }>(request);
  requireFields(body as unknown as Record<string, unknown>, ['refreshToken']);
  const hash = await hashToken(body.refreshToken);
  const row = await env.DB.prepare(
    'SELECT rt.id AS row_id, rt.expires_at, u.id, u.email, u.role FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id WHERE rt.token_hash = ? AND rt.revoked = 0',
  )
    .bind(hash)
    .first<{ row_id: string; expires_at: string; id: string; email: string; role: string }>();
  if (!row) throw new HttpError(401, 'UNAUTHORIZED', 'Invalid refresh token');
  if (new Date(row.expires_at).getTime() < Date.now()) throw new HttpError(401, 'UNAUTHORIZED', 'Refresh token expired');
  // rotation: revoke old token
  await env.DB.prepare('UPDATE refresh_tokens SET revoked = 1 WHERE id = ?').bind(row.row_id).run();
  return issueTokens(env, { id: row.id, email: row.email, role: row.role }, 200);
}

async function logout(env: Env, request: Request): Promise<Response> {
  const body = await readJson<{ refreshToken?: string }>(request).catch(() => ({ refreshToken: undefined }));
  if (body.refreshToken) {
    await env.DB.prepare('UPDATE refresh_tokens SET revoked = 1 WHERE token_hash = ?').bind(await hashToken(body.refreshToken)).run();
  }
  return json({ ok: true });
}

async function deleteAccount(env: Env, userId: string): Promise<Response> {
  const tables = [
    ['refresh_tokens', 'user_id'], ['profiles', 'user_id'], ['plans', 'user_id'],
    ['study_sessions', 'user_id'], ['flashcards', 'user_id'], ['quiz_attempts', 'user_id'],
    ['habits', 'user_id'], ['habit_logs', 'user_id'], ['diary', 'user_id'],
    ['pdf_docs', 'user_id'], ['pdf_annotations', 'user_id'], ['ai_usage', 'user_id'],
    ['users', 'id'],
  ] as const;
  for (const [table, column] of tables) {
    await env.DB.prepare(`DELETE FROM ${table} WHERE ${column} = ?`).bind(userId).run();
  }
  return json({ deleted: true });
}

// ---------- router ----------

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const cors = corsHeaders(env, request);
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });

    const url = new URL(request.url);
    let path = url.pathname;
    if (!path.startsWith(PREFIX) && path !== '/health') {
      return withCors(new Response('MedTech Review Hub API. See /api/v1/health.', { status: 404 }), cors);
    }
    if (path.startsWith(PREFIX)) path = path.slice(PREFIX.length);
    const method = request.method;

    try {
      const ip = request.headers.get('CF-ConnectingIP') ?? 'unknown';
      rateLimit(`${ip}:general`, 120);

      // --- public ---
      if (path === '/health') {
        const dbOk = await env.DB.prepare('SELECT 1 AS ok').first().then(() => 'ok').catch(() => 'error');
        return withCors(json({ status: 'ok', version: '1.0.0', database: dbOk, runtime: 'cloudflare-worker' }), cors);
      }
      if (method === 'POST' && path === '/auth/register') return withCors(await register(env, request), cors);
      if (method === 'POST' && path === '/auth/login') return withCors(await login(env, request), cors);
      if (method === 'POST' && path === '/auth/refresh') return withCors(await refresh(env, request), cors);
      if (method === 'POST' && path === '/auth/logout') return withCors(await logout(env, request), cors);
      if (path.startsWith('/content')) {
        const res = await handleContent(path, env);
        if (res) return withCors(res, cors);
      }

      // --- authenticated ---
      const user = await requireAuth(env, request);
      rateLimit(`${user.id}:general`, 240);

      if (method === 'GET' && path === '/me') return withCors(await study.getMe(env, user.id), cors);
      if (method === 'PATCH' && path === '/me') return withCors(await study.patchMe(env, user.id, request), cors);
      if (method === 'PUT' && path === '/me/profile') return withCors(await study.putProfile(env, user.id, request), cors);
      if (method === 'GET' && path === '/me/export') return withCors(await study.exportUserData(env, user.id), cors);
      if (method === 'DELETE' && path === '/auth/me') return withCors(await deleteAccount(env, user.id), cors);

      if (method === 'GET' && path === '/plan') return withCors(await study.getPlan(env, user.id), cors);
      if (method === 'PUT' && path === '/plan') return withCors(await study.putPlan(env, user.id, request), cors);
      if (method === 'POST' && path === '/plan/regenerate') return withCors(await study.regeneratePlan(env, user.id), cors);
      if (method === 'POST' && path === '/sessions') return withCors(await study.postSession(env, user.id, request), cors);
      if (method === 'GET' && path === '/sessions') return withCors(await study.listSessions(env, user.id), cors);
      if (method === 'GET' && path === '/analytics/overview') return withCors(await study.analyticsOverview(env, user.id), cors);

      if (method === 'GET' && path === '/flashcards') return withCors(await study.listCards(env, user.id, url), cors);
      if (method === 'POST' && path === '/flashcards') return withCors(await study.postCard(env, user.id, request), cors);
      if (method === 'POST' && path === '/flashcards/import') return withCors(await study.importCards(env, user.id, request), cors);
      if (method === 'GET' && path === '/flashcards/export') return withCors(await study.exportCards(env, user.id), cors);
      if (method === 'GET' && path === '/flashcards/queue') return withCors(await study.cardQueue(env, user.id), cors);
      const cardMatch = path.match(/^\/flashcards\/([^/]+)(\/review)?$/);
      if (cardMatch) {
        const id = decodeURIComponent(cardMatch[1]);
        if (cardMatch[2] && method === 'POST') return withCors(await study.reviewCard(env, user.id, id, request), cors);
        if (!cardMatch[2] && method === 'PATCH') return withCors(await study.patchCard(env, user.id, id, request), cors);
        if (!cardMatch[2] && method === 'DELETE') return withCors(await study.deleteCard(env, user.id, id), cors);
      }

      if (method === 'POST' && path === '/quizzes/start') return withCors(await study.startQuiz(env, user.id, request), cors);
      if (method === 'GET' && path === '/quizzes') return withCors(await study.listQuizzes(env, user.id), cors);
      const quizMatch = path.match(/^\/quizzes\/([^/]+)(\/answer|\/finish)?$/);
      if (quizMatch) {
        const id = decodeURIComponent(quizMatch[1]);
        if (quizMatch[2] === '/answer' && method === 'POST') return withCors(await study.answerQuiz(env, user.id, id, request), cors);
        if (quizMatch[2] === '/finish' && method === 'POST') return withCors(await study.finishQuiz(env, user.id, id), cors);
        if (!quizMatch[2] && method === 'GET') return withCors(await study.getQuiz(env, user.id, id), cors);
      }
      const reportMatch = path.match(/^\/questions\/([^/]+)\/report$/);
      if (reportMatch && method === 'POST') {
        return withCors(await study.reportQuestion(env, user.id, decodeURIComponent(reportMatch[1]), request), cors);
      }

      if (method === 'GET' && path === '/habits') return withCors(await study.listHabits(env, user.id), cors);
      if (method === 'POST' && path === '/habits') return withCors(await study.postHabit(env, user.id, request), cors);
      if (method === 'POST' && path === '/habits/log') return withCors(await study.logHabit(env, user.id, request), cors);
      const habitMatch = path.match(/^\/habits\/([^/]+)$/);
      if (habitMatch && method === 'DELETE') return withCors(await study.deleteHabit(env, user.id, decodeURIComponent(habitMatch[1])), cors);
      if (method === 'GET' && path === '/diary') return withCors(await study.listDiary(env, user.id), cors);
      if (method === 'POST' && path === '/diary') return withCors(await study.postDiary(env, user.id, request), cors);
      if (method === 'GET' && path === '/streak') return withCors(await study.getStreak(env, user.id), cors);

      if (method === 'GET' && path === '/pdfs') return withCors(await study.listPdfs(env, user.id), cors);
      if (method === 'POST' && path === '/pdfs') return withCors(await study.postPdfMeta(env, user.id, request), cors);
      const pdfMatch = path.match(/^\/pdfs\/([^/]+)(\/annotations)?$/);
      if (pdfMatch) {
        const id = decodeURIComponent(pdfMatch[1]);
        if (pdfMatch[2] && method === 'GET') return withCors(await study.getAnnotations(env, user.id, id), cors);
        if (pdfMatch[2] && method === 'PUT') return withCors(await study.putAnnotations(env, user.id, id, request), cors);
        if (!pdfMatch[2] && method === 'DELETE') return withCors(await study.deletePdf(env, user.id, id), cors);
      }

      if (path.startsWith('/ai/') && method === 'POST') {
        rateLimit(`${user.id}:ai`, 20);
        const tool = path.slice('/ai/'.length) as Parameters<typeof runAiTool>[2];
        const valid = ['summarize', 'explain', 'flashcards', 'quiz', 'keyterms', 'outline', 'diary-summary'];
        if (!valid.includes(tool)) throw new HttpError(404, 'NOT_FOUND', `Unknown AI tool: ${tool}`);
        return withCors(await runAiTool(env, user.id, tool, await readJson(request)), cors);
      }

      throw new HttpError(404, 'NOT_FOUND', `No route: ${method} ${PREFIX}${path}`);
    } catch (err) {
      return withCors(errorResponse(err), cors);
    }
  },
};

function withCors(res: Response, cors: Record<string, string>): Response {
  const headers = new Headers(res.headers);
  for (const [k, v] of Object.entries(cors)) headers.set(k, v);
  return new Response(res.body, { status: res.status, statusText: res.statusText, headers });
}
