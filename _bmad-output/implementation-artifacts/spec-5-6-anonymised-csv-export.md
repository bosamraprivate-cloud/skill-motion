---
title: 'Story 5.6: Anonymised CSV export'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '89005840d6be0a870d1c9c2089df8523e683f776'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
  - '{project-root}/tools/eval/compute_metrics.py'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The evaluation script needs `results.csv`; the app cannot export results.

**Approach:** `data/CsvExporter.kt` (pure formatter + repository read) and Settings → Evaluation → "Export results": writes `results.csv` through the system file picker (SAF CreateDocument, default name `results.csv`) with exactly these columns in order: `sessionId, childCode, taskId, stepId, attemptId, attemptNumber, promptLevel, verdict, reason, verifier, modelId, latencyMs, level, correctedLevel, blindVerdict, clipFile`. One row per attempt, rows ordered by session startedAt then step order then attemptNumber (the order `compute_metrics.py` checks). `level` = automatic Rule 1 level of that step in that session (`1`/`2`/`3`/`not_scored`, empty when open) repeated on each row of the step; `correctedLevel` = `1`/`2`/`3`/`not_scored` or empty; `blindVerdict` = `done`/`not_done`/`cannot_tell` or empty; verdict/verifier lowercase tokens as in the DB. Sessions: assessment + replay (eval child included, so replay clips can be measured); never names; `childCode` only. Reason = code only (never `modelReason` free text).

## Boundaries & Constraints

**Always:** Levels computed by RulesEngine, never stored. RFC 4180 quoting, UTF-8 without BOM, LF. A unit test asserts the header exactly, and a test that no child name appears in the file (fixture child "Youssef"). Round-trip test: the exported fixture passes `compute_metrics.py` column validation (keep a sample export under `tools/eval/fixtures/` generated from the Kotlin test, and a pytest that parses it). Export is read-only on data. Strings ar/en.

**Never:** No frames, no model free text, no names. No edits to ui/child.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Export | sessions with attempts, corrections, blind ratings | file with exact header and rows | N/A |
| Name search | child named Youssef | name absent | N/A |
| Comma in reason | | quoted | N/A |
| Open step | paused session | level empty | N/A |
| Not scored correction | null level | `not_scored` | N/A |
| Cancel picker | | nothing written | N/A |
| Write failure | IO error | calm message | N/A |

</frozen-after-approval>

## Code Map

- `android/.../data/Results.kt`, `Daos.kt`, `BlindReview.kt`, `Repositories.kt`; `scoring/RulesEngine.kt` (read only).
- New `android/.../data/CsvExporter.kt`; `ui/therapist/SettingsScreen.kt` (Evaluation group).
- `tools/eval/compute_metrics.py` (column contract), `tools/eval/fixtures/`.

## Tasks & Acceptance

**Execution:**
- [x] CsvExporter formatter + query + tests (header, no names, quoting, levels).
- [x] Settings entry with SAF; strings.
- [x] Sample export fixture + pytest round trip.

**Acceptance Criteria:**
- Given the coordinator's gate and `pytest tools/`, when run, then both pass.

## Implementation Notes

- Correction-only rows: a step with a correction but no attempts gets one row with sessionId, childCode, taskId, stepId and correctedLevel set and every other column empty (attemptId, attemptNumber, promptLevel, verdict, reason, verifier, modelId, latencyMs, level, blindVerdict, clipFile). `compute_metrics.py` counts it for corrections and skips it for agreement and kappa.
- Attempt rows are written in saved order (the order Rule 1 reads for `level`); attemptNumber never decreases within a step.
- Formula guard: any cell starting with `=`, `+`, `-` or `@` gets a leading `'`; `compute_metrics.py` strips it on read.
- A missing childCode, a catalogue that fails to load, or any write error (OOM included) is FAILED, and the created document is deleted (`DocumentsContract.deleteDocument`). DONE/FAILED resets to IDLE when the therapist leaves the screen.
- Sample export `tools/eval/fixtures/results_export_sample.csv` is checked by `CsvExporterTest` against the export; regenerate with `-DupdateFixtures=true` (path from the Gradle system property `evalFixturesDir`).

## Spec Change Log

## Review Triage Log

Privacy verified (childCode generated, never from the name). 6 findings patched: fixture compared not rewritten (opt-in regenerate), correction-only rows, saved-order rows, streaming + single read transaction + Throwable → FAILED, partial file deleted on failure, spreadsheet formula guard (stripped by compute_metrics).

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` (coordinator); `tools/.venv/Scripts/python.exe -m pytest tools/ -q`
