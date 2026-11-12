---
title: 'Story 2.11: Child profiles with effect level, character and cascade delete'
type: 'feature'
created: '2026-11-12'
status: 'in-review'
baseline_commit: 'a86e9efa9daf169bfabff6079b769bf4049d35eb'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The shell's Children destination is an empty placeholder; sessions need a child to belong to, the therapist must pick the child's character (boy/girl, AD-11) and effect level, and deleting a child must remove everything it owns.

**Approach:** A Children home (list-detail on the tablet) with search by name/code and an empty state, an add/edit form (name, code, age, character boy/girl with its idle still as a picture, effect level full/calm/off default calm), and a confirmed delete that cascades rows and frame folders through the existing `ChildRepository`.

## Boundaries & Constraints

**Always:** Validation: name and code required (trimmed), code unique (case-insensitive), age 1–18 whole years, character required (no default). Effect level default `calm`. The eval child (`isEval`) is never listed or editable. Delete needs an explicit confirmation dialog naming the child. All data access through repositories off the main thread; AD-13 UiState (Loading/Empty/Error/Content). Arabic strings + English; RTL. Never log name or code.

**Never:** No sessions, report, plan, training or compare tabs (later stories; the profile shows details only). No "Continue" card (Story 2.15). No photo of the real child. No consent field.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| No children | empty DB (eval child may exist) | empty state with "Add child" | N/A |
| Add valid | name, code, age 6, girl | appears in list; effect `calm` | N/A |
| Missing field | no name / no code / no age / no character | save refused, field message | inline |
| Duplicate code | code "A1" exists (any case) | save refused, "code in use" | inline |
| Bad age | 0, 19, "x" | save refused | inline |
| Search | "yo" / code part | list filters by name or code, case-insensitive | N/A |
| Edit | change effect to `off`, character to boy | persists after relaunch | N/A |
| Delete | child with sessions, attempts, frames, confirmed | rows and `files/frames/<sessionId>/` gone | N/A |
| Delete cancel | dialog dismissed | nothing removed | N/A |

</frozen-after-approval>

## Code Map

- `android/app/src/main/java/com/aitherapist/dls/data/Repositories.kt` -- `ChildRepository` (observe, insert, update?, delete with frame cleanup); add update/search/code-exists if missing.
- `.../data/Entities.kt`, `Daos.kt` -- `child` has name, code (unique), ageYears, effectLevel, characterVariant (`ChildCharacter`), isEval; DAO queries may need `codeExists(code, excludeId)` and non-eval observe.
- `.../data/Enums.kt` -- `EffectLevel`, `ChildCharacter`.
- `.../ui/therapist/TherapistShell.kt` -- Children destination placeholder to replace.
- `.../ui/components/{EmptyState,ErrorState,Skeleton}.kt` -- reuse.
- `android/app/src/main/assets/character/<variant>/react/idle.webp` -- first frame as the character picture (via `CharacterView` with effect OFF).
- `android/app/src/androidTest/.../data/DatabaseTest.kt` -- has cascade test pattern to copy for `ChildDeleteTest`.
- material3-adaptive (in the spine stack) -- `ListDetailPaneScaffold` for list-detail.

## Tasks & Acceptance

**Execution:**
- [x] `data/Daos.kt`, `Repositories.kt` -- non-eval observe, update, code-exists (case-insensitive, excluding self); keep internal DAOs.
- [x] `ui/therapist/ChildValidation.kt` -- pure validation per the matrix.
- [x] `ui/therapist/ChildList{Screen,ViewModel,UiState}.kt` -- search, list rows (name, code, age, character thumbnail), empty state, add button.
- [x] `ui/therapist/ChildProfile{Screen,ViewModel,UiState}.kt` -- add/edit form, character picker (two large cards with idle still), effect level segmented control, delete with confirm.
- [x] `TherapistShell.kt` -- Children uses list-detail (adaptive); phone-width falls back to list then detail.
- [ ] strings en + ar.
- [ ] tests: `ChildValidationTest`, `ChildListViewModelTest` (search, empty, eval hidden), `ChildProfileViewModelTest` (every matrix row incl. duplicate code on edit), `androidTest/ChildDeleteTest` (cascade + frames, cancel keeps data).

**Acceptance Criteria:**
- Given the phone, when a child is added, edited and deleted through the UI, then the list reflects each change and survives relaunch.
- Given the gate, when run, then `test lint ktlintCheck koverVerifyCore assembleDebug` and `connectedDebugAndroidTest` pass.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Evidence / route |
|---|---|---|---|
| 1 | Save after delete re-inserts the child | high | original null -> add branch; patch (terminal state) |
| 2 | navigateTo(List) after delete keeps stale detail / back stack; Saved pushes a second entry | medium | patch (navigateBack / replace) |
| 3 | reused "new" ViewModel replays old Saved result | medium | patch (consumeResult) |
| 4 | save/delete failure wipes form; constraint error generic | medium | patch (keep Content, map to CODE_IN_USE) |
| 5 | SQLite lower() ASCII-only vs Kotlin ignoreCase; no length limits | medium | patch (normalise code, ASCII set, limits) |
| 6 | raw exception class names in UI strings | low | patch (friendly text) |
| 7 | cancelledDeleteKeepsEverything cannot fail | low | patch (remove; VM test covers cancel) |
| 8 | real DAO/cascade only verified on device | medium | run connectedDebugAndroidTest at story close |
| 9 | ChildrenRoute navigation untested | low | covered by phone check; deferred (no Compose UI test setup) |
| 10 | age 1-18 has no clinical source | false | spec Design Notes records it as an agent choice; not a scoring parameter |
| 11 | no dirty-check on leaving the form; IME actions; "Saved" a11y | low | therapist-only form; rejected |
| 12 | delete during a running session; orphan frames after crash | low | sessions arrive in 2.13; orphan sweep already deferred (2.9) |

## Design Notes

Age 1–18 and case-insensitive code uniqueness are agent choices not stated in the plan. The character card uses the idle clip's first frame so the therapist sees exactly the character the child will see.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
- `cd android && ./gradlew connectedDebugAndroidTest` -- expected: pass on the phone
