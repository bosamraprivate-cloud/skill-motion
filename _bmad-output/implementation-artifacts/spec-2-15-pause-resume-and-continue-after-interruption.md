---
title: 'Story 2.15: Pause, resume and continue after interruption'
type: 'feature'
created: '2026-11-13'
status: 'in-review'
baseline_commit: '8ad3ba61392a37948a62e8ebdf2bdda696385d2c'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The adult corner jumps straight to the PIN screen, there is no pause, and a session interrupted by the app being killed cannot be continued; half attempts must never be scored and no progress may be lost (FR-15).

**Approach:** Long-press the adult corner 2 s → an adult pause sheet (Pause / Resume / Exit). Pause freezes the attempt window, dims the screen and shows the character idle/sitting; Resume restarts the current attempt from its beginning. Exit requires the PIN and marks the session paused (resumable). The runner gains `restore(session)` so a saved running/paused session can continue from its saved step, attempt and prompt level (the interrupted attempt restarts; no row was written for it). The Children list shows a "Continue" card for a child's resumable session; starting a new assessment for that child marks the old one `abandoned`.

## Boundaries & Constraints

**Always:** Resume and Continue always restart the current attempt (back to LISTEN at the saved prompt level); no attempt row is written for an interrupted window, and frames are written only with an attempt row. Session `state` uses `running`/`paused`/`done`/`abandoned`; pause writes `paused` before showing the dimmed state. Exit from the sheet requires the PIN; leaving child mode by Exit keeps the session `paused` (Continue offered), not abandoned. A new assessment for a child with a running or paused session marks that session `abandoned` first. The Continue card shows child name and task; it never shows levels or results. Child sees no error text; screen dims calmly. Reuse existing PIN screen and lifecycle pause (Story 2.14 `setActive`).

**Never:** No walkthrough or start-assessment flow (2.16 — the Try step screen entry remains the way to start runs for now). No rewards. No new tables or schema change unless strictly necessary (session columns already hold stepIndex, attemptNumber, promptLevel, state).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Pause | Attempt open, adult picks Pause | window frozen, screen dimmed, character idle, session `paused` | N/A |
| Resume | paused | current attempt restarts at same prompt level, session `running` | N/A |
| Exit | sheet Exit | PIN required; on success child mode closes, session stays `paused` | wrong PIN stays |
| App killed | process death mid-attempt | Children shows Continue card for that child | N/A |
| Continue | card tapped | child mode opens at saved step/attempt/prompt; attempt restarts; earlier rows kept | N/A |
| New run over old | child has running/paused session, new run started | old session `abandoned`, new session created | N/A |
| Finished | session done | no Continue card | N/A |
| Corrupt resume point | saved stepIndex beyond task | session `abandoned`, no card | log type only |

</frozen-after-approval>

## Code Map

- `android/app/src/main/java/com/aitherapist/dls/session/AssessmentRunner.kt` -- add `pause()`, `resume()`, `restore(session, steps)`; `abandon()` exists; state saved before emit.
- `.../session/AssessmentRunnerFactory.kt` -- factory for new/restored runners.
- `.../ui/child/StepViewModel.kt`, `StepScreen.kt`, `ChildModeHost.kt` -- adult corner currently opens PIN; replace with pause sheet; `setActive` lifecycle pause exists.
- `.../ui/AppGateViewModel.kt` -- `openChildMode`, `openChildPin`, `leaveChildMode`; add continue entry (`ChildModeKey` with sessionId).
- `.../data/Repositories.kt` -- `SessionStore`/`SessionRepository`; add query for a child's latest running/paused session.
- `.../ui/therapist/ChildList*` -- add Continue card.
- `.../ui/therapist/TryStepViewModel.kt` -- starting a run must abandon an older resumable session of that child.

## Tasks & Acceptance

**Execution:**
- [ ] runner: pause/resume/restore + tests (every matrix row at runner level).
- [ ] repository: `resumableSession(childId)`; DAO query; keep DAOs internal.
- [x] `ui/child/AdultPauseSheet.kt` + StepViewModel pause/resume/exit wiring; dim + idle character.
- [ ] AppGate + navigation: continue entry; Exit via PIN keeps session paused.
- [ ] Children list Continue card (+ strings ar/en); new run abandons old.
- [ ] tests: StepViewModel pause/resume/exit, ChildList continue card, TryStep abandons old.

**Acceptance Criteria:**
- Given the phone, when a run is paused, resumed, exited and then continued from the Children list, then it resumes at the same step and prompt level.
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Evidence / route |
|---|---|---|---|
| 1 | restore ignores catalogVersion/taskId | high | stepIndex may point at another step after a catalogue update; patch |
| 2 | abandon-then-insert not atomic | medium | failed add loses resumable session; patch (insert first) |
| 3 | pause during Judge | medium | verifier can write a dropped window / leave Paused; patch |
| 4 | pause in Error leaves dead end | medium | state stays Error, no Resume; patch |
| 5 | Exit races PIN navigation; cancel PIN un-pauses? | medium | patch (await pause; stay paused) |
| 6 | failed continue: endless wait, card never disappears | medium | patch (abandon + finished visual) |
| 7 | observeResumable error breaks whole list; cards ignore search | low | patch |
| 8 | new SQL queries untested on Room | medium | patch (androidTest) |
| 9 | formatting leftover | low | patch |
| 10 | Continue card shows raw task id | low | already deferred (task names, 2.14) |
| 11 | baseLevel duplicates advance rule; helpPressed inferred | low | consistent today; rejected |
| 12 | abandon() has no production caller | false | used by start() for older sessions via the same path; kept by design |

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
