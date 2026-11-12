---
title: 'Story 2.13: Assessment runner state machine with Rule 1'
type: 'feature'
created: '2026-11-13'
status: 'done'
baseline_commit: '87371ff55c692dc5bd874a89199b28e60ca3726b'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/docs/designs/daily-living-skills-app.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** There is no engine that walks a child through a task's steps, closes attempt windows, judges, saves each attempt and decides the step level; the step screen (2.14) and replay (3.7) need one shared, persisted state machine.

**Approach:** `AssessmentRunner` (pure Kotlin + coroutines, no Android UI) driven by events, emitting `StateFlow<RunnerState>` through SHOW → LISTEN → ATTEMPT → JUDGE per the design's session runner, saving every transition and attempt row through `SessionRepository` before emitting, and advancing only by `RulesEngine.stepLevel` on the saved rows. A `NoneVerifier` handles `method: none`.

## Boundaries & Constraints

**Always:** Exactly the design's runner and AD-6: one row per closed window; cannot_tell → repeat the same number once, a second cannot_tell on that number → not scored, next step; not_done → attempt < 3 ? next attempt with promptLevel = min(3, current + 1) : level 1, next step; done → next step. Help: promptLevel +1 (cap 3), helpPressed, replay prompt (back to LISTEN), restart window, no row; Repeat: replay current level, no change. `method none`: show, play, Done → row verifier `none`, outcome `cannot_tell`, reason `not_judgeable`, not scored. `stepLevel(rows, correction)`: first done row → 3 if number 1 and promptLevel 1, else 2; three not_done numbers → 1; two consecutive cannot_tell on one number or verifier none → not scored; correction wins. Session row stores catalogVersion and rulesVersion (`RulesEngine.RULES_VERSION`); `stepIndex/attemptNumber/promptLevel` saved before each emission. A failed step never stops the task. `scoring/RulesEngine` has no Android/Room/coroutine imports. Injected clock and dispatcher; verdicts and timings identical in tests.

**Never:** No UI, audio playback or camera wiring (2.14 / 3.5 provide `AudioEnded`, samples and frames as events/inputs). No pause/resume persistence UI (2.15) beyond saving state. No Rule 2/averages (4.1).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Done first try | verdict done, attempt 1, level 1 | 1 row; stepLevel 3; next step | N/A |
| Help then done | Help on attempt 1, done | 1 row (promptLevel 2, helpPressed); level 2 | N/A |
| Done on attempt 2/3 | not_done, done | 2/3 rows; level 2 | N/A |
| Three not_done | not_done ×3 | 3 rows numbers 1–3, promptLevel 1,2,3; level 1 | N/A |
| Cannot tell twice | cannot_tell, cannot_tell same number | 2 rows same number; not scored; next step | N/A |
| Cannot tell then done | cannot_tell, done same number | 2 rows; level per number/prompt | N/A |
| Method none | Done pressed | row none/cannot_tell/not_judgeable; not scored | N/A |
| Verifier throws | exception | row cannot_tell reason `verifier_error` | never not_done |
| Save fails | repository throws | runner emits Error state, does not advance | state unchanged on retry |
| Last step | step N finished | state Finished; session state `done`, endedAt set | N/A |

</frozen-after-approval>

## Code Map

- `android/app/src/main/java/com/aitherapist/dls/session/AttemptWindow.kt`, `PromptLadder.kt`, `WindowRules.kt`, `Movement.kt` -- Story 2.12; reuse (CloseReason.storageName → attempt.reason when window closes by still/timeout is irrelevant; verdict reason comes from verifier).
- `.../vision/Verifier.kt` -- `Verifier.verify(step, frames, landmarks, ctx): Verdict`, `Outcome`, `Verdict(outcome, reason, modelId?, promptVersion?, latencyMs)`; `VisibilityGate.kt`, `PoseVerifier.kt`, `CloudVerifier.kt` exist.
- `.../data/Repositories.kt` -- `SessionRepository` (session + attempt insert/update/delete); `Entities.kt` session/attempt columns; `Enums.kt` (`Verdict`/`Outcome` storage, session state, kind).
- `.../catalog/CatalogModels.kt` -- step model (id, method) if present; the runner takes `List<RunnerStep(id, method)>` so 2.8 can feed it from the catalogue later.
- `.../scoring/PackageMarker.kt` -- delete when `RulesEngine` lands.
- `android/app/build.gradle.kts` -- kover core filter lists exact classes; add `RulesEngine`, runner classes.

## Tasks & Acceptance

**Execution:**
- [x] `scoring/RulesEngine.kt` -- `RULES_VERSION`, `stepLevel(rows, correction)` returning `StepLevel` (THREE/TWO/ONE/NOT_SCORED).
- [x] `vision/NoneVerifier.kt` -- returns cannot_tell / `not_judgeable`.
- [x] `session/RunnerState.kt` -- sealed states (Show, Listen, Attempt, Judge, Finished, Error) carrying stepIndex, attempt, PromptState.
- [x] `session/AssessmentRunner.kt` -- events `AudioEnded, Done, Help, Repeat, Sample, Tick, VerdictReady`; persistence-before-emit; verifier selection by method (`none` → NoneVerifier, else injected verifier); exceptions → cannot_tell `verifier_error`.
- [x] `build.gradle.kts` -- kover filter entries.
- [x] `test/.../scoring/RulesEngineTest.kt`, `test/.../session/AssessmentRunnerTest.kt` (fake verifier, fake clock, fake repository) -- every matrix row plus "saved before emitted" ordering.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes with the new classes under `koverVerifyCore`.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Evidence / route |
|---|---|---|---|
| 1 | Retry after failed Judge save never judges; session hangs | high | pending holds only save+emit; test masks with manual VerdictReady; patch |
| 2 | verifier has no timeout and runs under the mutex | medium | hang freezes all events; patch (withTimeoutOrNull -> cannot_tell timeout) |
| 3 | openWindow timestamp captured before failed save | low | patch |
| 4 | Help/Repeat reset window before save | low | patch (save first) |
| 5 | frame/landmark buffers unbounded; framePaths empty | medium | memory at camera rate; patch cap; persistence is Story 3.5 |
| 6 | Event name generic and outside kover filter | low | patch (RunnerEvent) |
| 7 | NOT_DONE then CANNOT_TELL, Help cap, ignored events, start retry untested | medium | patch tests |
| 8 | Show conflated with Listen on StateFlow | low | both carry the same fields; UI renders Listen; rejected |
| 9 | Rule 1 branches lack a clinical source | false | AD-6 defines exactly these branches (first done row, none -> not scored) |
| 10 | no restore from saved session | low | Story 2.15 owns resume; rejected |
| 11 | redundant session writes / double attempt read | low | negligible; rejected |
| 12 | status files disagree | false | synced at story close |

## Design Notes

Next-attempt prompt level = current + 1 (cap 3) follows the design diagram "attempt < 3 ? SHOW(next prompt level)": after Help raised the level, the next attempt continues from there. Window close reason (done/still/timeout) is not stored as the verdict reason; the verifier's reason is.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
