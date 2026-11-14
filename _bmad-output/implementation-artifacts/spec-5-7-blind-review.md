---
title: 'Story 5.7: Blind review'
type: 'feature'
created: '2026-11-14'
status: 'draft'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Goal 8 needs a therapist rating of each attempt made without seeing the app's verdict; there is no way to record it.

**Approach:** `blind_rating` table (`id, attemptId, verdict done|not_done|cannot_tell, createdAt`; unique `attemptId`; FK attempt CASCADE; Room migration 6→7 + schema + instrumented test). `BlindReviewScreen` (Settings → Advanced/Evaluation, available in release for the evaluating therapist, behind the PIN): one unrated attempt at a time from any session with frames (assessment and replay, eval child included — coordinator decision), showing only the step sentence (Arabic) and the 3 decrypted frames / the replay clip — never verdict, reason, level, verifier, child name or code; three big buttons done / not done / cannot tell, plus skip; progress "rated N of M"; order shuffled deterministically per review run so consecutive attempts of the same step are not adjacent. Saving writes one row (upsert on attemptId). Ratings never change levels, report or plan (unit test proves results identical with/without blind rows).

## Boundaries & Constraints

**Always:** Only corrections change levels (AD-9). No verdict leak anywhere on the screen (including TalkBack labels and logs). Frames decrypted in memory only. Western digits, RTL, strings ar/en. Konsist/unit: scoring does not read blind_rating.

**Never:** No export (5.6) or metrics UI (5.8B) here. No edits to Progress tab / Diagnostics files.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Open | unrated attempts exist | first attempt frames only | N/A |
| Rate | tap done | row saved, next attempt | N/A |
| Re-rate | attempt rated before | upsert | N/A |
| Skip | | moves on, stays unrated | N/A |
| All rated | | calm "all rated" state | N/A |
| Missing frames | | placeholder; can still rate cannot tell or skip | N/A |
| Results | ratings exist | report/plan/levels unchanged (test) | N/A |
| Migration 6→7 | v6 data | kept; table exists | N/A |

</frozen-after-approval>

## Code Map

- `android/.../data/Entities.kt`, `Daos.kt`, `AppDatabase.kt`, `Results.kt`/`Repositories.kt`, `FrameStore.kt`, `android/app/schemas/`.
- New `android/.../ui/therapist/BlindReviewScreen.kt` (+ ViewModel/UiState), `SettingsScreen.kt` entry; reuse `EvidenceStrip` decode.
- Tests: unit (VM, no-leak, results unchanged), androidTest `MigrationTest` 6→7.

## Tasks & Acceptance

**Execution:**
- [ ] blind_rating + migration 6→7 + tests.
- [ ] BlindReview VM/screen (no leak) + Settings entry + strings.
- [ ] Results-unchanged test; architecture rule.

**Acceptance Criteria:**
- Given the coordinator's gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL (run by the coordinator)
