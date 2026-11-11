'use client';

// Self-healing error boundary. A stale cached page can reference JS chunks
// that no longer exist after a deploy ("Loading chunk ... failed"), which used
// to show a blank white error. We detect that class of failure and reload once
// to pick up the fresh build; real render errors get a friendly recovery card.
import React from 'react';

const RECOVERABLE = /loading chunk|failed to fetch dynamically imported|dynamically imported module|importing a module script|error loading dynamically imported/i;

interface State {
  error: Error | null;
}

export class ErrorBoundary extends React.Component<{ children: React.ReactNode }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error): void {
    if (typeof window === 'undefined') return;
    const isChunkError = RECOVERABLE.test(error.message);
    const alreadyReloaded = window.sessionStorage.getItem('mtrh-recovered') === '1';
    if (isChunkError && !alreadyReloaded) {
      window.sessionStorage.setItem('mtrh-recovered', '1');
      window.location.reload();
      return;
    }
    if (!isChunkError) {
      // keep a breadcrumb for debugging; never crash the study session
      console.warn('[Study Companion] recovered from an error:', error.message);
    }
  }

  render(): React.ReactNode {
    if (this.state.error) {
      return (
        <div className="card p-8 text-center max-w-lg mx-auto mt-10">
          <div className="text-4xl" aria-hidden>🌱</div>
          <h2 className="font-heading text-lg font-bold mt-2">A tiny hiccup — nothing lost</h2>
          <p className="text-sm mt-2" style={{ color: 'var(--muted-strong)' }}>
            Your study data is safe on this device. Reloading usually fixes it.
          </p>
          <button className="btn-primary mt-4" onClick={() => window.location.reload()}>
            Reload the page
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
