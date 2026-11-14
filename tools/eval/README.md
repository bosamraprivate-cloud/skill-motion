# tools/eval

Evaluation tools for the camera-scored tasks.

- `generate_clips.py`, `validate_clips.py`, `clip_manifest.py`: the volunteer clip manifest `eval/clips.csv`
  (protocol: `eval/recording-protocol.md`).
- `accuracy_report.py`: turns the Replay screen's "Export results" CSV into `docs/designs/m3-accuracy.md`.

## Tuning procedure

1. Split the clips by volunteer slot into a tuning subset and a hold-out subset before looking at any result.
   Tune only on the tuning subset; the hold-out subset is scored only for the final measurement.
2. Import the tuning clips on the Replay screen, tap "Export results" and run
   `tools/.venv/Scripts/python.exe tools/eval/accuracy_report.py <exported.csv>`. Look at the flagged steps.
3. Change only detector settings in `tools/catalog-builder/detectors.json` (thresholds, hold times, zones,
   landmarkers). Never change a question's text or a step's meaning; those come only from the design's
   camera-scored step table.
4. Log every change as one row of the "Threshold change log" in `docs/designs/m3-accuracy.md`: date, step,
   field, old -> new, reason, clips used.
5. Rebuild the catalogue (`python tools/catalog-builder/build_catalog.py`), which gives a new `catalogVersion`,
   install the app, and Re-score so every clip is scored under the new version.
6. Repeat 2 to 5 until the tuning subset meets the targets or no change helps. Then replay all clips, including
   the hold-out subset, export, run the report, and record the result.
7. Freeze: write the final `catalogVersion` in the "Frozen catalogVersion" line of `docs/designs/m3-accuracy.md`.
   Later detector changes need a new measurement.
