---
title: 'Story 3.8: Volunteer clip recording and manifest'
type: 'feature'
created: '2026-11-14'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/docs/designs/daily-living-skills-app.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Accuracy needs a scripted, labelled clip set for the 41 camera-scored steps, recorded the same way by every volunteer, with a manifest that a script can validate against the naming rule replay mode expects.

**Approach:** Write `eval/recording-protocol.md` (physical setup per framing from the design: table / standing / sink, tablet position, lighting, front camera un-mirrored or rear camera, clip length, what to perform for done-on-attempt-1, done-later, not-done, consent handling) and generate `eval/clips.csv` as the planned shot list (one row per step × scripted outcome × volunteer slot, file name following `<stepId>__<attemptNumber>__<outcome>__<volunteer>.mp4`). Add `tools/eval/validate_clips.py` (+ pytest) that checks every row has file, task, step, attempt, outcome, volunteer; every file name matches the rule and its row; step IDs exist in the built catalogue and are camera-scored (not `none`); optionally (`--clips-dir`) that every file exists and no extra files are present.

## Boundaries & Constraints

**Always:** Steps and their done/not-done meaning come only from the catalogue/design table (no invented content); step texts referenced by ID. Clip videos stay outside the repo (`.gitignore` an `eval/clips/` folder); only the manifest is committed. Consent: protocol says signed consent is kept in `eval/consent/` which is git-ignored, and records the path only. Recording guidance includes: front-camera video must be saved un-mirrored (or note the setting), because replay does not flip frames. Volunteers are adults acting the steps (no children in eval clips unless separate consent). `ruff` clean, Python 3.14.

**Never:** No real recordings created by the agent. No Android changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid manifest | generated clips.csv | validator passes | N/A |
| Bad name | row file not matching rule | validator fails naming the row | N/A |
| Mismatch | name says step X, row says Y | fails | N/A |
| Unknown / none step | step not camera-scored | fails | N/A |
| Missing file | `--clips-dir` and file absent | fails listing it | N/A |

</frozen-after-approval>

## Code Map

- `docs/designs/daily-living-skills-app.md` -- camera-scored table, framing, physical setup.
- `assets/catalog/assessment.json` / `android/app/src/main/assets/catalog/assessment.json` -- step IDs and detectors.
- `android/.../replay/ClipName.kt` -- the exact naming rule (outcome tokens `done`/`not_done`).
- New `eval/recording-protocol.md`, `eval/clips.csv`, `tools/eval/validate_clips.py`, `tools/eval/test_validate_clips.py`; `.gitignore`; link from `docs/README.md`.

## Tasks & Acceptance

**Execution:**
- [x] Protocol doc.
- [x] clips.csv shot list (generator script or committed output, deterministic).
- [x] Validator + tests; .gitignore; docs link.

**Acceptance Criteria:**
- Given `tools/.venv/Scripts/python.exe -m pytest tools/ -q` and `ruff check tools`, when run, then both pass and the validator passes on the committed clips.csv.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `tools/.venv/Scripts/python.exe -m pytest tools/ -q`; `tools/.venv/Scripts/python.exe -m ruff check tools` -- expected: pass
- `tools/.venv/Scripts/python.exe tools/eval/validate_clips.py eval/clips.csv` -- expected: OK
