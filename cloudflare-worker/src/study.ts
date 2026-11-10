// Authenticated study data routes: profile, plan, sessions, analytics,
// flashcards (FSRS schedules), quiz attempts, habits, diary, PDF metadata and
// annotations, content issue reports. See shared/api/API.md.

import { HttpError, json, readJson, requireFields, uid, type Env } from './auth';

export interface UserRow {
  id: string;
  email: string;
  password_hash: string;
  display_name: string;
  role: string;
  created_at: string;
}

function nowIso(): string {
  return new Date().toISOString();
}

// ---------- users / profile ----------

export async function getMe(env: Env, userId: string): Promise<Response> {
  const user = await env.DB.prepare('SELECT id, email, display_name, role, created_at FROM users WHERE id = ?')
    .bind(userId)
    .first();
  if (!user) throw new HttpError(404, 'NOT_FOUND', 'User not found');
  const profile = await env.DB.prepare('SELECT json FROM profiles WHERE user_id = ?').bind(userId).first();
  return json({
    user: {
      id: user.id,
      email: user.email,
      displayName: user.display_name,
      role: user.role,
      createdAt: user.created_at,
    },
    profile: profile ? JSON.parse(profile.json as string) : null,
  });
}

export async function patchMe(env: Env, userId: string, request: Request): Promise<Response> {
  const body = await readJson<Record<string, unknown>>(request);
  const fields: string[] = [];
  const values: unknown[] = [];
  if (typeof body.displayName === 'string') {
    fields.push('display_name = ?');
    values.push(body.displayName.slice(0, 120));
  }
  if (fields.length) {
    values.push(userId);
    await env.DB.prepare(`UPDATE users SET ${fields.join(', ')} WHERE id = ?`).bind(...values).run();
  }
  return getMe(env, userId);
}

export async function putProfile(env: Env, userId: string, request: Request): Promise<Response> {
  const body = await readJson<Record<string, unknown>>(request);
  await env.DB.prepare(
    `INSERT INTO profiles (user_id, json, updated_at) VALUES (?, ?, ?)
     ON CONFLICT(user_id) DO UPDATE SET json = excluded.json, updated_at = excluded.updated_at`,
  )
    .bind(userId, JSON.stringify(body), nowIso())
    .run();
  return json({ profile: body });
}

export async function exportUserData(env: Env, userId: string): Promise<Response> {
  const user = await env.DB.prepare('SELECT id, email, display_name, created_at FROM users WHERE id = ?').bind(userId).first();
  const profile = await env.DB.prepare('SELECT json FROM profiles WHERE user_id = ?').bind(userId).first();
  const plan = await env.DB.prepare('SELECT json FROM plans WHERE user_id = ?').bind(userId).first();
  const sessions = await env.DB.prepare('SELECT * FROM study_sessions WHERE user_id = ?').bind(userId).all();
  const cards = await env.DB.prepare('SELECT * FROM flashcards WHERE user_id = ?').bind(userId).all();
  const attempts = await env.DB.prepare('SELECT * FROM quiz_attempts WHERE user_id = ?').bind(userId).all();
  const habits = await env.DB.prepare('SELECT * FROM habits WHERE user_id = ?').bind(userId).all();
  const habitLogs = await env.DB.prepare('SELECT * FROM habit_logs WHERE user_id = ?').bind(userId).all();
  const diary = await env.DB.prepare('SELECT * FROM diary WHERE user_id = ?').bind(userId).all();
  const pdfs = await env.DB.prepare('SELECT * FROM pdf_docs WHERE user_id = ?').bind(userId).all();
  const annotations = await env.DB.prepare('SELECT * FROM pdf_annotations WHERE user_id = ?').bind(userId).all();

  return json({
    exportedAt: nowIso(),
    user,
    profile: profile ? JSON.parse(profile.json as string) : null,
    plan: plan ? JSON.parse(plan.json as string) : null,
    sessions: sessions.results,
    flashcards: cards.results.map((c) => JSON.parse(c.json as string)),
    quizAttempts: attempts.results.map((a) => JSON.parse(a.json as string)),
    habits: habits.results.map((h) => JSON.parse(h.json as string)),
    habitLogs: habitLogs.results,
    diary: diary.results.map((d) => JSON.parse(d.json as string)),
    pdfs: pdfs.results,
    pdfAnnotations: annotations.results.map((a) => JSON.parse(a.json as string)),
  });
}

