---
title: 'Epic 2 UI/UX polish pass'
type: 'refactor'
created: '2026-11-13'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Epic 2 screens work but look like default Material 3: purple/pink dynamic tints, no screen titles, a clipped rail label, plain list rows, an inline edit form as the child profile, a grey rectangle around step clips, placeholder stars. The owner wants the best possible design after every epic.

**Approach:** Apply DESIGN.md tokens strictly and polish the therapist shell and child screens (12 agreed points), with no behaviour change.

## Boundaries & Constraints

**Always (the 12 points):**
1. Therapist theme from DESIGN tokens only: neutral light surfaces, one blue primary (`t-primary`), cards with 1dp outline, no dynamic colour, no purple/pink containers anywhere (selected rows, segmented buttons, dialogs, chips, cards).
2. Rail: Skillmotion wordmark/logo at the top; short labels that never clip (e.g. الأطفال، التقييم، التشخيص، الإعدادات); consistent icons.
3. Every therapist destination has a page title (therapist type scale 28sp) and its actions top-end.
4. Child list rows as cards: character avatar in a circle, name, age; numbers in data use Western digits consistently (DESIGN typography).
5. Continue card: calm neutral card with an icon, child name + task name and a clear "كمّل" button — no pink.
6. Child profile: header card (avatar, name, age, character, effect level) with a prominent primary "ابدأ جلسة" button; editing in a separate edit mode (button "تعديل"), character picker cards smaller.
7. New session dialog: tasks as tiles with a small picture (first step still for the child's variant, or the character idle still) grouped by area.
8. Step visual card background matches the clip background colour and the visual fills the card (no visible inner rectangle); same on the start screen.
9. Hide the star bar until rewards exist (Epic 4).
10. Step schedule dots larger (readable at ~1 m) with distinct done / current / upcoming colours (child palette, no red/orange).
11. Help icon: a helping-hand style icon instead of the info "i".
12. Start screen: the reaction character uses the `point` clip toward the Start button; larger task name.
Keep all child-mode rules (no red/orange, targets ≥120dp, Done ≥220×160dp, RTL). Strings in values-ar and values.

**Never:** No behaviour, navigation or data changes. No new dependencies beyond icons already available (material-icons-extended is acceptable if already in the catalog; otherwise use a vector drawable).

</frozen-after-approval>

## Code Map

- `android/app/src/main/java/com/aitherapist/dls/ui/theme/{Tokens,Color,Theme,Type}.kt` -- tokens; disable dynamic colour; full light colour scheme mapping.
- `.../ui/therapist/TherapistShell.kt`, `ChildList*`, `ChildProfile*`, `ChildrenRoute.kt`, `NewSessionDialog.kt`, `SettingsScreen.kt`, `DiagnosticsScreen.kt` -- shell and therapist screens.
- `.../ui/child/StepScreen.kt`, `TaskStartScreen.kt`, `StepSchedule.kt`, `StarBar.kt`, `CharacterView.kt` -- child screens.
- `.../ui/components/*` -- shared EmptyState/ErrorState.

## Tasks & Acceptance

**Execution:**
- [ ] theme tokens + no dynamic colour.
- [ ] shell rail + page titles.
- [ ] child list cards + continue card + digits.
- [ ] child profile header/edit mode.
- [ ] new session tiles.
- [ ] child screens: visual bg, stars hidden, schedule, help icon, start screen point.
- [ ] update affected tests only where UI state shape changed.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.
- Given the phone in tablet dp, when screenshots are taken, then no purple/pink tints remain and the 12 points are visible.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
