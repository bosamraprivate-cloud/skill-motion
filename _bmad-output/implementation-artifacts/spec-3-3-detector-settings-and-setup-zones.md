---
title: 'Story 3.3: Detector settings for the 41 steps and setup zones'
type: 'feature'
created: '2026-11-13'
status: 'done'
baseline_commit: '073e0a70639aff66020fdc5178f1458758bc84c3'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/docs/designs/daily-living-skills-app.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `detectors.json` marks every step `method: none`, so no camera-scored step is judged; `inZone` checks need mat/sink/place zones that the therapist cannot draw yet.

**Approach:** Fill `detectors.json` for the 41 steps of the four camera-scored tasks verbatim from the design's "Camera-scored steps" table (method, framing, check or question), lock the counts with builder tests, rebuild the catalogue, and add a `ZoneEditor` (drag rectangles over a camera preview) reachable from Setup (optional step) and Settings, storing zones normalised and un-mirrored via `SecureSettings.setZones`.

## Boundaries & Constraints

**Always:** Values, questions, landmark names and thresholds copied exactly from the design table (lines ~385-431); if a step in the table lacks a value or uses a landmark/zone name the models cannot express, stop and report — never invent. Steps outside the four tasks stay `none`. Builder test asserts 41 steps = 5 none, 6 local (pose/hand), 30 cloud, and each cloud question equals the table text. Rebuild assets with `--allow-missing-media`; `catalogVersion` changes. Zone names are exactly those referenced by `inZone` checks in the table (e.g. mat, sink, place). ZoneEditor: front camera preview shown mirrored for the therapist, but stored coordinates un-mirrored (x' = 1 − x) and normalised 0..1, left<right, top<bottom; zones optional to skip; existing zones shown and editable; strings in values and values-ar; therapist design tokens. Camera permission handled with the existing pattern (Diagnostics uses CameraX).

**Never:** No new check types, no Kotlin thresholds, no runner wiring (3.5), no framing check screen (3.4).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Build | detectors filled | 41 steps: 5 none / 6 local / 30 cloud | test fails on mismatch |
| Unknown landmark/zone | table value not in model | stop and report | N/A |
| Draw zone | drag on mirrored preview | stored un-mirrored, normalised | tiny/zero rect rejected with message |
| Edit zone | existing zones | shown, re-drawable, clearable | N/A |
| No camera permission | denied | explanation + retry, can skip | N/A |
| Setup skip | therapist skips zones | setup completes | N/A |

</frozen-after-approval>

## Code Map

- `tools/catalog-builder/detectors.json`, `build_catalog.py`, `test_build_catalog.py` -- fill + count test; rebuild `android/app/src/main/assets/catalog/*`.
- `android/.../catalog/CatalogModels.kt` -- Check/Detector shapes (must decode the new data).
- `android/.../data/SecureSettings.kt` -- `setZones`/`zones()`.
- `android/.../ui/therapist/SetupScreen.kt`, `SetupViewModel.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt` -- entry points; new `ZoneEditor.kt` (+ ViewModel).
- `android/.../ui/therapist/DiagnosticsScreen.kt` -- CameraX preview/permission pattern.

## Tasks & Acceptance

**Execution:**
- [x] detectors.json from the design table; builder count/verbatim tests; rebuilt catalogue.
- [x] ZoneEditor + ViewModel (mirror math unit-tested), Setup optional step, Settings entry.
- [x] strings ar/en; tests.

**Acceptance Criteria:**
- Given `pytest tools/`, `ruff check tools` and `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug`, when run, then all pass.
- Given the phone, when the therapist draws mat/sink/place in Settings, then reopening shows them in place.

## Implementation Notes

- Coordinator decision: framing is per task — drink.cup (eating/drinking at table) `table`, hygiene.handwash `sink`, dress.jacket.* `standing`, for all 41 steps including `none` steps. The design table has no framing column.
- Coordinator decision: `Method.HAND` runs landmarkers [POSE, HAND] because hand checks scale by pose shoulder width.
- Coordinator decision: zone names stay mat, sink, place (`place` is not referenced by any `inZone` check).

## Spec Change Log

## Review Triage Log

7 findings (editor back button, compact layout, per-frame bitmaps, camera error hidden, permission re-prompt on rotation, drag start slop, save failure UX): all patched. Mirror math, coordinate mapping, setup completion and the 6 local checks verified correct.

## Verification

**Commands:**
- `tools/.venv/Scripts/python.exe -m pytest tools/ -q`; `python -m ruff check tools` -- expected: pass
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