// ---------- plan / sessions / analytics ----------

const ASCPI_WEIGHTS: Record<string, number> = {
  'blood-banking': 0.09, 'clinical-chemistry': 0.17, hematology: 0.16,
  microbiology: 0.18, immunology: 0.1, urinalysis: 0.1, 'lab-operations': 0.12,
};
const PH_WEIGHTS: Record<string, number> = {
  'blood-banking': 0.1, 'clinical-chemistry': 0.15, hematology: 0.15,
  microbiology: 0.2, immunology: 0.1, urinalysis: 0.15, 'lab-operations': 0.15,
};

export async function getPlan(env: Env, userId: string): Promise<Response> {
  const row = await env.DB.prepare('SELECT json FROM plans WHERE user_id = ?').bind(userId).first();
  return json(row ? JSON.parse(row.json as string) : { days: [] });
}

export async function putPlan(env: Env, userId: string, request: Request): Promise<Response> {
  const body = await readJson<Record<string, unknown>>(request);
  await env.DB.prepare(
    `INSERT INTO plans (user_id, json, updated_at) VALUES (?, ?, ?)
     ON CONFLICT(user_id) DO UPDATE SET json = excluded.json, updated_at = excluded.updated_at`,
  )
    .bind(userId, JSON.stringify(body), nowIso())
    .run();
  return json({ plan: body });
}

export async function regeneratePlan(env: Env, userId: string): Promise<Response> {
  const profileRow = await env.DB.prepare('SELECT json FROM profiles WHERE user_id = ?').bind(userId).first();
  const profile = profileRow ? JSON.parse(profileRow.json as string) : {};
  const examTarget = profile.examTarget ?? 'MLS_ASCPI';
  const weakest: string[] = profile.weakestSubjects ?? [];
  const minutesPerDay: number = profile.minutesPerDay ?? 120;
  const base = { ...(examTarget === 'PH_BOARD' ? PH_WEIGHTS : ASCPI_WEIGHTS) };
  const total = Object.values(base).reduce((a, b) => a + b, 0);
  const weights: Record<string, number> = {};
  for (const k of Object.keys(base)) weights[k] = base[k] / total;
  const ranked = Object.entries(weights).sort((a, b) => b[1] - a[1]);

  const contentRows = await env.DB.prepare("SELECT id, subject_id, json FROM content WHERE kind = 'lesson'").all();
  const lessonsBySubject = new Map<string, { id: string; topicId: string }[]>();
  for (const r of contentRows.results) {
    const l = JSON.parse(r.json as string);
    const list = lessonsBySubject.get(l.subjectId) ?? [];
    list.push({ id: l.id, topicId: l.topicId });
    lessonsBySubject.set(l.subjectId, list);
  }

  const kinds = ['READ', 'FLASHCARDS', 'QUIZ', 'REVIEW_WRONG'] as const;
  const days: { date: string; items: unknown[] }[] = [];
  for (let d = 0; d < 28; d++) {
    const date = new Date(Date.now() + d * 86400000);
    const dateKey = date.toISOString().slice(0, 10);
    const slots = minutesPerDay >= 90 ? 4 : minutesPerDay >= 50 ? 3 : 2;
    const items: unknown[] = [];
    for (let i = 0; i < slots; i++) {
      const [subjectId, w] = ranked[(d * slots + i) % ranked.length];
      const lessons = lessonsBySubject.get(subjectId) ?? [];
      const lesson = lessons.length ? lessons[(d + i) % lessons.length] : null;
      const boost = weakest.includes(subjectId) ? 1.5 : 1;
      items.push({
        id: `plan-${dateKey}-${i}`,
        type: kinds[(d + i) % kinds.length],
        subjectId,
        topicId: lesson?.topicId ?? null,
        lessonId: kinds[(d + i) % kinds.length] === 'READ' ? lesson?.id ?? null : null,
        targetMinutes: Math.max(10, Math.round((minutesPerDay / slots) * w * 7 * boost)),
        completed: false,
      });
    }
    days.push({ date: dateKey, items });
  }

  const plan = { generatedAt: nowIso(), examTarget, days };
  await env.DB.prepare(
    `INSERT INTO plans (user_id, json, updated_at) VALUES (?, ?, ?)
     ON CONFLICT(user_id) DO UPDATE SET json = excluded.json, updated_at = excluded.updated_at`,
  )
    .bind(userId, JSON.stringify(plan), nowIso())
    .run();
  return json({ plan });
}

