---
title: 'Stories 5.3 + 5.4: Compare two assessments and training history (Progress tab)'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '0f9818c5e018eaf919776552f78904b197b8882e'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
  - '{project-root}/docs/reference/scoring-rules.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The child profile's Progress tab is a placeholder: the therapist cannot follow progress between assessments or see what was practised.

**Approach:** Progress tab with two sections. (5.3) Compare: pure `RulesEngine.compare(a, b)` per the design ("uses sessions of the same taskId, includes steps with a level in both, and reports per-domain averages of those steps for each session and the difference"), corrections applied (effective levels), not scored excluded. `CompareScreen`: pick the first finished assessment, the second picker offers only finished sessions of the same task; table/bars per domain with average A, average B, difference (one decimal floor like the report, signed, with a calm up/down word — never red), steps used count, "no common scored steps" state. (5.4) Training history: each saved training result newest first — activity name (Arabic title), count (or "—" for guided), duration, criterion met (word + icon), date; empty state.

## Boundaries & Constraints

**Always:** Only finished assessment sessions of non-eval child; Visual-motor folds into Motor coordination as in Rule 2; domain names/colours as the report. RulesEngine coverage ≥ 90 %. Western digits, RTL, strings ar/en (plurals). Read-only.

**Never:** No schema change. No edits to ui/child, DiagnosticsScreen, export or blind review files (parallel stories).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Same task | two done sessions | per-domain A, B, diff over common steps | N/A |
| Corrected step | correction in A | effective level used | N/A |
| Not scored in one | step | excluded | N/A |
| No common steps | | calm empty message | N/A |
| Different task | picker | not offered | N/A |
| Fewer than 2 sessions | | hint to run another assessment | N/A |
| Training history | rows | newest first, guided count "—" | empty state |

</frozen-after-approval>

## Code Map

- `android/.../scoring/RulesEngine.kt`; `data/Results.kt`, `Repositories.kt` (SessionHistory, TrainingResultStore).
- `android/.../ui/therapist/ChildProfileScreen.kt` (Progress tab), new `CompareScreen.kt`, `TrainingHistoryScreen.kt` (+ ViewModels/UiState).

## Tasks & Acceptance

**Execution:**
- [x] compare + tests.
- [x] Compare UI + picker filtering + tests.
- [x] Training history + tests.

**Acceptance Criteria:**
- Given the coordinator's gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Compare engine verified. 6 findings patched: always older→newer with dated columns, trend from the real difference (slightly higher/lower under 0.1), LTR isolate for the signed number, merged row semantics, date+time chips on startedAt, eval child excluded + 'guided' word.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL (run by the coordinator)
