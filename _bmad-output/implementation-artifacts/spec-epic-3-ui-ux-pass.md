---
title: 'Epic 3 UI/UX polish pass'
type: 'refactor'
created: '2026-11-14'
status: 'done'
baseline_commit: '95fdd024'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
  - '{project-root}/_bmad-output/implementation-artifacts/deferred-work.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Epic 3 added therapist screens (zone editor, framing check, network banner, replay, advanced tools) built for function, and the agreed therapist IA (EXPERIENCE.md, last section) is not applied yet; phone testing also showed the tester cannot see verdicts and that a missing zone silently turns steps into cannot tell. The owner wants the best design after every epic.

**Approach:** Apply DESIGN.md tokens and the agreed IA to everything Epic 3 touched, plus three small usability fixes from the 3.5 phone test, with no change to scoring.

## Boundaries & Constraints

**Always:**
1. Rail = Children + Settings only (Skillmotion wordmark on top). Diagnostics, Replay & evaluation, Try step screen and Character preview move under Settings → "Advanced tools" (debug builds only).
2. Settings page: grouped cards (Security: PIN · Connection: proxy address/secret + reachability · Setup zones: mini preview of saved zones + "Draw zones" · Advanced tools) with section titles; consistent spacing and 1dp outlined cards.
3. Zone editor: zone chips with colour per zone (mat/sink/place from a calm palette, labelled), rectangle handles visually clear, short helper text, empty state per zone, Done button top-end; works in portrait and landscape.
4. Framing check: large preview, outline grey → sage with a smooth transition, guidance line in a pill with an icon, primary "Hand to child" large, "Start anyway" as a quiet text button; adult-facing tone.
5. Missing-zone warning: when a task needs a zone (`inZone` checks) that is not saved, the framing check shows a notice with a "Draw zones" action (opens the editor and returns); the task can still start.
6. Network banner: calm, slim, icon + text, dismiss-free, auto hides; never shown in child mode.
7. Debug-only result strip in child mode: after each attempt a small corner chip (step id, verdict, reason code, verifier) for 3 s; absent in release builds (BuildConfig.DEBUG); never blocks touches.
8. Replay screen: summary cards (clips, agreement %, cannot-tell %), results table readable (sticky header, verdict chips), actions grouped top-end.
9. Child profile header shows the agreed tabs scaffold (Overview, Sessions, Report, Plan, Progress) with Overview = current profile content and the others as calm empty states "comes later" — only if this fits without breaking current tests; otherwise record as deferred.
Strings in values and values-ar; RTL; Western digits in data; no red/orange in child mode.

**Never:** No scoring, data or navigation-logic changes beyond moving entries into Advanced tools and the zone shortcut. No new dependencies.

</frozen-after-approval>

## Code Map

- `android/.../ui/therapist/TherapistShell.kt`, `SettingsScreen.kt`, `ZoneEditor.kt`, `ReplayScreen.kt`, `DiagnosticsScreen.kt`, `ChildProfileScreen.kt`, `NetworkBannerViewModel.kt`.
- `android/.../ui/child/FramingCheckScreen.kt`, `FramingCheckViewModel.kt`, `StepScreen.kt`, `StepViewModel.kt`.
- `android/.../ui/theme/*`, `ui/components/*`.

## Tasks & Acceptance

**Execution:**
- [x] Rail + Advanced tools + Settings cards.
- [x] Zone editor polish.
- [x] Framing check polish + missing-zone notice.
- [x] Network banner polish.
- [x] Debug result strip.
- [x] Replay screen polish.
- [x] Profile tabs scaffold (or deferred note).
- [x] Update affected tests.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.
- Given the phone in tablet dp, when screenshots are taken, then the points above are visible.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Accepted: debug strip parses the debug trace text (debug only); framing check opens the zone editor in place; missing-zone notice in primary blue; cloud-failure notice fades after 8 s. Screenshot check pending the next phone session.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
