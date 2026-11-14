---
title: 'Story 4.1: Rule 2, corrections-aware levels and recalculation'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: 'f5204173019881096828e73bd1076cd59e9dfcf1'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
  - '{project-root}/docs/reference/scoring-rules.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Only Rule 1 (`stepLevel`) exists; there are no domain averages, plan levels or therapist corrections, so no report or plan can be computed.

**Approach:** Extend the pure `RulesEngine` with Rule 2 exactly as in `docs/reference/scoring-rules.md`: `domainAverages(stepLevels, tags)` and `planLevel(average)`, plus a corrections-aware `effectiveStepLevel` where a therapist correction (level 0–3 or null = not scored) wins over the automatic level. Add a `correction` table (Room migration 2→3, explicit Migration + exported schema + instrumented test) and a repository `results(childId, taskId?)` that recomputes from facts: only finished (`done`) `assessment` sessions; replay/running/paused/abandoned ignored. In the same migration add the nullable `session.nextTaskId` column and persist the jacket "both" chain (deferred from 2.16) so Continue/PIN interruptions keep the second task.

## Boundaries & Constraints

**Always:** Thresholds, domain names and the Visual-motor → Motor coordination counting rule come only from `scoring-rules.md` and catalogue tags (no literals beyond what the doc states; if the doc's boundaries are ambiguous, stop and report). A multi-domain step counts once in each of its domains; not scored steps excluded; empty domain → no average (null). Untagged steps feed no domain (reported separately as count). Results are always recomputed (never stored). Correction rows: id, attemptless (sessionId, stepId), level nullable, reason text optional, createdAt; latest correction per (session, step) wins. Pure Kotlin in RulesEngine, line coverage ≥ 90 % (koverVerifyCore). Destructive migration forbidden.

**Never:** No UI (4.2, Epic 5 Correct screen). No invented clinical values.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Boundaries | averages exactly 3, 2.5, 1.5 and just below | strength / mild / moderate / severe per Rule 2 | N/A |
| VM tag | step tagged Visual-motor + Motor | counted once in Motor coordination | N/A |
| Multi-domain | step in 2 domains | once in each | N/A |
| Not scored | level null | excluded | N/A |
| Empty domain | no steps | no average | N/A |
| Correction | correction exists (incl. null) | wins over automatic | N/A |
| Session states | replay/running/paused/abandoned | ignored | N/A |
| Migration | v2 DB with rows | v3 keeps rows; correction table + nextTaskId exist | N/A |
| Jacket both | interrupted after on, Continue | off still runs after on | N/A |

</frozen-after-approval>

## Code Map

- `android/.../scoring/RulesEngine.kt`, `catalog/CatalogModels.kt` (domain tags).
- `android/.../data/Entities.kt`, `Daos.kt`, `AppDatabase.kt`, `Repositories.kt`, `android/app/schemas/`.
- `android/.../ui/AppGateViewModel.kt`, `session/AssessmentRunner.kt` -- jacket chain (`thenTaskId`) persistence via `session.nextTaskId`.
- Tests: `RulesEngineTest`, androidTest `MigrationTest` (extend for 2→3), repository tests.

## Tasks & Acceptance

**Execution:**
- [x] RulesEngine Rule 2 + corrections-aware level + tests.
- [x] correction table + nextTaskId, migration 2→3 + schema + instrumented test.
- [x] results repository (finished assessment sessions only) + tests.
- [x] jacket chain persisted and restored + test.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

- Correction levels are 1-3 or null (the frozen "0-3" is a slip: `scoring-rules.md` has no score 0). Validated at write time (`CorrectionStore.addCorrection`); an invalid stored level is ignored on read and logged by type only.
- Reports read the latest done assessment per task (`latestPerTask`, startedAt DESC then rowid DESC); jacket on/off are separate tasks.
- Rule 2 table boundaries are applied as written (only an average of exactly 3 is a strength), not "nearest" rounding.

## Spec Change Log

## Review Triage Log

Migration SQL and Rule 2 math verified. 8 findings patched: write-time correction validation (read path never throws), latest done session per task, jacket on/off kept per task, no re-run of an already finished chained task, correction and session tie-breakers by rowid, unknownSteps separate from untagged, migration tests (nextTaskId null, cascade, 1→3). Migration androidTests pending a device run.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL

- Coordinator: correction levels are 1–3 or null (not scored); scoring-rules.md defines no level 0, the "0–3" in Boundaries was a spec slip. Plan level follows the doc's half-open table (only exactly 3 is strength).
