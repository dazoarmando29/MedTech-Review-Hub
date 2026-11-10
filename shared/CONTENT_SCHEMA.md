# Content Schema — Review Library (shared/content/)

All official reviewer content lives in `shared/content/` as UTF-8 JSON so the web app,
Android app, and backend consume exactly the same data. Files are organized:

```
shared/content/
├── manifest.json                 # index of subjects → topics → lessons
├── subjects/<subject-id>.json    # subject meta + topics + lesson list
├── lessons/<subject-id>/<lesson-id>.json
├── questions/<subject-id>.json   # quiz question bank
├── flashcards/<deck-id>.json     # flashcard decks
├── formulas.json                 # validated lab formulas
├── memes.json                    # meme-of-lesson, memory hooks, mini-games
└── glossary.json                 # seed glossary terms
```

## Lesson object

```json
{
  "id": "blood-banking/abo-basics",
  "subjectId": "blood-banking",
  "topicId": "abo-system",
  "title": "ABO Blood Group System: Basics and Forward/Reverse Typing",
  "summary": "One-paragraph overview used in cards and search results.",
  "readingMinutes": 12,
  "difficulty": "INTERMEDIATE",
  "keywords": ["ABO", "forward typing", "reverse typing", "A antigen", "B antigen", "isohemagglutinins"],
  "objectives": [
    "Explain the biochemical basis of A and B antigens on red cells.",
    "Perform and interpret forward and reverse ABO typing.",
    "Predict ABO group from secretor status and genotype."
  ],
  "quickReview": {
    "highYield": [
      "Group A red cells carry A antigen; plasma contains anti-B.",
      "Group O red cells carry neither A nor B antigen; plasma contains anti-A and anti-B.",
      "Discrepancy between forward and reverse typing must be resolved before issuing blood."
    ],
    "mustKnowTable": {
      "columns": ["ABO group", "RBC antigen", "Plasma antibody", "Genotype"],
      "rows": [
        ["A", "A", "anti-B", "AA or AO"],
        ["B", "B", "anti-A", "BB or BO"],
        ["AB", "A and B", "none", "AB"],
        ["O", "none", "anti-A + anti-B", "OO"]
      ]
    },
    "mnemonic": "O has nO antigens and bOth antibodies."
  },
  "deepStudy": {
    "introduction": "...",
    "sections": [
      { "heading": "Molecular basis", "body": "Markdown text...", "diagram": null }
    ],
    "clinicalSignificance": "...",
    "labInterpretation": "...",
    "comparisonTables": [ { "title": "...", "columns": [...], "rows": [[...]] } ],
    "examTraps": [
      "Reverse typing in newborns is weak/absent because antibodies are IgG maternal..."
    ],
    "workedExample": null
  },
  "memoryHooks": [
    { "type": "MNEMONIC", "text": "...", "funny": "Meow: 'nO antigens, bOth antibodies — like a cat with no manners and all the opinions.'" }
  ],
  "practiceQuestionIds": ["bb-q-001", "bb-q-002"],
  "relatedFlashcardIds": ["bb-fc-001"],
  "followUp": ["blood-banking/rh-system"],
  "references": [
    { "title": "Harmening's Blood Banking and Transfusion Fundamentals", "detail": "6th ed., Ch. 2", "url": null }
  ],
  "editorialStatus": "PUBLISHED",
  "reviewedAt": "2026-01-10",
  "reviewerNote": "Medical accuracy reviewed against listed references."
}
```

Rules for authoring:
- **Never invent reference values.** Ranges must include units and a note such as
  "reference intervals vary by laboratory, method, age, sex, and population; verify against your local laboratory".
- English for formal scientific text. `memoryHooks[].funny` and `memes.json` may use Taglish.
- Every lesson must list at least one real, identifiable reference (textbook + edition + chapter, or a reputable organization URL).
- Official exam content is mapped to the **ASCP BOC MLS examination content guideline** (see `subjects/*.json` `blueprintNote`) and to the Philippine MTLE subject list; the two profiles stay separate.
- Nothing may claim ASCP endorsement. Practice questions are original, never copied from proprietary banks.

