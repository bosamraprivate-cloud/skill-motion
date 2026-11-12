---
title: 'Story 2.12: Attempt window and prompt ladder (pure)'
type: 'feature'
created: '2026-11-13'
status: 'draft'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The runner (Story 2.13) needs the attempt-window close rules, the movement test and the prompt ladder, and live and replay scoring must time them identically.

**Approach:** Pure Kotlin in `session/` with an injected clock and no Android types: `AttemptWindow` decides when a window closes and why (FR-13), `Movement` decides whether the child moved, and `PromptLadder` applies Help/Repeat to the prompt state (FR-12). Exhaustive JVM tests.

## Boundaries & Constraints

**Always:** Constants exactly per FR-13: close on Done; or elapsed ≥ 20 s and no movement in the last 2 s; or elapsed ≥ 45 s. Movement = max displacement of either wrist or elbow over the last 0.5 s > 0.03 × shoulder width (strictly greater). Window opens when instruction audio ends (caller passes the open time). Prompt levels 1–3: Help → next level (cap 3), `helpPressed = true`, attempt number unchanged; Repeat → no change. Constants live in one `WindowRules` object (rules, not clinical content) with `rulesVersion`. No Android imports (Konsist: `session` core stays pure).

**Never:** No UI, no persistence, no runner state machine (2.13), no verifier calls. No hidden landmarks counted as movement: frames with low visibility for a joint are skipped for that joint.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Done | Done at 3 s | closes, reason `done` | N/A |
| Quiet after 20 s | still from 18 s, t = 20 s | closes, reason `still` | N/A |
| Moving after 20 s | movement at 25 s | open until 27 s still | N/A |
| Hard cap | moving constantly | closes at 45 s, reason `timeout` | N/A |
| Threshold | displacement 0.03 × sw / 0.031 × sw | not movement / movement | N/A |
| No shoulders | shoulder width unknown or 0 | no movement decision (treated as not moving) | N/A |
| Low visibility | joint visibility below gate | joint ignored | N/A |
| Help at 1 | level 1 | level 2, helpPressed, same attempt | N/A |
| Help at 3 | level 3 | stays 3, helpPressed | N/A |
| Repeat | any | unchanged | N/A |

</frozen-after-approval>

## Code Map

- `android/app/src/main/java/com/aitherapist/dls/session/PackageMarker.kt` -- delete when real code lands.
- `.../vision/LandmarkModels.kt` -- `LandmarkPoint(x, y, visibility)`; reuse for pose samples (wrists, elbows, shoulders by MediaPipe index).
- `.../vision/VisibilityGate.kt` -- existing visibility threshold; reuse its constant rather than inventing one.
- `.../vision/Geometry.kt` -- distance helpers to reuse.
- `android/app/src/test/.../ArchitectureTest.kt` -- Konsist rules; `session` must not import Android.

## Tasks & Acceptance

**Execution:**
- [ ] `session/WindowRules.kt` -- constants (20 s, 2 s, 45 s, 0.5 s, 0.03) + `RULES_VERSION`.
- [ ] `session/Movement.kt` -- `PoseSample(tMs, joints)` ring of recent samples; `moved(samples, nowMs): Boolean`.
- [ ] `session/AttemptWindow.kt` -- `Clock` interface; `AttemptWindow(openedAtMs)` with `onSample`, `onDone`, `check(nowMs): CloseReason?` (`done`/`still`/`timeout`).
- [ ] `session/PromptLadder.kt` -- `PromptState(level, helpPressed, attempt)`; `onHelp`, `onRepeat`.
- [ ] `test/.../session/AttemptWindowTest.kt`, `MovementTest.kt`, `PromptLadderTest.kt` -- every matrix row, fake clock.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore` runs, then it passes and the new classes are covered by `koverVerifyCore`.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
