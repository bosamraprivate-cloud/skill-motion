---
title: 'Story 1.2: Camera feed and landmarkers with frame-rate probe'
type: 'feature'
created: '2026-11-12'
status: 'done'
baseline_commit: '2761c0fd65ed0e2af79e2b65413e97c85b6c83b6'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-1-1-android-project-skeleton-with-quality-gates.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** We cannot choose the tablet or the Pose+Hand fallback (NFR-2) without measured frame rates, and later checks need un-mirrored landmarks paired with frames.

**Approach:** Feed the front camera (CameraX) into MediaPipe Pose and Hand landmarkers in LIVE_STREAM mode, un-mirror before any left/right logic, pair results with frames in a `FrameBuffer` (100 ms rule), and show a minimal therapist Diagnostics panel with live per-landmarker and combined fps plus a 60 s measurement run. Record the first result in `docs/designs/m1-spike-results.md`.

## Boundaries & Constraints

**Always:**
- Adapt from google-ai-edge/mediapipe-samples (Apache-2.0); every adapted file keeps the upstream licence header plus a "Modified by" line; credit listed in a new root `THIRD_PARTY_NOTICES.md`.
- MediaPipe tasks-vision is the pinned latest stable (1.2.3); models `pose_landmarker_full.task` and `hand_landmarker.task` from assets (Story 1.1 task). GPU delegate first, fall back to CPU on failure; report which delegate is active.
- Un-mirroring: front-camera frames are flipped horizontally before inference, so the subject's right wrist is MediaPipe `RIGHT_WRIST` (index 16). The preview may stay mirrored for the user.
- `FrameBuffer` is pure Kotlin (no `android.*`), takes an injected time source, keeps a bounded ring (last 3 s), pairs a result to the frame with the nearest timestamp within 100 ms; frames without a result in 100 ms are `visible = false` (AD-3).
- `FpsMeter` is pure Kotlin: rolling 1 s window per stream (pose, hand, combined = frames where both results arrived), plus run averages over a 60 s measurement.
- Diagnostics screen follows AD-13 (Hilt ViewModel, sealed UiState incl. permission-denied state, stateless screen, previews). Start destination becomes Diagnostics; the sample screen stays reachable but not the start.
- Camera permission requested at runtime; denied → explanation + button to app settings. Camera and landmarkers released on stop.
- Never log frames or landmarks; never save frames to disk in this story.

**Never:** No zones, Check evaluation, cloud calls, database writes, or child-mode UI. No new libraries beyond the catalogue (all needed ones are pinned).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Pairing hit | Frame t=1000, result t=1060 | Frame visible, result attached | N/A |
| Pairing miss | Frame t=1000, next result t=1150 or none by t=1100 | Frame `visible=false` | N/A |
| Nearest wins | Results at t=960 and t=1030 for frame t=1000 | t=1030 attached (closest) | N/A |
| Buffer bound | 10 s of frames | Only last 3 s kept | N/A |
| Fps | 30 results in 1 s window | 30.0 fps; window slides | Empty window → 0 |
| Combined fps | Pose 25/s, hand 12/s on shared frames | Combined = frames with both = 12 | N/A |
| Mirror | Synthetic landmark x=0.2 from front camera | Reported x=0.8 before left/right use | N/A |
| GPU fails | Delegate init throws | CPU used, UI shows "CPU" | Error state only if CPU also fails |
| Permission denied | User denies camera | `PermissionDenied` state, settings button | N/A |

</frozen-after-approval>

## Code Map

- `android/app/src/main/java/com/aitherapist/dls/ui/AppNavigation.kt` -- Navigation 3 shell; add `DiagnosticsKey` as start, keep `SampleKey`.
- `android/app/src/main/assets/{pose_landmarker_full,hand_landmarker}.task` -- present via `downloadModels`.
- CameraX 1.6.2 and tasks-vision 1.2.3 already in `libs.versions.toml`; `camera`, `vision`, `ui.therapist` packages contain only `PackageMarker.kt` (delete when real code lands).
- Upstream reference: mediapipe-samples `examples/pose_landmarker/android` (`PoseLandmarkerHelper.kt`, `CameraFragment.kt`) and `hand_landmarker/android`.
- Konsist `ArchitectureTest` forbids `android.*`/`androidx.*` in core packages — `FrameBuffer`/`FpsMeter` in `camera` are shell-side but must stay pure for JVM tests.

## Tasks & Acceptance

