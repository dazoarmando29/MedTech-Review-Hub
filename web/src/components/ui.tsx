'use client';

// Shared UI primitives (design system in shared/DESIGN.md).
import Link from 'next/link';
import React, { useEffect, useRef, useState } from 'react';
import type { TableData } from '@/lib/types';

export function cx(...parts: (string | false | null | undefined)[]): string {
  return parts.filter(Boolean).join(' ');
}

export function Card({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cx('card p-5', className)}>{children}</div>;
}

export function SectionTitle({ icon, children, action }: { icon?: string; children: React.ReactNode; action?: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 mb-3">
      <h2 className="section-title">
        {icon && <span aria-hidden>{icon}</span>}
        {children}
      </h2>
      {action}
    </div>
  );
}

export function StatTile({
  label, value, sub, accent = 'var(--brand)', href,
}: {
  label: string; value: React.ReactNode; sub?: string; accent?: string; href?: string;
}) {
  const body = (
    <div className="card p-4 flex flex-col gap-1 hover:shadow-lift transition-shadow">
      <div className="text-xs font-medium text-[var(--ink-muted)] uppercase tracking-wide">{label}</div>
      <div className="font-heading text-2xl font-bold" style={{ color: accent }}>{value}</div>
      {sub && <div className="text-xs text-[var(--ink-muted)]">{sub}</div>}
    </div>
  );
  return href ? <Link href={href} className="block">{body}</Link> : body;
}

export function ProgressRing({ percent, size = 72, stroke = 7, color = 'var(--brand-2)', label }: {
  percent: number; size?: number; stroke?: number; color?: string; label?: string;
}) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const p = Math.min(1, Math.max(0, percent));
  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} role="img" aria-label={label ?? `${Math.round(p * 100)}%`}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} className="stroke-slate-200 dark:stroke-slate-700" />
        <circle
          cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} stroke={color}
          strokeDasharray={`${c * p} ${c}`} strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <span className="absolute text-xs font-bold">{Math.round(p * 100)}%</span>
    </div>
  );
}

export function Badge({ children, tone = 'default' }: { children: React.ReactNode; tone?: 'default' | 'success' | 'warn' | 'danger' | 'info' }) {
  const tones: Record<string, string> = {
    default: 'bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200',
    success: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/60 dark:text-emerald-200',
    warn: 'bg-amber-100 text-amber-800 dark:bg-amber-900/60 dark:text-amber-200',
    danger: 'bg-red-100 text-red-800 dark:bg-red-900/60 dark:text-red-200',
    info: 'bg-blue-100 text-blue-800 dark:bg-blue-900/60 dark:text-blue-200',
  };
  return <span className={cx('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold', tones[tone])}>{children}</span>;
}

export function EmptyState({ title, hint, action, icon = '🧪' }: {
  title: string; hint?: string; action?: React.ReactNode; icon?: string;
}) {
  return (
    <div className="card p-10 text-center flex flex-col items-center gap-3">
      <div className="text-4xl" aria-hidden>{icon}</div>
      <div className="font-heading text-lg font-bold">{title}</div>
      {hint && <p className="text-sm text-[var(--ink-muted)] max-w-md">{hint}</p>}
      {action}
    </div>
  );
}

export function DataTable({ table }: { table: TableData }) {
  return (
    <div className="overflow-x-auto scrollbar-thin rounded-md border border-slate-200 dark:border-slate-700">
      <table className="w-full text-sm">
        <thead>
          <tr className="bg-slate-100 dark:bg-slate-700/60 text-left">
            {table.columns.map((c) => (
              <th key={c} className="px-3 py-2 font-semibold whitespace-nowrap">{c}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {table.rows.map((row, i) => (
            <tr key={i} className="border-t border-slate-100 dark:border-slate-700/60">
              {row.map((cell, j) => (
                <td key={j} className="px-3 py-2 align-top">{cell}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Minimal markdown renderer (headings, bullets, bold, italic, inline code). */
export function RichText({ text, className }: { text: string; className?: string }) {
  const blocks = text.split(/\n{2,}/).filter((b) => b.trim());
  return (
    <div className={cx('space-y-3 leading-relaxed', className)}>
      {blocks.map((block, i) => {
        const lines = block.split('\n');
        const isList = lines.every((l) => /^\s*([-*•]|\d+\.)\s+/.test(l) || !l.trim());
        if (isList) {
          return (
            <ul key={i} className="list-disc pl-5 space-y-1">
              {lines.filter((l) => l.trim()).map((l, j) => (
                <li key={j}>{inline(l.replace(/^\s*([-*•]|\d+\.)\s+/, ''))}</li>
              ))}
            </ul>
          );
        }
        if (lines[0].startsWith('#')) {
          return <h3 key={i} className="font-heading font-bold text-base">{inline(lines[0].replace(/^#+\s*/, ''))}</h3>;
        }
        return <p key={i}>{inline(block)}</p>;
      })}
    </div>
  );
}

function inline(text: string): React.ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)/g);
  return parts.map((p, i) => {
    if (p.startsWith('**') && p.endsWith('**')) return <strong key={i}>{p.slice(2, -2)}</strong>;
    if (p.startsWith('`') && p.endsWith('`')) return <code key={i} className="font-mono text-[0.9em] bg-slate-100 dark:bg-slate-800 rounded px-1">{p.slice(1, -1)}</code>;
    if (p.startsWith('*') && p.endsWith('*') && p.length > 2) return <em key={i}>{p.slice(1, -1)}</em>;
    return <React.Fragment key={i}>{p}</React.Fragment>;
  });
}

export function Tabs({ tabs, active, onChange }: {
  tabs: { id: string; label: string }[]; active: string; onChange: (id: string) => void;
}) {
  return (
    <div role="tablist" className="flex gap-2 flex-wrap">
      {tabs.map((t) => (
        <button
          key={t.id}
          role="tab"
          aria-selected={active === t.id}
          onClick={() => onChange(t.id)}
          className={cx(
            'btn text-sm',
            active === t.id
              ? 'bg-[var(--brand)] text-white'
              : 'border border-slate-300 dark:border-slate-600 hover:bg-slate-100 dark:hover:bg-slate-700/60',
          )}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

export function ConfirmButton({ label, confirmLabel, onConfirm, className }: {
  label: string; confirmLabel: string; onConfirm: () => void; className?: string;
}) {
  const [armed, setArmed] = useState(false);
  useEffect(() => {
    if (!armed) return;
    const t = setTimeout(() => setArmed(false), 3000);
    return () => clearTimeout(t);
  }, [armed]);
  return (
    <button
      className={className ?? 'btn-ghost text-sm'}
      onClick={() => (armed ? onConfirm() : setArmed(true))}
    >
      {armed ? confirmLabel : label}
    </button>
  );
}

export function useAutoFocus<T extends HTMLElement>(): React.RefObject<T> {
  const ref = useRef<T>(null);
  useEffect(() => {
    ref.current?.focus();
  }, []);
  return ref as React.RefObject<T>;
}
