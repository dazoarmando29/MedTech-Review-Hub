'use client';

// Original hand-drawn SVG illustrations for visual identification practice.
// All artwork is original (no copyrighted images) per shared/DESIGN.md.
import React from 'react';

const S = { width: 200, height: 160, viewBox: '0 0 200 160' } as const;

export function LabVisual({ id, size = 200 }: { id: string; size?: number }) {
  const art = VISUALS[id] ?? VISUALS['rbc-smear'];
  return (
    <svg {...S} width={size} height={(size * 160) / 200} role="img" aria-label={`Laboratory illustration: ${id}`}>
      <rect width="200" height="160" rx="16" fill="#FFF7FA" stroke="#F6D5E2" strokeWidth="2" />
      {art}
    </svg>
  );
}

const VISUALS: Record<string, React.ReactElement> = {
  'neutrophil': (
    <g>
      <circle cx="100" cy="80" r="46" fill="#F9D5E5" stroke="#D4789C" strokeWidth="3" />
      <path d="M72 72 q8 -16 22 -6 q10 -14 22 -2 q12 -8 14 8 q-6 14 -22 10 q-12 12 -24 2 q-12 4 -12 -12z" fill="#8B4C7B" />
      <circle cx="70" cy="96" r="4" fill="#E8A0C0" /><circle cx="88" cy="104" r="3.5" fill="#E8A0C0" />
      <circle cx="116" cy="98" r="4" fill="#E8A0C0" /><circle cx="130" cy="86" r="3" fill="#E8A0C0" />
    </g>
  ),
  'lymphocyte': (
    <g>
      <circle cx="100" cy="80" r="40" fill="#FBE3EE" stroke="#D4789C" strokeWidth="3" />
      <circle cx="100" cy="80" r="30" fill="#5C2E52" />
      <path d="M84 66 q10 -8 22 -2" stroke="#fff" strokeWidth="4" fill="none" strokeLinecap="round" opacity="0.5" />
    </g>
  ),
  'monocyte': (
    <g>
      <circle cx="100" cy="80" r="48" fill="#F7D9E7" stroke="#D4789C" strokeWidth="3" />
      <path d="M64 84 q0 -32 34 -30 q28 2 30 26 q2 22 -20 28 q-8 -12 -18 -10 q-16 2 -26 -14z" fill="#6E3B63" />
      <circle cx="76" cy="100" r="3" fill="#E8A0C0" /><circle cx="126" cy="96" r="3" fill="#E8A0C0" />
    </g>
  ),
  'eosinophil': (
    <g>
      <circle cx="100" cy="80" r="46" fill="#FBEAF2" stroke="#D4789C" strokeWidth="3" />
      <path d="M78 66 q12 -12 22 0 q12 -12 22 2 q-4 14 -20 12 q-14 8 -24 -14z" fill="#7B3F6E" />
      {[[72, 94], [84, 104], [100, 100], [116, 102], [128, 92], [92, 110], [112, 112], [132, 82]].map(([x, y], i) => (
        <circle key={i} cx={x} cy={y} r="5" fill="#E8574B" />
      ))}
    </g>
  ),
  'basophil': (
    <g>
      <circle cx="100" cy="80" r="46" fill="#F8E6F0" stroke="#D4789C" strokeWidth="3" />
      <path d="M80 72 q12 -14 24 -4 q14 -10 20 6 q-2 16 -18 14 q-16 6 -26 -16z" fill="#6B3A66" />
      {[[74, 96], [88, 106], [104, 102], [120, 98], [130, 86], [82, 88], [112, 112], [96, 112]].map(([x, y], i) => (
        <circle key={i} cx={x} cy={y} r="6" fill="#3F2B56" />
      ))}
    </g>
  ),
  'rbc-smear': (
    <g>
      {[[70, 66], [110, 58], [134, 86], [92, 96], [62, 104], [120, 116], [86, 128]].map(([x, y], i) => (
        <g key={i}>
          <circle cx={x} cy={y} r="17" fill="#E8919B" />
          <circle cx={x} cy={y} r="8" fill="#D96A78" />
        </g>
      ))}
    </g>
  ),
  'gram-positive-cocci': (
    <g>
      {[[60, 60], [80, 52], [72, 78], [100, 64], [118, 52], [128, 78], [96, 92], [66, 106], [142, 62], [110, 112], [86, 122], [132, 104]].map(([x, y], i) => (
        <circle key={i} cx={x} cy={y} r="11" fill="#8B5CF6" stroke="#6D28D9" strokeWidth="2" />
      ))}
    </g>
  ),
  'gram-negative-bacilli': (
    <g>
      {[[70, 56, -18], [110, 66, 12], [86, 96, -8], [128, 100, 22], [62, 118, 8], [142, 62, -14]].map(([x, y, r], i) => (
        <rect key={i} x={x - 20} y={y - 8} width="40" height="16" rx="8" fill="#F472B6" stroke="#DB2777" strokeWidth="2" transform={`rotate(${r} ${x} ${y})`} />
      ))}
    </g>
  ),
  'yeast-budding': (
    <g>
      <ellipse cx="88" cy="84" rx="34" ry="28" fill="#DDD6FE" stroke="#8B5CF6" strokeWidth="3" />
      <ellipse cx="126" cy="66" rx="18" ry="15" fill="#C4B5FD" stroke="#8B5CF6" strokeWidth="3" />
      <path d="M104 70 q8 -6 12 -8" stroke="#8B5CF6" strokeWidth="3" fill="none" />
      <ellipse cx="80" cy="82" rx="10" ry="8" fill="#8B5CF6" opacity="0.35" />
    </g>
  ),
  'calcium-oxalate-crystal': (
    <g>
      <path d="M100 36 L142 80 L100 124 L58 80 Z" fill="#BFDBFE" stroke="#60A5FA" strokeWidth="3" />
      <path d="M100 56 L122 80 L100 104 L78 80 Z" fill="#FFFFFF" opacity="0.7" />
      <path d="M100 36 L100 124 M58 80 L142 80" stroke="#60A5FA" strokeWidth="2" />
    </g>
  ),
  'cast-hyaline': (
    <g>
      <rect x="40" y="66" width="120" height="30" rx="15" fill="#E0F2FE" stroke="#7DD3FC" strokeWidth="3" />
      <path d="M52 81 h96" stroke="#BAE6FD" strokeWidth="4" strokeDasharray="2 8" strokeLinecap="round" />
      <rect x="40" y="66" width="10" height="30" rx="5" fill="#7DD3FC" opacity="0.7" />
    </g>
  ),
  'sickle-cell': (
    <g>
      <path d="M60 96 q28 -70 76 -46 q-18 16 -28 34 q-12 22 -28 26 q-14 2 -20 -14z" fill="#E8919B" stroke="#C2566B" strokeWidth="3" />
      <circle cx="122" cy="100" r="14" fill="#D96A78" />
    </g>
  ),
};
