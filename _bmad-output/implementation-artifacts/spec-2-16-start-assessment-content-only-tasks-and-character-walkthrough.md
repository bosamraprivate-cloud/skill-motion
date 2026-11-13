---
title: 'Story 2.16: Start assessment, content-only tasks and character walkthrough'
type: 'feature'
created: '2026-11-13'
status: 'draft'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Runs can only be started from the debug "Try step screen"; the real flow (therapist opens a child, starts any of the 15 tasks, hands the tablet over, the child runs the task and ends with the character walkthrough) does not exist, and tasks show as raw IDs.

**Approach:** Add Arabic task names to the catalogue (strings + builder), a "New session" action on the child profile with a task picker (jacket: on, off or both), a child task start screen (task name + picture + big Start), the full run through all steps (content-only tasks: every step plays and is saved not scored), and a closing walkthrough that plays each step's demonstration clip (or still) with its audio back to back, after which the session becomes `done`.

## Boundaries & Constraints

**Always:** Task names come from the source task headings in `docs/reference/` (already parsed as step `source`), translated to short Egyptian Arabic in `strings.ar-eg.json` under `tasks`; the builder adds `nameAr` per task (strict build fails if missing; dev flag warns). New session creates the session row (child, task, catalogVersion, rulesVersion, kind `assessment`, state `running`) via the existing runner start, which abandons an older resumable session of that child. Jacket choice "both" runs `dress.jacket.on` then `dress.jacket.off` as two sessions, the second starting after the first's walkthrough. Content-only/`none` steps behave exactly as today (Done → not scored). Walkthrough: for each step in order, the step's demonstration clip segment for the child's variant (or its still, or the reaction character if both missing) plus normal audio (or the 1.5 s fallback); no buttons except the adult corner; then session `done` and a calm end screen (rewards come in Epic 4). Replace raw task IDs with `nameAr` in the Continue card and Try picker. Child sees no levels or results.

**Never:** No reward screen or stars logic (Epic 4). No framing check or camera (Epic 3). No new clinical content: names are translations of existing headings only.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| New session | child profile → New session → eat.spoon | session row created; child mode opens on start screen | N/A |
| Start screen | child taps Start | first step shown | N/A |
| Jacket on/off | choose on | one session dress.jacket.on | N/A |
| Jacket both | choose both | on runs fully + walkthrough, then off runs as a second session | N/A |
| Content-only | all steps `none` | each Done → not scored; session done after walkthrough | N/A |
| Walkthrough | last step finished | clips/stills in order with audio, then `done` + end screen | missing media → still/reaction |
| Older session | child has paused session | it becomes `abandoned` | N/A |
| Task names | catalogue | Arabic name on picker, start screen, Continue card | missing name → strict build fails |

</frozen-after-approval>

## Code Map

- `tools/catalog-builder/build_catalog.py` + `strings.ar-eg.json` + tests -- add `tasks` names section, emit `tasks:[{id,nameAr,area}]` (or `nameAr` per task) in assessment.json; rebuild with `--allow-missing-media`.
- `android/.../catalog/CatalogModels.kt` -- `CatalogTask.nameAr`; strict decoding.
- `android/.../ui/therapist/ChildProfile*`, `ChildrenRoute.kt` -- add New session action; new `NewSessionDialog.kt` (task list by area, jacket on/off/both).
- `android/.../ui/AppGateViewModel.kt` -- `openChildMode(childId, taskId[, then])` for the jacket chain.
- `android/.../ui/child/StepViewModel.kt`, `StepScreen.kt`, `ChildModeHost.kt` -- add start screen state and walkthrough state; on Finished → walkthrough → session done; new `TaskStartScreen.kt`, `WalkthroughScreen.kt`.
- `android/.../session/AssessmentRunner.kt` -- `Finished` currently sets `done`; move `done` to after the walkthrough (e.g. `complete()`), keep a resumable state until then.
- `android/.../ui/therapist/ChildListScreen.kt`, `TryStepScreenDialog.kt` -- show `nameAr`.

## Tasks & Acceptance

**Execution:**
- [ ] builder + strings + tests: task names; regenerate catalogue assets.
- [ ] catalogue models: `nameAr`.
- [ ] New session dialog + profile action; jacket choice.
- [ ] child start screen; walkthrough; runner completion after walkthrough; jacket chain.
- [ ] task names in Continue card and Try picker.
- [ ] strings ar/en; tests: StepViewModel (start, walkthrough order, missing media, done after walkthrough), runner completion, dialog jacket choice, builder task names.

**Acceptance Criteria:**
- Given the phone, when a therapist opens a child, starts eat.sandwich, and the child completes all steps, then the walkthrough plays and the session is done (no Continue card).
- Given `pytest tools/`, `ruff check tools` and `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug`, when run, then all pass.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `tools/.venv/Scripts/python.exe -m pytest tools/ -q`; `python -m ruff check tools` -- expected: pass
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
