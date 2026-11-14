---
title: 'Epic 4 UI/UX polish pass'
type: 'refactor'
created: '2026-11-14'
status: 'done'
baseline_commit: 'e93a4628975ce6017be5b05e92bf236041650c58'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
  - '{project-root}/_bmad-output/implementation-artifacts/deferred-work.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Epic 4 added the report, plan, training, reward and sticker screens built per story; they need one consistent, polished design, and the agreed therapist IA still lacks the session summary and a real Sessions tab. The owner wants the best design after every epic.

**Approach:** Polish every Epic 4 screen to DESIGN.md and complete the agreed IA pieces that need no new data rules, with no change to scoring, rewards or stored data.

## Boundaries & Constraints

**Always:**
1. Report tab: clear task sections (card per task, date of the assessment), strengths/needs as readable chip lists, domain table aligned with the chart, consistent plan-level colour + word + number, calm empty state with a "Start a session" action.
2. Plan tab: domain section cards with level badge, activity rows with expand affordance, camera-counted badge, sticky bottom bar "Start training (n)" disabled with no selection; long verbatim texts readable (LTR for English).
3. Sessions tab: list of the child's sessions (assessment, training runs) newest first with date, task name, state chip (done / paused / abandoned) and stars earned — read-only; tapping a done assessment opens the session summary.
4. Session summary (therapist): after a child run returns to therapist mode (PIN), show a summary sheet for that session: task, steps count by Rule 1 level (3/2/1/not scored), cannot-tell count, duration, stars — with "Open report" action; also reachable from the Sessions tab. Read-only, computed from existing data (no corrections UI — Epic 5).
5. Training screens: counter ring and timer visually centred and large, per-side layout balanced, guided activity card clear; end/reward flow consistent with assessment.
6. Task reward + sticker book: balanced layout, sticker grid spacing, path visuals, buttons consistent with child design.
7. Overview tab: child header + quick stats (last assessment date, tasks finished, stickers) + sticker book entry.
8. Consistency: page titles, spacing scale, card outlines, typography, Western digits in therapist data / Arabic-Indic in child mode, RTL everywhere, plurals.
Strings ar/en; no red/orange in child mode; targets per child rules.

**Never:** No change to RulesEngine, RewardRules, recorder, runner logic or schema. No new dependencies. No corrections or export (Epic 5).

</frozen-after-approval>

## Code Map

- `android/.../ui/therapist/ChildProfileScreen.kt`, `ReportScreen.kt`, `DomainChart.kt`, `PlanScreen.kt`, new `SessionsTab.kt`, `SessionSummary.kt` (+ ViewModel), `data/Repositories.kt` read queries only.
- `android/.../ui/child/TrainingScreen.kt`, `TaskRewardScreen.kt`, `StickerBookScreen.kt`, `StickerArt.kt`.
- `android/.../ui/AppGateViewModel.kt` (return-from-child hook for the summary).

## Tasks & Acceptance

**Execution:**
- [x] Report + Plan polish.
- [x] Sessions tab + session summary (+ tests for the summary computation and list).
- [x] Training, reward, sticker book polish.
- [x] Overview quick stats.
- [x] Update affected tests.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Logic review: 6 findings patched (post-PIN summary uses this visit's session ids, only done sessions, jacket shows both; cannot-tell per step; steps from catalogue with not-reached count; training run not finished state; summary only for assessment non-eval sessions; shared training result id helper). New Arabic strings need owner review.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
