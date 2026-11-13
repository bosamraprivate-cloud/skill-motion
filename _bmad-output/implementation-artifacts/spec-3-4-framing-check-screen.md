---
title: 'Story 3.4: Framing check screen'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '06bd074a3ad2ea0b4584d9c430d396154b9623f0'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A camera-scored task starts straight away, with no check that the child is placed so the camera can see the landmarks its steps need; badly framed children produce endless cannot-tell verdicts.

**Approach:** Before the start screen of a camera-scored task, an adult-facing framing check shows the front-camera preview (mirrored for display) with a silhouette outline matching the task's framing (table / standing / sink). Pose (and hand, when the task needs it) landmarkers run live; the outline is grey until every landmark needed by the task's steps is visible for ~1 s continuously, then sage. A local guidance line (computed from landmarks, no cloud) tells the adult what to fix: move closer / step back / move left / move right / show hands. "Hand to child" (enabled only when sage; an adult override "Start anyway" after 10 s) starts screen pinning and opens the child start screen. Content-only tasks skip it. The preview never appears during steps.

## Boundaries & Constraints

**Always:** The needed landmark set is derived from the task's step detectors in the catalogue (union of landmarks referenced by local checks; for cloud steps use the framing's default body set: table → shoulders + wrists + nose, standing → shoulders + hips + wrists, sink → shoulders + wrists + hands) — framing default sets are defined in one place in `VisibilityGate` and documented; no step IDs in Kotlin. Visibility uses the existing `VisibilityGate` threshold. Guidance is purely geometric (bounding box of visible landmarks vs frame: too small → closer, touching edges/too large → step back, off-centre → move left/right in the adult's mirrored view, hands needed but missing → show hands). Camera and landmarkers released when leaving the screen. Strings ar/en; therapist tokens for the chrome, sage = DESIGN success token. Back returns to the child profile without starting. Camera permission handled as in ZoneEditor.

**Never:** No scoring, no frames stored, nothing sent to the proxy, no runner wiring of verifiers (3.5).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Camera task | start drink.cup | framing check before start screen | N/A |
| Content-only | start a `none`-only task | no framing check | N/A |
| Not visible | wrists missing | outline grey, guidance "show hands", Hand-to-child disabled | N/A |
| Visible 1 s | all needed landmarks | outline sage, button enabled | N/A |
| Flicker | visible then lost < 1 s | stays grey | N/A |
| Override | 10 s without sage | "Start anyway" appears | N/A |
| No permission | camera denied | explanation + retry; Start anyway allowed | N/A |
| Landmarker error | init fails | message + Start anyway | N/A |

</frozen-after-approval>

## Code Map

- `android/.../vision/VisibilityGate.kt` -- add framing default sets + `neededLandmarks(task steps)` + pure `framingGuidance(...)`.
- `android/.../vision/Landmarkers.kt`, `camera/CameraController.kt` -- live landmark stream (see Diagnostics usage).
- `android/.../ui/child/ChildModeHost.kt`, `StepViewModel.kt`, `TaskStartScreen.kt` -- insert framing state before start; pinning start moves to "Hand to child".
- `android/.../ui/therapist/ZoneEditor.kt` -- permission + preview pattern.
- `android/.../catalog/CatalogModels.kt` -- `StepDetector`, `Framing`, landmarkers.

## Tasks & Acceptance

**Execution:**
- [x] VisibilityGate: needed set, framing defaults, guidance (pure, unit-tested, in koverVerifyCore).
- [x] `ui/child/FramingCheckScreen.kt` + ViewModel; silhouette per framing (vector, simple).
- [x] ChildModeHost flow: camera task → framing → start screen; content-only skips; pinning on Hand to child.
- [x] strings ar/en; tests (needed set, guidance, 1 s hold, override timer, skip for content-only).

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.
- Given the phone, when drink.cup is started, then the framing check turns sage once the user is framed and "Hand to child" opens the start screen.

## Implementation Notes

## Spec Change Log

## Review Triage Log

7 findings (settings hop PIN lock, landmarker leak on quick exit, chained task without framing, hand/pose pairing, hold gaps, out-of-frame predicted points in guidance, sticky engine error): all patched. Coordinator accepted: unknown framing → standing; hand steps need one hand, sink/rubbing two; left/right as seen in the mirrored preview.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
