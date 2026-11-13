---
title: 'Story 3.2: Hand verifier and hands rubbing'
type: 'feature'
created: '2026-11-13'
status: 'done'
baseline_commit: 'c1e3950dce835649c6654aeffb7e7c82997b4d68'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/docs/designs/daily-living-skills-app.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `handsRubbing` (used by `hygiene.handwash.07` and `dress.jacket.off.02`) returns not judgeable; no verifier handles `method: hand`.

**Approach:** Extend the pure `CheckEvaluator` with `handsRubbing` per the design's Check types table, and add `HandVerifier` (implements `Verifier`, never throws) that delegates to it, mirroring `PoseVerifier`.

## Boundaries & Constraints

**Always:** Semantics from the design table: both hands tracked; wrists (hand landmark 0) closer than `maxWristRatio` × shoulder width (pose shoulders in the same frame; if pose shoulders are missing, use the mean hand size — wrist to middle-finger MCP (landmark 9) — as the scale, documented); relative movement of the two wrists over each 0.5 s window exceeds `minMotionRatio` × the same scale; condition held continuously for `holdSec`. Reuse Story 3.1's doubt rules: gaps > MAX_GAP_MS and window edges are unseen; fewer than two hands for a stretch ≥ hold or failing the >50 % gate → `CANNOT_TELL out_of_frame`; no usable scale → `not_judgeable`; non-finite values are unmeasurable; doubt is never failure. Pure Kotlin, in `koverVerifyCore` (add `HandVerifier`).

**Never:** No thresholds in Kotlin (they come from the catalogue check). No runner wiring (3.5). No MediaPipe changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Rubbing | two hands within 0.6, motion ≥ 0.05 per 0.5 s for 2.0 s | DONE `check_passed` | N/A |
| Still hands | close but no motion | NOT_DONE `check_failed` | N/A |
| Too short | rubbing 1.5 s then still, fully visible | NOT_DONE | N/A |
| Far apart | moving but wrists apart | NOT_DONE | N/A |
| One hand missing | only one hand tracked throughout | CANNOT_TELL `out_of_frame` | N/A |
| No scale | no shoulders and degenerate hands | CANNOT_TELL `not_judgeable` | N/A |
| Non-hand check via HandVerifier | e.g. `angleAbove` | delegated to CheckEvaluator body path | N/A |

</frozen-after-approval>

## Code Map

- `android/app/src/main/java/com/aitherapist/dls/vision/CheckEvaluator.kt` -- add `HandsRubbing` branch; reuse per-side/gap doubt machinery.
- `.../vision/PoseVerifier.kt` -- pattern for the verifier wrapper (catch → CANNOT_TELL).
- `.../vision/Verifier.kt` -- `LandmarkFrame.hands`, `Verdict`, `VerdictReason`.
- `.../catalog/CatalogModels.kt:111` -- `Check.HandsRubbing(maxWristRatio, minMotionRatio, holdSec)`.
- Hilt bindings for verifiers (find where `PoseVerifier` is provided) -- provide `HandVerifier` for `Method.HAND`.
- `android/app/build.gradle.kts` -- kover core filter.

## Tasks & Acceptance

**Execution:**
- [x] `CheckEvaluator` handsRubbing.
- [x] `vision/HandVerifier.kt` + binding.
- [x] `test/.../vision/HandsRubbingTest.kt` -- every matrix row with synthetic hand series.
- [x] kover filter.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

- Hold + 0.5 s: motion is measured over the 0.5 s window ending at each frame (largest distance of the wrist offset from its value at the window start), and the hold runs from the first passing frame, so DONE needs `holdSec` + 0.5 s of rubbing. Doubt stretches are compared against `holdSec` + 0.5 s. Accepted by the coordinator; the matrix "Rubbing" test feeds 2.5 s.
- Hand-size fallback: without pose shoulders, the scale is the mean hand size (wrist to middle-finger MCP). A frame scaled this way can pass, but a failing one counts as unmeasured (doubt), never as a fail. No extra constants.
- Hand identity is tracked by nearest wrist; ambiguous pairings (cost difference below `minMotionRatio` x scale) and frames with more than two hands are unmeasured. A single isolated far or unmeasured frame is skipped.
- Hilt binding: there is no verifier binding to extend yet (runner uses `NoneVerifier`); providing `HandVerifier` for `Method.HAND` moves to Story 3.5 with the runner wiring.

## Spec Change Log

## Review Triage Log

7 findings (end-point motion, hand swap pairing, 3+ hands, doubt vs window, hand-size fallback strictness, single noisy frame, test gaps): all patched.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