export async function postSession(env: Env, userId: string, request: Request): Promise<Response> {
  const body = await readJson<Record<string, unknown>>(request);
  const id = uid('sess_');
  await env.DB.prepare(
    'INSERT INTO study_sessions (id, user_id, started_at, kind, subject_id, minutes, items) VALUES (?, ?, ?, ?, ?, ?, ?)',
  )
    .bind(
      id, userId,
      (body.startedAt as string) ?? nowIso(),
      (body.kind as string) ?? 'READ',
      (body.subjectId as string) ?? null,
      Number(body.minutes ?? 0),
      Number(body.itemsCompleted ?? body.items ?? 0),
    )
    .run();
  return json({ id }, 201);
}

export async function listSessions(env: Env, userId: string): Promise<Response> {
  const rows = await env.DB.prepare('SELECT * FROM study_sessions WHERE user_id = ? ORDER BY started_at DESC LIMIT 500')
    .bind(userId)
    .all();
  return json(rows.results);
}

export async function analyticsOverview(env: Env, userId: string): Promise<Response> {
  const sessions = await env.DB.prepare('SELECT started_at, minutes FROM study_sessions WHERE user_id = ?')
    .bind(userId)
    .all<{ started_at: string; minutes: number }>();
  const attempts = await env.DB.prepare('SELECT json FROM quiz_attempts WHERE user_id = ? AND finished = 1')
    .bind(userId)
    .all<{ json: string }>();
  const cards = await env.DB.prepare('SELECT json FROM flashcards WHERE user_id = ?')
    .bind(userId)
    .all<{ json: string }>();

  const dayKey = (iso: string) => iso.slice(0, 10);
  const minutesByDay = new Map<string, number>();
  let totalMinutes = 0;
  for (const s of sessions.results) {
    totalMinutes += s.minutes;
    minutesByDay.set(dayKey(s.started_at), (minutesByDay.get(dayKey(s.started_at)) ?? 0) + s.minutes);
  }

  // streak
  const days = new Set(minutesByDay.keys());
  const cursor = new Date();
  if (!days.has(cursor.toISOString().slice(0, 10))) cursor.setUTCDate(cursor.getUTCDate() - 1);
  let streak = 0;
  while (days.has(cursor.toISOString().slice(0, 10))) {
    streak++;
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }

  const today = new Date().toISOString().slice(0, 10);
  const weeklyCalendar = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(Date.now() - i * 86400000).toISOString().slice(0, 10);
    weeklyCalendar.push({ date: d, minutes: minutesByDay.get(d) ?? 0 });
  }

  const questionIdsToday = new Set<string>();
  let correct = 0, total = 0;
  const bySubject = new Map<string, { correct: number; total: number }>();
  for (const a of attempts.results) {
    const attempt = JSON.parse(a.json);
    for (const ans of attempt.answers ?? []) {
      total++;
      if (ans.correct) correct++;
      if ((attempt.finishedAt ?? attempt.startedAt ?? '').slice(0, 10) === today) questionIdsToday.add(ans.questionId);
    }
  }

  let due = 0;
  for (const c of cards.results) {
    const card = JSON.parse(c.json);
    if (card.schedule && card.schedule.state !== 'NEW' && new Date(card.schedule.dueAt).getTime() <= Date.now()) due++;
  }

  return json({
    streakDays: streak,
    totalMinutes,
    minutesToday: minutesByDay.get(today) ?? 0,
    flashcardsDueToday: due,
    questionsAnsweredToday: questionIdsToday.size,
    recentQuizAccuracy: total ? correct / total : null,
    examCountdownDays: null,
    weeklyCalendar,
    weakTopics: [...bySubject.entries()].map(([subjectId, v]) => ({ subjectId, ...v })),
  });
}

