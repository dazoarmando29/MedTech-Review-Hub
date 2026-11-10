'use client';

// Philippine ASCPi Resources — verified review centers, official ASCP links,
// reading list, and exam info. Independent directory; no endorsements.
import React, { useState } from 'react';
import { Badge, Card, EmptyState, SectionTitle, Tabs } from '@/components/ui';
import { resourcesDoc } from '@/lib/content';

type Tab = 'centers' | 'official' | 'books' | 'exam';

export default function ResourcesPage() {
  const [tab, setTab] = useState<Tab>('centers');
  const doc = resourcesDoc;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-2xl font-bold">Philippine ASCPi Resources 🌸</h1>
        <p className="text-sm max-w-3xl" style={{ color: 'var(--muted-strong)' }}>
          A curated directory for Filipino Medical Technology graduates preparing for the
          MLS(ASCPi) examination — verified review centers, official ASCP BOC links, and the
          reference books examinees actually use. This is an independent listing; nobody here
          endorses our app, and we don’t distribute any provider’s proprietary materials.
        </p>
      </div>

      <Tabs
        tabs={[
          { id: 'centers', label: 'Review Centers' },
          { id: 'official', label: 'Official ASCP Links' },
          { id: 'books', label: 'Reading List' },
          { id: 'exam', label: 'ASCPi Prep Info' },
        ]}
        active={tab}
        onChange={(id) => setTab(id as Tab)}
      />

      {tab === 'centers' && (
        <div className="grid lg:grid-cols-3 gap-4">
          {doc.reviewCenters.map((c) => (
            <Card key={c.id} className="flex flex-col">
              <div className="flex items-start justify-between gap-2">
                <h2 className="font-heading font-bold">{c.name}</h2>
                <Badge tone="info">verified {c.verifiedOn}</Badge>
              </div>
              <p className="text-xs mt-1" style={{ color: 'var(--muted)' }}>{c.location}</p>
              <p className="text-sm mt-3"><strong>{c.program}</strong></p>
              <p className="text-sm mt-2" style={{ color: 'var(--muted-strong)' }}>{c.description}</p>
              <ul className="mt-3 space-y-1 text-xs">
                {c.inclusions.map((i) => (
                  <li key={i} className="flex gap-1.5">
                    <span aria-hidden style={{ color: 'var(--primary)' }}>♥</span>
                    <span>{i}</span>
                  </li>
                ))}
              </ul>
              <p className="text-xs mt-3 rounded-[10px] p-2" style={{ background: 'var(--chip)', color: 'var(--muted-strong)' }}>
                {c.postedFeeNote}
              </p>
              <p className="text-xs mt-2" style={{ color: 'var(--muted)' }}>{c.contactNote}</p>
              <a href={c.website} target="_blank" rel="noreferrer" className="btn-secondary mt-3 text-xs">
                Visit official website ↗
              </a>
            </Card>
          ))}
          {doc.reviewCenters.length === 0 && (
            <EmptyState title="Directory loading" hint="Review center entries will appear here." />
          )}
        </div>
      )}

      {tab === 'official' && (
        <div className="grid sm:grid-cols-2 gap-4">
          {doc.officialLinks.map((l) => (
            <Card key={l.id}>
              <Badge tone="info">{l.category.replace('_', ' ').toLowerCase()}</Badge>
              <h2 className="font-heading font-bold mt-2">{l.title}</h2>
              <p className="text-sm mt-1" style={{ color: 'var(--muted-strong)' }}>{l.description}</p>
              <a href={l.url} target="_blank" rel="noreferrer" className="btn-primary mt-3 text-xs">
                Open on ascp.org ↗
              </a>
            </Card>
          ))}
        </div>
      )}

      {tab === 'books' && (
        <div className="space-y-3">
          {doc.readingList.map((b) => (
            <Card key={b.id}>
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="flex-1 min-w-[240px]">
                  <h2 className="font-heading font-bold">{b.title}</h2>
                  <p className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
                    {b.authors}{b.edition ? ` · ${b.edition}` : ''}{b.publisher ? ` · ${b.publisher}` : ''}{b.year && b.year !== 'varies' ? ` · ${b.year}` : ''}
                    {b.isbn ? ` · ISBN ${b.isbn}` : ''}
                  </p>
                  <p className="text-sm mt-2">{b.description}</p>
                </div>
                <div className="flex flex-col items-end gap-1">
                  <Badge tone={b.category === 'EXAM_PREP' ? 'warn' : 'default'}>{b.category.toLowerCase()}</Badge>
                  <Badge tone={b.verified ? 'success' : 'danger'}>
                    {b.verified ? 'bibliography verified' : 'verify edition before buying'}
                  </Badge>
                </div>
              </div>
              <p className="text-xs mt-2 italic" style={{ color: 'var(--muted)' }}>⚖ {b.licenseNote}</p>
            </Card>
          ))}
        </div>
      )}

      {tab === 'exam' && (
        <div className="space-y-4">
          {doc.examInfo.map((e) => (
            <Card key={e.id}>
              <SectionTitle icon="📌">{e.title}</SectionTitle>
              <p className="text-sm leading-relaxed">{e.body}</p>
              <a href={e.sourceUrl} target="_blank" rel="noreferrer" className="link-cute text-xs">
                Source: official ASCP BOC website ↗
              </a>
            </Card>
          ))}
          <Card className="border-l-4" style={{ borderLeftColor: 'var(--primary-soft)' }}>
            <p className="text-xs leading-relaxed" style={{ color: 'var(--muted-strong)' }}>
              {doc.disclaimer}
            </p>
          </Card>
        </div>
      )}
    </div>
  );
}
