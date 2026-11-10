'use client';

// Shared UI primitives (Pink Study Companion — shared/DESIGN.md v2).
import Link from 'next/link';
import React, { useEffect, useRef, useState } from 'react';
import type { TableData } from '@/lib/types';
import { Mochi, type MochiMood } from './Mochi';

export function cx(...parts: (string | false | null | undefined)[]): string {
  return parts.filter(Boolean).join(' ');
}

export function Card({ children, className, style }: { children: React.ReactNode; className?: string; style?: React.CSSProperties }) {
  return <div className={cx('card p-5', className)} style={style}>{children}</div>;
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
  label, value, sub, href,
}: {
  label: string; value: React.ReactNode; sub?: string; href?: string;
}) {
  const body = (
    <div className="card p-4 flex flex-col gap-1 hover:shadow-lift transition-shadow">
      <div className="text-[11px] font-semibold uppercase tracking-wide" style={{ color: 'var(--muted)' }}>{label}</div>
      <div className="stat-number">{value}</div>
      {sub && <div className="text-[11px]" style={{ color: 'var(--muted)' }}>{sub}</div>}
    </div>
  );
  return href ? <Link href={href} className="block">{body}</Link> : body;
}

export function ProgressRing({ percent, size = 72, stroke = 8, label }: {
  percent: number; size?: number; stroke?: number; label?: string;
}) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const p = Math.min(1, Math.max(0, percent));
  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} role="img" aria-label={label ?? `${Math.round(p * 100)}%`}>
        <defs>
          <linearGradient id="petal-grad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="var(--primary)" />
            <stop offset="100%" stopColor="var(--primary-soft)" />
          </linearGradient>
        </defs>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} stroke="var(--chip)" />
        <circle
          cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} stroke="url(#petal-grad)"
          strokeDasharray={`${c * p} ${c}`} strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <span className="absolute text-xs font-bold" style={{ color: 'var(--primary)' }}>{Math.round(p * 100)}%</span>
    </div>
  );
}

export function Badge({ children, tone = 'default' }: { children: React.ReactNode; tone?: 'default' | 'success' | 'warn' | 'danger' | 'info' }) {
  const tones: Record<string, string> = {
    default: 'var(--chip)',
    success: '#dcfce7',
    warn: '#fef3c7',
    danger: '#ffe4e6',
    info: '#ede9fe',
  };
  const inks: Record<string, string> = {
    default: 'var(--muted-strong)',
    success: '#166534',
    warn: '#92400e',
    danger: '#9f1239',
    info: '#5b21b6',
  };
  return (
    <span
      className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold"
      style={{ background: tones[tone], color: inks[tone] }}
    >
      {children}
    </span>
  );
}

export function EmptyState({
  title, hint, action, icon = '🌸', mood = 'happy',
}: {
  title: string; hint?: string; action?: React.ReactNode; icon?: string; mood?: MochiMood;
}) {
  return (
    <div className="card p-10 text-center flex flex-col items-center gap-3">
      <Mochi mood={mood} size={110} className="loading-mochi" />
      <div className="text-2xl" aria-hidden>{icon}</div>
      <div className="font-heading text-lg font-bold">{title}</div>
      {hint && <p className="text-sm max-w-md" style={{ color: 'var(--muted)' }}>{hint}</p>}
      {action}
    </div>
  );
}

export function DataTable({ table }: { table: TableData }) {
  return (
    <div className="overflow-x-auto rounded-[14px] border" style={{ borderColor: 'var(--line)' }}>
      <table className="w-full text-sm">
        <thead>
          <tr style={{ background: 'var(--chip)' }} className="text-left">
            {table.columns.map((c) => (
              <th key={c} className="px-3 py-2 font-semibold whitespace-nowrap">{c}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {table.rows.map((row, i) => (
            <tr key={i} className="border-t" style={{ borderColor: 'var(--line)' }}>
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
    if (p.startsWith('`') && p.endsWith('`')) {
      return (
        <code key={i} className="font-mono text-[0.9em] rounded px-1" style={{ background: 'var(--chip)' }}>
          {p.slice(1, -1)}
        </code>
      );
    }
    if (p.startsWith('*') && p.endsWith('*') && p.length > 2) return <em key={i}>{p.slice(1, -1)}</em>;
    return <React.Fragment key={i}>{p}</React.Fragment>;
  });
}

export function Tabs({ tabs, active, onChange }: {
  tabs: { id: string; label: string }[]; active: string; onChange: (id: string) => void;
}) {
  return (
    <div role="tablist" className="flex gap-2 flex-wrap">
      {tabs.map((t) => {
        const on = active === t.id;
        return (
          <button
            key={t.id}
            role="tab"
            aria-selected={on}
            onClick={() => onChange(t.id)}
            className={cx('btn text-sm', on ? 'btn-primary' : 'btn-ghost')}
          >
            {t.label}
          </button>
        );
      })}
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

/** Small decorative stars (hidden in minimal/low-bandwidth via body.no-decor). */
export function Stars({ className }: { className?: string }) {
  return (
    <span className={cx('decor-only', className)} aria-hidden>
      <span className="decor-star">✦</span>{' '}
      <span className="decor-star text-xs">✧</span>
    </span>
  );
}
