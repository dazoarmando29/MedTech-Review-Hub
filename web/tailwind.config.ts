import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: { DEFAULT: '#2563EB', dark: '#60A5FA' },
        secondary: { DEFAULT: '#0D9488', dark: '#2DD4BF' },
        tertiary: { DEFAULT: '#7C3AED', dark: '#A78BFA' },
        accent: { DEFAULT: '#F59E0B', dark: '#FBBF24' },
        danger: { DEFAULT: '#DC2626', dark: '#F87171' },
        surface: {
          DEFAULT: '#FFFFFF',
          alt: '#F8FAFC',
          'dark': '#0F172A',
          'dark-alt': '#1E293B',
        },
        ink: { DEFAULT: '#0F172A', muted: '#475569', 'dark': '#E2E8F0', 'dark-muted': '#94A3B8' },
      },
      fontFamily: {
        heading: ['"Plus Jakarta Sans"', 'Inter', 'system-ui', 'sans-serif'],
        body: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      borderRadius: { sm: '8px', md: '12px', lg: '16px', xl: '24px' },
      boxShadow: {
        card: '0 1px 3px rgb(15 23 42 / 0.08)',
        lift: '0 8px 24px rgb(15 23 42 / 0.12)',
      },
      keyframes: {
        flip: { '0%': { transform: 'rotateY(0deg)' }, '100%': { transform: 'rotateY(180deg)' } },
        'fade-in': { '0%': { opacity: '0', transform: 'translateY(4px)' }, '100%': { opacity: '1', transform: 'none' } },
        'pulse-soft': { '0%,100%': { opacity: '1' }, '50%': { opacity: '0.6' } },
      },
      animation: {
        flip: 'flip 0.25s ease-out forwards',
        'fade-in': 'fade-in 0.2s ease-out',
        'pulse-soft': 'pulse-soft 2.5s ease-in-out infinite',
      },
    },
  },
  plugins: [],
};
export default config;
