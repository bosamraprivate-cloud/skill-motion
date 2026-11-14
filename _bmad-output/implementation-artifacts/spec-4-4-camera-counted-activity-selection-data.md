---
title: 'Story 4.4: Camera-counted activity selection data'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '595602d68e2dcb16ccc687d42aab139b74876ce5'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
  - '{project-root}/docs/designs/daily-living-skills-app.md'
  - '{project-root}/docs/reference/treatment-plans.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `activity_counters.json` is empty, so no training activity can be counted by the camera; every activity is guided.

**Approach:** Review all 148 activity criteria from `docs/reference/treatment-plans.md` against the design's selection rule ("Camera-counted activity selection": a count of repetitions or a hold time of ONE body movement describable by ONE `angleAbove`/`angleBelow`/`pointAbove`/`distanceBelow` check). Write `docs/designs/activity-counter-selection.md` with a decision for every activity (included / excluded + one-line reason), quoting the source criterion verbatim for each included one. Fill `activity_counters.json` with the included ones: `activityId`, `check` (same `Check` format as detectors), `kind` (`reps`|`hold`), `target`, `perSide`, `low`, `high`. Add `criterion.schema.json` and make the builder validate entries (schema + existing activity ID + check type in the allowed four), emitting `counter` into `activities.json`. Rebuild the catalogue.

## Boundaries & Constraints

**Always:** `target`, `kind`, `perSide` come verbatim from the source criterion (number of reps / seconds, per side when the text says so). `low`/`high` are the counter's movement thresholds (check-level detection parameters, like detector starting values) — documented per activity as starting values to tune in replay, with the body landmarks used. Never change activity text or meaning; when a criterion is ambiguous (e.g. not clearly one movement, or count unclear) → exclude and say why. Python 3.14, pytest, ruff clean; strict build still works with `--allow-missing-media`. `catalogVersion` changes.

**Never:** No Android changes (another story is in progress there). No invented criteria or targets.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Qualifying criterion | "raise arms overhead, 10 repetitions" | included, kind reps, target 10 | N/A |
| Hold criterion | "hold 5 seconds" | kind hold, target 5 | N/A |
| Two movements / object use | composite | excluded with reason | N/A |
| Bad entry | unknown activity ID / wrong check type / missing field | build fails naming it | N/A |
| Per side | "each side" | perSide true | N/A |

</frozen-after-approval>

## Code Map

- `docs/reference/treatment-plans.md` -- 148 activities and criteria.
- `tools/catalog-builder/build_catalog.py`, `activity_counters.json`, `detectors.json` (Check format), `test_build_catalog.py`; new `criterion.schema.json`.
- `android/app/src/main/assets/catalog/activities.json` (generated; rebuild only).
- New `docs/designs/activity-counter-selection.md`; link from `docs/README.md`.

## Tasks & Acceptance

**Execution:**
- [x] Selection doc for all 148 with quotes.
- [x] activity_counters.json + criterion.schema.json.
- [x] Builder validation + tests; rebuild catalogue.

**Acceptance Criteria:**
- Given `pytest tools/` and `ruff check tools`, when run, then both pass and the builder validates every entry.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Quotes verified verbatim. Patched: depth-axis knee activities excluded (mild.rom.05, moderate.rom.07, moderate.rom.06), severe.rom.10 excluded (assistance rule), elbow entries flagged for replay tuning, perSide requires side either, range and start-threshold validation, design direction wording aligned, more tests. Final: 5 counted, 143 guided.

## Verification

**Commands:**
- `tools/.venv/Scripts/python.exe -m pytest tools/ -q`; `tools/.venv/Scripts/python.exe -m ruff check tools` -- expected: pass
- `tools/.venv/Scripts/python.exe tools/catalog-builder/build_catalog.py --allow-missing-media` -- expected: OK