## Subject object (`subjects/<id>.json`)

```json
{
  "id": "blood-banking",
  "name": "Blood Banking / Immunohematology",
  "shortName": "Blood Banking",
  "description": "...",
  "blueprintNote": "ASCPi BOC content guide weight ~7-9% ... ; PH MTLE weight ...",
  "colorToken": "teal",
  "topics": [
    { "id": "abo-system", "name": "ABO System", "lessonIds": ["blood-banking/abo-basics"] }
  ]
}
```

Subject ids: `blood-banking`, `clinical-chemistry`, `hematology`, `microbiology`,
`immunology`, `urinalysis`, `lab-operations`.

## Quiz question object

```json
{
  "id": "bb-q-001",
  "subjectId": "blood-banking",
  "topicId": "abo-system",
  "type": "MCQ",
  "vignette": "A 32-year-old ... units of packed RBCs requested.",
  "stem": "Which of the following is the most likely explanation?",
  "choices": [ { "id": "A", "text": "..." }, { "id": "B", "text": "..." },
               { "id": "C", "text": "..." }, { "id": "D", "text": "..." } ],
  "correctChoiceId": "C",
  "difficulty": "MEDIUM",
  "explanation": "Teaching explanation of the underlying concept...",
  "rationales": { "A": "...", "B": "...", "C": "...", "D": "..." },
  "misconception": "Students often pick A because ...",
  "reference": "Harmening 6th ed., Ch. 5",
  "lessonId": "blood-banking/abo-basics",
  "examTraps": ["..."]
}
```

Every question must have exactly 4 choices, one best answer, a rationale for **each**
choice, and a `misconception` note (feeds the "Why was I wrong?" button).

## Flashcard deck object (`flashcards/<deck-id>.json`)

```json
{
  "id": "bb-deck-01",
  "title": "Blood Banking Essentials",
  "subjectId": "blood-banking",
  "cards": [
    {
      "id": "bb-fc-001",
      "type": "BASIC",
      "front": "Which immunoglobulin class is naturally occurring anti-A, anti-B?",
      "back": "IgM (with some IgG component)",
      "tags": ["abo", "high-yield"],
      "difficulty": 1,
      "note": "",
      "source": { "kind": "MANUAL", "lessonId": "blood-banking/abo-basics" }
    },
    {
      "id": "bb-fc-002",
      "type": "CLOZE",
      "front": "Anti-[[1]] reacts best at room temperature; anti-[[2]] reacts best at 37°C.",
      "back": "1: A/B (isohemagglutinins)  2: Rh (anti-D)",
      "tags": ["abo", "rh"],
      "difficulty": 2
    }
  ]
}
```

## Formulas (`formulas.json`)

```json
{ "id": "calc-corrected-wbc",
  "title": "Corrected WBC count (nucleated RBCs)",
  "formula": "Corrected WBC = measured WBC × 100 / (100 + nRBC per 100 WBC)",
  "variables": [{ "name": "measured WBC", "unit": "×10^9/L" }],
  "example": "WBC 25×10^9/L with 50 nRBC/100 WBC → 25 × 100/150 = 16.7×10^9/L",
  "caveat": "Applies when nRBCs are reported per 100 WBC on differential.",
  "reference": "Henry's Clinical Diagnosis, 24th ed." }
```

## Memes & mini-games (`memes.json`)

Entries of `type`: `MEME_OF_THE_LESSON`, `MEMORY_HOOK`, `KEYWORD_CHALLENGE`,
`GUESS_THE_DIAGNOSIS`, `LABORATORY_MYSTERY`, `IMPOSTOR`, `BOSS_BATTLE`,
`MISTAKE_OF_THE_DAY`. Each entry: `{ "id", "type", "subjectId", "title", "body",
"punchline", "scientificFact", "lessonId" }`. The `scientificFact` field is mandatory —
humor must reinforce accurate knowledge. No copyrighted meme images; illustrated
placeholders use original SVG in `web/public/memes/`.
