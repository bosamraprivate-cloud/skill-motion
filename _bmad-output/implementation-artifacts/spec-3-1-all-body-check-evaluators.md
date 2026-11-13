---
title: 'Story 3.1: All body Check evaluators'
type: 'feature'
created: '2026-11-13'
status: 'draft'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/docs/designs/daily-living-skills-app.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `PoseVerifier` only evaluates `distanceBelow` (Story 1.3); every other body `Check` type returns not judgeable, so most camera-scored body steps cannot be judged.

**Approach:** A pure `CheckEvaluator` that evaluates every body check type (`angleAbove`, `angleBelow`, `pointAbove`, `distanceBelow`, `distanceAbove`, `inZone`) on a landmark series with the same rules as today's `distanceBelow` path (visibility gate, `side` handling, continuous `holdSec`, doubt → cannot tell), and `PoseVerifier` delegating to it.

## Boundaries & Constraints

**Always:** Semantics exactly as the design's Check types table (angles in degrees at `vertex` between `a` and `b`; `pointAbove` = `point` higher on screen than `ref` by `margin` × shoulder width; distances normalised by shoulder width; `inZone` = landmark inside the named zone rectangle in normalised coordinates, zones from `SecureSettings.zones()` passed in via `VerifyContext`). `side: either` passes if either side passes; `left`/`right` resolve landmark names; `holdSec` requires the condition on consecutive frames spanning that time (reuse `holdsContinuously`). Visibility first: required landmarks not visible long enough → `CANNOT_TELL out_of_frame`; no shoulder width → `CANNOT_TELL not_judgeable`; `inZone` with a missing zone → `CANNOT_TELL no_zone`. Doubt is never failure. Pure Kotlin (no Android), covered by `koverVerifyCore`. Front camera frames are already un-mirrored upstream.

**Never:** No hand checks (`handsRubbing` is Story 3.2), no cloud questions, no runner wiring (3.5), no threshold values in Kotlin (they come from catalogue checks).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Each type pass | synthetic series meeting the check for ≥ holdSec | DONE `check_passed` | N/A |
| Each type fail | series never meeting it, fully visible | NOT_DONE `check_failed` | N/A |
| Hold broken | condition met but interrupted before holdSec | NOT_DONE | N/A |
| Either side | only right side meets | DONE | N/A |
| Out of frame | required landmarks invisible | CANNOT_TELL `out_of_frame` | N/A |
| No shoulders | shoulder width 0 | CANNOT_TELL `not_judgeable` | N/A |
| Missing zone | `inZone` zone not configured | CANNOT_TELL `no_zone` | N/A |
| Non-body check | `handsRubbing` / `question` | CANNOT_TELL `not_judgeable` (other verifiers handle them) | N/A |

</frozen-after-approval>

## Code Map

- `android/app/src/main/java/com/aitherapist/dls/vision/PoseVerifier.kt` -- `evaluate()` handles only `DistanceBelow`; helpers `holdsContinuously`, `candidateSides`, `resolveIndex`, `indices`, `point`, `shoulderWidth`; move logic into `CheckEvaluator`.
- `.../vision/Geometry.kt` -- angle/distance helpers.
- `.../vision/VisibilityGate.kt`, `LandmarkModels.kt` (`PoseLandmarkIndex`), `Verifier.kt` (`VerifyContext`, `VerdictReason`) -- add `NO_ZONE` reason and zones in `VerifyContext` if absent.
- `.../catalog/CatalogModels.kt` -- `Check` subtypes (fields above).
- `.../data/SecureSettings.kt` -- `zones()` (`Zone` normalised rect); `SecureSettings` must not be imported by `vision` core — pass zones as plain data.
- `android/app/build.gradle.kts` -- kover core filter exact names; add `CheckEvaluator`.
- Existing tests: `test/.../vision/PoseVerifierTest.kt` (keep passing).

## Tasks & Acceptance

**Execution:**
- [ ] `vision/CheckEvaluator.kt` -- evaluate(check, landmarks, zones) → (Outcome, reason) for all six body types.
- [ ] `vision/PoseVerifier.kt` -- delegate; zones from `VerifyContext`.
- [ ] `Verifier.kt` -- `VerdictReason.NO_ZONE`; `VerifyContext.zones` (plain map) if missing.
- [ ] `test/.../vision/CheckEvaluationTest.kt` -- every matrix row for every type (synthetic landmark builders).
- [ ] kover filter.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
