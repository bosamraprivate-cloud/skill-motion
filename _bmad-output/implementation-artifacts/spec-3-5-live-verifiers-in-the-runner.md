---
title: 'Story 3.5: Live verifiers in the runner with evidence frames'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: 'b5a72fd8b9dfa5faf3ac1358195e83858bd85694'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-Ai therapist-2026-11-11/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The runner records attempts with `NoneVerifier`; camera-scored steps are never judged and no evidence frames are kept.

**Approach:** During a child run of a camera-scored task, the front camera and only the landmarkers in the step's `Detector.landmarkers` run in the background (no preview shown to the child). Frames and paired landmarks (nearest within 100 ms, else not visible) are buffered per attempt window. At window close the runner calls a `VerifierRouter` that picks `NoneVerifier` / `PoseVerifier` / `HandVerifier` / `CloudVerifier` by `method`, with `VisibilityGate` first, zones from `SecureSettings`. Three evidence frames (start/middle/end of the window) are encrypted into `FrameStore` and their paths saved with the attempt row; cloud verdicts store `modelId` and `promptVersion`.

## Boundaries & Constraints

**Always:** Body steps (pose/hand) make no network request (test with a failing HTTP client). Verifier errors/timeouts → `CANNOT_TELL`, never `NOT_DONE`. Frames are written only together with an attempt row (interrupted windows write none). Front image un-mirrored before rules. Camera and landmarkers stop on pause, exit, finish, and when the step is `none`/content-only. Memory bounded (downscaled frames, ring buffer per window). No child name, code or session ID in Logcat or request bodies (test the CloudVerifier request body). Existing CloudVerifier is reused as is for the request; offline/network handling beyond "error → cannot tell" is Story 3.6. Content-only tasks behave exactly as today.

**Never:** No camera preview shown to the child. No new scoring rules, no thresholds in Kotlin. No schema change unless `framePaths`/`modelId`/`promptVersion` columns are missing (then an explicit Migration + test).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Pose step | window closes | gate → PoseVerifier; verdict saved; 3 frames | N/A |
| Hand step | handsRubbing | POSE+HAND landmarkers; HandVerifier | N/A |
| Cloud step | question | CloudVerifier; modelId + promptVersion saved | error → CANNOT_TELL |
| Body step offline | failing HTTP client | judged locally, no request | N/A |
| None step | Done | not scored as today; camera idle | N/A |
| Pause mid-window | pause | buffer dropped, no row, no frames | N/A |
| Verifier throws/hangs | exception/timeout | CANNOT_TELL | logged by type only |
| Camera unavailable | bind fails | steps judged CANNOT_TELL out_of_frame | N/A |

</frozen-after-approval>

## Code Map

- `android/.../session/AssessmentRunner.kt`, `AssessmentRunnerFactory.kt`, `RunnerState.kt` -- Judge state calls the verifier; pass frames/landmarks.
- `android/.../vision/*` -- PoseVerifier, HandVerifier, CloudVerifier, NoneVerifier, VisibilityGate, Landmarkers, FrameCropper, BitmapFrames; new `VerifierRouter.kt` and a frame/landmark capture component (e.g. `vision/LiveCapture.kt`).
- `android/.../camera/CameraController.kt` -- analysis binding.
- `android/.../data/FrameStore.kt`, `Repositories.kt`, `Entities.kt`, `SecureSettings.kt` (zones).
- `android/.../ui/child/ChildModeHost.kt`, `StepViewModel.kt`, `ChildModeModule` (Hilt) -- provide the router instead of NoneVerifier; lifecycle of capture.

## Tasks & Acceptance

**Execution:**
- [x] VerifierRouter (+ Hilt) and capture component with pairing and per-window buffer.
- [x] Runner: judge via router at window close; 3 encrypted frames saved with the row; cloud metadata.
- [x] Child mode lifecycle: capture runs only for camera-scored steps, stops on pause/exit/finish.
- [x] Tests: router selection, no network for body steps, frames only with a row, pause drops buffer, errors → CANNOT_TELL, no identifiers in request body.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.
- Given the phone, when drink.cup runs, then body steps get verdicts and the attempt rows have 3 frame paths.

## Implementation Notes

## Spec Change Log

## Review Triage Log

8 findings (dropped frames counted invisible, still-close producing NOT_DONE, pre-window samples, orphan frame files, sample buffer memory, timestamp collisions, camera failure silent, capture concurrency): all patched. Rules version bumped to 2 (movement rules). Provider future not cancelled (shared app-wide); a bind generation counter ignores late completions instead. Deferred: with a dead camera each attempt still waits for Done or the 45 s cap.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
