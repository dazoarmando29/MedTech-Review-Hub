import type { Metadata, Viewport } from 'next';
import '@fontsource/quicksand/600.css';
import '@fontsource/quicksand/700.css';
import '@fontsource/nunito/400.css';
import '@fontsource/nunito/600.css';
import '@fontsource/nunito/700.css';
import '@fontsource/inter/400.css';
import '@fontsource/inter/500.css';
import '@fontsource/inter/600.css';
import './globals.css';
import { AppShell } from '@/components/AppShell';

const basePath = process.env.NEXT_PUBLIC_BASE_PATH || '';

export const metadata: Metadata = {
  title: 'MedTech Study Companion — ASCPi Reviewer',
  description:
    'A cozy pink ASCPi MLS board exam study companion for Filipino Medical Technology graduates: lessons, FSRS flashcards, quizzes, PDF reviewer tools, study garden, and a cat named Mochi.',
  manifest: `${basePath}/manifest.webmanifest`,
  icons: { icon: `${basePath}/icon.svg`, apple: `${basePath}/apple-touch-icon.png` },
  appleWebApp: {
    capable: true,
    title: 'Study Companion',
    statusBarStyle: 'default',
  },
  applicationName: 'MedTech Study Companion',
};

export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#FFF0F5' },
    { media: '(prefers-color-scheme: dark)', color: '#241B2E' },
  ],
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning style={{ ['--font-heading' as string]: 'Nunito', ['--font-display' as string]: 'Quicksand', ['--font-body' as string]: 'Inter' }}>
      <body>
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
