import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

// Regression guard for the "Mochi glitches / moves too fast" bug class.
// The rule set lives in web/src/app/globals.css and MUST hold forever:
//  1. No scale()/rotate()/skew() in any mochi animation — SVG inner-part
//     transforms re-rasterize and shimmer (that's the "glitching" look).
//  2. Whole-mascot motion is translateY-only (compositor-safe).
//  3. Every infinite mochi loop runs >= 5s (slow and calm, never jittery).
//  4. Kill switches exist: prefers-reduced-motion, body.no-motion, body.calm-mascot.

const css = readFileSync(join(__dirname, '..', '..', 'app', 'globals.css'), 'utf8');

function keyframeBlock(name: string): string {
  const m = css.match(new RegExp(`@keyframes ${name} \\{([\\s\\S]*?)\\n\\}`, 'm'));
  return m ? m[1] : '';
}

describe('Mochi animation rules (regression guard)', () => {
  const mochiKeyframes = [...css.matchAll(/@keyframes (mochi-[\w-]+)/g)].map((m) => m[1]);

  it('defines at least the breathe and twinkle animations', () => {
    expect(mochiKeyframes).toContain('mochi-breathe');
    expect(mochiKeyframes).toContain('mochi-twinkle');
  });

  it('never uses scale/rotate/skew in mochi keyframes (SVG re-rasterization glitch)', () => {
    for (const name of mochiKeyframes) {
      const block = keyframeBlock(name);
      expect(block).not.toMatch(/scale\s*\(/);
      expect(block).not.toMatch(/rotate\s*\(/);
      expect(block).not.toMatch(/skew\s*\(/);
    }
  });

  it('only allows translateY (tiny) or opacity as animated properties', () => {
    for (const name of mochiKeyframes) {
      const block = keyframeBlock(name);
      const transforms = [...block.matchAll(/transform:\s*([^;]+);/g)].map((m) => m[1].trim());
      for (const t of transforms) {
        expect(t).toMatch(/^translateY\((0|-?\d+(\.\d+)?px)\)$/); // e.g. translateY(-2px) or translateY(0)
        const num = t.match(/-?\d+(\.\d+)?/);
        const px = num ? Math.abs(parseFloat(num[0])) : 0;
        expect(px).toBeLessThanOrEqual(3); // tiny distance only
      }
    }
  });

  it('every mochi animation loop is slow (>= 5s)', () => {
    const durations = [...css.matchAll(/animation:\s*(mochi-[\w-]+)\s+([\d.]+)s/g)];
    expect(durations.length).toBeGreaterThan(0);
    for (const [, name, secs] of durations) {
      expect(parseFloat(secs), `${name} must be slow`).toBeGreaterThanOrEqual(5);
    }
  });

  it('keeps the kill switches (reduced motion / no-motion / calm-mascot)', () => {
    expect(css).toMatch(/prefers-reduced-motion/);
    expect(css).toMatch(/body\.no-motion/);
    expect(css).toMatch(/body\.calm-mascot/);
    // tail & blink stay intentionally static
    expect(css).toMatch(/\.mochi-tail,[\s\S]*?\.mochi-blink \{[\s\S]*?animation: none;/);
  });
});
