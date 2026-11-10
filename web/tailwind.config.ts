import type { Config } from 'tailwindcss';

// Colors alias CSS variables so every theme (DESIGN.md v2) recolors all pages.
const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  darkMode: ['selector', '[data-theme="night"]'],
  theme: {
    extend: {
      colors: {
        primary: { DEFAULT: 'var(--primary)', soft: 'var(--primary-soft)' },
        secondary: { DEFAULT: 'var(--primary-soft)', dark: 'var(--primary)' },
        tertiary: { DEFAULT: 'var(--accent)', dark: 'var(--accent)' },
        accent: { DEFAULT: 'var(--accent-2)', dark: 'var(--accent-2)' },
        danger: { DEFAULT: '#e11d48', dark: '#fb7185' },
        surface: { DEFAULT: 'var(--surface)', alt: 'var(--bg-soft)' },
        ink: { DEFAULT: 'var(--ink)', muted: 'var(--muted)' },
        line: { DEFAULT: 'var(--line)', strong: 'var(--line-strong)' },
        // legacy aliases used across pages — now theme-aware
        slate: {
          50: 'var(--bg-soft)',
          100: 'var(--chip)',
          200: 'var(--line)',
          300: 'var(--line-strong)',
          400: 'var(--muted)',
          500: 'var(--muted)',
          600: 'var(--muted-strong)',
          700: 'var(--muted-strong)',
          800: 'var(--surface-2)',
          900: 'var(--bg-soft)',
        },
      },
      fontFamily: {
        heading: ['var(--font-heading)', 'Nunito', 'system-ui', 'sans-serif'],
        display: ['var(--font-display)', 'Quicksand', 'system-ui', 'sans-serif'],
        body: ['var(--font-body)', 'Inter', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      borderRadius: { sm: '12px', md: '14px', lg: '20px', xl: '24px' },
      boxShadow: {
        card: 'var(--shadow)',
        lift: '0 10px 28px rgb(219 39 119 / 0.14)',
      },
      keyframes: {
        flip: { '0%': { transform: 'rotateY(0deg)' }, '100%': { transform: 'rotateY(180deg)' } },
        'fade-in': { '0%': { opacity: '0', transform: 'translateY(4px)' }, '100%': { opacity: '1', transform: 'none' } },
        'pulse-soft': { '0%,100%': { opacity: '1' }, '50%': { opacity: '0.6' } },
        floaty: { '0%,100%': { transform: 'translateY(0)' }, '50%': { transform: 'translateY(-5px)' } },
      },
      animation: {
        flip: 'flip 0.25s ease-out forwards',
        'fade-in': 'fade-in 0.2s ease-out',
        'pulse-soft': 'pulse-soft 2.5s ease-in-out infinite',
        floaty: 'floaty 3s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
export default config;
