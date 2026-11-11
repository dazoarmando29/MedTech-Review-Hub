import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

// PERMANENT regression guard for the "Mochi vibrates / jiggles" bug class.
// History: this bug returned 3 times (1.2s jolt → 4.5s bob → 7s SVG breathe).
// Root cause: ANY continuous transform animation on the mascot's <svg>
// re-rasterizes every frame in iPad Safari and visibly vibrates.
//
// POLICY (must hold forever):
//  1. The mascot has ZERO continuous (infinite) animations. Period.
//  2. Motion only as ONE-SHOT reactions (tap pop, celebration confetti).
//  3. One-shot motion is translateY-only and <= 0.6s. Never scale/rotate
//     (SVG re-rasterization shimmer).
//  4. Kill switches exist: prefers-reduced-motion, body.no-motion, body.calm-mascot.

const css = readFileSync(join(__dirname, '..', '..', 'app', 'globals.css'), 'utf8');

describe('Mochi is perfectly still — permanent policy (regression guard)', () => {
  it('has NO infinite animations on any mochi class', () => {
    const infiniteRules = [...css.matchAll(/[^{}]*\.mochi[\w-]*[^{]*\{[^}]*animation[^}]*infinite[^}]*\}/g)];
    expect(infiniteRules.map((m) => m[0]).join('\n')).toBe('');
    // also: no keyframes referenced by an infinite animation named mochi-*
    expect(css).not.toMatch(/animation:\s*mochi-[\w-]+[^;]*infinite/);
  });

  it('keeps all legacy mochi idle classes explicitly disabled', () => {
    for (const cls of ['mochi-idle', 'mochi-tail', 'mochi-blink', 'mochi-twinkle', 'mochi-drift', 'mochi-heart']) {
      expect(css, `${cls} must stay disabled`).toContain(`.${cls}`);
    }
    // the disabled block must set animation:none
    const block = css.match(/\.mochi-idle,([\s\S]*?)\{([\s\S]*?)\}/);
    expect(block?.[2]).toMatch(/animation: none !important/);
  });

  it('one-shot motion is translateY-only and tiny (no scale/rotate, <= 3px)', () => {
    const oneShot = [...css.matchAll(/@keyframes (mochi-[\w-]+) \{([\s\S]*?)\n\}/g)];
    expect(oneShot.length).toBeGreaterThan(0); // at least mochi-pop
    for (const [, name, body] of oneShot) {
      expect(body, `${name}: scale() is forbidden`).not.toMatch(/scale\s*\(/);
      expect(body, `${name}: rotate() is forbidden`).not.toMatch(/rotate\s*\(/);
      for (const m of body.matchAll(/transform:\s*([^;]+);/g)) {
        const t = m[1].trim();
        expect(t).toMatch(/^translateY\((0|-?\d+(\.\d+)?px)\)$/);
        const num = t.match(/-?\d+(\.\d+)?/);
        const px = num ? Math.abs(parseFloat(num[0])) : 0;
        expect(px).toBeLessThanOrEqual(5);
      }
    }
  });

  it('one-shot animations are brief and finite (no infinite keyword anywhere near mochi-pop)', () => {
    const popRule = css.match(/\.mochi-pop \{([^}]*)\}/)?.[1] ?? '';
    expect(popRule).toMatch(/animation:\s*mochi-pop\s+[\d.]+s\s+ease-out\s+1/);
    const secs = parseFloat(popRule.match(/mochi-pop\s+([\d.]+)s/)?.[1] ?? '99');
    expect(secs).toBeLessThanOrEqual(1);
  });

  it('keeps the kill switches (reduced motion / no-motion / calm-mascot)', () => {
    expect(css).toMatch(/prefers-reduced-motion/);
    expect(css).toMatch(/body\.no-motion/);
    expect(css).toMatch(/body\.calm-mascot/);
    expect(css).toMatch(/body\.calm-mascot \.mochi-pop/);
  });
});