// ---------- flashcards ----------

export async function listCards(env: Env, userId: string, url: URL): Promise<Response> {
  const rows = await env.DB.prepare('SELECT json FROM flashcards WHERE user_id = ?').bind(userId).all<{ json: string }>();
  let cards = rows.results.map((r) => JSON.parse(r.json));
  const deck = url.searchParams.get('deck');
  const tag = url.searchParams.get('tag');
  const due = url.searchParams.get('due');
  if (deck) cards = cards.filter((c) => c.deckId === deck);
  if (tag) cards = cards.filter((c) => (c.tags ?? []).includes(tag));
  if (due === 'true') {
    cards = cards.filter((c) => c.schedule && c.schedule.state !== 'NEW' && new Date(c.schedule.dueAt).getTime() <= Date.now());
  }
  return json(cards);
}

async function upsertCard(env: Env, userId: string, card: Record<string, unknown>): Promise<void> {
  await env.DB.prepare(
    `INSERT INTO flashcards (id, user_id, deck_id, json, updated_at) VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(id) DO UPDATE SET json = excluded.json, updated_at = excluded.updated_at`,
  )
    .bind(card.id as string, userId, (card.deckId as string) ?? null, JSON.stringify(card), nowIso())
    .run();
}

export async function postCard(env: Env, userId: string, request: Request): Promise<Response> {
  const body = await readJson<Record<string, unknown>>(request);
  requireFields(body, ['front', 'back']);
  const card = {
    id: (body.id as string) ?? uid('fc_'),
    deckId: body.deckId ?? 'user-created',
    front: body.front,
    back: body.back,
    type: body.type ?? 'BASIC',
    tags: body.tags ?? [],
    subjectId: body.subjectId ?? null,
    topicId: body.topicId ?? null,
    difficulty: body.difficulty ?? 2,
    note: body.note ?? '',
    favorite: Boolean(body.favorite),
    imageRef: body.imageRef ?? null,
    source: body.source ?? { kind: 'MANUAL' },
    schedule: body.schedule ?? {
      state: 'NEW', stability: 0, difficulty: 0,
      dueAt: nowIso(), lastReviewAt: null, reps: 0, lapses: 0,
    },
    history: body.history ?? [],
  };
  await upsertCard(env, userId, card);
  return json({ card }, 201);
}

export async function patchCard(env: Env, userId: string, id: string, request: Request): Promise<Response> {
  const existing = await env.DB.prepare('SELECT json FROM flashcards WHERE id = ? AND user_id = ?')
    .bind(id, userId)
    .first<{ json: string }>();
  if (!existing) throw new HttpError(404, 'NOT_FOUND', 'Card not found');
  const merged = { ...JSON.parse(existing.json), ...(await readJson<Record<string, unknown>>(request)), id };
  await upsertCard(env, userId, merged);
  return json({ card: merged });
}

