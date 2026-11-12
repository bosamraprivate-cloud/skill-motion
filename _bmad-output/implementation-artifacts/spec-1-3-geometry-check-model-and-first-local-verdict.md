---
title: 'Story 1.3: Geometry, Check model and first local verdict'
type: 'feature'
created: '2026-11-12'
status: 'done'
baseline_commit: '9f7fd763ec8faa051d2cb30635d5aa9ebfdeffbf'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-1-2-camera-feed-and-landmarkers-with-frame-rate-probe.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Landmarks exist but nothing judges a step. Every camera-scored step needs pure geometry, the closed `Check` model (AD-4), the single `Verifier` contract with a visibility gate (AD-3), and proof that a local verdict arrives within 300 ms (NFR-1).

**Approach:** Add pure `Geometry`, the sealed `Check` (all 8 types decode; only `distanceBelow` is evaluated in this story), `Verdict`/`Verifier` types, `VisibilityGate` and a `PoseVerifier` that evaluates `distanceBelow` with continuous hold. Prove it on the device with a debug-only "Try drink.cup.05" attempt on the Diagnostics screen that shows the verdict and latency.

## Boundaries & Constraints

**Always:**
- Check fields and semantics exactly as the design's "Check types" table and AD-4: discriminator `type`; refs `{"lm":"X"}` or `{"mid":["A","B"]}`; side-less name + `side` (`left|right|either`) resolves to `LEFT_/RIGHT_`; `either` passes if either side passes; distances are ratios of shoulder width (LEFT_SHOULDER–RIGHT_SHOULDER) in the same frame; `holdSec` must hold continuously within the attempt window; unknown `type` fails decoding.
- `Verifier.verify(step, frames, landmarks, ctx): Verdict` per AD-3; `Verdict(outcome DONE|NOT_DONE|CANNOT_TELL, reason, modelId, promptVersion, latencyMs)`; enums serialise lower_snake. Verifiers never throw: any exception → `CANNOT_TELL`, reason `not_judgeable`. `NOT_DONE` only from a completed evaluation.
- `VisibilityGate` runs first: needed landmarks have MediaPipe visibility > 0.5 in more than half the paired frames; frames not paired within 100 ms count as not visible; failure → `CANNOT_TELL` reason `out_of_frame`, check not evaluated.
- Landmarks carry `visibility` (add to `LandmarkPoint`); left/right names are MediaPipe names in the un-mirrored inference frame (Story 1.2).
- `Geometry`, `Check`, `VisibilityGate`, `PoseVerifier` evaluation are pure Kotlin (JVM-tested); `catalog` and `vision` core files import no `android.*`/`androidx.*`.
- drink.cup.05's check is copied verbatim from the design's camera-scored step table into a **debug-only** asset (`src/debug/assets/spike/drink.cup.05.json`); no step ID or threshold in main Kotlin.
- Enable the Kover 90% line rule for `Geometry` (deferred from 1.1) and run it as `koverVerifyCore`.

**Never:** No HandVerifier, CloudVerifier, runner state machine, database, or catalogue builder logic (only the JSON Schema file). No invented thresholds or steps.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Decode | Story JSON for `distanceBelow` | `Check.DistanceBelow` with resolved fields | N/A |
| Unknown type | `{"type":"spin"}` | Decoding fails | Exception at load |
| All types | One JSON per design type | Each decodes to its subclass | N/A |
| Hold met | Wrist ≤ 0.6 shoulder widths from mouth midpoint for 0.3 s continuous | `DONE` | N/A |
| Hold short | Same for 0.2 s only | `NOT_DONE` | N/A |
| Hold broken | 0.2 s + gap + 0.2 s | `NOT_DONE` (not cumulative) | N/A |
| Either side | Only RIGHT wrist meets | `DONE` | N/A |
| Not visible | Needed landmarks visibility ≤ 0.5 in most frames | `CANNOT_TELL`, `out_of_frame` | Check not evaluated |
| Zero shoulder width | Shoulders coincide | Frame treated as not passing | No divide-by-zero |
| Exception | Evaluator throws | `CANNOT_TELL`, `not_judgeable` | Never propagates |
| Unsupported type | `angleAbove` given to this story's PoseVerifier | `CANNOT_TELL`, `not_judgeable` | N/A |

</frozen-after-approval>

## Code Map

- `vision/Landmarkers.kt:39` -- `LandmarkPoint(x, y)`: add `visibility: Float` (pose has it; hand points use 1f). `PoseLandmarkIndex` exists: extend to the names needed (shoulders, wrists, mouth L/R) and a name→index map.
- `camera/FrameBuffer.kt` -- pairing (100 ms) and visibility; reuse to build the attempt's `LandmarkFrame` list.
- `ui/therapist/DiagnosticsScreen.kt` / `DiagnosticsViewModel.kt` -- add debug-only "Try drink.cup.05" (BuildConfig.DEBUG): records a 5 s window, verifies, shows outcome, reason, latency; logs numbers-only latency line (tag `DlsVerdict`).
- `catalog/` and `vision/` hold only `PackageMarker.kt` in catalog — delete when real code lands.
- `app/build.gradle.kts` -- Kover `core` variant rule currently `disabled = true`; Konsist `ArchitectureTest` already guards core packages.

