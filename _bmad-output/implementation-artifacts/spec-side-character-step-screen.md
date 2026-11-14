---
title: 'Side character guiding each step'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '3ef5f8e'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The step screen shows the character only inside the visual card (still frame or clip), so the child does not feel guided; the owner wants the character always present as a guide.

**Approach:** Re-lay the child step screen (and training guided screen for consistency) into two zones: a **side character panel** (the child's variant, boy or girl, full-height, always visible, animated reaction clips) and a **main area** (step picture + sentence at prompt level 1; the step's demonstration clip from level 2, large at level 3). The character follows the EXPERIENCE state machine: `talk` while instruction audio plays, `idle` while the child tries (attempt window), `point` toward the main area when the step was not done or Help moved to level 2+ — with the demonstration clip sliding/fading in smoothly in the main area at the same moment —, `celebrateSmall` on success (with the Story 4.8 effect), `encourage` after not done at attempt 3. All transitions are smooth (cross-fade between character clips, slide+fade for the main area). Point direction follows RTL (character on the start side, pointing toward the main area).

## Boundaries & Constraints

**Always:** Child-mode rules unchanged (Done ≥220×160 dp in its band, Help/Repeat ≥120 dp, no red/orange, RTL, predictable invariant layout every step). Prompt ladder logic, runner, scoring, rewards untouched — only presentation. Uses existing clips (`talk`, `idle`, `point`, `celebrateSmall`, `encourage`) for both variants; missing girl clips fall back to the boy clip or idle (no crash). Reduced motion → instant switches without slide. Works in tablet landscape and phone portrait (portrait: character panel above or beside at smaller width, never hiding Done). Effects from Story 4.8 keep their timing; the celebration happens on the side character.

**Never:** No new art. No change to when the demonstration appears (from attempt 2 / Help, decided 2026-11-13).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Instruction | audio playing | side character `talk`, main = picture + sentence | N/A |
| Trying | window open | character `idle` | N/A |
| Not done → level 2 | attempt 1 not done | character `point`, demo clip slides in | clip missing → still |
| Help | child taps Help | same as level 2 | N/A |
| Level 3 | | demo large, slow audio, character `point`/`talk` | N/A |
| Success | done | `celebrateSmall` + 4.8 effect on the side | N/A |
| Not done ×3 | | `encourage` | N/A |
| Reduced motion | system | instant switches | N/A |

</frozen-after-approval>

## Code Map

- `android/.../ui/child/StepScreen.kt`, `StepViewModel.kt` (character state already derived), `CharacterView.kt`, `RewardEffects.kt` (star origin), `TaskStartScreen.kt`, `TrainingScreen.kt` (guided layout consistency).
- `android/app/src/main/assets/character/<variant>/react/`, `steps/`.

## Tasks & Acceptance

**Execution:**
- [x] Two-zone layout with persistent side character (landscape + portrait).
- [x] Character state → clip with cross-fade; main area transitions; RTL point direction.
- [x] Training guided screen uses the same side character.
- [x] Update affected tests (UI state only).

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.
- Given the phone, when a step is not done, then the side character points and the demonstration clip appears smoothly.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Review: 7 findings; patched state order (point only briefly at level change), portrait no point, snap clip on level change, calm star from character, training publish, Done band. Accepted: whole-character flip in RTL. Agent stopped before reporting; coordinator ran the full gate (pass) after fixing the portrait test. Verify the training Done band on the phone.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
