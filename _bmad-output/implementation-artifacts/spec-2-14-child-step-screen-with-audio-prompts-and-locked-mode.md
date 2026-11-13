---
title: 'Story 2.14: Child step screen with audio, prompts and locked mode'
type: 'feature'
created: '2026-11-13'
status: 'done'
baseline_commit: '3f4e9df6cf12284249c975f6aef1600dddd2f4de'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The runner (2.13), catalogue (2.8) and character clips (2.3) exist, but there is no child-facing screen: nothing shows a step, plays its audio, offers Done / Help / Repeat, or keeps the child inside the app.

**Approach:** A child-mode host (`ChildModeHost`) bound to one `AssessmentRunner` renders the fixed-zone `StepScreen` from `RunnerState`: top band (star bar display, step schedule, adult corner), main area (step visual from the catalogue media for the child's character variant, reaction character beside it), sentence, bottom band (Done large, Help, Repeat). `AudioPlayer` plays the step's normal or slow mp3 and sends `AudioEnded`; Bluetooth/any key press sends Done (1 s debounce); screen pinning keeps the child in. A therapist-only "Try step screen" entry (Diagnostics) starts a run for a chosen child and task so the screen can be tested now.

## Boundaries & Constraints

**Always:** Layout invariant on every step (DESIGN Layout; RTL: reading start is the right edge): step visual ≈60% width on the start side, reaction character beside it, sentence under the visual (40sp, max 2 lines, Cairo), Done area at the start of the bottom band ≥ 220×160dp, Help and Repeat ≥ 120dp at the end, 32dp margins. Prompt presentation: level 1 = step still frame + sentence + normal audio, character `talk` while audio plays then `idle`; level 2 = audio replayed, character `point`, still enlarged +15%; level 3 = the step's demonstration clip segment large (reaction character hidden so only one clip moves), slow audio, buttons still visible. Done dimmed (not hidden) until `AudioEnded`. Help → runner Help; Repeat → runner Repeat (replays audio + talk). Missing audio (dev catalogue): show the sentence and send `AudioEnded` after a short fixed delay (1.5 s) so the flow still works; missing visual: show the reaction character only. Child never sees red/orange, scores, levels, countdowns, errors, cannot-tell or network state; a runner `Error` shows a calm "wait" visual to the child and is retried automatically. Adult corner: long-press 2 s opens the existing PIN screen to leave child mode (pause sheet is Story 2.15). Screen pinning via `startLockTask` on entering child mode and `stopLockTask` on leaving. Effect level only full/calm (no off).

**Never:** No reward effects or star logic beyond displaying the runner's star count placeholder (Epic 4). No pause/resume sheet (2.15). No camera, verifiers or framing check (Epic 3). No walkthrough (2.16). No new playback stack (animated WebP via Coil + `MediaPlayer`/ExoPlayer-free audio).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Level 1 | Listen, step with audio | still + sentence + audio; talk; Done dimmed until audio ends | N/A |
| Level 2 | after Help or not_done | audio again, point, still 115% | N/A |
| Level 3 | prompt level 3 | clip segment large, slow audio, reaction hidden | clip missing → still large |
| No audio | audio null | sentence shown; AudioEnded after 1.5 s | N/A |
| No visual | media null for variant | reaction character only | N/A |
| Done early | tap while audio plays | ignored | N/A |
| BT key | key press in Attempt | one Done; second within 1 s ignored | N/A |
| Repeat | any | audio + talk replay; no prompt change | N/A |
| Runner error | save failure | child sees calm wait; auto retry | no error text to child |
| Leave | back/home | blocked by pinning; adult corner + PIN exits | N/A |
| Finished | last step | calm end visual; returns to therapist after PIN | N/A |

</frozen-after-approval>

## Code Map

- `android/app/src/main/java/com/aitherapist/dls/session/AssessmentRunner.kt`, `RunnerState.kt` -- events `AudioEnded, Done, Help, Repeat, Tick, ...`; states Show/Listen/Attempt/Judge/Finished/Error; needs a Hilt-free factory (steps, store, verifier, clock).
- `.../catalog/CatalogRepository.kt` + `CatalogModels.kt` -- tasks, `CatalogStep` (textAr, audio, media per variant, detector), `toStep()`.
- `.../ui/child/CharacterView.kt` -- `clip`, `still`, `loop`, `stillMode`; `catalog/CharacterAssets.kt` -- reaction clip paths per variant.
- `.../data/Repositories.kt` -- `SessionStore`/`SessionRepository`, `ChildProfiles` (child's `characterVariant`, `effectLevel`).
- `.../vision/NoneVerifier.kt` -- all detectors are `none` today, so Done → not scored (fine for this story).
- `.../ui/AppNavigation.kt`, `AppGateViewModel.kt`, `ui/therapist/DiagnosticsScreen.kt` -- add child-mode destination and the "Try step screen" entry (child + task picker).
- `.../MainActivity.kt` -- key events (dispatchKeyEvent → Done) and lock task.
- `.../ui/theme/Tokens.kt`, `Type.kt` -- child palette, `child-instruction` 40sp, `child-button` 32sp.

## Tasks & Acceptance

**Execution:**
- [x] `audio/AudioPlayer.kt` -- play asset mp3 (normal/slow), completion callback, stop; interface for fakes.
- [x] `ui/child/StepViewModel.kt` + `StepUiState.kt` -- maps RunnerState + catalogue step + variant to UI (visual, character clip, sentence, done enabled, prompt level); drives audio, debounce, missing-audio delay, auto-retry on Error.
- [x] `ui/child/StepScreen.kt`, `StepSchedule.kt`, `StarBar.kt` (display only), `ChildModeHost.kt` -- fixed zones per DESIGN; adult corner long-press.
- [x] `MainActivity.kt` -- key → Done when child mode active; lock task start/stop.
- [x] navigation + Diagnostics "Try step screen" (pick child, pick task) starting a session (`kind` assessment).
- [x] strings (ar + en) for any new UI text.
- [x] tests: `StepViewModelTest` (every matrix row with fake runner/audio/clock), `AudioPlayer` fake contract test, debounce test.

**Acceptance Criteria:**
- Given the phone, when "Try step screen" runs `eat.sandwich` for a boy child, then each step shows its clip/still, sentence, buttons; Help raises the prompt level visibly; Done advances; leaving needs the adult corner + PIN.
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

- Runner is real in `StepViewModelTest` (fake `SessionStore`, virtual-time `Clock`), not a fake runner: covers the actual Listen/Attempt/Error transitions.
- Audio is triggered per LISTEN entry by object identity (each prompt entry is a new object), so Repeat while listening replays even though the state is equal.
- `AudioPlayer.play` returns false for a missing/unplayable file; the 1.5 s fallback covers both null audio and missing files.
- The VM ticks the runner every 1 s during ATTEMPT so the 20 s still / 45 s timeout rules close windows without Done.
- Keys: `MainActivity.dispatchKeyEvent` turns any key except Back into Done (via `ChildModeKeys`) only while the step screen is on top; Back is swallowed there. `@SuppressLint("RestrictedApi")` for the known ComponentActivity false positive.
- Lock task follows `AppGateViewModel.childModeActive` (step screen or its PIN on the stack). Without device-owner, Android shows its own pin confirmation.
- Verifier for non-`none` steps is `NoneVerifier` until Epic 3.

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Evidence / route |
|---|---|---|---|
| 1 | second run reuses finished StepViewModel | high | no ViewModelStore decorator; patch |
| 2 | adult corner fires on cancelled swipe | medium | null from waitForUpOrCancellation treated as timeout; patch |
| 3 | ticks/audio run while PIN screen or background | medium | windows close unseen; patch (pause, restart attempt) |
| 4 | leaving mid-run leaves session running | medium | patch (abandoned) |
| 5 | volume keys swallowed and count as Done | medium | patch (pass through) |
| 6 | MediaPlayer.prepare on main thread; real player untested | medium | patch (prepareAsync + androidTest) |
| 7 | wall clock for intervals | medium | patch (elapsedRealtime) |
| 8 | Help/Repeat not debounced | medium | double tap skips a level; patch |
| 9 | silent start() failure | low | patch (log type) |
| 10 | double openChildMode | low | patch (guard) |
| 11 | TryStepViewModel untested | low | patch tests |
| 12 | lock-task failure not surfaced to adult | low | deferred (device-owner provisioning later) |
| 13 | task picker shows raw ids; adult corner a11y label | low | therapist debug entry; deferred |
| 14 | activity key/lock wiring untested | low | covered by phone check; deferred |

## Design Notes

Two characters on screen (reaction character + the step still that also shows the character) is intended: the still demonstrates, the reaction character guides; at level 3 the reaction character hides so only one clip moves (AD-11). The 1.5 s missing-audio delay keeps the flow testable while audio is postponed to the end of the project.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL

**Manual checks (if no CLI):**
- On the phone: Diagnostics → Try step screen → a boy child → eat.sandwich; walk all 12 steps, try Help and Repeat, try leaving.
