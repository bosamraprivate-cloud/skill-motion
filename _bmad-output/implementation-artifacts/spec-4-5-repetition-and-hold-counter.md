---
title: 'Story 4.5: Repetition and hold counter'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: 'ee662ad7845758fe5a25cb847d8c5ec590e6d9c3'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
  - '{project-root}/docs/designs/activity-counter-selection.md'
  - '{project-root}/tools/catalog-builder/schema/criterion.schema.json'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Camera-counted activities (Story 4.4: 5 activities with `counter`) have no counter; training cannot count repetitions or holds.

**Approach:** A pure `training/RepCounter` that consumes landmark frames (same `LandmarkFrame`/`Geometry` as vision) for one activity `counter` (`check`, `kind`, `target`, `perSide`, `low`, `high`) and exposes count(s) per side, hold seconds, and target reached. Reps use hysteresis with the per-check-type direction convention from `criterion.schema.json`/`activity-counter-selection.md` (angleAbove/pointAbove: rep = above `high` then below `low`; angleBelow/distanceBelow: below `low` then above `high`); a crossing whose excursion lasts < 0.4 s is ignored; noise between thresholds never counts. `perSide`: left and right counted separately, each must reach `target`. Hold: accumulate continuous time the check is met; reset on break (and on gaps > MAX_GAP_MS like vision). Frames where the needed landmarks are invisible or values non-finite are ignored (they neither count nor break a rep in progress, but a gap > MAX_GAP_MS resets the in-progress phase). Catalogue `counter` model decoded in `CatalogModels` if not already.

## Boundaries & Constraints

**Always:** Pure Kotlin, no Android; thresholds only from the catalogue entry; reuse `Geometry`, landmark name resolution and side handling from vision (no duplication). Kover: add `RepCounter` to `koverVerifyCore`, line coverage ≥ 90 %. Deterministic given timestamps.

**Never:** No UI/runner (4.6). No catalogue edits. Do not touch `ui/therapist/*` or `scoring/RulesEngine.kt` (another story is patching there).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 10 reps | series crossing high then low 10× | count 10 | N/A |
| Short blip | excursion < 0.4 s | ignored | N/A |
| Noise | oscillation between low and high | 0 | N/A |
| angleBelow direction | below low then above high | counted | N/A |
| Per side | left 10, right 7 | left 10, right 7, target not reached | N/A |
| Hold | met 4 s, break, met 6 s | resets; best/current 6 s; target 5 reached | N/A |
| Invisible frames | landmarks missing mid-rep | ignored; long gap resets phase | N/A |
| Target reached | count ≥ target | targetReached true, keeps counting | N/A |

</frozen-after-approval>

## Code Map

- `android/.../vision/Geometry.kt`, `PoseVerifier.kt`/`CheckEvaluator.kt` (landmark resolution, side), `Verifier.kt` (`LandmarkFrame`).
- `android/.../catalog/CatalogModels.kt` -- `Activity.counter` model.
- New `android/.../training/RepCounter.kt`; `test/.../training/RepCounterTest.kt`; `android/app/build.gradle.kts` kover filter.

## Tasks & Acceptance

**Execution:**
- [x] counter model decoding (if missing) + test.
- [x] RepCounter reps (hysteresis, 0.4 s, direction, perSide) + hold.
- [x] Tests for every matrix row; kover filter.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Direction convention verified. 7 findings patched: rep needs ≥0.4 s start→end and ≥100 ms past start (blip vs brisk rep), out-of-order frames ignored, per-side trackers (either = best side), check threshold must equal start threshold, real-catalogue counting tests, open/close framing tuning note. counts/holdSec keyed by side.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
