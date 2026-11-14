---
title: 'Story 5.8 (part B): Kotlin Metrics and accuracy dashboard'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '07691d0e85dba69f8fe38a89acc1b98907153033'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
  - '{project-root}/tools/eval/compute_metrics.py'
  - '{project-root}/tools/eval/fixtures/README.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Goal 8 numbers exist only in the Python script; the therapist/researcher cannot see accuracy in the app, and nothing proves the app and script compute the same numbers.

**Approach:** Pure `scoring/Metrics.kt` computing exactly what `compute_metrics.py` computes from the same rows (per-attempt agreement app vs blind with cannot-tell excluded and counted; quadratic-weighted kappa on levels with blind level = Rule 1 on fully-rated steps only; cannot-tell rate; per-step confusion; corrected count; Goal 8 flags ≥80 % / ≥0.6 / ≤15 %). A `MetricsTest` loads `tools/eval/fixtures/metrics_fixture.csv` and `metrics_fixture.expected.json` (path via the existing Gradle `evalFixturesDir` system property) and asserts identical numbers, and also runs on `results_export_sample.csv`. `MetricsScreen` (Settings → Evaluation → "Accuracy") computes from the same data the export uses (reuse CsvExporter's read, no file) and shows summary cards (agreement, kappa, cannot-tell rate with target and met/below word), excluded counts, and a per-step confusion table; titled "Volunteer accuracy" when the data are replay/eval-child sessions, otherwise "Accuracy (live sessions)", with a filter chip to switch between them.

## Boundaries & Constraints

**Always:** Same Rule 1 as RulesEngine (call it, don't copy). Doubt never counted as disagreement. Read-only. Western digits, RTL, plurals, TalkBack descriptions for tables; colour never alone. Kover: add Metrics to koverVerifyCore (≥ 90 %).

**Never:** No schema change; no edits to ui/child.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Fixture | metrics_fixture.csv | equals expected.json (agreement 0.8, kappa 7/12, CT 0.2) | N/A |
| Export sample | results_export_sample.csv | same as python on it | N/A |
| No blind ratings | | agreement/kappa "n/a" with hint to run blind review | N/A |
| Single category | kappa undefined | "n/a" | N/A |
| Filter | volunteer vs live | numbers recomputed | N/A |

</frozen-after-approval>

## Code Map

- `android/.../scoring/RulesEngine.kt`; new `scoring/Metrics.kt`; `data/CsvExporter.kt` (read model); new `ui/therapist/MetricsScreen.kt` (+ VM); `SettingsScreen.kt` (Evaluation group); `android/app/build.gradle.kts` kover filter.
- `tools/eval/compute_metrics.py`, `tools/eval/fixtures/*`.

## Tasks & Acceptance

**Execution:**
- [x] Metrics + MetricsTest against both fixtures.
- [x] MetricsScreen + filter + Settings entry; strings.

**Acceptance Criteria:**
- Given the coordinator's gate, when run, then it passes and MetricsTest proves parity with the Python script.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Parity with compute_metrics.py confirmed on both fixtures. 6 findings patched (filter recompute, n/a kappa on single category, excluded counts, TalkBack table labels, Western digits, kover filter).

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` (coordinator)
