# M3 accuracy

Measured accuracy of the four camera-scored tasks on volunteer clips scored in replay mode (Story 3.9), against
design Goal 8: agreement with the scripted outcome at least 80 %, "cannot tell" at most 15 %. Agreement compares
scripted `done` with verdict done and scripted `not_done` with verdict not done; cannot tell and not scored are
reported separately and never count as disagreement.

How to produce it: score the clips on the Replay screen, tap "Export results", then run
`tools/.venv/Scripts/python.exe tools/eval/accuracy_report.py <exported.csv>` from the repo root. The script
replaces only the section between the report markers. The tuning procedure is in `tools/eval/README.md`.

## Results

<!-- report:start -->
**Status:** pending clips. The volunteer clips (`eval/recording-protocol.md`, `eval/clips.csv`) are not recorded
yet, so there are no measured numbers.
<!-- report:end -->

## Threshold change log

No threshold has been changed: no clips are recorded yet, so nothing has been tuned.

| Date | Step | Field | Old -> new | Reason | Clips used |
|---|---|---|---|---|---|

## Frozen catalogVersion

Frozen catalogVersion: none yet (set after tuning and the final replay of all clips).
