'use client';

// Original album-style illustrations for the Study Music categories
// (shared/DESIGN.md — original artwork only).

export function MusicArt({ id, size = 96, className }: { id: string; size?: number; className?: string }) {
  const art = ART[id] ?? ART['focus-orb'];
  return (
    <svg viewBox="0 0 120 120" width={size} height={size} className={className} role="img" aria-label={`Playlist art: ${id}`}>
      <rect width="120" height="120" rx="22" fill="var(--surface-2)" />
      {art}
    </svg>
  );
}

const ART: Record<string, React.ReactElement> = {
  'pink-piano': (
    <g>
      <rect x="22" y="52" width="76" height="34" rx="8" fill="#F8BBD0" stroke="#E78AB4" strokeWidth="2" />
      <rect x="22" y="42" width="76" height="12" rx="5" fill="#C9B2F5" />
      {[30, 41, 52, 63, 74, 85].map((x) => (
        <rect key={x} x={x} y="58" width="7" height="22" rx="2" fill="#FFF7FA" />
      ))}
      {[37, 59, 81].map((x) => (
        <rect key={x} x={x} y="58" width="5" height="13" rx="2" fill="#7C5A70" />
      ))}
      <circle cx="34" cy="28" r="6" fill="#F48FB1" />
      <circle cx="86" cy="24" r="5" fill="#B39DDB" />
      <path d="M52 22 q4 -8 10 -4 q-2 8 -10 4z" fill="#F8BBD0" />
    </g>
  ),
  'cat-violin': (
    <g>
      <circle cx="58" cy="46" r="22" fill="#FFF" stroke="#F3B9CD" strokeWidth="2" />
      <path d="M42 32 l-4 -12 12 6z" fill="#F8BBD0" />
      <path d="M74 32 l4 -12 -12 6z" fill="#F8BBD0" />
      <ellipse cx="50" cy="44" rx="3" ry="4" fill="#3D2B3D" />
      <ellipse cx="66" cy="44" rx="3" ry="4" fill="#3D2B3D" />
      <path d="M54 54 q4 4 8 0" stroke="#3D2B3D" strokeWidth="2" fill="none" strokeLinecap="round" />
      <ellipse cx="44" cy="52" rx="4" ry="2.5" fill="#F8A8C6" />
      <ellipse cx="72" cy="52" rx="4" ry="2.5" fill="#F8A8C6" />
      <path d="M86 40 q10 8 8 30 q-2 14 -12 18 q-4 -16 -2 -30 q2 -12 6 -18z" fill="#C9B2F5" stroke="#A98CF0" strokeWidth="2" />
      <path d="M92 38 v-8" stroke="#7C5A70" strokeWidth="3" strokeLinecap="round" />
    </g>
  ),
  'desk-headphones': (
    <g>
      <rect x="18" y="72" width="84" height="10" rx="4" fill="#E78AB4" />
      <rect x="30" y="56" width="26" height="16" rx="3" fill="#C9B2F5" />
      <rect x="33" y="59" width="20" height="10" rx="2" fill="#FFF7FA" />
      <path d="M70 68 v-14 a12 12 0 0 1 24 0 v14" fill="none" stroke="#7C5A70" strokeWidth="5" strokeLinecap="round" />
      <rect x="64" y="62" width="10" height="16" rx="5" fill="#F8BBD0" stroke="#E78AB4" strokeWidth="2" />
      <rect x="90" y="62" width="10" height="16" rx="5" fill="#F8BBD0" stroke="#E78AB4" strokeWidth="2" />
      <circle cx="24" cy="34" r="5" fill="#F48FB1" />
      <circle cx="98" cy="28" r="4" fill="#B39DDB" />
    </g>
  ),
  'quartet': (
    <g>
      {[30, 52, 74, 92].map((x, i) => (
        <g key={x}>
          <path d={`M${x} ${44 + (i % 2) * 6} q-8 6 -6 24 q2 14 8 18 q6 -12 6 -26 q0 -12 -8 -16z`} fill={i % 2 ? '#C9B2F5' : '#F8BBD0'} stroke={i % 2 ? '#A98CF0' : '#E78AB4'} strokeWidth="2" />
          <path d={`M${x + 2} ${38 + (i % 2) * 6} v-10`} stroke="#7C5A70" strokeWidth="2.5" strokeLinecap="round" />
        </g>
      ))}
      <circle cx="20" cy="24" r="4" fill="#F48FB1" />
      <circle cx="104" cy="22" r="5" fill="#B39DDB" />
    </g>
  ),
  'rainy-window': (
    <g>
      <rect x="20" y="18" width="80" height="72" rx="8" fill="#E9D5FF" stroke="#A98CF0" strokeWidth="3" />
      <path d="M60 18 v72 M20 54 h80" stroke="#A98CF0" strokeWidth="3" />
      {[32, 46, 74, 88].map((x, i) => (
        <path key={x} d={`M${x} ${26 + i * 8} q-2 12 -4 20`} stroke="#8FA8E8" strokeWidth="3" strokeLinecap="round" />
      ))}
      <rect x="38" y="88" width="26" height="18" rx="4" fill="#F8BBD0" stroke="#E78AB4" strokeWidth="2" />
      <path d="M64 93 q8 0 8 6 q0 6 -8 6" fill="none" stroke="#E78AB4" strokeWidth="2.5" />
      <path d="M44 84 q0 -6 4 -6 q-2 4 2 6" stroke="#FFF" strokeWidth="2" fill="none" opacity="0.8" />
    </g>
  ),
  'sleepy-moon': (
    <g>
      <path d="M74 22 a34 34 0 1 0 26 52 a28 28 0 0 1 -26 -52z" fill="#FDE7F1" stroke="#E78AB4" strokeWidth="2" />
      <circle cx="34" cy="30" r="3" fill="#C9B2F5" />
      <circle cx="46" cy="18" r="2.5" fill="#F8BBD0" />
      <circle cx="22" cy="48" r="2.5" fill="#F48FB1" />
      <path d="M40 86 q12 -10 26 0 q-12 8 -26 0z" fill="#FFF" stroke="#F3B9CD" strokeWidth="2" />
      <path d="M46 84 q3 3 6 0" stroke="#3D2B3D" strokeWidth="2" fill="none" strokeLinecap="round" />
      <text x="76" y="34" fontSize="14" fill="#C9B2F5" fontFamily="sans-serif">z</text>
      <text x="86" y="26" fontSize="11" fill="#C9B2F5" fontFamily="sans-serif">z</text>
    </g>
  ),
  'focus-orb': (
    <g>
      <circle cx="60" cy="58" r="34" fill="#E9D5FF" opacity="0.6" />
      <circle cx="60" cy="58" r="24" fill="#C9B2F5" opacity="0.8" />
      <circle cx="60" cy="58" r="13" fill="#FFF7FA" />
      <circle cx="60" cy="58" r="6" fill="#7C5A70" />
      <path d="M60 12 v8 M60 96 v8 M14 58 h8 M98 58 h8" stroke="#B39DDB" strokeWidth="3" strokeLinecap="round" />
    </g>
  ),
};
