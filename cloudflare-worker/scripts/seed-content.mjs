#!/usr/bin/env node
// Generates .seed.sql: one INSERT per JSON file in shared/content, ready for
// `wrangler d1 execute medtech-review-hub --remote --file=.seed.sql`.
import { readFileSync, readdirSync, statSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const contentDir = join(here, '..', '..', 'shared', 'content');
if (!existsSync(contentDir)) {
  console.error('[seed] shared/content not found');
  process.exit(1);
}

function walk(dir, acc = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, acc);
    else if (name.endsWith('.json')) acc.push(p);
  }
  return acc;
}

function sqlString(s) {
  return `'${s.replace(/'/g, "''")}'`;
}

function kindOf(relPath) {
  if (relPath.startsWith('subjects/')) return 'subject';
  if (relPath.startsWith('lessons/')) return 'lesson';
  if (relPath.startsWith('questions/')) return 'question';
  if (relPath.startsWith('flashcards/')) return 'deck';
  if (relPath === 'formulas.json') return 'formula';
  if (relPath === 'memes.json') return 'meme';
  if (relPath === 'glossary.json') return 'glossary';
  return 'other';
}

const files = walk(contentDir).sort();
const lines = [];
let count = 0;
for (const abs of files) {
  const rel = relative(contentDir, abs).split(sep).join('/');
  const raw = readFileSync(abs, 'utf8');
  const parsed = JSON.parse(raw);
  // For array files (questions/decks/formulas/memes/glossary) store one row per item.
  const items = Array.isArray(parsed) ? parsed : [parsed];
  let idx = 0;
  for (const item of items) {
    const id = item.id ?? `${rel}#${idx}`;
    const subjectId = item.subjectId ?? null;
    lines.push(
      `INSERT OR REPLACE INTO content (path, kind, id, subject_id, json) VALUES (${sqlString(`${rel}#${idx}`)}, ${sqlString(kindOf(rel))}, ${sqlString(String(id))}, ${subjectId ? sqlString(String(subjectId)) : 'NULL'}, ${sqlString(JSON.stringify(item))});`,
    );
    idx++;
    count++;
  }
}
lines.push('');
writeFileSync(join(here, '..', '.seed.sql'), lines.join('\n'), 'utf8');
console.log(`[seed] wrote .seed.sql with ${count} rows from ${files.length} files`);
