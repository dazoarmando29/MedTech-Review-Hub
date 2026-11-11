'use client';

// Mochi 🐾 — the original pink-and-white study cat in a tiny lab coat.
// One parameterized SVG (8 moods) per shared/DESIGN.md v2. Lightweight and
// disableable via settings (no-decor / no-motion classes handle the rest).

export type MochiMood =
  | 'happy' | 'sleepy' | 'studying' | 'celebrating'
  | 'confused' | 'encouraging' | 'disappointed' | 'excited';

export function Mochi({
  mood = 'happy',
  size = 120,
  equipped = [],
  className,
}: {
  mood?: MochiMood;
  size?: number;
  equipped?: string[];
  className?: string;
}) {
  const sleepy = mood === 'sleepy';
  const happyEyes = mood === 'happy' || mood === 'celebrating' || mood === 'excited';
  const closedEyes = sleepy;
  const worried = mood === 'confused' || mood === 'disappointed';

  return (
    <svg
      viewBox="0 0 160 160"
      width={size}
      height={size}
      className={className}
      role="img"
      aria-label={`Mochi the study cat (${mood})`}
    >
      {/* body + lab coat */}
      <ellipse cx="80" cy="128" rx="42" ry="26" fill="#fff" stroke="#f3b9cd" strokeWidth="2.5" />
      <path d="M56 118 q24 10 48 0 l-4 22 q-20 8 -40 0 z" fill="#fff" stroke="#f3b9cd" strokeWidth="2" />
      {/* coat opening */}
      <path d="M80 118 v26" stroke="#f3b9cd" strokeWidth="2" />
      {/* lab badge */}
      <circle cx="66" cy="130" r="6.5" fill="#f8bbd0" stroke="#db2777" strokeWidth="1.5" />
      <path d="M63 130 h6 M66 127 v6" stroke="#db2777" strokeWidth="1.4" strokeLinecap="round" />

      {/* tail */}
      <path d="M120 132 q22 -6 18 -26" fill="none" stroke="#f3b9cd" strokeWidth="7" strokeLinecap="round" />

      {/* ears */}
      <path d="M44 56 l-6 -28 24 14 z" fill="#f8bbd0" stroke="#e79ab8" strokeWidth="2" />
      <path d="M116 56 l6 -28 -24 14 z" fill="#f8bbd0" stroke="#e79ab8" strokeWidth="2" />
      <path d="M45 52 l-3 -16 14 8 z" fill="#fce7f0" />
      <path d="M115 52 l3 -16 -14 8 z" fill="#fce7f0" />

      {/* head */}
      <circle cx="80" cy="72" r="42" fill="#fff" stroke="#f3b9cd" strokeWidth="2.5" />
      {/* pink head patches */}
      <path d="M52 48 q14 -14 32 -8 q-8 14 -22 16 q-8 0 -10 -8z" fill="#f8bbd0" />
      <path d="M112 58 q6 -12 -4 -18 q-8 8 -6 18z" fill="#f8bbd0" />

      {/* eyes */}
      {closedEyes ? (
        <>
          <path d="M58 72 q6 6 12 0" fill="none" stroke="#4b3548" strokeWidth="3.2" strokeLinecap="round" />
          <path d="M90 72 q6 6 12 0" fill="none" stroke="#4b3548" strokeWidth="3.2" strokeLinecap="round" />
        </>
      ) : happyEyes ? (
        <>
          <path d="M57 74 q6 -9 13 0" fill="none" stroke="#4b3548" strokeWidth="3.4" strokeLinecap="round" />
          <path d="M90 74 q6 -9 13 0" fill="none" stroke="#4b3548" strokeWidth="3.4" strokeLinecap="round" />
        </>
      ) : (
        <>
          <ellipse cx="64" cy="73" rx="8" ry={worried ? 9.5 : 10} fill="#4b3548" />
          <ellipse cx="96" cy="73" rx="8" ry={worried ? 9.5 : 10} fill="#4b3548" />
          <circle cx="67" cy="69.5" r="3" fill="#fff" />
          <circle cx="99" cy="69.5" r="3" fill="#fff" />
          {worried && (
            <>
              <path d="M55 59 q9 -5 18 -1" fill="none" stroke="#b98da5" strokeWidth="2.4" strokeLinecap="round" />
              <path d="M87 58 q9 -4 18 1" fill="none" stroke="#b98da5" strokeWidth="2.4" strokeLinecap="round" />
            </>
          )}
        </>
      )}

      {/* nose + mouth */}
      <path d="M76 84 h8 l-4 4 z" fill="#e778a8" />
      {mood === 'disappointed' ? (
        <path d="M72 96 q8 -6 16 0" fill="none" stroke="#4b3548" strokeWidth="2.6" strokeLinecap="round" />
      ) : worried ? (
        <ellipse cx="80" cy="94" rx="3.5" ry="4.5" fill="#4b3548" />
      ) : (
        <path d="M72 91 q8 8 16 0" fill="none" stroke="#4b3548" strokeWidth="2.6" strokeLinecap="round" />
      )}

      {/* blush */}
      <ellipse cx="52" cy="86" rx="7" ry="4.5" fill="#fbc4d6" opacity="0.8" />
      <ellipse cx="108" cy="86" rx="7" ry="4.5" fill="#fbc4d6" opacity="0.8" />

      {/* whiskers */}
      <path d="M40 82 h14 M40 90 h14 M120 82 h-14 M120 90 h-14" stroke="#e0a8bf" strokeWidth="1.8" strokeLinecap="round" />

      {/* mood extras */}
      {mood === 'studying' && (
        <g>
          <rect x="20" y="118" width="16" height="8" rx="2" fill="#e9d5ff" stroke="#c3aef0" strokeWidth="1.5" />
          <rect x="22" y="110" width="12" height="8" rx="2" fill="#f8bbd0" stroke="#e79ab8" strokeWidth="1.5" />
          <rect x="24" y="103" width="8" height="7" rx="2" fill="#fde68a" stroke="#e5c066" strokeWidth="1.5" />
        </g>
      )}
      {(mood === 'celebrating' || mood === 'excited') && (
        <g className="decor-only">
          <path d="M28 40 l3 7 7 1 -5 5 1 7 -6 -3 -6 3 1 -7 -5 -5 7 -1z" fill="#fbbf24" />
          <path d="M128 34 l2.5 6 6 1 -4.5 4 1 6 -5 -2.5 -5 2.5 1 -6 -4.5 -4 6 -1z" fill="#f472b6" />
          <circle cx="136" cy="66" r="3.5" fill="#a78bfa" />
          <circle cx="24" cy="70" r="3" fill="#34d399" />
        </g>
      )}
      {mood === 'sleepy' && (
        <text x="116" y="52" fontSize="16" fill="#c3aef0" fontFamily="sans-serif" fontWeight="bold">z z</text>
      )}
      {mood === 'encouraging' && (
        <text x="112" y="48" fontSize="18" fill="#f472b6" fontFamily="sans-serif">♥</text>
      )}
      {mood === 'confused' && (
        <text x="112" y="50" fontSize="20" fill="#c3aef0" fontFamily="sans-serif" fontWeight="bold">?</text>
      )}
      {mood === 'disappointed' && (
        <text x="114" y="50" fontSize="14" fill="#a78bfa" fontFamily="sans-serif">…</text>
      )}

      {/* cat room cosmetics */}
      {equipped.includes('bow') && (
        <path d="M108 36 q10 -8 16 0 q-6 8 -16 4 q-8 -6 0 -4z" fill="#f472b6" stroke="#db2777" strokeWidth="1.5" />
      )}
      {equipped.includes('glasses') && (
        <g fill="none" stroke="#7c3aed" strokeWidth="2.6">
          <circle cx="64" cy="73" r="11" />
          <circle cx="96" cy="73" r="11" />
          <path d="M75 73 h10" />
        </g>
      )}
      {equipped.includes('grad-cap') && (
        <g>
          <path d="M48 34 l32 -12 32 12 -32 10z" fill="#352735" />
          <path d="M62 40 v10 q18 8 36 0 v-10" fill="#4b3548" />
          <path d="M110 36 v16" stroke="#fbbf24" strokeWidth="2" />
          <circle cx="110" cy="53" r="3" fill="#fbbf24" />
        </g>
      )}
      {equipped.includes('flower-crown') && (
        <g>
          <circle cx="56" cy="38" r="5" fill="#f8bbd0" />
          <circle cx="72" cy="31" r="5" fill="#e9d5ff" />
          <circle cx="88" cy="31" r="5" fill="#f8bbd0" />
          <circle cx="104" cy="38" r="5" fill="#fde68a" />
        </g>
      )}
    </svg>
  );
}

export function MochiBubble({
  mood = 'happy',
  text,
  name = 'Mochi',
  onAdvance,
}: {
  mood?: MochiMood;
  text: string;
  name?: string;
  onAdvance?: () => void;
}) {
  return (
    <div className="flex items-start gap-2">
      <Mochi mood={mood} size={64} className="shrink-0" />
      <div className="card-soft p-3 text-xs leading-snug max-w-[220px]">
        <strong className="font-heading block" style={{ color: 'var(--primary)' }}>{name}</strong>
        <p>{text}</p>
        {onAdvance && (
          <button className="underline mt-1" style={{ color: 'var(--muted)' }} onClick={onAdvance}>
            another one
          </button>
        )}
      </div>
    </div>
  );
}
