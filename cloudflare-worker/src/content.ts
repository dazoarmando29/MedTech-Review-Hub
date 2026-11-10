// Content endpoints (public, read-only) — served from the D1 content table
// which is seeded from shared/content by scripts/seed-content.mjs.

import { HttpError, json, type Env } from './auth';

export async function handleContent(path: string, env: Env): Promise<Response | null> {
  if (path === '/content/subjects') {
    const rows = await env.DB.prepare("SELECT json FROM content WHERE kind = 'subject' ORDER BY path").all();
    const subjects = rows.results.map((r) => JSON.parse(r.json as string));
    return json(subjects);
  }

  if (path === '/content/lessons') {
    const rows = await env.DB.prepare("SELECT json FROM content WHERE kind = 'lesson' ORDER BY path").all();
    return json(rows.results.map((r) => {
      const l = JSON.parse(r.json as string);
      return {
        id: l.id, subjectId: l.subjectId, topicId: l.topicId, title: l.title,
        summary: l.summary, readingMinutes: l.readingMinutes, difficulty: l.difficulty,
        keywords: l.keywords, editorialStatus: l.editorialStatus,
      };
    }));
  }

  const lessonMatch = path.match(/^\/content\/lessons\/([^/]+)$/);
  if (lessonMatch) {
    const id = decodeURIComponent(lessonMatch[1]);
    const row = await env.DB.prepare("SELECT json FROM content WHERE kind = 'lesson' AND (id = ? OR path = ?)")
      .bind(id, `./lessons/${id}.json`)
      .first<{ json: string }>();
    if (!row) {
      // id may be "subject/lesson"
      const row2 = await env.DB.prepare("SELECT json FROM content WHERE kind = 'lesson' AND id = ?").bind(id).first<{ json: string }>();
      if (!row2) throw new HttpError(404, 'NOT_FOUND', `Lesson not found: ${id}`);
      return json(JSON.parse(row2.json));
    }
    return json(JSON.parse(row.json));
  }

  if (path === '/content/questions') {
    const rows = await env.DB.prepare("SELECT json FROM content WHERE kind = 'question' ORDER BY path").all();
    return json(rows.results.map((r) => JSON.parse(r.json as string)));
  }

  if (path === '/content/formulas') {
    const rows = await env.DB.prepare("SELECT json FROM content WHERE kind = 'formula' ORDER BY path").all();
    return json(rows.results.map((r) => JSON.parse(r.json as string)));
  }

  if (path === '/content/memes') {
    const rows = await env.DB.prepare("SELECT json FROM content WHERE kind = 'meme' ORDER BY path").all();
    return json(rows.results.map((r) => JSON.parse(r.json as string)));
  }

  if (path === '/content/glossary') {
    const rows = await env.DB.prepare("SELECT json FROM content WHERE kind = 'glossary' ORDER BY path").all();
    return json(rows.results.map((r) => JSON.parse(r.json as string)));
  }

  return null;
}
