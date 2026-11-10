'use client';

// Admin content management (local editorial workspace). Server-side admin lives
// at /api/v1/admin with role checks and an audit trail (backend/README.md).
import React, { useState } from 'react';
import { Badge, Card, EmptyState, SectionTitle, Tabs, cx } from '@/components/ui';
import { lessons, questionBank } from '@/lib/content';
import { adminAudit, setEditorialStatus, useStore } from '@/lib/store';

type Tab = 'lessons' | 'questions' | 'reports' | 'audit';

const STATUSES = ['DRAFT', 'IN_REVIEW', 'PUBLISHED', 'ARCHIVED'] as const;

export default function AdminPage() {
  const admin = useStore((s) => s.admin);
  const reports = useStore((s) => s.reports);
  const [tab, setTab] = useState<Tab>('lessons');
  const [filter, setFilter] = useState('');

  const lessonRows = lessons.filter((l) => !filter || l.title.toLowerCase().includes(filter.toLowerCase()));
  const questionRows = questionBank.filter((q) => !filter || q.stem.toLowerCase().includes(filter.toLowerCase()));

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-heading text-2xl font-bold">Content Management</h1>
        <p className="text-sm text-[var(--ink-muted)]">
          Editorial workspace: review statuses, track changes, and resolve reported issues.
          Publishing on the live backend requires an ADMIN account and is fully audited.
        </p>
      </div>

      <Tabs
        tabs={[
          { id: 'lessons', label: `Lessons (${lessons.length})` },
          { id: 'questions', label: `Questions (${questionBank.length})` },
          { id: 'reports', label: `Reports (${reports.length})` },
          { id: 'audit', label: `Audit (${admin.audit.length})` },
        ]}
        active={tab}
        onChange={(id) => setTab(id as Tab)}
      />

      <input className="input" placeholder="Filter…" value={filter} onChange={(e) => setFilter(e.target.value)} />

      {tab === 'lessons' && (
        <div className="space-y-2">
          {lessonRows.map((l) => {
            const status = admin.statuses[l.id] ?? l.editorialStatus;
            return (
              <Card key={l.id} className="!p-4">
                <div className="flex flex-wrap items-center gap-3">
                  <div className="flex-1 min-w-[220px]">
                    <div className="font-semibold text-sm">{l.title}</div>
                    <div className="text-xs text-[var(--ink-muted)]">
                      {l.id} · reviewed {l.reviewedAt} · {l.references.length} reference(s)
                    </div>
                  </div>
                  <Badge tone={status === 'PUBLISHED' ? 'success' : status === 'ARCHIVED' ? 'default' : 'warn'}>
                    {status.toLowerCase()}
                  </Badge>
                  <div className="flex gap-1">
                    {STATUSES.map((s) => (
                      <button
                        key={s}
                        className={cx('btn !py-1 !px-2 !min-h-[32px] text-[11px]', status === s ? 'bg-[var(--brand)] text-white' : 'btn-ghost')}
                        onClick={() => {
                          setEditorialStatus(l.id, s);
                          adminAudit(`lesson:${s}`, l.id);
                        }}
                      >
                        {s.toLowerCase()}
                      </button>
                    ))}
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {tab === 'questions' && (
        <div className="space-y-2">
          {questionRows.slice(0, 50).map((q) => (
            <Card key={q.id} className="!p-4">
              <div className="flex flex-wrap items-center gap-3">
                <div className="flex-1 min-w-[220px]">
                  <div className="text-sm">{q.stem}</div>
                  <div className="text-xs text-[var(--ink-muted)]">
                    {q.id} · {q.subjectId}/{q.topicId} · correct {q.correctChoiceId} · ref: {q.reference}
                  </div>
                </div>
                <Badge tone={q.misconception ? 'success' : 'warn'}>{q.misconception ? 'rationales complete' : 'needs misconception note'}</Badge>
              </div>
            </Card>
          ))}
          {questionRows.length > 50 && (
            <p className="text-xs text-[var(--ink-muted)]">Showing 50 of {questionRows.length} — refine the filter.</p>
          )}
        </div>
      )}

      {tab === 'reports' && (
        reports.length === 0 ? (
          <EmptyState
            icon="📮"
            title="No open reports"
            hint="User-submitted issue reports (wrong answers, outdated references, technical problems) appear here."
          />
        ) : (
          <div className="space-y-2">
            {reports.map((r) => (
              <Card key={r.id} className="!p-4">
                <div className="flex items-center gap-2">
                  <Badge tone="danger">{r.targetKind.toLowerCase()}</Badge>
                  <span className="text-xs text-[var(--ink-muted)]">{r.targetId} · {r.createdAt.slice(0, 10)}</span>
                  <Badge tone={r.status === 'OPEN' ? 'warn' : 'success'}>{r.status.toLowerCase()}</Badge>
                </div>
                <p className="text-sm mt-1">{r.reason} — {r.detail}</p>
              </Card>
            ))}
          </div>
        )
      )}

      {tab === 'audit' && (
        admin.audit.length === 0 ? (
          <EmptyState icon="📜" title="No editorial changes yet" hint="Status changes and edits are logged here automatically." />
        ) : (
          <Card>
            <ul className="text-xs space-y-1">
              {admin.audit.map((a, i) => (
                <li key={i} className="flex gap-2 border-b border-slate-100 dark:border-slate-800 py-1">
                  <span className="text-[var(--ink-muted)] w-32">{a.at.slice(0, 19).replace('T', ' ')}</span>
                  <span className="font-mono">{a.action}</span>
                  <span>{a.target}</span>
                </li>
              ))}
            </ul>
          </Card>
        )
      )}

      <Card>
        <SectionTitle icon="✅">Editorial workflow</SectionTitle>
        <p className="text-sm text-[var(--ink-muted)]">
          DRAFT → IN_REVIEW → PUBLISHED → ARCHIVED. AI-generated material enters as DRAFT with a
          “needs verification” flag and cannot be published without human review. Published revisions
          are versioned on the backend (lesson_revisions) with source references preserved.
        </p>
      </Card>
    </div>
  );
}
