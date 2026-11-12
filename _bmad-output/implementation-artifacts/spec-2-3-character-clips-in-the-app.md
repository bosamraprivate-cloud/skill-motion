---
title: 'Story 2.3: character clips in the app (CharacterView + preview screen, eating pilot)'
type: 'feature'
created: '2026-11-12'
status: 'done'
baseline_commit: '15f6c43734bba4de04f5fc9fde09b11706b9f473'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The boy and girl reaction clips and the boy's eating step clips exist only as source videos in `art/`; the app cannot show the character moving, so nothing can be tested on the tablet.

**Approach:** A build tool exports app-ready animated WebP files into assets (transparent reaction clips; opaque per-step segments of the step clips, cut by `tools/art/step_clips.json`, plus a still frame per step). One `CharacterView(variant, clip)` composable plays them with Coil 3, and a therapist-side "Character preview" screen (from Diagnostics) lets the user pick boy/girl and any reaction or eating step.

## Boundaries & Constraints

**Always:** Clips are data: Kotlin never hard-codes a step ID or clip name; the preview lists what an asset index file says. Reaction clips loop; step segments play once and end on their last frame. Effect level `off` shows the still frame (first frame for reactions). One moving clip on screen at a time. Strings in `strings.xml`. AD-13 UiState pattern for the preview screen.

**Never:** No Media3/ExoPlayer or other new playback stack (spine: animated WebP via Coil). No Rive. No child-facing step screen, runner or audio (Stories 2.12–2.14). No girl step clips or non-eating tasks yet (pilot). No hand-editing generated assets.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Reaction | boy, `celebrate` | transparent clip loops over the screen background | N/A |
| Step segment | boy, `eat.spoon.09` | plays only that step's part of `eat.spoon.to-mouth` once, holds last frame | N/A |
| Still-only step | boy, `eat.spoon.02` | shows the still frame, no motion | N/A |
| Effect off | effectLevel `off` | still frame only | N/A |
| Missing asset | index lists a file that is absent | `ErrorState` for that item, app keeps running | logged without child data |
| Null seconds | `startSec`/`endSec` null for a shared clip | exporter splits the clip evenly among its steps in order | printed warning per task |

</frozen-after-approval>

## Code Map

- `tools/art/step_clips.json` -- step → clip/segment/still mapping (eat.sandwich, eat.spoon, eat.fork). Reuse, extend only with seconds.
- `tools/art/cutout_video.py` -- RVM matting + `encode()` (ffmpeg libwebp_anim); reuse `encode` for step segments.
- `art/animations/<variant>/react/*.webp` -- matted reaction clips (output of the running RVM batch).
- `art/animations/boy/steps/raw/*.mp4` -- 18 eating action clips (720x1280, 24 fps, ~4 s).
- `android/app/src/main/java/com/aitherapist/dls/ui/AppNavigation.kt` -- Navigation 3 keys; add `CharacterPreviewKey`.
- `android/.../ui/therapist/DiagnosticsScreen.kt` -- add a button to open the preview (like `onOpenSample`).
- `android/.../ui/sample/*` -- AD-13 reference pattern (Route/Screen/UiState/ViewModel) to copy.
- `android/gradle/libs.versions.toml` -- `coil = "3.6.3"`; add `coil-gif` (animated WebP decoder, same version).
- `android/.../ui/child/PackageMarker.kt` -- delete when `CharacterView` lands in `ui/child`.

## Tasks & Acceptance

