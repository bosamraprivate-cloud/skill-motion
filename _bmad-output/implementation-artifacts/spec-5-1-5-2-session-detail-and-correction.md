---
title: 'Stories 5.1 + 5.2: Session detail with evidence, and correcting a step level'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '579b1ffe9806bfe0d79139a8319bcf3274d7534c'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The therapist sees only a session summary; they cannot check each step's attempts and evidence, and cannot correct a level although corrections are already part of Rule 2.

**Approach:** (5.1) The Sessions tab lists every session with task, date, kind (assessment / training) and state; tapping an assessment opens `SessionDetailScreen`: one row per catalogue step with its `LevelChip` (automatic level, or corrected), expandable to the attempts — number, prompt level, Help, verdict, reason code (+ model reason text when present), method/verifier, latency — and an `EvidenceStrip` of the 3 decrypted frame thumbnails (FrameStore) that enlarge on tap; missing/undecryptable frames show a calm placeholder. (5.2) A "Correct" action opens `CorrectSheet` (3 / 2 / 1 / not scored, showing the automatic level); saving upserts the correction (unique `(sessionId, stepId)` via Room migration 5→6 that dedupes keeping the latest row, then a unique index; explicit Migration + schema + instrumented test); the chip shows the automatic level struck and the corrected level in the corrected colour with "reviewed"; "Remove correction" deletes it and restores the automatic level; "not scored" stores null. Report, plan and summary update with no extra action (they already recompute from facts).

## Boundaries & Constraints

**Always:** Levels are never stored except as corrections (AD-5). Frames decrypted in memory only, never written decrypted, never logged; thumbnails downscaled. No child name in logs. Corrected colour from DESIGN tokens (add a token if missing), always with the word "reviewed" (never colour alone). Read path never throws on bad data (Story 4.1 rule). Western digits in therapist data, RTL, strings ar/en.

**Never:** No blind review (5.7), no export (5.6), no compare (5.3). No changes to Rule 1/Rule 2 logic.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Sessions list | assessments + training | all with task, date, kind, state | N/A |
| Detail | done session | each step + attempts + 3 thumbnails | missing frame → placeholder |
| Enlarge | tap thumbnail | full-size viewer | decode error → placeholder |
| Correct 2→3 | save | chip: 2 struck, 3 corrected + reviewed; report/plan updated | N/A |
| Second correction | save again | replaces first (upsert) | N/A |
| Remove | delete | automatic level back | N/A |
| Not scored | save | level null | N/A |
| Migration 5→6 | duplicates exist | latest kept, unique index | N/A |
| Paused/abandoned session | open | detail read-only, correct disabled for open steps | N/A |

</frozen-after-approval>

## Code Map

- `android/.../ui/therapist/SessionsTab.kt`, `SessionsUiState.kt`, `SessionSummary.kt`; new `SessionDetailScreen.kt` (+ ViewModel/UiState), `EvidenceStrip.kt`, `LevelChip.kt`, `CorrectSheet.kt`.
- `android/.../data/Results.kt` (CorrectionStore), `Daos.kt`, `Entities.kt`, `AppDatabase.kt`, `FrameStore.kt`, `android/app/schemas/`.
- Tests: ViewModel tests, ResultsTest, androidTest `CorrectionTest` + `MigrationTest` (5→6).

## Tasks & Acceptance

**Execution:**
- [x] Sessions list kind/state; SessionDetail + EvidenceStrip + LevelChip.
- [x] Migration 5→6 (dedupe + unique) + upsert/delete in CorrectionStore + tests.
- [x] CorrectSheet + chip states; strings.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Migration 5→6 dedupe and unique index verified; frames decrypted in memory only. 7 findings patched: correctable steps in done sessions incl. never tried, separate 'not tried' label, no-op save disabled, explicit latest-correction comparator, thumbnail cache, always reload, stale Epic 5 text. CorrectionTest + 5→6 MigrationTest pending a device run.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
