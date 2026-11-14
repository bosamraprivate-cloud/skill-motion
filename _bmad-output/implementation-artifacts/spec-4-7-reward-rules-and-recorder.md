---
title: 'Story 4.7: Reward rules and recorder'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '9b1a14b3daa74f01832c0588c98c747d6f1caed8'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
  - '{project-root}/docs/designs/daily-living-skills-app.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Children earn nothing; the star bar is hidden and no reward is stored.

**Approach:** Pure `rewards/RewardRules` from the design ("Rewards and effects"): a finished step earns 1 star (done on any attempt, or finished with Done even if not done, or not scored / content-only finished), plus a bonus star when done on attempt 1 → 2 / 1 / 1. `RewardRecorder` writes `reward` rows (`id, childId, sessionId?, stars, stickerId?, createdAt`; Room migration 3→4 + schema + instrumented test) only AFTER the step's attempt facts are saved (hook in `AssessmentRunner` after Rule 1 advance of a finished step), never for replay sessions or the eval child. A `RewardRecorder.recordTrainingSet(childId, outcome)` API using the same rules for Story 4.6. Expose `starsForSession(sessionId)` and `totalFinishedTasks(childId)` (finished assessment + training tasks) for 4.8/4.9; sticker unlock every 3 finished tasks is computed (pure) but sticker IDs are assigned in 4.9 — store `stickerId` null now. Re-show the star bar in child mode with the live star count for the current session (stars only, no effects — 4.8).

## Boundaries & Constraints

**Always:** Rewards never feed RulesEngine/levels (Konsist test: `scoring` must not depend on `rewards`; unit check that results ignore reward rows). Recording is idempotent per (session, step) — a retry/restore never double-counts. Child mode only; replay/eval earns nothing. No negative feedback. Star bar respects child-mode rules (no red/orange, RTL).

**Never:** No effects/sounds/confetti (4.8), no reward screen or sticker book (4.9).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Done attempt 1 | | 2 stars | N/A |
| Done attempt 2–3 | | 1 star | N/A |
| Not done after 3, Done pressed | | 1 star | N/A |
| Not scored / content-only finished | | 1 star | N/A |
| Order | facts saved first | reward after; facts-save failure → no reward | N/A |
| Replay / eval child | | nothing | N/A |
| Restore / retry | same step again | no double reward | N/A |
| Training set | completed | same rules | N/A |
| Migration 3→4 | v3 rows | kept; reward table exists | N/A |

</frozen-after-approval>

## Code Map

- New `android/.../rewards/RewardRules.kt`, `RewardRecorder.kt`; `data/Entities.kt`, `Daos.kt`, `AppDatabase.kt`, `Repositories.kt`, `android/app/schemas/`.
- `android/.../session/AssessmentRunner.kt`, `AssessmentRunnerFactory.kt`; `ui/child/StepViewModel.kt`, `StepScreen.kt`, `StarBar.kt`.
- Tests: `RewardRulesTest`, runner tests, `ArchitectureTest` (Konsist), androidTest `MigrationTest`.

## Tasks & Acceptance

**Execution:**
- [x] RewardRules + tests; kover core filter.
- [x] reward table + migration 3→4 + instrumented test.
- [x] RewardRecorder (idempotent, after facts, no replay) + runner hook + tests.
- [x] Star bar shown with live count.
- [x] Konsist rule.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Migration and ordering verified. 6 findings patched: bonus star mirrors Rule 1 (cannot-tell repeat keeps the bonus), adaptive top band, recordTrainingSet resultId required, stricter Konsist rule (rewards → data allowed by the spine), results-ignore-rewards test, star flow retry. Design doc celebration line no longer says Rive; other Rive mentions remain (docs cleanup later).

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
