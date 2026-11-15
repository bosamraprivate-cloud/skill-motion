---
title: 'Story 6.3: Portrait support on tablets (every screen, both orientations)'
type: 'feature'
created: '2026-11-15'
status: 'done'
route: 'dispatch'
context:
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

## Intent (owner request 2026-11-15)

**Problem:** The app is locked to landscape (`android:screenOrientation="sensorLandscape"` in the manifest) and every screen is laid out for a wide window. The owner wants the tablet to work in portrait too, with the same sizes and quality ("same measurements, everything").

**Approach:**
1. **Manifest:** `screenOrientation="fullSensor"` (or remove the lock) so the tablet rotates freely; keep `configChanges` handling so rotation never restarts a running child session (check what `MainActivity` declares today; state must survive rotation: ViewModels + `rememberSaveable`).
2. **One adaptive rule:** a small shared helper (e.g. `ui/theme/Adaptive.kt`: `isPortrait()` / `rememberLayoutMode()` from `LocalConfiguration` or `currentWindowAdaptiveInfo()`), used everywhere instead of ad-hoc checks. Tokens (Spacing, Radius, type) stay the same in both orientations; only arrangement changes.
3. **Therapist screens (agent A):** shell navigation rail stays (or becomes a bottom bar in portrait if the rail crowds the content; pick the better one and say why); list–detail panes (`ChildrenRoute`, Settings two-pane) stack or show one pane at a time in portrait with a back affordance; `ChildProfileScreen` header (avatar/name/start column + sticker cabinet) stacks vertically in portrait, cabinet 6 columns; stat tiles keep equal heights; dialogs (`NewSessionDialog`) fit portrait width; report, plan, progress, sessions, session detail, metrics, diagnostics, blind review, compare, replay, zone editor, setup, PIN, about: no clipping, no horizontal overflow, scroll where needed.
4. **Child screens + camera (agent B):** `StepScreen`, `TaskStartScreen`, `TaskRewardScreen` (character above, stars/path/buttons below in portrait), `StickerBookScreen` (fewer columns), `StickerUnlock`, `FramingCheckScreen`, `TrainingScreen`, `AdultPauseSheet`, `SideCharacter`, `TimeRingView`, `RewardEffects`: rearrange row→column in portrait, keep big touch targets and RTL. Camera: preview, analysis transform and landmark overlay must stay correct when the device is portrait (`CameraController` target rotation from the display; `FrameTransform` already handles rotationDegrees — verify). **Zones** (`ZoneEditor`/`ZoneMath`): zones are drawn on a camera frame; decide and implement one safe rule: either zones are stored per orientation, or a running session keeps the orientation it started in (lock the current orientation while a child session/camera step is active, release after). Prefer the lock for sessions (simplest, no scoring risk) and document it.
5. **Docs:** EXPERIENCE.md orientation line; child-flow.md / therapist-flow.md if behaviour changes.

## Boundaries

**Always:** same tokens and touch-target sizes in both orientations; RTL; Western digits in therapist UI; no scoring or clinical change; previews for portrait (`@Preview(widthDp = 800, heightDp = 1280)`) on the main screens; unit tests for any pure logic added (layout-mode helper, zone/orientation rule).
**Never:** no new dependencies unless already in the catalog (material3-adaptive is allowed if present); do not run the full Gradle gate (coordinator runs it); do not touch files owned by the other agent.

## Ownership

- Agent A: `ui/therapist/**` (except nothing in child), `ui/components/**`, `ui/theme/Adaptive.kt` (creates it first; B imports it).
- Agent B: `ui/child/**`, `camera/**`, `AndroidManifest.xml`, `MainActivity.kt`.

## Review Triage Log

- Agent B (child + camera): manifest `fullSensor` (no `configChanges`; rotation outside child mode recreates the activity, ViewModels survive, camera rebinds). Orientation rule `keepsOrientation()` in MainActivity: framing check / training framing on top or child mode active -> `SCREEN_ORIENTATION_LOCKED`, else `FULL_SENSOR` (tested). CameraController sets preview + analysis target rotation from the display; FrameTransform already handles 0/90/180/270 (existing tests), no math change. Framing preview box 3:4 in portrait (`framingAspect`, tested). TaskRewardScreen stacks character above details in portrait; Step/TaskStart/Training already stack via `SideCharacterLayout`; StickerBook grid is adaptive (fewer columns in portrait); FramingCheck already stacks below 840 dp. Portrait previews added (Step, TaskReward, StickerBook). Docs: child-flow.md rotation line, EXPERIENCE.md form factor + orientation line.
- Polish pass (tabs, transitions, portrait screens): new `ui/theme/Motion.kt` (calm slide+fade 260 ms / crossfade 240 ms, FastOutSlowIn, no bounce, none when ANIMATOR_DURATION_SCALE = 0; pure `enterOffsetSign` / `isForwardTab` tested in `MotionTest`). NavDisplay push/pop/predictive-pop use an RTL-aware short slide; entries tagged `CHILD_SCENE` (child mode, training run, child PIN) only crossfade. Child profile tabs: fixed equal-width `PrimaryTabRow` with icons in portrait, scrollable in landscape; tab content `AnimatedContent` slides in the tab direction. New-session domain tabs fixed in portrait when <= 4, content animated. Children single-pane (<840 dp) now renders its own animated list <-> detail with the shared `DetailTopBar` (back icon + child name); Settings sub-pages animate and use the same bar; system Back unchanged (navigator / page BackHandler). PIN and setup-PIN column centred in portrait. Gutters to Spacing tokens on Pin/Setup.

## Verification
- Coordinator gate; device check in portrait and landscape at 1600x2560 (rotate with `adb shell settings put system user_rotation`).
