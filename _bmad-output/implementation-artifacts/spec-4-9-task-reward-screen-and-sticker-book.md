---
title: 'Story 4.9: Task reward screen and sticker book'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: 'd2ec8911d2766fb73dbfe32ae44e88a046a67d71'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A finished task ends on a plain calm screen; there is no task reward or sticker collection.

**Approach:** After the last step (assessment) and at the end of a training run, show `TaskRewardScreen`: stars earned in this session/run (from `RewardRecorder`), the character `celebrateBig` clip (full: with a gentle Konfetti burst; calm: clip only, slow fade — same effect-level rules and ≤3 flashes/s as Story 4.8), and a 3-slot sticker path showing progress toward the next sticker (a sticker unlocks every 3 finished tasks). Pure `RewardRules.stickersUnlocked(finishedTasks)` → 3,6,9 → 1,2,3. Finished tasks = finished assessment sessions + finished training runs (existing `totalFinishedTasks`; replay/eval never). A `StickerBookScreen` reachable from the reward screen (child taps a big book button) and from the child profile (therapist) shows earned stickers in colour and locked ones as soft outlines, plus a simple progress path. Stickers art does not exist yet (Story 2.6 deferred): use a fixed list of placeholder sticker IDs rendered as calm vector badges (Material icons in DESIGN child palette) behind one `StickerArt` lookup so real art can replace them later without code changes elsewhere; store the unlocked `stickerId` on the reward row of the unlocking task.

## Boundaries & Constraints

**Always:** Child-mode rules (no red/orange, targets ≥120 dp, RTL, Arabic-Indic digits for child counts, no text-only feedback). Reward screen then continues to the existing flow (jacket chain next task / end / PIN) — never blocks; a big "next" button and auto-continue after a calm delay. Sticker unlock idempotent (no duplicate sticker for the same threshold on restore/retry). Rewards never feed scoring. Strings ar/en.

**Never:** No new art files, no sfx files. No therapist reward editing.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Task finished | 7 stars | reward screen with 7 stars, celebrateBig | N/A |
| 3rd task | finishedTasks 3 | sticker 1 unlocked, path full | N/A |
| 3/6/9 | | 1/2/3 stickers | N/A |
| 4th task | | path 1 of 3 | N/A |
| Sticker book | 2 unlocked of N | 2 coloured, rest outlines | N/A |
| Calm | effect calm | clip only, no confetti | N/A |
| Restore | same task again | no duplicate sticker | N/A |
| Jacket both | first task ends | reward screen then second task | N/A |

</frozen-after-approval>

## Code Map

- `android/.../rewards/RewardRules.kt`, `RewardRecorder.kt`; `data/*` (reward.stickerId).
- `android/.../ui/child/ChildModeHost.kt`, `StepViewModel.kt` (end of task), `TrainingScreen.kt`/`TrainingViewModel.kt` (end of run), `RewardEffects.kt`, `CharacterView.kt`; new `TaskRewardScreen.kt`, `StickerBookScreen.kt`, `StickerArt.kt`.
- `android/.../ui/therapist/ChildProfileScreen.kt` -- sticker book entry (Overview).

## Tasks & Acceptance

**Execution:**
- [x] stickersUnlocked + idempotent unlock + tests.
- [x] TaskRewardScreen (assessment end + training end) + flow continuation + tests.
- [x] StickerBookScreen + StickerArt placeholders; profile entry; strings.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

8 findings patched: a training run is one task, at most one sticker per task, sticker unlock in one transaction, each task carries its own zero-star task row for the sticker, auto-continue pauses (sheet, book, background), reward replaces the screen (no tap-through), no reused sticker ids, consistent path, forced Arabic-Indic digits in child mode. Placeholder sticker visuals until art exists.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