**Execution:**
- [x] `tools/art/export_clips.py` -- copy reaction WebPs to `android/app/src/main/assets/character/<variant>/react/<state>.webp`; for each step in `step_clips.json` cut its segment from the raw mp4 (720 px long side, speed 1.25, q75) to `assets/character/boy/steps/<stepId>.webp` and its first frame to `<stepId>.still.webp`; still-only steps get just the still; write `assets/character/index.json` (variants, reactions, tasks → ordered step IDs with file names). Fails on a missing source file.
- [x] `tools/art/test_export_clips.py` -- unit tests: even split for null seconds, still-only step, missing source fails, index content.
- [x] `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts` -- add `coil-gif`.
- [x] `android/.../ui/child/CharacterView.kt` -- composable: asset path + `loop` + `effectLevel`; Coil `AnimatedImageDecoder`; repeatCount 0 for segments; still when `off`.
- [x] `android/.../catalog/CharacterAssets.kt` -- parse `index.json` (kotlinx.serialization) into models; unit-tested.
- [x] `android/.../ui/therapist/CharacterPreview{Screen,UiState,ViewModel}.kt` -- variant toggle, reaction chips, eating task → step list; Loading/Error/Content states.
- [x] `AppNavigation.kt`, `DiagnosticsScreen.kt`, `strings.xml` -- route and entry button.
- [x] `android/.../test/.../CharacterAssetsTest.kt`, `CharacterPreviewViewModelTest.kt` -- parsing and state tests.

**Acceptance Criteria:**
- Given the exported assets, when the preview opens and boy + `idle` is chosen, then the boy breathes/blinks in a loop with no box or halo around him on the app background.
- Given the eating task `eat.sandwich`, when each step is tapped in order, then every step shows a clip segment or a still, and segments stop on their last frame.
- Given the full gradle check, when run, then `test lint ktlintCheck koverVerifyCore` and `pytest tools/` and `ruff check tools` pass.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Evidence / route |
|---|---|---|---|
| 1 | plan_task group order = JSON insertion, output = sorted | medium | real when mapping keys are unordered; patch + test |
| 2 | mixed explicit/null seconds in one shared clip split over all | medium | null steps overlap explicit ranges; patch |
| 3 | stale exported files never removed | medium | export overwrites only; patch (clear output dir) |
| 4 | shipped index.json never decoded in tests; files() unused | medium | contract drift only caught on device; patch (JVM test) |
| 5 | CharacterView decision logic untested; both-null path; private REPEAT constant | low | patch (pure function + tests) |
| 6 | Retry resets effectOff; toggle keeps clipFailed | low | direct fix; patch |
| 7 | empty-state string names a build script | low | user-facing text; patch |
| 8 | Rive dependency still in build.gradle | medium | AD-11 dropped Rive; patch |
| 9 | ffprobe 0/0 fps, sub-frame segment, missing art dir, ffprobe caching | low | unlikely, loud failure; rejected |
| 10 | stale onError after selection change; retry cache; concurrent retries | maybe-false/low | no demonstrated path; rejected |
| 11 | warning only for multi-step groups | low | single-step clips play whole clip correctly; rejected |
| 12 | EffectLevel CALM == FULL | low | real setting arrives in Story 2.11; deferred |
| 13 | contentDescription null, raw IDs in chips | low | therapist-only preview; deferred |
| 14 | STEP_SPEED 1.25 undocumented | false | user asked for livelier motion; matches reaction clips |
| 15 | spec checks differ from AGENTS.md | false | koverVerifyCore is the project's established gate (Epic 1) |
| 16 | index.json maybe not committed; spec sections empty | false | committed at story close; sections filled now |
| 17 | no APK size check | false | user accepted the size (2026-11-12) |
| 18 | sprint key still says "rive" | low | key names are stable IDs; rejected |

## Design Notes

Per-step segments are cut at build time instead of seeking at runtime: animated WebP cannot seek, and this keeps the spine's playback stack (Coil) unchanged. Even splitting is a provisional default until segment seconds are reviewed by eye; it is recorded in `step_clips.json` as null.

## Verification

**Commands:**
- `python tools/art/export_clips.py` -- expected: assets + index.json written, no errors
- `pytest tools/` and `ruff check tools` -- expected: pass
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL

**Manual checks (if no CLI):**
- Install the APK on the phone, open Diagnostics → Character preview, check boy and girl reactions and all eating steps.
