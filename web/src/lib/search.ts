// Global search across subjects, lessons, flashcards, questions, glossary, formulas, highlights.
import { decks, formulas, glossarySeed, lessons, questionBank, subjects } from './content';
import type { AppState } from './store';

export interface SearchHit {
  kind: 'subject' | 'lesson' | 'flashcard' | 'question' | 'glossary' | 'formula' | 'highlight' | 'note';
  id: string;
  title: string;
  snippet: string;
  href: string;
  subjectId: string;
}

export function globalSearch(query: string, state?: AppState, limit = 40): SearchHit[] {
  const q = query.trim().toLowerCase();
  if (q.length < 2) return [];
  const hits: SearchHit[] = [];
  const match = (text: string) => text.toLowerCase().includes(q);

  for (const s of subjects) {
    if (match(s.name) || match(s.description)) {
      hits.push({
        kind: 'subject',
        id: s.id,
        title: s.name,
        snippet: s.description.slice(0, 140),
        href: `/library/${s.id}/`,
        subjectId: s.id,
      });
    }
  }

  for (const l of lessons) {
    const haystack = [l.title, l.summary, ...l.keywords, ...l.quickReview.highYield,
      l.deepStudy.introduction, ...l.deepStudy.sections.map((x) => x.heading + ' ' + x.body)].join(' ');
    if (match(haystack)) {
      const idx = haystack.toLowerCase().indexOf(q);
      const snippet = idx >= 0
        ? '…' + haystack.slice(Math.max(0, idx - 40), idx + 100).trim() + '…'
        : l.summary.slice(0, 140);
      hits.push({
        kind: 'lesson',
        id: l.id,
        title: l.title,
        snippet,
        href: `/library/${l.subjectId}/${encodeURIComponent(l.id.split('/')[1])}/`,
        subjectId: l.subjectId,
      });
    }
  }

  for (const d of decks) {
    for (const c of d.cards) {
      if (match(c.front) || match(c.back) || c.tags.some(match)) {
        hits.push({
          kind: 'flashcard',
          id: c.id,
          title: c.front.slice(0, 80),
          snippet: c.back.slice(0, 140),
          href: '/flashcards/',
          subjectId: d.subjectId,
        });
      }
    }
  }

  for (const qq of questionBank) {
    if (match(qq.stem) || (qq.vignette && match(qq.vignette))) {
      hits.push({
        kind: 'question',
        id: qq.id,
        title: qq.stem.slice(0, 80),
        snippet: qq.explanation.slice(0, 140),
        href: '/quiz/',
        subjectId: qq.subjectId,
      });
    }
  }

  for (const g of glossarySeed) {
    if (match(g.term) || match(g.definition)) {
      hits.push({
        kind: 'glossary',
        id: g.id,
        title: g.term,
        snippet: g.definition.slice(0, 140),
        href: '/glossary/',
        subjectId: g.subjectId,
      });
    }
  }

  for (const f of formulas) {
    if (match(f.title) || match(f.formula) || match(f.example)) {
      hits.push({
        kind: 'formula',
        id: f.id,
        title: f.title,
        snippet: f.formula.slice(0, 140),
        href: '/formulas/',
        subjectId: '',
      });
    }
  }

  if (state) {
    for (const h of state.highlights) {
      if (match(h.text) || match(h.note)) {
        hits.push({
          kind: 'highlight',
          id: h.id,
          title: h.text.slice(0, 80),
          snippet: h.note || h.text.slice(0, 140),
          href: h.lessonId ? `/library/${h.lessonId.split('/')[0]}/` : '/glossary/',
          subjectId: h.lessonId?.split('/')[0] ?? '',
        });
      }
    }
  }

  return hits.slice(0, limit);
}
