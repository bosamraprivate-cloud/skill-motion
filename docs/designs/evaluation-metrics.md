# Evaluation metrics

Goal 8 volunteer accuracy (design Goal 8, Story 5.8/5.9): per-attempt agreement of the app with the blind therapist
rating at least 80 %, quadratic-weighted kappa on step levels at least 0.6, "cannot tell" at most 15 % of attempts.
Cannot tell on either side is counted separately and never as disagreement. The blind level is Rule 1 on the blind
verdicts of a step, only when every attempt of the step is rated; partially rated, open and not scored steps are
excluded from kappa and counted. App accuracy uses the automatic `level`; therapist corrections are shown apart.
The scripted outcome from `eval/clips.csv` (joined by `clipFile`) is a secondary table.

How to produce it: export `results.csv` (Settings -> Export), then run
`tools/.venv/Scripts/python.exe tools/eval/compute_metrics.py <results.csv>` from the repo root. The script
replaces only the section between the report markers. Hand-computed fixture: `tools/eval/fixtures/`.

## Results

<!-- report:start -->
**Status:** pending. No blind-rated volunteer clips yet, so there are no measured numbers.
<!-- report:end -->