export async function deleteCard(env: Env, userId: string, id: string): Promise<Response> {
  await env.DB.prepare('DELETE FROM flashcards WHERE id = ? AND user_id = ?').bind(id, userId).run();
  return json({ deleted: true });
}

export async function reviewCard(env: Env, userId: string, id: string, request: Request): Promise<Response> {
  const existing = await env.DB.prepare('SELECT json FROM flashcards WHERE id = ? AND user_id = ?')
    .bind(id, userId)
    .first<{ json: string }>();
  if (!existing) throw new HttpError(404, 'NOT_FOUND', 'Card not found');
  const body = await readJson<{ rating: string; schedule?: Record<string, unknown>; reviewedAt?: string }>(request);
  const card = JSON.parse(existing.json);
  // The client computes the FSRS schedule (shared/FSRS.md); the server stores it.
  if (body.schedule) {
    card.schedule = body.schedule;
    card.history = [...(card.history ?? []), {
      at: body.reviewedAt ?? nowIso(),
      rating: body.rating,
      intervalDays: body.schedule.intervalDays ?? null,
    }];
    await upsertCard(env, userId, card);
    return json({ schedule: card.schedule });
  }
  // Server-side fallback when the client omits a computed schedule.
  const minutes = { AGAIN: 10, HARD: 1440, GOOD: 4320, EASY: 10080 }[body.rating] ?? 1440;
  card.schedule = {
    ...(card.schedule ?? {}),
    dueAt: new Date(Date.now() + minutes * 60000).toISOString(),
    lastReviewAt: body.reviewedAt ?? nowIso(),
    reps: (card.schedule?.reps ?? 0) + 1,
    lapses: (card.schedule?.lapses ?? 0) + (body.rating === 'AGAIN' ? 1 : 0),
    state: body.rating === 'AGAIN' ? 'RELEARNING' : (card.schedule?.state ?? 'LEARNING'),
  };
  await upsertCard(env, userId, card);
  return json({ schedule: card.schedule });
}

export async function cardQueue(env: Env, userId: string): Promise<Response> {
  const rows = await env.DB.prepare('SELECT json FROM flashcards WHERE user_id = ?').bind(userId).all<{ json: string }>();
  const now = Date.now();
  const due: unknown[] = [], fresh: unknown[] = [], learning: unknown[] = [];
  for (const r of rows.results) {
    const c = JSON.parse(r.json);
    if (c.schedule?.state === 'NEW') fresh.push(c);
    else if (new Date(c.schedule?.dueAt ?? 0).getTime() <= now) due.push(c);
    else if (c.schedule?.state === 'LEARNING' || c.schedule?.state === 'RELEARNING') learning.push(c);
  }
  return json({ due, new: fresh, learning });
}

export async function exportCards(env: Env, userId: string): Promise<Response> {
  const rows = await env.DB.prepare('SELECT json FROM flashcards WHERE user_id = ?').bind(userId).all<{ json: string }>();
  const cards = rows.results.map((r) => JSON.parse(r.json));
  const header = 'id,front,back,type,tags,subjectId,dueAt,state,stability,difficulty\n';
  const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const csv = header + cards.map((c) => [
    c.id, c.front, c.back, c.type, (c.tags ?? []).join(' '), c.subjectId,
    c.schedule?.dueAt, c.schedule?.state, c.schedule?.stability, c.schedule?.difficulty,
  ].map(esc).join(',')).join('\n');
  return new Response(csv, { headers: { 'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="medtech-flashcards.csv"' } });
}

export async function importCards(env: Env, userId: string, request: Request): Promise<Response> {
  const body = await readJson<{ cards: Record<string, unknown>[] }>(request);
  const cards = body.cards ?? [];
  if (!Array.isArray(cards) || cards.length === 0) throw new HttpError(400, 'VALIDATION_ERROR', 'No cards provided');
  if (cards.length > 5000) throw new HttpError(413, 'PAYLOAD_TOO_LARGE', 'Import limit is 5000 cards');
  for (const c of cards) {
    await upsertCard(env, userId, {
      ...c,
      id: (c.id as string) ?? uid('fc_'),
      schedule: c.schedule ?? { state: 'NEW', stability: 0, difficulty: 0, dueAt: nowIso(), lastReviewAt: null, reps: 0, lapses: 0 },
      history: c.history ?? [],
    });
  }
  return json({ imported: cards.length }, 201);
}