## Tasks & Acceptance

**Execution:**
- [ ] `<pkg>/vision/Geometry.kt` -- angle (deg), distance, midpoint, shoulder width, ratio -- pure math
- [ ] `<pkg>/catalog/CatalogModels.kt` -- sealed `Check` (8 types), `LandmarkRef` (lm/mid), `Side`, `Criterion` (`reps|hold`), `Step` (id, method, check), `Detector` (landmarkers) -- AD-4
- [ ] `<pkg>/vision/Verifier.kt` -- `Outcome`, `Verdict`, `LandmarkFrame`, `VerifyContext` (zones), `Verifier` interface -- AD-3
- [ ] `<pkg>/vision/VisibilityGate.kt` -- per Boundaries -- AD-3
- [ ] `<pkg>/vision/PoseVerifier.kt` -- gate → `distanceBelow` with continuous hold; others `not_judgeable`; latency measured -- FR-18
- [ ] `tools/catalog-builder/schema/check.schema.json` -- JSON Schema for `Check` and `Criterion` matching the design table -- contract
- [ ] `src/debug/assets/spike/drink.cup.05.json` + Diagnostics debug action -- device latency proof -- NFR-1
- [ ] `app/build.gradle.kts` -- enable Kover `Geometry` rule in `core` variant -- NFR-7
- [ ] `<test>/vision/GeometryTest.kt`, `<test>/vision/CheckEvaluationTest.kt`, `<test>/catalog/CheckDecodingTest.kt` -- matrix rows -- verification

**Acceptance Criteria:**
- Given the device, when the debug drink.cup.05 attempt closes, then the verdict and its latency (≤ 300 ms) are shown and logged.
- Given `./gradlew koverVerifyCore`, then `Geometry` line coverage ≥ 90%.

## Implementation Notes

- Environment as in Stories 1.1/1.2; device Xiaomi 21061110AG over adb. Git repo on `main`; do not commit (the orchestrator commits).

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Route | Evidence |
|---|---|---|---|---|
| 1 | Hold ignores frame gaps (false DONE) | medium | patch | holdsContinuously compares first/last ts only |
| 2 | Hold met on side that failed the gate | medium | patch | evaluation not restricted to gated sides |
| 3 | Partly invisible attempt → NOT_DONE | medium | patch | AD-3 doubt is never failure |
| 4 | All-zero shoulder width → NOT_DONE | medium | patch | unmeasurable attempt must be CANNOT_TELL |
| 5 | Spike decode errors swallowed; double load; results buffered when idle; attempt not cancelled on clear | low | patch | DiagnosticsViewModel |
| 6 | Spike asset never decoded by a test | medium | patch | no test reads src/debug/assets/spike |
| 7 | Kover core misses PoseVerifier/VisibilityGate | medium | patch | includes only Geometry |
| 8 | Latency mixes pairing wait and evaluation | low | patch | split numbers |
| 9 | Weak test assertions | low | patch | reason-only / outcome-only asserts |
| 10 | CRLF churn hides diffs | low | patch | full-file rewrites; add .gitattributes |
| 11 | Spike thresholds not traced to sources | false | rejected | copied verbatim from design camera-scored step table (drink.cup.05 row) |
| 12 | Window bounds clock vs frame-timestamp domain | maybe-false | defer-to-device | would show as always out_of_frame on device test |
| 13 | Schema ↔ Kotlin drift; no pytest for schema; Step not in schema | medium | defer | catalog builder story |
| 14 | Content errors (side-less name, unknown landmark) only caught at runtime | medium | defer | builder/decode validation story |
| 15 | No DiagnosticsViewModel attempt tests | medium | defer | needs Landmarkers seam |
| 16 | Raw English codes in Arabic debug UI | low | rejected | debug-only spike screen |
| 17 | LandmarkPoint default visibility 1f | low | rejected | fix adds types; pose path sets visibility explicitly |
| 18 | Purity rule keyed by file names | low | defer | move pure code to a dedicated package later |

## Verification

**Commands:**
- `./gradlew assembleDebug test lint ktlintCheck koverVerifyCore` (in `android/`) -- expected: BUILD SUCCESSFUL

**Manual checks:**
- On device: bring wrist to mouth and hold → DONE; keep hands down → NOT_DONE; cover camera → CANNOT_TELL out_of_frame; latency ≤ 300 ms shown.
