'use client';

// Smart PDF reader: zoom, navigation, in-document search, bookmarks, resume
// position, semantic highlight colors, notes, and AI tools (server-side AI).
import Link from 'next/link';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Badge, Card, ConfirmButton, EmptyState, SectionTitle, cx } from '@/components/ui';
import { Mochi } from '@/components/Mochi';
import { api, configureApi, friendlyApiError, isSignedIn } from '@/lib/api';
import { parseFlashcardsFromAi, parseQuestionsFromAi, type ParsedFlashcard, type ParsedQuestion } from '@/lib/aiParse';
import { toQuizQuestion } from '@/lib/questions';
import { chunkText, MAX_AI_PARTS, stripFrontMatter } from '@/lib/pdfText';
import { newAttemptId } from '@/lib/quiz';
import {
  addCustomQuestions, addPdf, addPdfAnnotation, removePdf, removePdfAnnotation, setPdfLastPage,
  addAiNote, removeAiNote, startQuiz, updateSettings, upsertCard, useStore,
} from '@/lib/store';
import type { HighlightColor, PdfAnnotation, PdfDocMeta, QuizAttempt } from '@/lib/types';

const COLORS: { id: HighlightColor }[] = [
  { id: 'YELLOW' },
  { id: 'PINK' },
  { id: 'PURPLE' },
  { id: 'BLUE' },
  { id: 'GREEN' },
  { id: 'ROSE' },
];

type PdfHandle = {
  numPages: number;
  getPage: (n: number) => Promise<{ getTextContent: () => Promise<{ items: { str: string }[] }> }>;
};

type ScopeChoice = { mode: 'selection' | 'page' | 'whole' | 'pages'; from?: number; to?: number };