// ---------- quizzes ----------

export async function startQuiz(env: Env, userId: string, request: Request): Promise<Response> {
  const body = await readJson<Record<string, unknown>>(request);
  const attempt = {
    id: (body.id as string) ?? uid('qa_'),
    startedAt: nowIso(),
    finishedAt: null,
    mode: body.mode ?? 'STUDY',
    filters: body.filters ?? { label: 'custom' },
    questionIds: body.questionIds ?? [],
    answers: [],
    timeLimitSeconds: body.timeLimitSeconds ?? null,
    finished: false,
  };
  await env.DB.prepare('INSERT INTO quiz_attempts (id, user_id, json, finished, updated_at) VALUES (?, ?, ?, 0, ?)')
    .bind(attempt.id, userId, JSON.stringify(attempt), nowIso())
    .run();
  return json({ attempt }, 201);
}

async function loadAttempt(env: Env, userId: string, id: string) {
  const row = await env.DB.prepare('SELECT json FROM quiz_attempts WHERE id = ? AND user_id = ?')
    .bind(id, userId)
    .first<{ json: string }>();
  if (!row) throw new HttpError(404, 'NOT_FOUND', 'Quiz attempt not found');
  return JSON.parse(row.json);
}

async function saveAttempt(env: Env, userId: string, attempt: Record<string, unknown>): Promise<void> {
  await env.DB.prepare('UPDATE quiz_attempts SET json = ?, finished = ?, updated_at = ? WHERE id = ? AND user_id = ?')
    .bind(JSON.stringify(attempt), attempt.finished ? 1 : 0, nowIso(), attempt.id, userId)
    .run();
}

export async function answerQuiz(env: Env, userId: string, id: string, request: Request): Promise<Response> {
  const body = await readJson<{ questionId: string; chosenId: string; correct: boolean; seconds?: number }>(request);
  const attempt = await loadAttempt(env, userId, id);
  attempt.answers = [...(attempt.answers ?? []), {
    questionId: body.questionId,
    chosenId: body.chosenId,
    correct: Boolean(body.correct),
    seconds: body.seconds ?? 0,
  }];
  await saveAttempt(env, userId, attempt);
  return json({ attempt });
}

export async function finishQuiz(env: Env, userId: string, id: string): Promise<Response> {
  const attempt = await loadAttempt(env, userId, id);
  attempt.finished = true;
  attempt.finishedAt = nowIso();
  await saveAttempt(env, userId, attempt);

  const answers = attempt.answers ?? [];
  const correct = answers.filter((a: { correct: boolean }) => a.correct).length;
  const total = answers.length;

  // by-subject breakdown using the seeded question bank
  const qRows = await env.DB.prepare("SELECT id, json FROM content WHERE kind = 'question'").all<{ id: string; json: string }>();
  const subjectOf = new Map<string, string>();
  for (const r of qRows.results) {
    const q = JSON.parse(r.json);
    if (q.id) subjectOf.set(q.id, q.subjectId);
  }
  const bySubject = new Map<string, { correct: number; total: number }>();
  for (const a of answers) {
    const subjectId = subjectOf.get(a.questionId) ?? 'unknown';
    const e = bySubject.get(subjectId) ?? { correct: 0, total: 0 };
    e.total++;
    if (a.correct) e.correct++;
    bySubject.set(subjectId, e);
  }

  return json({
    attempt,
    result: {
      score: total ? correct / total : 0,
      correct,
      total,
      timeSeconds: answers.reduce((s: number, a: { seconds?: number }) => s + (a.seconds ?? 0), 0),
      bySubject: [...bySubject.entries()].map(([subjectId, v]) => ({
        subjectId, ...v, accuracy: v.total ? v.correct / v.total : 0,
      })),
      incorrectQuestionIds: answers.filter((a: { correct: boolean }) => !a.correct).map((a: { questionId: string }) => a.questionId),
    },
  });
}

