---
title: 'Story 5.8 (part A): Accuracy metrics script'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '579b1ffe9806bfe0d79139a8319bcf3274d7534c'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
  - '{project-root}/docs/designs/daily-living-skills-app.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Goal 8 needs agreement (app vs blind therapist), quadratic-weighted kappa on levels, cannot-tell rate and per-step confusion matrices from the exported `results.csv`; no script computes them.

**Approach:** `tools/eval/compute_metrics.py results.csv [--clips eval/clips.csv] [--out docs/designs/evaluation-metrics.md]` (location per epic-5 coordinator decision) reading the exact `results.csv` columns of Story 5.6 (`sessionId, childCode, taskId, stepId, attemptId, attemptNumber, promptLevel, verdict, reason, verifier, modelId, latencyMs, level, correctedLevel, blindVerdict, clipFile`). Outputs: per-attempt agreement app verdict vs blind verdict (cannot-tell attempts on either side reported separately, not as disagreement); quadratic-weighted kappa on step levels (app level vs blind level, where blind level = Rule 1 applied to the blind verdicts of a step only when every attempt of that step is rated — partially rated steps excluded and counted; not scored excluded); cannot-tell rate; per-step confusion matrix; secondary table vs scripted outcome when joined to `eval/clips.csv` by `clipFile`; Goal 8 thresholds flagged (agreement ≥ 80 %, kappa ≥ 0.6, cannot-tell ≤ 15 %). Also a JSON fixture `tools/eval/fixtures/metrics_fixture.csv` + expected numbers, hand-computed and documented, so the Kotlin `Metrics` (part B, later) can be tested against the same values.

## Boundaries & Constraints

**Always:** Pure Python 3.14 standard library (no numpy needed), deterministic, ruff clean, pytest. Rule 1 re-implemented exactly as `RulesEngine.stepLevel` (read the Kotlin and mirror it, including the cannot-tell repeat rule) with tests that mirror the Kotlin cases. Never treat cannot-tell as not done. Labels say "volunteer accuracy" when data comes from replay/eval child.

**Never:** No Android changes (other agents work there). No invented data.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Fixture | hand-computed CSV | exact agreement, kappa, rate | N/A |
| Cannot tell | app or blind CT | excluded from agreement denominator, counted | N/A |
| Partial blind | some attempts unrated | step excluded from kappa, counted | N/A |
| Corrected level | correctedLevel set | app level uses it? — NO: app accuracy uses automatic `level`; corrected shown separately | N/A |
| Empty / bad header | | clear error, exit non-zero | N/A |
| Clips join | clipFile matches | scripted-outcome table | unmatched listed |

</frozen-after-approval>

## Code Map

- `android/.../scoring/RulesEngine.kt` (`stepLevel`) — mirror only.
- `tools/eval/accuracy_report.py` (existing helpers, style), new `tools/eval/compute_metrics.py`, `tools/eval/test_compute_metrics.py`, `tools/eval/fixtures/`.
- `docs/designs/m3-accuracy.md` / new `docs/designs/evaluation-metrics.md` template; `docs/README.md` link.

## Tasks & Acceptance

**Execution:**
- [x] compute_metrics.py + Rule 1 mirror + kappa.
- [x] Fixture with hand-computed expected values (documented derivation).
- [x] Tests; docs template + link.

**Acceptance Criteria:**
- Given `pytest tools/` and `ruff check tools eval`, when run, then both pass.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Fixture numbers and Rule 1 mirror verified by hand. 6 input-robustness findings patched (level values, row-numbered errors, attempt order check, clips header and explicit path, not_scored correction token, LF output). Kotlin Metrics + dashboard (part B) still to do.

## Verification

**Commands:**
- `tools/.venv/Scripts/python.exe -m pytest tools/ -q`; `tools/.venv/Scripts/python.exe -m ruff check tools eval` -- expected: pass