**Execution:**
- [x] `<pkg>/camera/CameraController.kt` -- CameraX front camera, ImageAnalysis (RGBA, keep-only-latest, ~640×480), flip horizontally, timestamp, hand bitmap+ts to Landmarkers -- feed
- [x] `<pkg>/camera/FrameBuffer.kt` -- pure pairing ring buffer per Boundaries -- AD-3
- [x] `<pkg>/vision/Landmarkers.kt` -- Pose + Hand LIVE_STREAM wrappers (adapted, licence header), GPU→CPU fallback, results as `Flow` with timestamps -- landmarks
- [x] `<pkg>/vision/FpsMeter.kt` -- pure rolling + run-average fps -- NFR-2
- [x] `<pkg>/ui/therapist/{DiagnosticsScreen,DiagnosticsViewModel,DiagnosticsUiState}.kt` -- preview + skeleton overlay of pose/hand points, fps per stream and combined, delegate, "Run 60 s" with results, permission states -- FR-29
- [x] `<pkg>/ui/AppNavigation.kt` -- Diagnostics as start destination -- reachability
- [x] `res/values/strings.xml`, `res/values-ar/strings.xml` -- new strings -- NFR-8
- [x] `<test>/camera/FrameBufferTest.kt`, `<test>/vision/FpsMeterTest.kt`, `<test>/camera/MirrorTest.kt` -- matrix rows -- verification
- [x] `THIRD_PARTY_NOTICES.md` (root) -- mediapipe-samples credit + Apache-2.0 notice -- licence
- [x] `docs/designs/m1-spike-results.md` -- device model, Android version, delegate, pose/hand/combined fps over 60 s, decision (combined ≥ 15 or fallback), marked provisional until the tablet is chosen -- spike record

**Acceptance Criteria:**
- Given the app on the connected device with permission granted, when "Run 60 s" completes, then pose, hand and combined average fps are shown.
- Given a person raising their right hand, when landmarks are drawn, then the wrist labelled `RIGHT_WRIST` is the raised one.
- Given adapted files, when inspected, then each has the Apache-2.0 header and `THIRD_PARTY_NOTICES.md` lists the credit.

## Implementation Notes

- Environment: same as Story 1.1 (JDK 21 at `C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot`, SDK at `%LOCALAPPDATA%\Android\Sdk`). Test device over adb: Xiaomi 21061110AG, Android 13 (API 33), 2400×1080. Install with `adb install -r`, grant camera with `adb shell pm grant com.aitherapist.dls android.permission.CAMERA`, read fps via screenshot or a debug-only Logcat line that contains numbers only.

## Spec Change Log

- 2026-11-12, device test: the frozen Boundaries line "front-camera frames are flipped horizontally before inference" was wrong. CameraX front ImageAnalysis frames are already un-mirrored, so the flip swapped left/right (raised right wrist was labelled LEFT_WRIST, overlay mirrored). Amended in code with the user's goal kept: inference frames are rotated only; the overlay applies the single mirror for the mirrored preview; overlay box takes the real analysis aspect with PreviewView FIT_CENTER. Verified on device: all points on the subject, RIGHT_WRIST on the raised right wrist. KEEP: FrameTransform pure helper + tests encoding "subject's right = image left in inference frame".

## Review Triage Log

| # | Finding | Verdict | Route | Evidence |
|---|---|---|---|---|
| 1 | Camera can bind after unbind (listener race) | medium | patch | CameraController.bind listener has no disposed check |
| 2 | Bind/provider exceptions crash app | medium | patch | future.get()/bindToLifecycle uncaught on main executor |
| 3 | Rotate+flip matrix untested; MirrorTest tests unused helper | medium | patch | Mirror.flipX only used by overlay |
| 4 | detectAsync exceptions uncaught on analysis thread | medium | patch | Landmarkers.detect has no try/catch |
| 5 | CancellationException becomes Failed | medium | patch | catch (e: Exception) in start() |
| 6 | Dropped frames counted invisible | medium | patch | onFrame adds before detect result known |
| 7 | close() on main during model load; start/stop race leaks landmarkers | medium | patch | @Synchronized close, start on Default not joined |
| 8 | Stale FrameBuffer/pending fps state across stop/startRun; run continues after engine failure | low | patch | not cleared |
| 9 | Permission not re-checked after returning from settings | medium | patch | no ON_RESUME re-check |
| 10 | "Visible frames" is pose-only; durations hard-coded in strings | low | patch | strings.xml |
| 11 | Line-ending rewrite hides real diff | low | patch | AppNavigation.kt and strings.xml fully replaced |
| 12 | Hand/combined fps count empty results | low | patch | doc clarifies inference rate |
| 13 | No DiagnosticsViewModel / CameraController / fallback tests | medium | defer | Landmarkers is final with no seam; spike screen |
| 14 | Overlay may misalign if analysis ratio is not 4:3 | low | defer | measurements unaffected |
| 15 | Two bitmaps allocated per frame | low | defer | perf tuning after first measurement |
| 16 | Diagnostics is the start destination for all builds | medium | defer | gated behind therapist PIN when the shell lands (Epic 2) |
| 17 | First denial sends user to settings instead of re-prompt | low | defer | rationale handling with first-run setup story |
| 18 | Manifest permission, CameraX deps, model download missing | false | rejected | present since Story 1.1 (baseline commit) |
| 19 | 100 ms rule marks late results invisible | false | rejected | that is AD-3's rule as specified |
| 20 | Spike results not measured | maybe-false | pending | requires device run; done before story closes |

## Verification

**Commands:**
- `./gradlew assembleDebug test lint ktlintCheck` (in `android/`) -- expected: BUILD SUCCESSFUL
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` then launch -- expected: no crash in `adb logcat -b crash`

**Manual checks:**
- Run 60 s on the device; numbers recorded in `docs/designs/m1-spike-results.md`.
