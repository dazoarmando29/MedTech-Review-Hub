import { describe, expect, it } from 'vitest';
import {
  addGardenMinutes, catRoomItems, computeStreak, flowerStage, pickMotivation,
  weeklyWrapped, MOTIVATION_LINES, STAGES,
} from '../cozy';
import { migrateState, DEFAULT_SETTINGS, initialState, type AppState } from '../store';
import type { GardenFlower, Settings } from '../types';

const flower = (minutes: number): GardenFlower => ({
  id: `fl-${minutes}`, kind: 'tulip', minutes,
  plantedAt: '2026-11-01T00:00:00Z',
  bloomedAt: minutes >= STAGES.bloom ? '2026-11-02T00:00:00Z' : null,
});

describe('Study Garden', () => {
  it('grows the youngest flower through stages and blooms at 75 minutes', () => {
    let flowers = addGardenMinutes([], 10);
    expect(flowers.length).toBe(1);
    expect(flowerStage(flowers[0])).toBe('seed');

    flowers = addGardenMinutes(flowers, 10); // 20 -> sprout
    expect(flowerStage(flowers[0])).toBe('sprout');

    flowers = addGardenMinutes(flowers, 30); // 50 -> bud
    expect(flowerStage(flowers[0])).toBe('bud');

    flowers = addGardenMinutes(flowers, 30); // 80 -> bloom
    expect(flowerStage(flowers[0])).toBe('bloom');
    expect(flowers[0].bloomedAt).not.toBeNull();

    // next minutes plant a NEW seed
    flowers = addGardenMinutes(flowers, 20);
    expect(flowers.length).toBe(2);
    expect(flowerStage(flowers[1])).toBe('sprout');
  });

  it('ignores zero/negative minutes', () => {
    expect(addGardenMinutes([], 0)).toEqual([]);
    expect(addGardenMinutes([], -5)).toEqual([]);
  });

  it('stage thresholds match the spec', () => {
    expect(STAGES.bloom).toBe(75);
    expect(flowerStage(flower(0))).toBe('seed');
    expect(flowerStage(flower(STAGES.sprout))).toBe('sprout');
    expect(flowerStage(flower(STAGES.bud))).toBe('bud');
    expect(flowerStage(flower(STAGES.bloom))).toBe('bloom');
  });
});

describe('Cat Room unlocks', () => {
  it('unlocks items as study stats grow', () => {
    const none = catRoomItems({ sessions: 0, streak: 0, blooms: 0 }, []);
    expect(none.every((i) => !i.unlocked)).toBe(true);

    const some = catRoomItems({ sessions: 3, streak: 3, blooms: 3 }, ['bow']);
    expect(some.find((i) => i.id === 'bow')?.unlocked).toBe(true);
    expect(some.find((i) => i.id === 'bow')?.equipped).toBe(true);
    expect(some.find((i) => i.id === 'milk')?.unlocked).toBe(true);
    expect(some.find((i) => i.id === 'grad-cap')?.unlocked).toBe(false);
  });
});

describe('motivation rotation', () => {
  it('is deterministic per day and stays in the line pool', () => {
    expect(pickMotivation(5)).toBe(MOTIVATION_LINES[5 % MOTIVATION_LINES.length]);
    expect(pickMotivation(5)).toBe(pickMotivation(5));
    expect(MOTIVATION_LINES.length).toBeGreaterThanOrEqual(8);
  });
});

describe('weekly wrapped', () => {
  it('aggregates the current week', () => {
    const base = initialState();
    const state: AppState = {
      ...base,
      sessions: [
        { id: 's1', startedAt: new Date().toISOString(), kind: 'READ', subjectId: 'hematology', minutes: 45, itemsCompleted: 1 },
      ],
      garden: { flowers: [flower(STAGES.bloom)] },
    };
    const wrapped = weeklyWrapped(state);
    expect(wrapped.minutes).toBe(45);
    expect(wrapped.flowersBloomed).toBe(1);
    expect(wrapped.topCompliment.length).toBeGreaterThan(5);
  });

  it('computes streaks from sessions and diary (rest days never shamed)', () => {
    const base = initialState();
    const today = new Date().toISOString().slice(0, 10);
    const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
    const state: AppState = {
      ...base,
      sessions: [
        { id: 's1', startedAt: `${yesterday}T10:00:00Z`, kind: 'READ', subjectId: null, minutes: 30, itemsCompleted: 1 },
      ],
      diary: [{ id: 'd1', date: today, mood: 'good', text: 'yay', tags: [], createdAt: today }],
    };
    expect(computeStreak(state)).toBe(2);
  });
});

