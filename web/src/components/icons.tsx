'use client';

// Original minimal SVG icon set (DESIGN.md v2 — no emoji glyphs in UI chrome).
import React from 'react';

type P = { size?: number; className?: string };

const base = (size: number) => ({
  width: size,
  height: size,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
});

export const IconHome = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <path d="M4 11.5 12 4l8 7.5" />
    <path d="M6 10.5V20h12v-9.5" />
    <path d="M10 20v-5h4v5" />
  </svg>
);

export const IconBook = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <path d="M5 4h9a3 3 0 0 1 3 3v13H8a3 3 0 0 0-3 3z" />
    <path d="M17 7h2v13h-2" />
    <path d="M8 8h6M8 11h6" />
  </svg>
);

export const IconCards = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <rect x="6" y="6" width="12" height="15" rx="2.5" />
    <path d="M9 3.5h9a2.5 2.5 0 0 1 2.5 2.5v11" />
    <path d="M9 11h6M9 15h4" />
  </svg>
);

export const IconQuiz = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <rect x="4.5" y="3.5" width="15" height="17" rx="2.5" />
    <path d="M8.5 8.5h7M8.5 12h7M8.5 15.5h4" />
  </svg>
);

export const IconPdf = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <path d="M7 3.5h7l4 4V20a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 6 20V5a1.5 1.5 0 0 1 1-1.5z" />
    <path d="M14 3.5V8h4" />
    <path d="M9 13h6M9 16.5h4" />
  </svg>
);

export const IconPlanner = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <rect x="4" y="5.5" width="16" height="15" rx="2.5" />
    <path d="M4 10h16M8.5 3.5v4M15.5 3.5v4" />
    <path d="M8 14h2M14 14h2M8 17.5h2" />
  </svg>
);

export const IconHabits = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <path d="M5 12.5 9.5 17 19 7.5" />
    <rect x="3.5" y="3.5" width="17" height="17" rx="4" />
  </svg>
);

export const IconGarden = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <path d="M12 20v-7" />
    <path d="M12 13c-3.5 0-5-2-5-4.5S9 5 12 8c3-3 5-1 5 1.5S15.5 13 12 13z" />
    <path d="M6 20h12" />
  </svg>
);

export const IconResources = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <path d="M12 3.5 20 7v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7z" />
    <path d="M9 11.5h6M9 14.5h4" />
    <circle cx="12" cy="8.5" r="1" fill="currentColor" stroke="none" />
  </svg>
);

export const IconSearch = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <circle cx="10.5" cy="10.5" r="6" />
    <path d="M15 15l5 5" />
  </svg>
);

export const IconSettings = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <circle cx="12" cy="12" r="3" />
    <path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2" />
  </svg>
);

export const IconSparkle = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <path d="M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z" />
  </svg>
);

export const IconPaw = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <circle cx="7" cy="9" r="2" />
    <circle cx="12" cy="7" r="2" />
    <circle cx="17" cy="9" r="2" />
    <path d="M12 11c-3 0-5 2-5 4.5S9.5 20 12 20s5-2 5-4.5S15 11 12 11z" />
  </svg>
);

export const IconSticker = ({ size = 20, className }: P) => (
  <svg {...base(size)} className={className} aria-hidden>
    <path d="M12 3.5l2.2 5.2 5.8.5-4.4 3.8 1.4 5.6-5-3-5 3 1.4-5.6L4 9.2l5.8-.5z" />
  </svg>
);
