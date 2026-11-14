# Epic 4 Context: Results, plan, training and rewards (M4)

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Turn saved attempt facts into results the therapist can act on and give the child a reason to continue. A pure `RulesEngine` computes step levels (with corrections), domain averages, plan levels, strengths/needs and the activity list. The therapist sees a functional report and a treatment plan in the child profile, picks activities, and the child trains with a camera counter or a guided timer. Stars, effects, a task reward screen and a sticker book reward taking part, never the level. Exit (design M4): rules and counter tests pass; report and plan are shown.

## Stories

- Story 4.1: Rule 2, corrections-aware levels and recalculation
- Story 4.2: Functional report screen
- Story 4.3: Treatment plan and activity picker
- Story 4.4: Camera-counted activity selection data
- Story 4.5: Repetition and hold counter
- Story 4.6: Training runner, screens and saved results
- Story 4.7: Reward rules and recorder
- Story 4.8: Reward effects by effect level
- Story 4.9: Task reward screen and sticker book

## Requirements & Constraints

- Rule 1 and Rule 2 come only from `docs/reference/scoring-rules.md`. Rule 2: average 3 = strength (no plan); 2.5 to <3 = mild; 1.5 to <2.5 = moderate; <1.5 = severe. Test exactly 3, 2.5, 1.5 and just below each.
- Domains are the scale's "Functional domain" tags. "Visual-motor coordination" counts as Motor coordination; a step tagged with both counts once. A multi-domain step counts once in each domain. Not scored steps are excluded. A domain with no scored steps returns no average ("No scored steps").
- Strengths = level-3 steps; needs = level-2/1 steps, worded "may be related to" their domains (project-details Stage 7).
- Plan: for each weak domain, list all activities of the matching plan level and domain section, weakest domain first, several plan levels at once, nothing for a strength. Tools, how to perform, goal and criterion are shown verbatim.
- Only finished (`done`) assessment sessions feed reports and plans. The child plan uses the latest finished assessment per task. Replay, running, paused and abandoned sessions never count. Replay (eval child) earns no rewards.
- Camera-counted only when the criterion is a count or hold of one body movement describable by one `angleAbove/angleBelow/pointAbove/distanceBelow` check. Each decision quotes the source criterion. Everything else is guided (illustration, instruction audio when present, timer, Done).
- Counter: hysteresis on `high` then `low`; ignore reps under 0.4 s; noise between thresholds is not counted; `perSide` counts left and right separately; a hold accumulates while true and resets on break.
- Stars: done on attempt 1 = 2; done on attempt 2-3 = 1; not done after attempt 3 with Done = 1. A training set follows the same rules. Stickers: 1 per 3 finished assessment tasks (3/6/9 -> 1/2/3), computed, not stored as truth.
- Effects never start while instruction audio plays; at most 3 flashes per second; not done after attempt 3 plays `encourage` and the encouraging line, never a negative sound or image.
- Kover: `RulesEngine` and `RepCounter` line coverage >= 90%. Every schema change ships an explicit Migration plus instrumented test; destructive migration is forbidden.

## Technical Decisions

- **AD-5 RulesEngine:** `scoring/RulesEngine.kt` is pure (no Android, Room or coroutine imports) and exposes `stepLevel`, `domainAverages`, `planLevel`, `activitiesFor`, `strengthsAndNeeds`, `compare`. Inputs are plain data. No level, average, plan level or activity list is ever written to the DB or cached past a screen state. The latest correction wins. Bump `RULES_VERSION` on any output change.
- **AD-9 one writer per table:** `correction` only from the therapist Session detail screen (upsert on `sessionId`,`stepId`; `level` null = not scored; delete returns to automatic). `training_result` only `TrainingRunner`. `reward` only `rewards/RewardRecorder`, called by `AssessmentRunner` after a step's facts are saved and by `TrainingRunner` after a result is saved, using pure `RewardRules`. Rewards never feed scoring (Konsist/unit check).
- **AD-4 Criterion:** a sealed type keyed by `kind` (`reps|hold`) holding a `Check` plus `target`, `perSide`, `low`, `high`; for counting, `low`/`high` override the check's `deg`. The JSON Schema in `tools/catalog-builder/schema/` is the contract; the builder validates each entry and its activity ID.
- **AD-11 effects:** one `RewardEffects` entry point taking `effectLevel` (`full|calm`; off removed 2026-11-12). Full = star flight + Konfetti + chime + `celebrateSmall`; calm = slow fade + soft sound + `celebrateSmall`. Task end plays `celebrateBig`. One `CharacterView` plays clips; training plays the step/activity clip segment.
- **Data model (design):** `reward(id, childId, sessionId?, stars, stickerId?, createdAt)`, FK child CASCADE. `training_result(id, childId, activityId, count?, durationSec, criterionMet, createdAt)`, FK child CASCADE. `correction(sessionId, stepId, level?)`.
- **AD-2 IDs:** activity ID `<plan>.<domain>.<NN>` (`mild|moderate|severe`, `rom|coord|hand|midline|planning`); never changes once released. Any change to `activity_counters.json` changes `catalogVersion`.
- **UI state:** one ViewModel per screen with sealed `Loading/Empty/Content/Error` UiState; therapist charts and lists use a shimmer skeleton after 150 ms; child mode never shows spinners.

