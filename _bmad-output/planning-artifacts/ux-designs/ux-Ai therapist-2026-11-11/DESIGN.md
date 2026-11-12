---
name: Daily Living Skills (Ai therapist)
description: Android tablet app in Egyptian Arabic (RTL). Child mode is a calm, predictable game for autistic children; therapist mode is a clean, data-focused dashboard.
status: final
created: 2026-11-11
updated: 2026-11-11
author: Ahmed Samir
sources:
  - _bmad-output/planning-artifacts/prds/prd-Ai therapist-2026-11-11/prd.md
  - _bmad-output/planning-artifacts/prds/prd-Ai therapist-2026-11-11/addendum.md
  - _bmad-output/planning-artifacts/briefs/brief-ai-therapist-2026-11-11/brief.md
  - docs/designs/daily-living-skills-app.md (wins on conflict)
  - docs/reference/project-details.md (Stage 1)
colors:
  # Child mode (fixed light theme, low saturation)
  child-canvas: '#F6F4EE'
  child-surface: '#FFFFFF'
  child-ink: '#2E3A40'
  child-ink-soft: '#5C6B70'
  done: '#5E9C8A'
  done-pressed: '#4C8473'
  help: '#7FA7C9'
  repeat: '#B9A6D3'
  star: '#E8C26A'
  star-empty: '#E4E0D6'
  schedule-current: '#5E9C8A'
  schedule-done: '#A9CFC2'
  schedule-todo: '#E4E0D6'
  framing-ok: '#5E9C8A'
  framing-wait: '#C9C3B6'
  # Therapist mode
  t-canvas: '#F7F8FA'
  t-surface: '#FFFFFF'
  t-ink: '#1F2933'
  t-ink-secondary: '#52606D'
  t-border: '#E1E5EA'
  t-primary: '#2F6F8F'
  t-on-primary: '#FFFFFF'
  level-3: '#3E8E6E'
  level-2: '#D2A03C'
  level-1: '#C46B4E'
  not-scored: '#9AA5B1'
  cannot-tell: '#7B8794'
  corrected: '#6B5BA8'
  plan-strength: '#3E8E6E'
  plan-mild: '#8DB86B'
  plan-moderate: '#D2A03C'
  plan-severe: '#C46B4E'
  warning: '#B7791F'
  error: '#B23B3B'
typography:
  family: 'Cairo (OFL, bundled; Arabic + Latin)'
  child-instruction:
    size: 40sp
    weight: 700
    lineHeight: 1.4
  child-button:
    size: 32sp
    weight: 700
  child-count:
    size: 96sp
    weight: 800
  t-headline:
    size: 28sp
    weight: 700
  t-title:
    size: 20sp
    weight: 600
  t-body:
    size: 16sp
    weight: 400
  t-label:
    size: 14sp
    weight: 600
  t-data:
    size: 16sp
    weight: 500
    note: 'Tabular figures, Western digits for levels and averages'
rounded:
  sm: 8dp
  md: 16dp
  lg: 28dp
  pill: 999dp
spacing:
  '1': 4dp
  '2': 8dp
  '3': 16dp
  '4': 24dp
  '5': 32dp
  '6': 48dp
  '7': 64dp
components:
  done-area:
    backgroundColor: '{colors.done}'
    textColor: '#FFFFFF'
    typography: '{typography.child-button}'
    rounded: '{rounded.lg}'
    minSize: '220dp x 160dp'
  help-button:
    backgroundColor: '{colors.help}'
    textColor: '{colors.child-ink}'
    rounded: '{rounded.lg}'
    minSize: '120dp x 120dp'
  repeat-button:
    backgroundColor: '{colors.repeat}'
    textColor: '{colors.child-ink}'
    rounded: '{rounded.lg}'
    minSize: '120dp x 120dp'
  star-bar:
    backgroundColor: '{colors.child-surface}'
    rounded: '{rounded.pill}'
    height: 64dp
  step-schedule:
    dot: 20dp
    currentDot: 32dp
  illustration-frame:
    backgroundColor: '{colors.child-surface}'
    rounded: '{rounded.lg}'
  level-chip:
    rounded: '{rounded.pill}'
    typography: '{typography.t-label}'
  t-card:
    backgroundColor: '{colors.t-surface}'
    rounded: '{rounded.md}'
    border: '1dp {colors.t-border}'
  t-button-primary:
    backgroundColor: '{colors.t-primary}'
    textColor: '{colors.t-on-primary}'
    rounded: '{rounded.sm}'
    height: 48dp
---

## Brand & Style

Two faces on one tablet, sharing one font and one shape language.

**Child mode** is a modern, calm game. An original boy character in a flat illustration style (soft outlines, no texture, limited palette) guides every step. Screens are quiet: one illustration, one sentence, three buttons, a row of stars. The child should always know what is on the screen, what to do, and what comes next. Nothing on screen competes with the instruction. Celebration is warm and short, never loud, and there is no negative feedback anywhere.

**Therapist mode** is a clean clinical workspace: white cards on a pale grey canvas, dense but readable tables, colour used only to encode levels and plan levels. It looks like a tool, not a toy.

Source: Stage 1 of `project-details.md` asks for calm colours and clear drawings; the author asked for a modern calm game for children with autism.

