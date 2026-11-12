---
title: 'Story 2.7: Catalogue validation, merge and version stamp'
type: 'feature'
created: '2026-11-13'
status: 'in-progress'
baseline_commit: 'a02899b5aae6dc73c1c2ba268032a9e6448c0c57'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The catalogue builder only parses and checks steps/activities; the app has no generated `assessment.json`/`activities.json`, and nothing stops a catalogue with missing text, audio or visuals from shipping.

**Approach:** Extend `build_catalog.py` to merge parsed steps/activities with `strings.ar-eg.json`, `detectors.json` (all steps default `none`), `activity_counters.json` and the character media index (`tools/art/step_clips.json` + exported assets), validate everything (JSON Schema for detector checks), write the three asset files and stamp `catalogVersion` = first 12 hex of SHA-256 over `assessment.json` + `activities.json` bytes.

## Boundaries & Constraints

**Always:** Output to `android/app/src/main/assets/catalog/{assessment,activities,version}.json`, deterministic (sorted keys, stable order, UTF-8, `\n`). Step entry: id, area, task, number, textEn, textAr, tags, subSkills, source, detector (`method`, `framing`, `check` or null), audio (`<id>.mp3`, `<id>.slow.mp3`), media per variant (`clip` file or null + `still` file). Activity entry: id, plan, domain, number, title/text, textAr, audio (optional), counter (from activity_counters or null). Strict mode (default) fails naming every ID that lacks Arabic text, normal or slow audio, or a still (and a clip or still-only mapping) for each variant `boy` and `girl`; missing activity audio never fails. `--allow-missing-media` (dev/pilot only) downgrades missing audio/visuals to warnings and marks `version.json` `"complete": false`. Detector checks validated against `schema/check.schema.json` (rename/extend to `criterion.schema.json` only if needed). `catalogVersion` changes on any change to a step, tag, detector setting or activity (test). Clinical content only from `docs/reference/` parsing; JSON inputs hold no clinical content.

**Never:** No hand-edited output files. No new parsing rules for `docs/reference/`. No Kotlin changes (Story 2.8 loads the JSON).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| All present | complete inputs | exit 0; 3 files; version 12 hex | N/A |
| Missing Arabic | one step id absent in strings | exit 1 naming the id | N/A |
| Missing audio | `<id>.slow.mp3` absent | strict: exit 1 naming id; dev flag: warning + complete=false | N/A |
| Missing visual | girl has no mapping for a step | strict: fail naming id+variant | dev flag: warning |
| Activity audio missing | no activity mp3 | no failure | N/A |
| Bad detector | check fails schema | exit 1 naming id | N/A |
| Unknown detector id | detectors.json lists a non-existent step | exit 1 | N/A |
| Version sensitivity | change one tag/detector/activity | different catalogVersion | N/A |
| Determinism | build twice | byte-identical outputs | N/A |

</frozen-after-approval>

## Code Map

- `tools/catalog-builder/build_catalog.py` -- `parse_steps()`, `parse_activities()`, `validate*()`, `main(--check)`; extend `main` (remove the "only --check" guard).
- `tools/catalog-builder/schema/check.schema.json` -- existing check schema; reuse.
- `tools/catalog-builder/strings.ar-eg.json` -- steps/activities/ui/notes (Story 2.5).
- `tools/art/step_clips.json` + `android/app/src/main/assets/character/index.json` -- step→clip/still mapping and exported files (Story 2.3); only boy eating steps exist today.
- `android/app/src/main/assets/audio/ar-eg/` -- audio (being generated).
- `tools/catalog-builder/test_build_catalog.py`, `conftest.py` -- extend with fixtures in tmp dirs (no dependency on real assets).

## Tasks & Acceptance

**Execution:**
- [x] `tools/catalog-builder/detectors.json` -- every step id → `{"method":"none","framing":null,"check":null}` (structure only).
- [x] `tools/catalog-builder/activity_counters.json` -- `[]`.
- [x] `build_catalog.py` -- merge, validate, strict/dev modes, write outputs, version stamp; paths overridable for tests.
- [x] `test_build_catalog.py` -- every matrix row with tmp fixtures.
- [x] Run the builder with `--allow-missing-media` once and commit the generated assets (complete=false) so Story 2.8 has real input.

**Acceptance Criteria:**
- Given `pytest tools/` and `ruff check tools`, when run, then they pass.
- Given the real repo inputs, when built with `--allow-missing-media`, then outputs exist and the warning list names the missing media.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

`--allow-missing-media` exists because media are produced in stages (eating pilot first, girl later, audio in progress); Story 2.17 runs the strict build as the M2 gate.

## Verification

**Commands:**
- `python -m pytest tools/ -q` -- expected: pass
- `python -m ruff check tools` -- expected: clean
- `tools/.venv/Scripts/python.exe tools/catalog-builder/build_catalog.py --allow-missing-media` -- expected: exit 0, warnings
