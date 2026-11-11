// Gemini AI adapter (provider-agnostic per shared/api/API.md).
// The API key exists ONLY as a Worker secret — never shipped to clients.
// Every response must cite the source passage or explicitly say the answer is
// not found in the provided document.

import { HttpError, json, type Env } from './auth';

interface AiEnvelope {
  result: unknown;
  citations: { pdfId?: string | null; page?: number | null; quote: string }[];
  warnings: string[];
  usage: { cached: boolean; tokensEstimate: number };
}

const cache = new Map<string, AiEnvelope>();
const CACHE_LIMIT = 200;

function cacheGet(key: string): AiEnvelope | null {
  const hit = cache.get(key);
  if (hit) {
    cache.delete(key);
    cache.set(key, hit);
  }
  return hit ?? null;
}

function cacheSet(key: string, value: AiEnvelope): void {
  if (cache.size >= CACHE_LIMIT) {
    const oldest = cache.keys().next().value as string | undefined;
    if (oldest) cache.delete(oldest);
  }
  cache.set(key, value);
}

async function enforceBudget(env: Env, userId: string, estimate: number): Promise<void> {
  // Signed-in users get the full budget; anonymous devices get a smaller
  // (but permanent, no-login) daily allowance — this is a personal study app.
  const isDevice = userId.startsWith('device:');
  const budget = Number(
    (isDevice ? env.AI_DAILY_TOKEN_BUDGET_ANON : env.AI_DAILY_TOKEN_BUDGET) ?? 20000,
  );
  const day = new Date().toISOString().slice(0, 10);
  const row = await env.DB.prepare('SELECT tokens FROM ai_usage WHERE user_id = ? AND day = ?')
    .bind(userId, day)
    .first<{ tokens: number }>();
  const used = row?.tokens ?? 0;
  if (used + estimate > budget) {
    throw new HttpError(
      429,
      'RATE_LIMITED',
      isDevice
        ? "Today's free AI limit was reached on this device. It resets tomorrow — sign in (Login page) only if you want a bigger daily allowance and cross-device sync."
        : 'Daily AI budget exhausted. Try again tomorrow.',
    );
  }
  await env.DB.prepare(
    `INSERT INTO ai_usage (user_id, day, tokens) VALUES (?, ?, ?)
     ON CONFLICT(user_id, day) DO UPDATE SET tokens = tokens + excluded.tokens`,
  )
    .bind(userId, day, estimate)
    .run();
}

const SYSTEM_RULES = `You are the AI study assistant inside MedTech Review Hub, an ASCPi board exam reviewer.
Rules you MUST follow:
1. Use ONLY the SOURCE TEXT provided in the prompt. If the answer is not supported by the source text, reply exactly: "Not found in the provided document." Never invent facts, reference values, or citations.
2. When you state anything from the source, cite it inline as [p.<page>: "<short quote>"] when page info is given, otherwise ["<short quote>"].
3. Medical content: if the source seems ambiguous, outdated, or inconsistent, add a line starting with "WARNING:" describing the concern.
4. Keep explanations exam-focused and accurate. Optional light Taglish only when the requested style is TAGLISH.
5. Respond in plain text (markdown ok).`;

let geminiQuotaExhausted = false;

/** Backup brain: Cloudflare Workers AI (built into this Worker's account). */
const CF_MODELS = [
  '@cf/meta/llama-3.3-70b-instruct-fp8',
  '@cf/mistral/mistral-small-3.1-24b-instruct',
  '@cf/google/gemma-3-12b-it',
  '@cf/qwen/qwen2.5-coder-32b-instruct',
  '@cf/meta/llama-3.1-8b-instruct',
];

async function callCloudflareAi(env: Env, prompt: string, maxOutputTokens: number): Promise<{ text: string; tokensEstimate: number }> {
  if (!env.AI) throw new HttpError(502, 'INTERNAL', 'No AI fallback available');
  const fullPrompt = SYSTEM_RULES + '\n\n' + prompt.slice(0, 6000);
  let lastErr: unknown = new Error('no Cloudflare AI model available');
  // Try current models in order — self-heals when Cloudflare retires one.
  for (const model of CF_MODELS) {
    try {
      const res = (await env.AI.run(model, {
        prompt: fullPrompt,
        max_tokens: Math.min(maxOutputTokens, 1400),
      })) as { response?: string };
      const text = (res?.response ?? '').trim();
      if (text) return { text, tokensEstimate: Math.ceil(prompt.length / 4) + 800 };
      lastErr = new Error(`${model} returned no text`);
    } catch (e) {
      lastErr = e;
    }
  }
  throw new HttpError(502, 'INTERNAL', String((lastErr as Error)?.message ?? lastErr).slice(0, 160));
}

