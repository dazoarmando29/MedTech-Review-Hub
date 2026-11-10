# Spaced Repetition Scheduler — FSRS-4.5 (shared by web + Android)

Both clients implement this **identically** (TypeScript: `web/src/lib/fsrs.ts`,
Kotlin: `android/.../domain/SpacedRepetition.kt`). Reference implementation:
[py-fsrs](https://github.com/open-spaced-repetition/py-fsrs) / FSRS-4.5 by Jarrett Ye
(license: BSD-3), default parameters from `fsrs4anki` v4.5.

This is a genuine spaced-repetition scheduler: every rating reschedules the card's
next review from predicted retrievability. It is **not** a random shuffle.

## Constants

```
DECAY = -0.5
FACTOR = 0.9 ^ (1 / DECAY) - 1        # = 19/81 ≈ 0.2345679
DEFAULT_RETENTION = 0.9               # user-tunable 0.80 – 0.95
W = [0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031,
     1.6474, 0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755]
```

Rating mapping: `Again=1, Hard=2, Good=3, Easy=4`.
State: `NEW → LEARNING → REVIEW`; a lapse (`Again` on a review card) → `RELEARNING` then `REVIEW`.

## Formulas

```
retrievability(t, S) = (1 + FACTOR * t / S) ^ DECAY     # t = days since last review

init_stability(G)     = max(W[G-1], 0.1)
init_difficulty(G)    = clamp(W[4] - (G - 3) * W[5], 1, 10)

next_difficulty(D, G):
    d'      = D - W[6] * (G - 3) * (10 - D) / 9          # damped change
    D_new   = clamp(W[7] * init_difficulty(4) + (1 - W[7]) * d', 1, 10)

next_stability(D, S, G, R):        # R = retrievability at review time
    if G == Again:
        S_new = clamp(W[11] * D^(-W[12]) * ((S + 1)^W[13] - 1) * exp(W[14] * (1 - R)), 0.1, S)
    else:
        hard = W[15] if G == Hard else 1
        easy = W[16] if G == Easy else 1
        S_new = clamp(S * (1 + exp(W[8]) * (11 - D) * S^(-W[9])
                          * (exp(W[10] * (1 - R)) - 1) * hard * easy), S, 36500)

interval_days(S, request=DEFAULT_RETENTION):
    I = S / FACTOR * (request ^ (1 / DECAY) - 1)         # = S when request = 0.9
    return clamp(round(I), 1, 36500)
```

## Scheduling flow

- **New card:** enter `LEARNING`. Steps: `1 min`, `10 min`.
  - `Again` → restart at step 1 (1 min). `Hard` → repeat the **current** step × 1.2.
  - `Good` → advance to the next step; after the last step, graduate to `REVIEW`.
  - `Easy` → graduate immediately.
  - Graduation sets `S = init_stability(G)`, `D = init_difficulty(G)`,
    `due = now + interval_days(S)` (`G` = the graduating rating).
- **Learning card:** step list as above.
- **Review card:** compute `R` from elapsed days, update `S` and `D`, `due = now + interval_days(S_new)`.
  - `Again`: `lapses += 1`, state `RELEARNING`, step `10 min`; `S` from the lapse formula is stored.
    `Hard` repeats the relearning step × 1.2; `Good`/`Easy` graduate keeping the stored lapse `S`.
- **Same-day reviews:** a review less than `1 min` after the previous one is ignored (no schedule change).
- Cards due `<= now` appear in the review queue ordered by ascending `dueAt`.
- Daily new-card limit (default 20) and review limit (default 200) are user-configurable.

## Stored fields (must match shared/api/API.md `Flashcard.schedule`)

`state`, `stability` (days), `difficulty` (1–10), `dueAt`, `lastReviewAt`, `reps`, `lapses`,
and per-card `history[]` entries `{ at, rating, intervalDays }`.

`next estimated review date` shown in UI = `dueAt`.

## Honest labeling

We call this "spaced repetition (FSRS-4.5)". Practice percentages and readiness scores in
the quiz engine are **estimates of study performance, not ASCP scores or pass guarantees**
(see docs/DISCLAIMERS.md).