export async function getQuiz(env: Env, userId: string, id: string): Promise<Response> {
  return json({ attempt: await loadAttempt(env, userId, id) });
}

export async function listQuizzes(env: Env, userId: string): Promise<Response> {
  const rows = await env.DB.prepare('SELECT json FROM quiz_attempts WHERE user_id = ? ORDER BY updated_at DESC LIMIT 100')
    .bind(userId)
    .all<{ json: string }>();
  return json(rows.results.map((r) => JSON.parse(r.json)));
}

export async function reportQuestion(env: Env, userId: string, id: string, request: Request): Promise<Response> {
  const body = await readJson<Record<string, unknown>>(request);
  await env.DB.prepare('INSERT INTO reports (id, user_id, json, created_at) VALUES (?, ?, ?, ?)')
    .bind(uid('rep_'), userId, JSON.stringify({ targetKind: 'QUESTION', targetId: id, ...body }), nowIso())
    .run();
  return json({ reported: true }, 201);
}

// ---------- habits / diary / streak ----------

export async function listHabits(env: Env, userId: string): Promise<Response> {
  const rows = await env.DB.prepare('SELECT json FROM habits WHERE user_id = ?').bind(userId).all<{ json: string }>();
  return json(rows.results.map((r) => JSON.parse(r.json)));
}

export async function postHabit(env: Env, userId: string, request: Request): Promise<Response> {
  const body = await readJson<Record<string, unknown>>(request);
  requireFields(body, ['name']);
  const habit = {
    id: (body.id as string) ?? uid('hb_'),
    name: body.name,
    kind: body.kind ?? 'COUNT',
    targetPerDay: body.targetPerDay ?? 1,
    createdAt: nowIso(),
    archived: false,
  };
  await env.DB.prepare('INSERT INTO habits (id, user_id, json) VALUES (?, ?, ?) ON CONFLICT(id) DO UPDATE SET json = excluded.json')
    .bind(habit.id, userId, JSON.stringify(habit))
    .run();
  return json({ habit }, 201);
}

export async function deleteHabit(env: Env, userId: string, id: string): Promise<Response> {
  await env.DB.prepare('DELETE FROM habits WHERE id = ? AND user_id = ?').bind(id, userId).run();
  return json({ deleted: true });
}

export async function logHabit(env: Env, userId: string, request: Request): Promise<Response> {
  const body = await readJson<{ habitId: string; date?: string; amount: number }>(request);
  requireFields(body as unknown as Record<string, unknown>, ['habitId']);
  await env.DB.prepare(
    `INSERT INTO habit_logs (user_id, habit_id, date, amount) VALUES (?, ?, ?, ?)
     ON CONFLICT(user_id, habit_id, date) DO UPDATE SET amount = excluded.amount`,
  )
    .bind(userId, body.habitId, body.date ?? nowIso().slice(0, 10), Number(body.amount ?? 0))
    .run();
  return json({ logged: true });
}

export async function listDiary(env: Env, userId: string): Promise<Response> {
  const rows = await env.DB.prepare('SELECT json FROM diary WHERE user_id = ? ORDER BY date DESC LIMIT 500')
    .bind(userId)
    .all<{ json: string }>();
  return json(rows.results.map((r) => JSON.parse(r.json)));
}

