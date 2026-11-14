---
title: 'Story 4.2: Functional report screen'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '595602d68e2dcb16ccc687d42aab139b74876ce5'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
  - '{project-root}/docs/reference/scoring-rules.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Results are computed (Story 4.1) but the therapist cannot see them; the child profile's Report tab is an empty placeholder.

**Approach:** Add pure `RulesEngine.strengthsAndNeeds(...)` and a `ReportScreen` in the child profile's Report tab built from `latestResults(childId)` (latest finished assessment per task, corrections applied): per-task section with strengths (level-3 steps), needs (level-2/1 steps, each with "may be related to" + its domains), and a domain table/chart (average with one decimal, plan level word, scored-step count, or "No scored steps"). `DomainChart` uses Vico (stack) bars coloured by plan level with number + word labels and TalkBack descriptions. Empty state when no finished assessment.

## Boundaries & Constraints

**Always:** Wording "may be related to" (never causal). Step texts and domain names from the catalogue/strings (Arabic UI). Plan-level colours from DESIGN tokens (calm, no red), always paired with a word and number (never colour alone). Western digits for numbers, one decimal. Not scored steps listed separately as "not scored" count (no blame). Untagged/unknown steps shown as a small note with counts. Report always recomputed on open (no stored report). RulesEngine coverage stays ≥ 90 %. Strings ar/en, RTL.

**Never:** No plan/activities (4.3), no corrections UI (Epic 5), no export.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Finished assessment | levels 3/2/1 mixed | strengths, needs with domains, domain rows | N/A |
| Empty domain | no scored steps | "No scored steps" | N/A |
| No assessment | none done | empty state with hint to start a session | N/A |
| Correction applied | correction exists | report reflects it | N/A |
| Several tasks | 2 tasks done | one section per task (latest each) | N/A |
| TalkBack | chart | each bar has a content description | N/A |
| Load error | repository throws | ErrorState with retry | N/A |

</frozen-after-approval>

## Code Map

- `android/.../scoring/RulesEngine.kt`; `data/Results.kt` (`latestResults`).
- `android/.../ui/therapist/ChildProfileScreen.kt` (Report tab), new `ReportScreen.kt`, `ReportViewModel.kt`, `ReportUiState.kt`, `DomainChart.kt`.
- `android/gradle/libs.versions.toml` -- Vico (in the fixed stack; latest stable) if not present.
- `android/.../catalog/*` -- step texts, domain tags.

## Tasks & Acceptance

**Execution:**
- [x] strengthsAndNeeds + tests.
- [x] ReportViewModel/UiState (AD-13 pattern) + tests.
- [x] ReportScreen + DomainChart (Vico) in the Report tab; strings ar/en.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.
- Given the phone with a finished assessment, when the Report tab opens, then strengths, needs and domain bars show.

## Implementation Notes

## Spec Change Log

## Review Triage Log

7 findings patched: averages floored to one decimal (number never contradicts plan word), single chart description + accessible table rows, all domains listed (not covered / no scored steps), RULES_VERSION back to 3, tags from catalog.steps, plurals, short x-axis labels + remembered components. Arabic domain/level labels need owner review.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