async function callGemini(env: Env, prompt: string, maxOutputTokens = 2048): Promise<{ text: string; tokensEstimate: number }> {
  const key = env.GEMINI_API_KEY;
  if (!key) throw new HttpError(503, 'AI_UNAVAILABLE', 'AI features are not configured on this server');
  // Preferred model first, then fallbacks (Google retires model versions over time).
  const models = [...new Set([env.GEMINI_MODEL ?? 'gemini-3.5-flash', 'gemini-3.5-flash', 'gemini-flash-latest'])];

  let lastErr: unknown;
  for (const model of models) {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const res = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}`,
          {
            signal: AbortSignal.timeout(45000),
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              systemInstruction: { parts: [{ text: SYSTEM_RULES }] },
              contents: [{ role: 'user', parts: [{ text: prompt }] }],
              generationConfig: { temperature: 0.3, maxOutputTokens },
            }),
          },
        );
        if (res.status === 404) {
          lastErr = new HttpError(502, 'INTERNAL', `Model ${model} not available`);
          break; // try next model
        }
        if (!res.ok) {
          const bodyText = await res.text();
          if (res.status === 429 && /quota|billing/i.test(bodyText)) {
            geminiQuotaExhausted = true;
            lastErr = new HttpError(429, 'RATE_LIMITED', 'Gemini quota exhausted');
            break; // stop trying Gemini models; use the fallback
          }
          if ((res.status === 429 || res.status >= 500) && attempt < 1) {
            await new Promise((r) => setTimeout(r, 1200));
            continue;
          }
          lastErr = new HttpError(502, 'INTERNAL', `AI provider error ${res.status}: ${bodyText.slice(0, 200)}`);
          continue;
        }
        const data = (await res.json()) as {
          candidates?: { content?: { parts?: { text?: string }[] } }[];
          usageMetadata?: { totalTokenCount?: number };
        };
        const text = data.candidates?.[0]?.content?.parts?.map((p) => p.text ?? '').join('') ?? '';
        return {
          text: text.trim() || 'No content generated.',
          tokensEstimate: data.usageMetadata?.totalTokenCount ?? Math.ceil(prompt.length / 4) + 500,
        };
      } catch (e) {
        lastErr = e instanceof HttpError ? e : new HttpError(502, 'INTERNAL', 'AI provider unreachable');
      }
    }
  }
  throw lastErr instanceof HttpError ? lastErr : new HttpError(502, 'INTERNAL', 'AI provider unreachable');
}

function parseEnvelope(text: string, pdfId?: string, page?: number, cached = false, tokens = 0): AiEnvelope {
  const warnings = text
    .split('\n')
    .filter((l) => l.trim().startsWith('WARNING:'))
    .map((l) => l.replace(/^WARNING:\s*/, '').trim());
  const citationRegex = /\[(?:p\.(\d+):\s*)?"([^"]{4,120})"\]/g;
  const citations: AiEnvelope['citations'] = [];
  let m: RegExpExecArray | null;
  while ((m = citationRegex.exec(text)) !== null) {
    citations.push({ pdfId: pdfId ?? null, page: m[1] ? Number(m[1]) : (page ?? null), quote: m[2] });
  }
  return { result: text, citations, warnings, usage: { cached, tokensEstimate: tokens } };
}

export interface AiRequest {
  text: string;
  mode?: string;
  style?: string;
  pdfId?: string;
  page?: number;
}

export async function runAiTool(
  env: Env,
  userId: string,
  tool: 'summarize' | 'explain' | 'flashcards' | 'quiz' | 'keyterms' | 'outline' | 'diary-summary',
  req: AiRequest,
): Promise<Response> {
  const source = (req.text ?? '').slice(0, 12000);
  if (source.trim().length < 20) {
    throw new HttpError(400, 'VALIDATION_ERROR', 'Provide at least some source text');
  }

  const cacheKey = `${tool}|${req.mode ?? ''}|${req.style ?? ''}|${source}`;
  const cachedHit = cacheGet(cacheKey);
  if (cachedHit) return json(cachedHit);

  const pageNote = req.page ? ` (page ${req.page})` : '';
  const styleNote =
    req.style === 'TAGLISH'
      ? ' Use Taglish (natural Tagalog-English mix) for friendly asides while keeping medical terms in English.'
      : req.style === 'SIMPLE_ENGLISH'
        ? ' Use simple English.'
        : req.style === 'FUNNY'
          ? ' Explain in a funny, meme-like way — like the wittiest reviewer on TikTok — but keep every fact scientifically accurate and end with one memory hook.'
          : req.style === 'TAGLISH_FUNNY'
            ? ' Explain in funny Taglish (natural Tagalog-English mix) like a funny study buddy — so easy that even someone who is NOT a medtech student can understand. Keep every fact accurate. End with 1-2 practical, kind study tips (what to review first).'
            : '';

  const prompts: Record<typeof tool, string> = {
    summarize:
      `Task: Summarize the following study material${pageNote} (${req.mode ?? 'PAGE'} level) for an exam candidate. ` +
      `Include the key facts a student must memorize. Cite quotes.\n\nSOURCE TEXT:\n${source}`,
    explain:
      `Task: Explain the following passage in a way a struggling student understands.${styleNote} Cite quotes.\n\nSOURCE TEXT:\n${source}`,
    flashcards:
      `Task: Generate 5-6 study flashcards from the source text.\n` +
      `Return ONLY strict JSON (no markdown fences, no commentary) in exactly this shape:\n` +
      `{"flashcards":[{"front":"question or cloze prompt","back":"answer","quote":"short exact quote"}]}` +
      ` Keep each quote under 12 words. If the source contains ONLY front matter or marketing text (no testable subject facts), return {"flashcards":[]}. Focus ONLY on subject/review content (lab science facts, definitions, findings, values, procedures). IGNORE prefaces, author bios, publisher/marketing text, copyright pages, tables of contents, and ads. Only create an item when the answer is explicitly in the source text - every answer must be supported by its quote. Skip passages with no testable facts.\n\nSOURCE TEXT:\n${source.slice(0, 8000)}`,
    quiz:
      `Task: Write 3 multiple-choice questions (4 options A-D, one best answer) from the source text.\n` +
      `Return ONLY strict JSON (no markdown fences, no commentary) in exactly this shape:\n` +
      `{"questions":[{"stem":"question","vignette":"short clinical setup or null","choices":{"A":"...","B":"...","C":"...","D":"..."},"correct":"A","rationale":"why the correct answer is right","misconception":"the trap behind the common wrong answer","quote":"short exact quote"}]}` +
      ` Every answer must be supported by the source text. If the source contains ONLY front matter or marketing text (no testable subject facts), return {"questions":[]}. Focus ONLY on subject/review content (lab science facts, definitions, findings, values, procedures). IGNORE prefaces, author bios, publisher/marketing text, copyright pages, tables of contents, and ads. Only create an item when the answer is explicitly in the source text - every answer must be supported by its quote. Skip passages with no testable facts.\n\nSOURCE TEXT:\n${source.slice(0, 8000)}`,
    keyterms:
      `Task: Extract the key terms and their definitions from the source text as a bullet list. Cite quotes.\n\nSOURCE TEXT:\n${source}`,
    outline:
      `Task: Create a study outline (headings + bullets) of the source text. Cite quotes.\n\nSOURCE TEXT:\n${source}`,
    'diary-summary':
      `Task: Summarize the student's diary entries below. Identify recurring difficulties and suggest 2-3 concrete next steps. ` +
      `Be encouraging; never shame missed days. Cite quotes where you reference specific struggles.\n\nDIARY:\n${source}`,
  };

  await enforceBudget(env, userId, 800);
  const genBudget = tool === 'flashcards' ? 1600 : tool === 'quiz' ? 1800 : tool === 'keyterms' ? 1024 : 2048;
  let text: string;
  let tokensEstimate: number;
  try {
    ({ text, tokensEstimate } = await callGemini(env, prompts[tool], genBudget));
  } catch (geminiErr) {
    // Provider-agnostic design: when Gemini quota is exhausted (or down),
    // answer with the built-in Cloudflare Workers AI instead.
    try {
      ({ text, tokensEstimate } = await callCloudflareAi(env, prompts[tool], genBudget));
    } catch (cfErr) {
      if (geminiQuotaExhausted) {
        throw new HttpError(
          429,
          'RATE_LIMITED',
          `Gemini quota is exhausted and the backup AI said: ${String((cfErr as Error)?.message ?? cfErr).slice(0, 160)}`,
        );
      }
      throw geminiErr;
    }
  }
  const envelope = parseEnvelope(text, req.pdfId, req.page, false, tokensEstimate);
  cacheSet(cacheKey, envelope);
  return json(envelope);
}