## UX & Interaction Patterns

- **Profile tabs (agreed IA):** Overview (avatar, name, age, last session, Start -> assessment or training), Sessions (history -> session detail with per-step levels, evidence, Correct), Report (domain averages, strengths/needs), Plan (activities, Start training), Progress (compare, training history). Tabs without content show a calm empty state.
- **Session summary:** after every child-mode session, the PIN leads to a summary (each step, its level, Correct), then Overview.
- **Report chart (Vico):** bars carry plan-level colour plus number/word labels and TalkBack descriptions; average to one decimal and scored-step count per domain.
- **Training screen:** big count and target ("٣ من ١٠" style) with a sage ring; completes at the criterion.
- **Rewards:** star bar at top; task reward screen with stars, `celebrateBig` and a 3-slot sticker path; sticker book shows earned stickers in colour and locked ones as soft outlines.

## Current Code Facts

- `android/app/src/main/java/com/aitherapist/dls/scoring/RulesEngine.kt`: only `stepLevel(rows, correction)` with `StepLevel {THREE, TWO, ONE, NOT_SCORED}`, `RuleOutcome`, `RuleAttempt`; `RULES_VERSION = "2"`. No `domainAverages`, `planLevel`, `activitiesFor`, `strengthsAndNeeds`, `compare` yet.
- `data/Entities.kt` + `data/AppDatabase.kt`: entities `child`, `session`, `attempt`; DB **version 2** (v2 added `attempt.modelReason`), schemas `android/app/schemas/.../1.json, 2.json`. Story 4.1's "migration 1->2" is therefore **2->3**; 4.6 and 4.7 follow as 3->4, 4->5 (or fold tables into fewer migrations).
- `session/AssessmentRunner.kt` is the only writer of `session`/`attempt`; no reward hook yet.
- `rewards/` and `training/` contain only `PackageMarker.kt`.
- `ui/child/StarBar.kt` is display only ("star logic and effects come in Epic 4"); `CharacterView.kt` exists. No `RewardEffects`, `TrainingScreen`, `TaskRewardScreen`, `StickerBookScreen`.
- `ui/therapist/ChildProfileScreen.kt`: `ProfileTab` enum OVERVIEW/SESSIONS/REPORT/PLAN/PROGRESS with `PrimaryScrollableTabRow`; only Overview has content, others show `profile_tab_later` empty state. No session summary, session detail or Correct screen exists yet.
- `assets/catalog/activities.json`: 148 activities (mild 48, moderate 50, severe 50; mild rom has 8), fields `id, plan, domain, number, title, tools, howToPerform, goal, criterion, generalGoal, counter (all null), audio (all null), textAr, source`. No `mode` field; guided = `counter == null`.
- `assets/catalog/assessment.json`: 147 steps with `tags` (Motor coordination 74, Motor planning 67, Range of motion 54, Hand skills 47, Visual-motor coordination 26, Midline crossing 12); 8 steps untagged in source.
- `tools/catalog-builder/activity_counters.json` is `[]`. `schema/` holds only `check.schema.json`; **no `criterion.schema.json` yet** (4.4 must add it).
- No `art/stickers/` or `art/sfx/` folders exist.

## Deferred Items Relevant Here

- Story 2.6 stickers and sound effects were deferred: 4.8/4.9 need at least 30 sticker images and the chime, fanfare and soft calm sound (ElevenLabs SFX, one-time). Give step-by-step asset instructions before starting them.
- Jacket "both" chain: `thenTaskId` lives only on the navigation key; persisting it needs a session column and migration. Could ride on one of this epic's migrations.
- Task-name audio on the child task start screen is deferred to the end-of-project audio run; ElevenLabs audio is currently blocked, so training instruction audio may be absent (guided screen must work without it).

## Cross-Story Dependencies

- 4.1 (rules + `correction` table) -> 4.2 (report) and 4.3 (plan). The Correct UI and session summary are not explicitly in any Epic 4 story but 4.1 needs a writer screen for corrections.
- 4.4 (counter data + schema) -> 4.5 (`RepCounter`) -> 4.6 (runner, screen, `training_result`); 4.3's "Start training" hand-off feeds 4.6.
- 4.7 (rules, recorder, `reward`) needs the runner hooks in `AssessmentRunner` and 4.6's `TrainingRunner` -> 4.8 (effects) -> 4.9 (reward screen, sticker book).
- Epic 5 (compare, progress, export) builds on `compare`, `training_result` and corrections from this epic.

## Open Questions

- Effect level `off`: epics 4.8 and the design still list full/calm/off, but AD-11 and the agreed decision keep only full/calm. Drop the `off` AC.
- Who writes `correction` in Epic 4? AD-9 names the Session detail screen, but it and the session summary have no story here; add them to 4.1/4.2 or a new story.
- Stars in 4.7 (2/1/1) count per step; is a step that ends `not scored` (two cannot tells) rewarded? Docs are silent (the child should not notice doubt, which suggests 1 star).
- Untagged steps (8) are scored but feed no domain; confirm they appear in strengths/needs without a domain.
- Whether the 148 criteria yield any camera-counted activities is only known after 4.4; if few qualify, training is mostly guided (design Open Questions).
- Which sticker image goes with which unlock, and how many stickers ship (design says at least 30), is not specified.
