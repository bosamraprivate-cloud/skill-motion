---
title: 'Story 2.12: Attempt window and prompt ladder (pure)'
type: 'feature'
created: '2026-11-13'
status: 'done'
baseline_commit: '1cb8e004fffef7dd03b253fe6d8970cc37dba424'
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
- [x] `session/WindowRules.kt` -- constants (20 s, 2 s, 45 s, 0.5 s, 0.03) + `RULES_VERSION`.
- [x] `session/Movement.kt` -- `PoseSample(tMs, joints)` ring of recent samples; `moved(samples, nowMs): Boolean`.
- [x] `session/AttemptWindow.kt` -- `Clock` interface; `AttemptWindow(openedAtMs)` with `onSample`, `onDone`, `check(nowMs): CloseReason?` (`done`/`still`/`timeout`).
- [x] `session/PromptLadder.kt` -- `PromptState(level, helpPressed, attempt)`; `onHelp`, `onRepeat`.
- [x] `test/.../session/AttemptWindowTest.kt`, `MovementTest.kt`, `PromptLadderTest.kt` -- every matrix row, fake clock.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore` runs, then it passes and the new classes are covered by `koverVerifyCore`.

## Implementation Notes

- Movement is detected over a trailing 0.5 s, so a window closes as `still` 2 s after the last *detected* movement, which is up to 0.5 s after the last actual jump. Tests encode the matrix rows that way (last jump 17.5 s -> closes 20 s).
- Shoulder width = latest sample in the 0.5 s span with both shoulders visible. Added `LEFT_ELBOW`/`RIGHT_ELBOW` to `PoseLandmarkIndex`; Konsist rule added for pure session files; session classes added to the `koverVerifyCore` filter.

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Evidence / route |
|---|---|---|---|
| 1 | closed window reopens; late Done overrides timeout | medium | no latch in check(); patch |
| 2 | out-of-order / pre-open samples corrupt the deque | low | caller contract only; patch (drop) |
| 3 | PromptState accepts invalid level/attempt | low | patch (require) |
| 4 | CloseReason lower_snake promised, not built | low | patch (storageName) |
| 5 | equality boundary and right joints untested | medium | `>` vs `>=` and joint set unpinned; patch tests |
| 6 | kover wildcard filters | low | patch (exact names) |
| 7 | matrix reread: movement dated by 0.5 s lookback | false | FR-13 defines movement over the last 0.5 s; "no movement for 2 s" = test false for 2 s; Implementation Notes record it |
| 8 | no new-attempt transition in the ladder | low | attempt progression belongs to the runner (2.13); rejected here |
| 9 | Clock used unevenly; RULES_VERSION not persisted | low | runner (2.13) owns time source and stores rulesVersion on the session; rejected |
| 10 | status files disagree | false | synced at story close |

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