describe('v1.0.0 -> v2 state migration', () => {
  const v1Settings = {
    theme: 'dark',
    textScale: 115,
    seriousMode: true,
    reduceMotion: true,
    mascot: 'dog',
    mascotCommentary: false,
    explanationStyle: 'TAGLISH',
    ttsEnabled: true,
    apiBaseUrl: 'https://example.test',
    dailyNewCards: 25,
    dailyReviewLimit: 150,
    targetRetention: 0.85,
    highlightColors: {
      YELLOW: 'Must memorize',
      RED: 'High-priority exam fact',
      BLUE: 'Definition',
      GREEN: 'Fully understood',
      PURPLE: 'Confusing / needs further study',
    },
  } as unknown as Partial<Settings>;

  it('maps legacy theme and highlight colors without losing data', () => {
    const migrated = migrateState({ settings: v1Settings } as Partial<AppState>);
    const s = migrated.settings!;
    expect(s.appTheme).toBe('night'); // dark -> Cozy Night
    expect(s.textScale).toBe(115); // untouched fields preserved
    expect(s.seriousMode).toBe(true);
    expect(s.mascot).toBe('dog');
    expect(s.highlightColors.YELLOW).toBe('Important fact'); // v1 default -> v2 default
    expect(s.highlightColors.PINK).toBe('Must memorize'); // legacy RED label carried to PINK
    expect(s.highlightColors.ROSE).toBe('Needs review'); // legacy PURPLE ("confusing") -> ROSE
  });

  it('remaps legacy annotation color keys', () => {
    const migrated = migrateState({
      pdfAnnotations: [
        { id: 'a1', pdfId: 'p1', page: 1, type: 'HIGHLIGHT', color: 'RED', selectedText: 'x', note: '', createdAt: '' },
        { id: 'a2', pdfId: 'p1', page: 2, type: 'HIGHLIGHT', color: 'PURPLE', selectedText: 'y', note: '', createdAt: '' },
        { id: 'a3', pdfId: 'p1', page: 3, type: 'HIGHLIGHT', color: 'GREEN', selectedText: 'z', note: '', createdAt: '' },
      ],
    } as Partial<AppState>);
    expect(migrated.pdfAnnotations!.map((a) => a.color)).toEqual(['PINK', 'ROSE', 'GREEN']);
  });

  it('keeps user-customized highlight labels', () => {
    const custom = {
      ...v1Settings,
      highlightColors: {
        YELLOW: 'My custom label',
        RED: 'Super important',
        BLUE: 'Def',
        GREEN: 'Got it',
        PURPLE: 'Confusing',
      },
    } as unknown as Partial<Settings>;
    const s = migrateState({ settings: custom } as Partial<AppState>).settings!;
    expect(s.highlightColors.YELLOW).toBe('My custom label');
  });

  it('maps legacy light theme to strawberry and adds missing slices', () => {
    const migrated = migrateState({ settings: { theme: 'light' } as Partial<Settings> } as Partial<AppState>);
    expect(migrated.settings!.appTheme).toBe('strawberry');
    expect(migrated.garden).toEqual({ flowers: [] });
    expect(migrated.catRoom).toEqual({ equipped: [] });
  });
});

describe('default settings sanity', () => {
  it('ships the warm defaults from the brief', () => {
    expect(DEFAULT_SETTINGS.appTheme).toBe('strawberry');
    expect(DEFAULT_SETTINGS.mascotName).toBe('Mochi');
    expect(DEFAULT_SETTINGS.giftNote).toContain('believes in you');
    expect(DEFAULT_SETTINGS.displayName).toBe('future MLS');
    expect(DEFAULT_SETTINGS.funnyCommentary).toBe(true);
    expect(Object.keys(DEFAULT_SETTINGS.highlightColors)).toEqual(
      ['YELLOW', 'PINK', 'PURPLE', 'BLUE', 'GREEN', 'ROSE'],
    );
  });
});
