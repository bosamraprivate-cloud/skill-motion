---
title: 'Story 4.6: Training runner, screens and saved results'
type: 'feature'
created: '2026-11-14'
status: 'draft'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** "Start training" (Story 4.3) opens a placeholder; children cannot practise the picked activities and nothing is saved.

**Approach:** A `TrainingRunner` (session-like state machine, one activity at a time, in the order picked) drives child-mode training screens. Camera-counted activities (catalogue `counter`) run the background camera + pose landmarker (reuse `LiveCapture`/`CameraController.bindAnalysis`, no preview to the child) into `RepCounter`; the screen shows a big counter "٣ من ١٠" style (Arabic-Indic digits in child mode as elsewhere in child UI) with a sage progress ring, per-side when `perSide`, a hold timer for `hold`, and completes at the criterion (or Done by the adult/child). Guided activities show the activity title/illustration (character idle still until art exists), instruction audio when present (1.5 s fallback), a running timer and Done. When an activity ends, `TrainingRunner` (sole writer) saves one `training_result` row (`id, childId, activityId, count nullable, durationSec, criterionMet, createdAt`; Room migration 4→5 + schema + instrumented test), then records stars via `RewardRecorder.recordTrainingSet(childId, outcome, resultId)` after the row is saved. The training flow replaces the placeholder: hand-off → framing check (if any selected activity is camera-counted) → pinning → activities → calm end screen → back to therapist.

## Boundaries & Constraints

**Always:** Child-mode rules (no red/orange, targets ≥120 dp, Done ≥220×160 dp, RTL, no text-only errors). Adult corner pause sheet works (pause freezes the timer/counter; exit via PIN). Camera released between activities and on pause/exit. Doubt is never failure: if the camera cannot count (no landmarks), the counter shows nothing negative, the adult can press Done, and `criterionMet` is false with count as counted (never a penalty). Stars follow RewardRules per set. Result rows only from finished activities (interrupted activity writes nothing). Strings ar/en.

**Never:** No scoring/levels changes. No training history screen (Epic 5). No new art.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Counted | moderate.rom.01, 10 reps | counter to 10/10, ring full, row count 10 criterionMet true, stars | N/A |
| Per side | elbow 10 per side | both sides shown; completes when both reach target | N/A |
| Guided | non-counted activity | timer + Done; row count null | N/A |
| No landmarks | camera can't see | no negative UI; Done → row criterionMet false | N/A |
| Pause / exit | adult corner | frozen; exit writes no row for the current activity | N/A |
| Sequence | 3 picked | run in order, end screen | N/A |
| Migration 4→5 | v4 rows | kept; table exists | N/A |

</frozen-after-approval>

## Code Map

- New `android/.../training/TrainingRunner.kt`; `training/RepCounter.kt` (counts keyed by side).
- `android/.../ui/child/TrainingScreen.kt`, `TrainingViewModel.kt`; reuse `FramingCheckScreen`, `AdultPauseSheet`, `ChildModeHost` patterns, `vision/LiveCapture.kt`.
- `android/.../ui/AppGateViewModel.kt`, `AppNavigation.kt` (replace `TrainingPlaceholderScreen`).
- `android/.../data/*` (`training_result`, migration 4→5), `rewards/RewardRecorder.kt`.

## Tasks & Acceptance

**Execution:**
- [ ] training_result table + migration 4→5 + instrumented test.
- [ ] TrainingRunner (+ tests: order, save once, interrupted writes nothing, stars after save).
- [ ] TrainingScreen counted/guided + ViewModel; flow wiring; strings.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.
- Given the phone, when two activities are picked and started, then the counter/timer run and results are saved.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