export async function postDiary(env: Env, userId: string, request: Request): Promise<Response> {
  const body = await readJson<Record<string, unknown>>(request);
  requireFields(body, ['text']);
  const entry = {
    id: (body.id as string) ?? uid('dy_'),
    date: (body.date as string) ?? nowIso().slice(0, 10),
    mood: body.mood ?? 'okay',
    text: String(body.text).slice(0, 8000),
    tags: body.tags ?? [],
    createdAt: nowIso(),
  };
  await env.DB.prepare('INSERT INTO diary (id, user_id, date, mood, text, tags, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)')
    .bind(entry.id, userId, entry.date, entry.mood, entry.text, JSON.stringify(entry.tags), entry.createdAt)
    .run();
  return json({ entry }, 201);
}

export async function getStreak(env: Env, userId: string): Promise<Response> {
  const sessions = await env.DB.prepare('SELECT started_at FROM study_sessions WHERE user_id = ?').bind(userId)
    .all<{ started_at: string }>();
  const diary = await env.DB.prepare('SELECT date FROM diary WHERE user_id = ?').bind(userId).all<{ date: string }>();
  const days = new Set<string>([
    ...sessions.results.map((s) => s.started_at.slice(0, 10)),
    ...diary.results.map((d) => d.date),
  ]);
  const cursor = new Date();
  if (!days.has(cursor.toISOString().slice(0, 10))) cursor.setUTCDate(cursor.getUTCDate() - 1);
  let current = 0;
  while (days.has(cursor.toISOString().slice(0, 10))) {
    current++;
    cursor.setUTCDate(cursor.getUTCDate() - 1);
  }
  return json({ current, best: current, freezeDaysLeft: 0 });
}

// ---------- PDF metadata + annotations ----------

export async function listPdfs(env: Env, userId: string): Promise<Response> {
  const rows = await env.DB.prepare('SELECT * FROM pdf_docs WHERE user_id = ? ORDER BY added_at DESC').bind(userId).all();
  return json(rows.results);
}

export async function postPdfMeta(env: Env, userId: string, request: Request): Promise<Response> {
  const body = await readJson<Record<string, unknown>>(request);
  requireFields(body, ['name']);
  const id = (body.id as string) ?? uid('pdf_');
  await env.DB.prepare(
    'INSERT INTO pdf_docs (id, user_id, name, subject_id, page_count, last_page, size_bytes, added_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
  )
    .bind(id, userId, body.name as string, (body.subjectId as string) ?? null,
      Number(body.pageCount ?? 0), Number(body.lastPage ?? 1), Number(body.sizeBytes ?? 0), nowIso())
    .run();
  return json({ id }, 201);
}

export async function deletePdf(env: Env, userId: string, id: string): Promise<Response> {
  await env.DB.prepare('DELETE FROM pdf_docs WHERE id = ? AND user_id = ?').bind(id, userId).run();
  await env.DB.prepare('DELETE FROM pdf_annotations WHERE pdf_id = ? AND user_id = ?').bind(id, userId).run();
  return json({ deleted: true });
}

export async function getAnnotations(env: Env, userId: string, pdfId: string): Promise<Response> {
  const rows = await env.DB.prepare('SELECT json FROM pdf_annotations WHERE user_id = ? AND pdf_id = ?')
    .bind(userId, pdfId)
    .all<{ json: string }>();
  return json(rows.results.map((r) => JSON.parse(r.json)));
}

export async function putAnnotations(env: Env, userId: string, pdfId: string, request: Request): Promise<Response> {
  const body = await readJson<{ annotations: Record<string, unknown>[] }>(request);
  await env.DB.prepare('DELETE FROM pdf_annotations WHERE user_id = ? AND pdf_id = ?').bind(userId, pdfId).run();
  for (const a of body.annotations ?? []) {
    await env.DB.prepare('INSERT INTO pdf_annotations (id, user_id, pdf_id, json) VALUES (?, ?, ?, ?)')
      .bind((a.id as string) ?? uid('ann_'), userId, pdfId, JSON.stringify(a))
      .run();
  }
  return json({ saved: (body.annotations ?? []).length });
}
