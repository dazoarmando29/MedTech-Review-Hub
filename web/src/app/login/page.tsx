'use client';

// Optional account sign-in for cross-device sync. Without an account the whole
// platform works locally on this device.
import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Card, SectionTitle } from '@/components/ui';
import { api, ApiUnavailableError, configureApi } from '@/lib/api';
import { updateSettings, useStore } from '@/lib/store';

export default function LoginPage() {
  const router = useRouter();
  const settings = useStore((s) => s.settings);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    setError(null);
    configureApi(settings.apiBaseUrl);
    try {
      const res =
        mode === 'login'
          ? await api.login(email, password)
          : await api.register(email, password, displayName || email.split('@')[0]);
      updateSettings({ apiBaseUrl: settings.apiBaseUrl });
      if (typeof window !== 'undefined') {
        window.localStorage.setItem('mtrh-auth', JSON.stringify({ email: res.user.email, token: res.accessToken }));
      }
      router.push('/');
    } catch (e) {
      if (e instanceof ApiUnavailableError) {
        setError('Could not reach the backend. Check the API URL in Settings — or keep using the app offline without an account.');
      } else {
        setError(String(e));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="max-w-md mx-auto">
      <Card>
        <SectionTitle icon="🔐">{mode === 'login' ? 'Sign in' : 'Create account'}</SectionTitle>
        <p className="text-xs text-[var(--ink-muted)] mb-3">
          Accounts enable sync between this website and the Android app (same lessons, flashcards
          schedule, quiz history, streaks). Everything also works without an account.
        </p>
        <div className="space-y-3">
          {mode === 'register' && (
            <label className="block text-sm">
              Display name
              <input className="input mt-1" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            </label>
          )}
          <label className="block text-sm">
            Email
            <input className="input mt-1" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label className="block text-sm">
            Password {mode === 'register' && <span className="text-xs text-[var(--ink-muted)]">(min 10 characters)</span>}
            <input className="input mt-1" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
        </div>
        {error && <p className="text-sm text-red-600 mt-3">{error}</p>}
        <button className="btn-primary w-full mt-4" disabled={busy || !email || password.length < 10} onClick={submit}>
          {busy ? 'Working…' : mode === 'login' ? 'Sign in' : 'Create account'}
        </button>
        <button
          className="btn-ghost w-full mt-2 text-xs"
          onClick={() => setMode((m) => (m === 'login' ? 'register' : 'login'))}
        >
          {mode === 'login' ? 'Need an account? Register' : 'Have an account? Sign in'}
        </button>
      </Card>
    </div>
  );
}