export default function PdfPage() {
  const router = useRouter();
  const pdfs = useStore((s) => s.pdfs);
  const annotations = useStore((s) => s.pdfAnnotations);
  const settings = useStore((s) => s.settings);
  const aiNotes = useStore((s) => s.aiNotes);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [selection, setSelection] = useState<string | null>(null);
  const [pageText, setPageText] = useState('');
  const [aiResult, setAiResult] = useState<string | null>(null);
  const [aiError, setAiError] = useState<string | null>(null);
  const [aiBusy, setAiBusy] = useState(false);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [genLoading, setGenLoading] = useState<'cards' | 'quiz' | null>(null);
  const [uploading, setUploading] = useState<{ name: string; pct: number } | null>(null);
  const [scopeFor, setScopeFor] = useState<'cards' | 'quiz' | 'summarize' | null>(null);
  const [genProgress, setGenProgress] = useState<string | null>(null);
  const [authed, setAuthed] = useState(false);
  // NOTE: must come after the activeId declaration above (TDZ safety).
  const activePdfName = useStore((s) => s.pdfs.find((p) => p.id === activeId)?.name ?? null);

  // Stable callbacks + memoized annotations keep the heavy PDF layer from
  // re-rendering while typing, selecting text, or running AI tools.
  const handlePageText = useCallback((t: string) => setPageText(t), []);
  const handleSelection = useCallback((t: string | null) => setSelection(t), []);
  const activeAnnotations = useMemo(
    () => annotations.filter((a) => a.pdfId === activeId),
    [annotations, activeId],
  );
  const [genCards, setGenCards] = useState<ParsedFlashcard[] | null>(null);
  const [genQuestions, setGenQuestions] = useState<ParsedQuestion[] | null>(null);

  useEffect(() => {
    configureApi(settings.apiBaseUrl);
    setAuthed(isSignedIn());
  }, [settings.apiBaseUrl]);

  useEffect(() => {
    const onSel = () => {
      const t = window.getSelection()?.toString().trim();
      setSelection(t && t.length > 5 ? t.slice(0, 2000) : null);
    };
    document.addEventListener('mouseup', onSel);
    return () => document.removeEventListener('mouseup', onSel);
  }, []);

  const runAi = async (fn: () => Promise<{ result: unknown; warnings: string[] }>, label: string) => {
    setAiBusy(true);
    setAiError(null);
    try {
      const res = await fn();
      const body = typeof res.result === 'string' ? res.result : JSON.stringify(res.result, null, 2);
      setAiResult(`${label}\n\n${body}${res.warnings?.length ? `\n\nWarnings: ${res.warnings.join('; ')}` : ''}`);
    } catch (e) {
      setAiError(friendlyApiError(e));
    } finally {
      setAiBusy(false);
      setBusyAction(null);
    }
  };

  // Anki-style flow: AI drafts flashcards/quiz from the text -> user reviews
  // -> real deck cards / real quiz session (never silently added).
  const runGenCards = async (text: string) => {
    setAiBusy(true);
    setBusyAction('flashcards');
    setGenLoading('cards'); // dialog opens instantly with a buffering skeleton
    setGenCards([]);
    setAiError(null);
    try {
      const res = await api.aiFlashcards(text);
      const cards = parseFlashcardsFromAi(typeof res.result === 'string' ? res.result : JSON.stringify(res.result));
      if (!cards.length) {
        setGenCards(null);
        setAiError('The AI did not return usable flashcards for this text. Try selecting a fuller passage.');
      } else {
        setGenCards(cards);
      }
    } catch (e) {
      setGenCards(null);
      setAiError(friendlyApiError(e));
    } finally {
      setGenLoading(null);
      setAiBusy(false);
      setBusyAction(null);
    }
  };

  const runGenQuiz = async (text: string) => {
    setAiBusy(true);
    setBusyAction('quiz');
    setGenLoading('quiz'); // dialog opens instantly with a buffering skeleton
    setGenQuestions([]);
    setAiError(null);
    try {
      const res = await api.aiQuiz(text);
      const qs = parseQuestionsFromAi(typeof res.result === 'string' ? res.result : JSON.stringify(res.result));
      if (!qs.length) {
        setGenQuestions(null);
        setAiError('The AI did not return usable questions for this text. Try selecting a fuller passage.');
      } else {
        setGenQuestions(qs);
      }
    } catch (e) {
      setGenQuestions(null);
      setAiError(friendlyApiError(e));
    } finally {
      setGenLoading(null);
      setAiBusy(false);
      setBusyAction(null);
    }
  };

  const startGeneratedQuiz = (selected: ParsedQuestion[], label: string) => {
    const questions = selected.map((p, i) => toQuizQuestion(p, label, `${Date.now()}-${i}`));
    addCustomQuestions(questions);
    const attempt: QuizAttempt = {
      id: newAttemptId(),
      startedAt: new Date().toISOString(),
      finishedAt: null,
      mode: 'STUDY',
      filters: { label: `From your PDF — ${label}` },
      questionIds: questions.map((q) => q.id),
      answers: [],
      timeLimitSeconds: null,
      finished: false,
    };
    startQuiz(attempt);
    setGenQuestions(null);
    router.push('/quiz/');
  };

  // --- scope-aware generation: selection / this page / page range / whole PDF ---
  // Per-page text cache: once a page has been read it is reused instantly,
  // so repeat generations and scope changes feel immediate.
  const pageTextCache = useRef<Map<number, string>>(new Map());

  const extractOnePage = async (pdf: PdfHandle, n: number): Promise<string> => {
    const cached = pageTextCache.current.get(n);
    if (cached !== undefined) return cached;
    const pg = await pdf.getPage(n);
    const tc = await pg.getTextContent();
    const text = stripFrontMatter(tc.items.map((i) => i.str).join(' '));
    pageTextCache.current.set(n, text);
    return text;
  };

  const collectPagesText = async (from: number, to: number): Promise<string> => {
    const pdf = (window as unknown as { __pdf?: PdfHandle }).__pdf;
    if (!pdf) return stripFrontMatter(pageText);
    const start = Math.max(1, from);
    const end = Math.min(to, pdf.numPages);
    const numbers: number[] = [];
    for (let n = start; n <= end; n++) numbers.push(n);

    const parts: string[] = [];
    // Read pages in parallel batches of 4: pdf.js parses in its worker, so
    // this saturates it and reads a 200-page document in seconds.
    const BATCH = 4;
    for (let i = 0; i < numbers.length; i += BATCH) {
      const batch = numbers.slice(i, i + BATCH);
      setGenProgress(`Reading pages ${batch[0]}–${batch[batch.length - 1]} of ${end}…`);
      const texts = await Promise.all(batch.map((n) => extractOnePage(pdf, n).catch(() => '')));
      parts.push(...texts.filter(Boolean));
    }
    return parts.join('\n');
  };

  const scopeText = async (choice: ScopeChoice): Promise<{ text: string; label: string }> => {
    if (choice.mode === 'selection' && selection) return { text: selection, label: 'your selection' };
    if (choice.mode === 'pages') {
      const text = await collectPagesText(choice.from ?? 1, choice.to ?? 1);
      return { text, label: `pages ${choice.from}-${choice.to}` };
    }
    if (choice.mode === 'whole') {
      const pdf = (window as unknown as { __pdf?: { numPages: number } }).__pdf;
      const text = await collectPagesText(1, pdf?.numPages ?? 1);
      return { text, label: 'the whole PDF' };
    }
    return { text: stripFrontMatter(pageText), label: 'this page' };
  };

  const handleScope = async (choice: ScopeChoice) => {
    setScopeFor(null);
    setAiError(null);
    const { text, label } = await scopeText(choice);
    if (text.trim().length < 40) {
      setAiError('This passage looks like front matter (publisher/author info). Choose a page with subject content — or pick "Whole PDF" so the AI can pick out the review-worthy parts.');
      setGenProgress(null);
      return;
    }
    if (scopeFor === 'summarize') {
      setAiBusy(true);
      setBusyAction('Summary');
      try {
        const res = await api.aiSummarize(text, label === 'the whole PDF' ? 'CHAPTER' : 'PAGE');
        const body = typeof res.result === 'string' ? res.result : JSON.stringify(res.result, null, 2);
        setAiResult(`Summary of ${label}\n\n${body}`);
        addAiNote({
          id: `note-${Date.now()}`,
          kind: 'summary',
          title: `Summary - ${label}`,
          text: body,
          pdfName: activePdfName,
          createdAt: new Date().toISOString(),
        });
      } catch (e) {
        setAiError(friendlyApiError(e));
      } finally {
        setAiBusy(false);
        setBusyAction(null);
        setGenProgress(null);
      }
      return;
    }
    // flashcards / quiz: generate per chunk so whole documents work too
    const parts = chunkText(text).slice(0, MAX_AI_PARTS);
    if (!parts.length) {
      setAiError('Nothing question-worthy was found in that range. Try another part of the document.');
      setGenProgress(null);
      return;
    }
    if (scopeFor === 'cards') {
      setGenCards([]);
      setGenLoading('cards');
      setAiBusy(true);
      setBusyAction('flashcards');
      try {
        const all: ParsedFlashcard[] = [];
        // 3 concurrent AI calls — big documents finish in a third of the time.
        const CONCURRENCY = 3;
        for (let i = 0; i < parts.length; i += CONCURRENCY) {
          const batch = parts.slice(i, i + CONCURRENCY);
          setGenProgress(`Writing cards… part ${i + 1}–${Math.min(i + CONCURRENCY, parts.length)} of ${parts.length}`);
          const results = await Promise.all(batch.map((part) => api.aiFlashcards(part)));
          for (const res of results) {
            all.push(...parseFlashcardsFromAi(typeof res.result === 'string' ? res.result : JSON.stringify(res.result)));
          }
        }
        const seen = new Set<string>();
        const unique = all.filter((c) => {
          const key = c.front.toLowerCase();
          if (seen.has(key)) return false;
          seen.add(key);
          return true;
        });
        if (!unique.length) {
          setGenCards(null);
          setAiError('The AI found no testable facts here (this can happen on publisher or preface pages). Try subject-content pages, or "Whole PDF".');
        } else {
          setGenCards(unique.map((c) => ({ ...c, note: label })));
        }
      } catch (e) {
        setGenCards(null);
        setAiError(friendlyApiError(e));
      } finally {
        setGenLoading(null);
        setAiBusy(false);
        setBusyAction(null);
        setGenProgress(null);
      }
      return;
    }
    // quiz
    setGenQuestions([]);
    setGenLoading('quiz');
    setAiBusy(true);
    setBusyAction('quiz');
    try {
      const all: ParsedQuestion[] = [];
      const CONCURRENCY = 3;
      for (let i = 0; i < parts.length; i += CONCURRENCY) {
        const batch = parts.slice(i, i + CONCURRENCY);
        setGenProgress(`Writing questions… part ${i + 1}–${Math.min(i + CONCURRENCY, parts.length)} of ${parts.length}`);
        const results = await Promise.all(batch.map((part) => api.aiQuiz(part)));
        for (const res of results) {
          all.push(...parseQuestionsFromAi(typeof res.result === 'string' ? res.result : JSON.stringify(res.result)));
        }
      }
      const unique = all.filter((q, i) => all.findIndex((x) => x.stem === q.stem) === i).slice(0, 20);
      if (!unique.length) {
        setGenQuestions(null);
        setAiError('The AI found no question-worthy facts here. Try subject-content pages, or "Whole PDF".');
      } else {
        setGenQuestions(unique);
      }
    } catch (e) {
      setGenQuestions(null);
      setAiError(friendlyApiError(e));
    } finally {
      setGenLoading(null);
      setAiBusy(false);
      setBusyAction(null);
      setGenProgress(null);
    }
  };

  return (
    <div className="space-y-6">
      {uploading && (
        <div className="card p-4 flex items-center gap-4" role="status" aria-live="polite">
          <Mochi mood="studying" size={64} className="loading-mochi" />
          <div className="flex-1">
            <p className="text-sm font-semibold">
              {uploading.pct < 100 ? `Buffering ${uploading.name}… ${uploading.pct}%` : `Opening ${uploading.name}…`}
            </p>
            <div className="h-2 rounded-full overflow-hidden mt-2" style={{ background: 'var(--chip)' }}>
              <div
                className="h-full rounded-full transition-all"
                style={{ width: `${uploading.pct}%`, background: 'var(--primary)' }}
              />
            </div>
            <div className="flex gap-1.5 mt-2"><span className="buffer-dot" /><span className="buffer-dot" /><span className="buffer-dot" /></div>
          </div>
        </div>
      )}
      <div>
        <h1 className="font-heading text-2xl font-bold">Smart PDF Reader</h1>
        <p className="text-sm text-[var(--ink-muted)] max-w-2xl">
          Upload your own legally obtained reviewers and notes. Files stay in your browser (and on
          your device) — private by default. Highlights use five semantic colors you can customize
          in Settings.
        </p>
      </div>

      <div className="grid lg:grid-cols-3 gap-4">
        <Card>
          <SectionTitle icon="🗂️">Your documents</SectionTitle>
          <label className="btn-primary w-full cursor-pointer">
            Upload PDF
            <input
              type="file"
              accept="application/pdf"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (!file) return;
                if (file.type !== 'application/pdf') {
                  alert('Only PDF files are accepted.');
                  return;
                }
                if (file.size > 50 * 1024 * 1024) {
                  alert('File exceeds the 50 MB limit.');
                  return;
                }
                const id = `pdf-${Date.now()}`;
                setUploading({ name: file.name, pct: 0 });
                // FileReader progress gives a real buffering indicator for big PDFs
                const reader = new FileReader();
                reader.onprogress = (ev) => {
                  if (ev.lengthComputable) {
                    setUploading({ name: file.name, pct: Math.round((ev.loaded / ev.total) * 100) });
                  }
                };
                reader.onload = async () => {
                  setUploading({ name: file.name, pct: 100 });
                  await saveBlob(id, new Blob([reader.result as ArrayBuffer], { type: 'application/pdf' }));
                  addPdf({
                    id,
                    name: file.name,
                    subjectId: null,
                    pageCount: 0,
                    lastPage: 1,
                    addedAt: new Date().toISOString(),
                    sizeBytes: file.size,
                  });
                  setActiveId(id);
                  setUploading(null);
                };
                reader.onerror = () => {
                  setUploading(null);
                  alert('Could not read that file. Please try again.');
                };
                reader.readAsArrayBuffer(file);
              }}
            />
          </label>

          <ul className="space-y-2 mt-4">
            {pdfs.length === 0 && <li className="text-sm text-[var(--ink-muted)]">No documents yet.</li>}
            {pdfs.map((p) => (
              <li key={p.id} className="flex items-center gap-2 text-sm">
                <button
                  className={cx('flex-1 text-left rounded-md p-2 min-h-[44px]', activeId === p.id ? 'bg-blue-50 dark:bg-blue-950/40' : 'hover:bg-slate-50 dark:hover:bg-slate-800')}
                  onClick={() => setActiveId(p.id)}
                >
                  <div className="font-medium line-clamp-1">{p.name}</div>
                  <div className="text-xs text-[var(--ink-muted)]">
                    page {p.lastPage}{p.pageCount ? `/${p.pageCount}` : ''} · {(p.sizeBytes / 1024 / 1024).toFixed(1)} MB
                  </div>
                </button>
                <ConfirmButton
                  label="🗑"
                  confirmLabel="Sure?"
                  className="btn-ghost !p-2 !min-h-[36px]"
                  onConfirm={() => {
                    deleteBlob(p.id);
                    removePdf(p.id);
                    if (activeId === p.id) setActiveId(null);
                  }}
                />
              </li>
            ))}
          </ul>

          <div className="mt-4 text-xs text-[var(--ink-muted)] space-y-1">
            {COLORS.map((c) => (
              <div key={c.id} className="flex items-center gap-2">
                <span className={cx('h-3 w-3 rounded-sm inline-block', `hl-${c.id}`)} />
                {c.id.toLowerCase()} — {settings.highlightColors[c.id]}
              </div>
            ))}
          </div>
        </Card>

        <div className="lg:col-span-2 space-y-4">
          {activeId ? (
            <MemoPdfViewer
              pdfId={activeId}
              onPageText={handlePageText}
              onSelection={handleSelection}
              annotations={activeAnnotations}
            />
          ) : (
            <EmptyState
              icon="📄"
              title="Open a document to start reading"
              hint="PDFs are processed locally in your browser. Text search works on text-based PDFs; scanned documents render but have no extractable text."
            />
          )}

          {selection && activeId && (
            <Card>
              <SectionTitle icon="🖍️">Selection actions</SectionTitle>
              <p className="text-xs text-[var(--ink-muted)] mb-2">“{selection.slice(0, 120)}…”</p>
              <div className="flex flex-wrap gap-2">
                {COLORS.map((c) => (
                  <button
                    key={c.id}
                    className={cx('btn-ghost !py-1.5 !px-3 !min-h-[36px] text-xs', `hl-${c.id}`)}
                    onClick={() => {
                      addPdfAnnotation({
                        id: `ann-${Date.now()}`,
                        pdfId: activeId,
                        page: 0,
                        type: 'HIGHLIGHT',
                        color: c.id,
                        selectedText: selection,
                        note: '',
                        createdAt: new Date().toISOString(),
                      });
                      setSelection(null);
                    }}
                  >
                    {c.id.toLowerCase()}
                  </button>
                ))}
              </div>
            </Card>
          )}

          <Card>
            <SectionTitle icon="🤖" action={
              <>
                <Badge tone={authed ? 'success' : 'warn'}>
                  {authed ? 'sync on' : 'AI ready · no account'}
                </Badge>
                <Badge tone={settings.apiBaseUrl ? 'success' : 'warn'}>
                  {settings.apiBaseUrl ? 'server configured' : 'offline'}
                </Badge>
              </>
            }>
              AI review tools
            </SectionTitle>
            <p className="text-xs text-[var(--ink-muted)] mb-2">
              No account needed — AI works on this device right away. Every answer cites the
              source page or passage — and says so when the document does not contain the answer.
            </p>
            <div className="flex flex-wrap gap-2">
              <button className="btn-ghost text-xs" disabled={aiBusy || !pageText} onClick={() => setScopeFor('summarize')}>
                Summarize…
              </button>
              <button className="btn-ghost text-xs" disabled={aiBusy || !(selection || pageText)}
                onClick={() => runAi(() => api.aiKeyterms(selection || pageText), 'Key terms')}>
                Extract key terms
              </button>
              <button className="btn-secondary text-xs" disabled={aiBusy || !pageText} onClick={() => setScopeFor('cards')}>
                Turn into flashcards
              </button>
              <button className="btn-secondary text-xs" disabled={aiBusy || !pageText} onClick={() => setScopeFor('quiz')}>
                Make me a quiz
              </button>
              <button className="btn-ghost text-xs" disabled={aiBusy || !(selection || pageText)}
                onClick={() => runAi(() => api.aiExplain(selection || pageText, settings.explanationStyle), 'Simple explanation')}>
                Explain simply
              </button>
            </div>
            {aiBusy && (
              <div className="card-soft p-3 mt-3 flex items-center gap-3" role="status" aria-live="polite">
                <Mochi mood="studying" size={48} />
                <div className="flex-1">
                  <p className="text-sm font-semibold">
                    {genProgress ??
                      (busyAction === 'flashcards' ? 'Writing your flashcards…'
                        : busyAction === 'quiz' ? 'Writing your quiz…'
                        : busyAction ? `${busyAction}…` : 'Working…')}
                  </p>
                  <div className="flex gap-1.5 mt-1.5"><span className="buffer-dot" /><span className="buffer-dot" /><span className="buffer-dot" /></div>
                </div>
              </div>
            )}
            {aiError && (
              <p className="text-sm mt-3 rounded-md bg-amber-50 dark:bg-amber-950/40 p-3">{aiError}</p>
            )}
            {aiResult && (
              <pre className="text-xs mt-3 whitespace-pre-wrap rounded-md bg-slate-50 dark:bg-slate-800 p-3 max-h-64 overflow-auto">{aiResult}</pre>
            )}
          </Card>
        </div>
      </div>

      {aiNotes.length > 0 && (
        <Card>
          <SectionTitle icon="🗒️">Your saved AI notes ({aiNotes.length})</SectionTitle>
          <p className="text-xs mb-2" style={{ color: 'var(--muted)' }}>
            Every summary, key-term list, and explanation is saved here so you can find it again.
          </p>
          <ul className="space-y-2">
            {aiNotes.map((n) => (
              <li key={n.id} className="rounded-[12px] p-3" style={{ background: 'var(--surface-2)' }}>
                <div className="flex items-center justify-between gap-2">
                  <strong className="text-sm">{n.title}</strong>
                  <ConfirmButton label="Delete" confirmLabel="Confirm?" className="chip"
                    onConfirm={() => removeAiNote(n.id)} />
                </div>
                <p className="text-xs mt-1 whitespace-pre-wrap max-h-24 overflow-auto">{n.text}</p>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {scopeFor && (
        <GenScopeDialog
          kind={scopeFor}
          hasSelection={Boolean(selection)}
          defaultTo={1}
          onClose={() => setScopeFor(null)}
          onChoose={(choice) => void handleScope(choice)}
        />
      )}
      {(genCards !== null || genLoading === 'cards') && (
        <GenCardsDialog
          key={(genCards ?? []).length}
          cards={genCards ?? []}
          loading={genLoading === 'cards'}
          pdfId={activeId}
          onClose={() => { setGenCards(null); setGenLoading(null); }}
        />
      )}
      {(genQuestions !== null || genLoading === 'quiz') && (
        <GenQuizDialog
          key={(genQuestions ?? []).length}
          questions={genQuestions ?? []}
          loading={genLoading === 'quiz'}
          label={selection ? 'your selection' : 'this page'}
          onStart={(sel, label) => startGeneratedQuiz(sel, label)}
          onClose={() => { setGenQuestions(null); setGenLoading(null); }}
        />
      )}
    </div>
  );
}

function PdfViewer({
  pdfId, onPageText, onSelection, annotations,
}: {
  pdfId: string;
  onPageText: (t: string) => void;
  onSelection: (t: string | null) => void;
  annotations: PdfAnnotation[];
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const renderTaskRef = useRef<{ cancel: () => void } | null>(null);
  const [doc, setDoc] = useState<{ numPages: number } | null>(null);
  const [page, setPage] = useState(1);
  const [scale, setScale] = useState(1.2);
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<number[]>([]);
  const [textItems, setTextItems] = useState<{ str: string; style: React.CSSProperties }[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const pdfjs = await import('pdfjs-dist');
        pdfjs.GlobalWorkerOptions.workerSrc = `${process.env.NEXT_PUBLIC_BASE_PATH || ''}/pdf.worker.mjs`;
        const data = await loadBlob(pdfId);
        if (!data) {
          setError('Stored file not found in this browser. Re-upload the PDF.');
          return;
        }
        const pdf = await pdfjs.getDocument({ data }).promise;
        if (cancelled) return;
        setDoc({ numPages: pdf.numPages });
        (window as unknown as { __pdf?: unknown }).__pdf = pdf;
      } catch (e) {
        setError(`Could not open PDF: ${String(e)}`);
      }
    })();
    return () => {
      cancelled = true;
      renderTaskRef.current?.cancel();
    };
  }, [pdfId]);

  useEffect(() => {
    const pdf = (window as unknown as { __pdf?: { getPage: (n: number) => Promise<unknown>; numPages: number } }).__pdf;
    if (!pdf || !canvasRef.current) return;
    let cancelled = false;
    (async () => {
      try {
        const pageObj = (await pdf.getPage(page)) as {
          getViewport: (o: { scale: number }) => { width: number; height: number };
          render: (o: { canvasContext: unknown; viewport: unknown }) => { promise: Promise<void>; cancel: () => void };
          getTextContent: () => Promise<{ items: { str: string; transform: number[]; width: number; height: number }[] }>;
        };
        const viewport = pageObj.getViewport({ scale });
        const canvas = canvasRef.current!;
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        const ctx = canvas.getContext('2d')!;
        const task = pageObj.render({ canvasContext: ctx, viewport });
        renderTaskRef.current = task;
        await task.promise;
        if (cancelled) return;

        const content = await pageObj.getTextContent();
        const items = content.items
          .filter((it) => it.str.trim())
          .map((it) => {
            const [a, b, c, d, e, f] = it.transform;
            return {
              str: it.str,
              style: {
                position: 'absolute' as const,
                left: `${e * (scale / 1.2)}px`,
                top: `${(viewport.height - f * (scale / 1.2) - it.height * scale)}px`,
                fontSize: `${Math.abs(d) * (scale / 1.2) * 0.9}px`,
                lineHeight: 1.1,
                whiteSpace: 'pre' as const,
                color: 'transparent',
                cursor: 'text',
              },
            };
          });
        setTextItems(items);
        onPageText(content.items.map((i) => i.str).join(' '));
      } catch {
        // render cancelled
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, scale, doc]);

  const search = useCallback(() => {
    const pdf = (window as unknown as { __pdf?: { numPages: number; getPage: (n: number) => Promise<{ getTextContent: () => Promise<{ items: { str: string }[] }> }> } }).__pdf;
    if (!pdf || !query.trim()) return;
    (async () => {
      const found: number[] = [];
      for (let p = 1; p <= pdf.numPages; p++) {
        const pg = await pdf.getPage(p);
        const tc = await pg.getTextContent();
        const text = tc.items.map((i) => i.str).join(' ');
        if (text.toLowerCase().includes(query.toLowerCase())) found.push(p);
      }
      setHits(found);
      if (found.length) setPage(found[0]);
    })();
  }, [query]);

  return (
    <Card>
      <div className="flex flex-wrap items-center gap-2">
        <button className="btn-ghost !p-2" onClick={() => setPage((p) => Math.max(1, p - 1))} aria-label="Previous page">◀</button>
        <span className="text-sm">
          Page <input
            type="number"
            className="input !w-16 !py-1 text-center"
            value={page}
            min={1}
            max={doc?.numPages ?? 1}
            onChange={(e) => setPage(Math.min(doc?.numPages ?? 1, Math.max(1, Number(e.target.value))))}
          /> of {doc?.numPages ?? '…'}
        </span>
        <button className="btn-ghost !p-2" onClick={() => setPage((p) => Math.min(doc?.numPages ?? 1, p + 1))} aria-label="Next page">▶</button>
        <button className="btn-ghost text-xs" onClick={() => setScale((s) => Math.max(0.6, s - 0.2))}>−</button>
        <button className="btn-ghost text-xs" onClick={() => setScale((s) => Math.min(3, s + 0.2))}>+</button>
        <div className="flex items-center gap-1 ml-auto">
          <input className="input !w-40 !py-1" placeholder="Search in PDF…" value={query}
            onChange={(e) => setQuery(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && search()} />
          <button className="btn-ghost text-xs" onClick={search}>Find</button>
          {hits.length > 0 && <Badge tone="info">{hits.length} pages</Badge>}
        </div>
      </div>

      {error && <p className="text-sm text-red-600 mt-3">{error}</p>}

      <div className="relative mt-3 overflow-auto max-h-[70vh] rounded-md bg-slate-100 dark:bg-slate-900 flex justify-center">
        <div className="relative">
          <canvas ref={canvasRef} className="block shadow-lift" onMouseUp={() => onSelection(window.getSelection()?.toString() ?? null)} />
          <div className="absolute inset-0 overflow-hidden select-text" aria-hidden={false} aria-label="Text layer for selection">
            {textItems.map((it, i) => (
              <span key={i} style={it.style}>{it.str}{' '}</span>
            ))}
          </div>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 mt-3 text-xs">
        <button className="btn-ghost !py-1.5 !min-h-[36px]" onClick={() => setPdfLastPage(pdfId, page)}>
          🔖 Bookmark page {page} (resume point)
        </button>
        {hits.map((h) => (
          <button key={h} className="chip" onClick={() => setPage(h)}>hit p.{h}</button>
        ))}
      </div>

      {annotations.length > 0 && (
        <div className="mt-4">
          <SectionTitle icon="🖍️">Annotations</SectionTitle>
          <ul className="space-y-2">
            {annotations.map((a) => (
              <li key={a.id} className="text-sm rounded-md border border-slate-200 dark:border-slate-700 p-2">
                <div className="flex items-start justify-between gap-2">
                  <span className={`hl-${a.color} rounded px-1`}>{a.selectedText.slice(0, 160)}</span>
                  <ConfirmButton label="✕" confirmLabel="Delete?" className="btn-ghost !p-1 !min-h-[28px] text-xs"
                    onConfirm={() => removePdfAnnotation(a.id)} />
                </div>
                <div className="text-xs text-[var(--ink-muted)] mt-1">{a.color.toLowerCase()} · page {a.page || '?'}</div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}

async function saveBlob(id: string, blob: Blob): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction('files', 'readwrite');
    tx.objectStore('files').put(blob, id);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

async function loadBlob(id: string): Promise<ArrayBuffer | null> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('files', 'readonly');
    const req = tx.objectStore('files').get(id);
    req.onsuccess = async () => {
      const blob = req.result as Blob | undefined;
      resolve(blob ? await blob.arrayBuffer() : null);
    };
    req.onerror = () => reject(req.error);
  });
}

async function deleteBlob(id: string): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve) => {
    const tx = db.transaction('files', 'readwrite');
    tx.objectStore('files').delete(id);
    tx.oncomplete = () => resolve();
  });
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open('mtrh-pdfs', 1);
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains('files')) req.result.createObjectStore('files');
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

const MemoPdfViewer = React.memo(PdfViewer);

// ---- Anki-style review-before-add dialogs ----

function GenScopeDialog({ kind, hasSelection, defaultTo, onClose, onChoose }: {
  kind: 'cards' | 'quiz' | 'summarize';
  hasSelection: boolean;
  defaultTo: number;
  onClose: () => void;
  onChoose: (choice: { mode: 'selection' | 'page' | 'whole' | 'pages'; from?: number; to?: number }) => void;
}) {
  const [from, setFrom] = useState(defaultTo);
  const [to, setTo] = useState(defaultTo);
  const label = kind === 'cards' ? 'flashcards' : kind === 'quiz' ? 'a quiz' : 'a summary';
  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div className="card p-5 w-full max-w-md">
        <SectionTitle icon="🎯">What should become {label}?</SectionTitle>
        <div className="space-y-2">
          {hasSelection && (
            <button className="btn-primary w-full justify-start" onClick={() => onChoose({ mode: 'selection' })}>
              🖍 My selection
            </button>
          )}
          <button className="btn-secondary w-full justify-start" onClick={() => onChoose({ mode: 'page' })}>
            📄 This page
          </button>
          <button className="btn-secondary w-full justify-start" onClick={() => onChoose({ mode: 'whole' })}>
            📚 The whole PDF (best for making a full deck)
          </button>
          <div className="rounded-[12px] p-3" style={{ background: 'var(--surface-2)' }}>
            <p className="text-xs mb-2">Or a page range:</p>
            <div className="flex items-center gap-2">
              <input className="input !w-20" type="number" min={1} value={from} onChange={(e) => setFrom(Number(e.target.value))} aria-label="From page" />
              <span>to</span>
              <input className="input !w-20" type="number" min={1} value={to} onChange={(e) => setTo(Number(e.target.value))} aria-label="To page" />
              <button className="btn-ghost text-xs" onClick={() => onChoose({ mode: 'pages', from: Math.min(from, to), to: Math.max(from, to) })}>
                Go
              </button>
            </div>
          </div>
        </div>
        <button className="btn-ghost w-full mt-4" onClick={onClose}>Cancel</button>
      </div>
    </div>
  );
}

function GenCardsDialog({ cards, loading, pdfId, onClose }: {
  cards: ParsedFlashcard[]; loading: boolean; pdfId: string | null; onClose: () => void;
}) {
  const [checked, setChecked] = useState<boolean[]>(cards.map(() => true));
  const [edits, setEdits] = useState(cards.map((c) => ({ front: c.front, back: c.back })));
  const [done, setDone] = useState(0);

  const addSelected = () => {
    const chosen = cards.map((c, i) => ({ ...c, ...edits[i], on: checked[i] })).filter((c) => c.on);
    chosen.forEach((c, i) => {
      upsertCard({
        id: `fc-pdf-${Date.now()}-${i}`,
        deckId: 'from-my-pdfs',
        front: c.front,
        back: c.back,
        type: 'BASIC',
        tags: ['from-pdf'],
        subjectId: 'custom',
        topicId: null,
        difficulty: 2,
        note: c.quote ? `Source: “${c.quote}”` : '',
        favorite: false,
        imageRef: null,
        source: { kind: 'PDF_HIGHLIGHT', pdfId, page: null, needsReview: false },
        schedule: {
          state: 'NEW', stability: 0, difficulty: 0,
          dueAt: new Date().toISOString(), lastReviewAt: null, reps: 0, lapses: 0,
        },
        history: [],
      });
    });
    setDone(chosen.length);
    setTimeout(onClose, 1200);
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div className="card p-5 w-full max-w-2xl max-h-[85vh] overflow-auto">
        <SectionTitle icon="🃏">Review flashcards before adding</SectionTitle>
        <p className="text-xs mb-3" style={{ color: 'var(--muted)' }}>
          AI drafted these from your document — edit anything, untick what you do not want,
          then add them to your real review deck (they will be scheduled with spaced repetition).
        </p>
        {loading && (
          <div className="card-soft p-4 mb-3 flex items-center gap-3" role="status" aria-live="polite">
            <Mochi mood="studying" size={56} />
            <div className="flex-1">
              <p className="text-sm font-semibold">Mochi is writing your flashcards…</p>
              <div className="flex gap-1.5 mt-2"><span className="buffer-dot" /><span className="buffer-dot" /><span className="buffer-dot" /></div>
            </div>
          </div>
        )}
        {loading && [0, 1, 2].map((i) => (
          <div key={i} className="rounded-[12px] p-3 space-y-2" style={{ background: 'var(--surface-2)' }}>
            <div className="h-10 rounded-md shimmer" />
            <div className="h-14 rounded-md shimmer" />
          </div>
        ))}
        {done > 0 && <p className="sticky-note mb-3">🌸 Added {done} cards to your deck. Find them under Flashcards!</p>}
        <ul className="space-y-3">
          {cards.map((c, i) => (
            <li key={i} className="rounded-[12px] p-3" style={{ background: 'var(--surface-2)' }}>
              <label className="flex items-start gap-2">
                <input type="checkbox" checked={checked[i] ?? true} className="mt-1"
                  onChange={(e) => setChecked((arr) => arr.map((v, j) => (j === i ? e.target.checked : v)))} />
                <div className="flex-1 space-y-2">
                  <input className="input" value={edits[i]?.front ?? c.front}
                    onChange={(e) => setEdits((arr) => arr.map((v, j) => (j === i ? { ...v, front: e.target.value } : v)))} />
                  <textarea className="input" rows={2} value={edits[i]?.back ?? c.back}
                    onChange={(e) => setEdits((arr) => arr.map((v, j) => (j === i ? { ...v, back: e.target.value } : v)))} />
                  {c.quote && <p className="text-[11px]" style={{ color: 'var(--muted)' }}>source: “{c.quote}”</p>}
                </div>
              </label>
            </li>
          ))}
        </ul>
        <div className="flex justify-end gap-2 mt-4">
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" onClick={addSelected} disabled={loading || !checked.some(Boolean)}>
            {loading ? 'Generating…' : `Add ${checked.filter(Boolean).length} cards to my deck`}
          </button>
        </div>
        {done > 0 && (
          <p className="text-xs mt-2" style={{ color: 'var(--muted)' }}>
            Find them under <Link href="/flashcards/" className="link-cute">Flashcards → deck “From my PDFs”</Link> —
            they are scheduled with spaced repetition automatically.
          </p>
        )}
      </div>
    </div>
  );
}

function GenQuizDialog({ questions, loading, label, onStart, onClose }: {
  questions: ParsedQuestion[]; loading: boolean; label: string;
  onStart: (selected: ParsedQuestion[], label: string) => void; onClose: () => void;
}) {
  const [checked, setChecked] = useState<boolean[]>(questions.map(() => true));
  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div className="card p-5 w-full max-w-2xl max-h-[85vh] overflow-auto">
        <SectionTitle icon="📝">Quiz questions from {label}</SectionTitle>
        <p className="text-xs mb-3" style={{ color: 'var(--muted)' }}>
          Review before you start — untick anything that looks off. These questions are saved
          to your collection so you can practice them again later.
        </p>
        {loading && (
          <div className="card-soft p-4 mb-3 flex items-center gap-3" role="status" aria-live="polite">
            <Mochi mood="studying" size={56} />
            <div className="flex-1">
              <p className="text-sm font-semibold">Mochi is writing your quiz…</p>
              <div className="flex gap-1.5 mt-2"><span className="buffer-dot" /><span className="buffer-dot" /><span className="buffer-dot" /></div>
            </div>
          </div>
        )}
        {loading && [0, 1, 2].map((i) => (
          <div key={i} className="rounded-[12px] p-3 space-y-2" style={{ background: 'var(--surface-2)' }}>
            <div className="h-6 rounded-md shimmer w-3/4" />
            <div className="h-12 rounded-md shimmer" />
          </div>
        ))}
        <ul className="space-y-3">
          {questions.map((q, i) => (
            <li key={i} className="rounded-[12px] p-3" style={{ background: 'var(--surface-2)' }}>
              <label className="flex items-start gap-2">
                <input type="checkbox" checked={checked[i] ?? true} className="mt-1"
                  onChange={(e) => setChecked((arr) => arr.map((v, j) => (j === i ? e.target.checked : v)))} />
                <div className="flex-1 text-sm">
                  <p className="font-semibold">{q.stem}</p>
                  <ul className="mt-1 text-xs space-y-0.5">
                    {q.choices.map((c) => (
                      <li key={c.id} className={cx(c.id === q.correctChoiceId && 'font-semibold')}>
                        {c.id}. {c.text} {c.id === q.correctChoiceId && '✓'}
                      </li>
                    ))}
                  </ul>
                  <p className="text-xs mt-1" style={{ color: 'var(--muted)' }}>{q.rationale}</p>
                </div>
              </label>
            </li>
          ))}
        </ul>
        <div className="flex justify-end gap-2 mt-4">
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" onClick={() => onStart(questions.filter((_, i) => checked[i]), label)}
            disabled={loading || !checked.some(Boolean)}>
            {loading ? 'Generating…' : `Start quiz with ${checked.filter(Boolean).length} questions`}
          </button>
        </div>
        <p className="text-xs mt-2" style={{ color: 'var(--muted)' }}>
          Saved automatically — practice them again anytime from{' '}
          <Link href="/quiz/" className="link-cute">Quiz → “From my materials”</Link>.
        </p>
      </div>
    </div>
  );
}