## Colors

Child palette is low-saturation sage, sky, lavender and sand on a warm off-white canvas, chosen to keep sensory load low [ASSUMPTION: exact values chosen by the agent].

- **Done (`#5E9C8A` sage)** is the strongest colour in child mode and appears only on the Done area and the current schedule dot.
- **Help (`#7FA7C9` sky)** and **Repeat (`#B9A6D3` lavender)** are softer so Done stays dominant.
- **Star (`#E8C26A`)** is the only warm accent; empty stars are sand.
- **No red, no orange in child mode**, ever, including for errors. A child never sees an error state colour.
- Text on light surfaces uses `child-ink` (`#2E3A40`), contrast above 10:1. White text on Done is used at 32sp bold (large text, AA).

Therapist palette is neutral with one blue primary (`t-primary`). Levels use a fixed scale: `level-3` green, `level-2` amber, `level-1` terracotta, `not-scored` grey. Plan levels reuse the same ramp (strength, mild, moderate, severe). `corrected` purple marks therapist corrections. Colour is never the only signal: every chip also carries the number or word.

Dark mode is not offered in v1 [ASSUMPTION]: child mode keeps one fixed theme for predictability.

## Typography

One family, **Cairo**, bundled (OFL), for Arabic and Latin. Right-to-left throughout.

- `child-instruction` 40sp bold: the one sentence per step, max two lines. Readable at about 1 m; at 1.8 m audio and the illustration carry the instruction.
- `child-button` 32sp bold: Done/Help/Repeat labels, always paired with an icon.
- `child-count` 96sp: the repetition counter in training.
- Therapist scale: 28 / 20 / 16 / 14sp. Data uses tabular Western digits (levels 1–3, averages to one decimal) [ASSUMPTION].

## Layout & Spacing

Landscape tablet, 8dp grid (4 / 8 / 16 / 24 / 32 / 48 / 64). Mirrored for RTL: the reading start is the right edge.

**Child step screen (fixed zones, identical on every step):**
- Top band (64dp): star bar at the start (right), step schedule in the centre, adult corner (small, low-contrast) at the end (left).
- Main area: illustration frame takes about 60% of width on the right; the character (Rive) stands beside it on the left.
- Sentence under the illustration, centred.
- Bottom band: Done area large at the start side; Help and Repeat at the end side, fixed positions.
- Screen margins 32dp; touch targets at least 120dp in child mode.

**Therapist layout:** start-side navigation rail (Children, Replay & evaluation, Diagnostics, Settings), content in cards on a 12-column grid, 24dp gutters; list–detail split for child profile and session detail.

## Elevation & Depth

Flat. Child mode uses tone, not shadow; only the Done area has a soft 2dp shadow so it reads as pressable. Therapist cards use a 1dp border, no shadow; dialogs use Material 3 level 3.

## Shapes

Generous soft corners in child mode (`rounded.lg` 28dp) for buttons and the illustration frame; star bar and schedule dots are pills/circles. Therapist mode uses `rounded.md` cards and `rounded.sm` controls. Illustrations follow their frame corners.

## Components

- **Illustration frame** — white card, one character illustration per step, no background clutter, object of the step in the centre. At prompt level 2 it scales up about 15%; at level 3 it fills the screen.
- **Character (Rive)** — flat-style boy; state machine inputs `idle`, `talk`, `point`, `celebrate` (generic, reserved; matches AD-11 in the Architecture Spine), `celebrateSmall`, `celebrateBig`, `encourage`. Same proportions as the step illustrations (one character sheet).
- **Done area** — biggest element on screen, sage, check icon plus the word "خلصت". Also triggered by the Bluetooth button.
- **Help button** — sky, hand icon plus "ساعدني". **Repeat button** — lavender, replay icon plus "تاني".
- **Star bar** — pill showing stars collected in this task; stars land here.
- **Step schedule** — one dot per step of the task: done steps filled light sage, current larger sage, upcoming sand. No numbers, no failure marking (a step not done looks the same as a done step once passed).
- **Framing outline** — body silhouette outline over camera preview, grey until landmarks visible, then sage.
- **Task reward screen** — character `celebrateBig`, stars earned, sticker progress path (3 slots toward the next sticker).
- **Sticker book** — grid of sticker slots, earned stickers in colour, locked ones as soft outlines (no lock icons).
- **Training counter** — big count and target ("٣ من ١٠" style, number large), progress ring in sage; hold activities show a filling ring.
- **Level chip** (therapist) — pill with level number and word; corrected chips show automatic level struck and corrected level in `corrected`.
- **Evidence frame strip** — 3 thumbnails per attempt (start/middle/end), tap to enlarge.
- **Domain chart** — Vico bar chart of domain averages with plan-level colour bands and step count labels.

## Do's and Don'ts

| Do | Don't |
|---|---|
| Keep every child screen in the same zones | Move buttons between steps or tasks |
| One illustration, one sentence per step | Show text-only or multi-step instructions |
| Pair every icon with a word and audio | Rely on reading |
| Use stars for taking part | Show levels, scores, crosses or red to the child |
| Respect the child's effect level | Flash more than 3 times per second, or animate during instruction audio |
| Encode levels with colour + number | Use colour alone in therapist charts |
