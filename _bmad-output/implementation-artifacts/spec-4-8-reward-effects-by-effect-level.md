---
title: 'Story 4.8: Reward effects by effect level'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '38a8ef2ab4112d78cb4b2e7719fc314fa1514934'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Stars are counted (Story 4.7) but a finished step has no celebration, and a not-done step has no encouraging moment.

**Approach:** `ui/child/RewardEffects.kt` plays the step-end effect by the child's effect level (only `full` and `calm` exist — the epic's "off" was removed 2026-11-12, AD-11): **full** = star flies into the star bar + Konfetti burst around the visual + chime + character `celebrateSmall`; **calm** = slow fade-in of the star + soft sound + `celebrateSmall`, no particles. Not done after attempt 3 → character `encourage` + the encouraging line (existing catalogue/ui string or audio; text shown calmly), no negative sound or image. Effects start only after the instruction audio has ended (wait), never overlap the next step's audio, and a pure `FlashSchedule` guarantees ≤ 3 flashes per second (unit-tested). Sound effects: no sfx assets exist yet (Story 2.6 deferred) — play nothing when the asset is missing (silent fallback) and keep asset paths in one place so the sfx can be dropped in later. Konfetti added (fixed stack, newest stable).

## Boundaries & Constraints

**Always:** Effects are shown after the verdict/reward is saved (driven by runner state, not by taps). Never flash > 3/s; motion respects the system "remove animations" setting (fall back to calm). Effect duration bounded (≤ 2 s full, ≤ 1.5 s calm) and never blocks the Done/next flow longer than that. No red/orange, RTL, targets unchanged. Effect level read from the child profile.

**Never:** No reward screen/sticker book (4.9). No new art or sfx files. Do not edit training/, data/, AppNavigation, or TrainingScreen (another story in progress).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Full, done | effect full | star flight + confetti + chime + celebrateSmall | missing sfx → silent |
| Calm, done | effect calm | fade + soft sound + celebrateSmall, no particles | N/A |
| Not done ×3 | step ends | encourage + encouraging line; nothing negative | N/A |
| Audio playing | instruction audio | effect waits for its end | N/A |
| Flash rate | any effect | ≤ 3 flashes/s (FlashSchedule test) | N/A |
| Reduced motion | system setting | calm behaviour | N/A |

</frozen-after-approval>

## Code Map

- `android/.../ui/child/StepScreen.kt`, `StepViewModel.kt` (state when a step finishes), `StarBar.kt`, `CharacterView.kt`, `AudioPlayer.kt`; new `RewardEffects.kt`, `FlashSchedule` (pure).
- `android/gradle/libs.versions.toml` -- Konfetti.
- `android/app/src/main/assets/character/<variant>/react/` -- celebrateSmall, encourage clips.

## Tasks & Acceptance

**Execution:**
- [x] FlashSchedule + tests; StepViewModel effect event after save, waits for audio.
- [x] RewardEffects full/calm/encourage; StarBar flight/fade; CharacterView clip switch.
- [x] Konfetti dependency; silent sfx fallback; reduced motion.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Patched: effect plays at step end and holds the next step's audio (no late celebration), star target measured (RTL single mirror), last step waits for the effect, pause drops the effect, reduced-motion re-read. Doubt-ended step gets the celebration (child never notices doubt). No sfx files yet (silent fallback).

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
