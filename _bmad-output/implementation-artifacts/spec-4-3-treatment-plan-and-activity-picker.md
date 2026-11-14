---
title: 'Story 4.3: Treatment plan and activity picker'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: 'bf59e9bc37e4b8160ebefabe00dc2632483637ad'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
  - '{project-root}/docs/reference/treatment-plans.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Plan tab is a placeholder; weak domains do not lead to activities and training cannot start.

**Approach:** Pure `RulesEngine.activitiesFor(domainPlanLevels, activities)` returns, for every domain at mild/moderate/severe, all activities of that plan level and domain section (from `activities.json`), weakest domain first, none for strengths. The domain plan levels come from the latest finished assessment per task (`latestResults`), combined per domain across tasks as the design/epic context states (if the docs define no cross-task combination, use one plan section per task-domain and say so in Implementation Notes). `PlanScreen` in the Plan tab lists domain sections with activity rows; a row expands to show tools, how to perform, goal and criterion verbatim (Arabic text from the catalogue) plus a "camera-counted" badge when the activity has a `counter`. Checkbox selection across sections; "Start training" passes the selected activity IDs to a training hand-off (an `AppGateViewModel.openTraining(childId, activityIds)` entry that for now opens a placeholder screen until Story 4.6).

## Boundaries & Constraints

**Always:** Activities and texts verbatim from the catalogue (no invented content). Order: weakest plan level first (severe > moderate > mild), ties by domain order in the catalogue. Both plan levels shown at once when domains differ. Empty state when no finished assessment; "all strengths" state when no weak domain. Selection survives rotation (SavedStateHandle). Start disabled with no selection. RulesEngine coverage ≥ 90 %. Strings ar/en, RTL, DESIGN tokens.

**Never:** No training runner (4.6), no counting (4.5). No catalogue edits (Story 4.4 is changing `activity_counters.json` in parallel; read `counter` only if present).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Mixed | mild + severe + strength | severe section first, then mild; none for strength | N/A |
| Expand | row tapped | tools, how, goal, criterion verbatim | N/A |
| Start | 3 selected | hand-off receives the 3 IDs | N/A |
| No selection | none | Start disabled | N/A |
| No assessment | none | empty state | N/A |
| All strengths | no weak domain | calm "no plan needed" state | N/A |

</frozen-after-approval>

## Code Map

- `android/.../scoring/RulesEngine.kt`, `data/Results.kt`, `catalog/CatalogModels.kt` (activities model).
- `android/.../ui/therapist/ChildProfileScreen.kt` (Plan tab), new `PlanScreen.kt`, `PlanViewModel.kt`, `PlanUiState.kt`.
- `android/.../ui/AppGateViewModel.kt`, `AppNavigation.kt` -- training hand-off placeholder.

## Tasks & Acceptance

**Execution:**
- [x] activitiesFor + tests.
- [x] PlanViewModel/UiState + tests (ordering, selection, states).
- [x] PlanScreen + hand-off placeholder; strings.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

- Coordinator decision (review), for owner review: the docs define no cross-task combination, so the plan has one section per domain across all tasks, at the weakest plan level that domain reached in the latest finished assessment per task. The header lists each source task with its own average ("—" when that task has no scored step for the domain). No activity list is duplicated.
- Tie order: first appearance of the domain within the same plan code in `activities.json` (doc order per level).
- Draft selection is persisted per child in the existing DataStore (`PlanDraftStore`, key `plan_drafts`, activity IDs only), so it survives the PIN lock and process death; cleared when training starts.
- A reload failure keeps the old content and shows an inline error with retry. The hand-off uses the PlanRoute childId; a refused hand-off (gate returns false) shows a short message.
- English detail text uses TextDirection.Content with left alignment; badges and source lines are wrapped with BidiFormatter.
- Deferred: returning to the same profile tab after the PIN lock (already in deferred-work.md, Epic 4 notes).
- Scale tag -> activity domain code mapping (`rom|coord|hand|midline|planning`) is in `RulesEngine.activityDomain`, copied from `build_catalog.py` `DOMAIN_NAMES`; Visual-motor folds into Motor coordination.
- Row shows `textAr` plus the catalogue `title`; expanded shows tools/how/goal/criterion verbatim (these are English in the current catalogue; there are no Arabic copies of them).
- Hand-off: `AppGateViewModel.openTraining(childId, activityIds)` pushes `TrainingKey`, rendered by `TrainingPlaceholderScreen` until Story 4.6.

## Spec Change Log

## Review Triage Log

Plan-level/section mapping verified. 8 findings patched: tie order per plan level, one section per domain across tasks at the weakest level (coordinator decision, owner review), draft selection persisted per child, hand-off child from PlanRoute with refusal message, bidi for English detail text, reload error keeps content, missing average shows a dash. Deferred: return to the same profile tab after PIN.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
