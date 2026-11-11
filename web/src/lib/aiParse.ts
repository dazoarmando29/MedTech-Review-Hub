// Parsers for AI generation output (web + shared formats).
// The AI is asked for strict JSON (see cloudflare-worker/src/gemini.ts), but we
// parse leniently: code fences stripped, JSON located anywhere in the text, and
// a Q:/A: fallback for older/plain-text responses.

export interface ParsedFlashcard {
  front: string;
  back: string;
  quote?: string;
}

export interface ParsedQuestion {
  stem: string;
  vignette: string | null;
  choices: { id: string; text: string }[];
  correctChoiceId: string;
  rationale: string;
  misconception: string;
  quote?: string;
}

function extractJson(text: string): unknown | null {
  const cleaned = text.replace(/```(?:json)?/g, '');
  // direct parse
  try {
    return JSON.parse(cleaned.trim());
  } catch {
    /* fall through */
  }
  // find the first {...} or [...] block that parses
  for (const start of ['{', '[']) {
    let idx = cleaned.indexOf(start);
    while (idx !== -1) {
      for (const end of ['}', ']']) {
        const stop = cleaned.lastIndexOf(end);
        if (stop > idx) {
          try {
            return JSON.parse(cleaned.slice(idx, stop + 1));
          } catch {
            /* try next */
          }
        }
      }
      idx = cleaned.indexOf(start, idx + 1);
    }
  }
  return null;
}

function isFrontMatterJunk(text: string): boolean {
  return /copyright|isbn|all rights reserved|published by|table of contents|resource pages|davisplus|publisher/i.test(
    text,
  );
}

export function parseFlashcardsFromAi(text: string): ParsedFlashcard[] {
  const json = extractJson(text) as
    | { flashcards?: { front?: unknown; back?: unknown; quote?: unknown }[] }
    | { front?: unknown; back?: unknown }[]
    | null;
  const raw = Array.isArray(json) ? json : json?.flashcards ?? [];
  const cards = raw
    .map((c) => ({
      front: String((c as { front?: unknown }).front ?? '').trim(),
      back: String((c as { back?: unknown }).back ?? '').trim(),
      quote: (c as { quote?: unknown }).quote ? String((c as { quote?: unknown }).quote).trim() : undefined,
    }))
    .filter((c) => c.front && c.back)
    .filter((c) => !isFrontMatterJunk(c.front) && !isFrontMatterJunk(c.back));
  if (cards.length) return cards;

  // Tolerant pass: pick {"front":...,"back":...} objects even out of a
  // truncated response (long generations can get cut mid-JSON).
  const out: ParsedFlashcard[] = [];
  const objRe = /\{[^{}]*"front"\s*:\s*"((?:[^"\\]|\\.)*)"[^{}]*"back"\s*:\s*"((?:[^"\\]|\\.)*)"[^{}]*\}/g;
  for (const m of text.matchAll(objRe)) {
    const front = JSON.parse(`"${m[1]}"`) as string;
    const back = JSON.parse(`"${m[2]}"`) as string;
    if (front.trim() && back.trim()) out.push({ front: front.trim(), back: back.trim() });
  }
  if (out.length) return out;

  // fallback: plain-text "Q: ... A: ..." pairs
  const pairs = text.split(/\n(?=Q[:.])/i);
  for (const p of pairs) {
    const m = p.match(/Q[:.]\s*([\s\S]+?)\nA[:.]\s*([\s\S]+?)(?:\n|$)/i);
    if (m) out.push({ front: m[1].trim(), back: m[2].trim() });
  }
  return out.filter((c) => c.front && c.back && !isFrontMatterJunk(c.front));
}

export function parseQuestionsFromAi(text: string): ParsedQuestion[] {
  const json = extractJson(text) as
    | { questions?: Record<string, unknown>[] }
    | Record<string, unknown>[]
    | null;
  const raw = Array.isArray(json) ? json : json?.questions ?? [];
  const out: ParsedQuestion[] = [];
  for (const q of raw as Record<string, unknown>[]) {
    const stem = String(q.stem ?? '').trim();
    const rawChoices = q.choices as Record<string, unknown> | string[] | undefined;
    let choices: { id: string; text: string }[];
    if (Array.isArray(rawChoices)) {
      choices = rawChoices.map((t, i) => ({ id: 'ABCD'[i] ?? String(i), text: String(t) }));
    } else if (rawChoices) {
      choices = Object.entries(rawChoices).map(([id, t]) => ({ id, text: String(t) }));
    } else {
      choices = (['A', 'B', 'C', 'D'] as const).map((id) => ({ id, text: String(q[id.toLowerCase()] ?? q[id] ?? '') }));
    }
    choices = choices.filter((c) => c.text.trim()).slice(0, 4);
    const rawCorrect = String(q.correct ?? q.correctChoiceId ?? '').trim();
    const correctRaw = rawCorrect.toUpperCase();
    const correctChoiceId = /^[A-D]$/.test(correctRaw)
      ? correctRaw
      : (choices.find((c) => c.text.trim().toLowerCase() === rawCorrect.toLowerCase())?.id ?? 'A');
    if (!stem || choices.length < 2 || isFrontMatterJunk(stem)) continue;
    out.push({
      stem,
      vignette: q.vignette ? String(q.vignette).trim() : null,
      choices,
      correctChoiceId: choices.some((c) => c.id === correctChoiceId) ? correctChoiceId : choices[0].id,
      rationale: String(q.rationale ?? q.explanation ?? '').trim(),
      misconception: String(q.misconception ?? '').trim(),
      quote: q.quote ? String(q.quote).trim() : undefined,
    });
  }
  return out;
}
