---
title: 'Story 2.17: M2 content completeness check'
type: 'feature'
created: '2026-11-13'
status: 'done'
baseline_commit: 'a1f0d757ba048f13565e392f4bcb91809a43dc3d'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** SM-4 needs proof that every one of the 147 steps has working text, audio and visuals on the device, plus a manual run of all 15 tasks; today media are produced in stages (audio and most clips come at the end of the project), so the check must work now and become strict later.

**Approach:** An instrumented `ContentCompletenessTest` loads the shipped catalogue on the device and, for every step and both character variants, opens every referenced asset (still, clip, normal and slow audio) and decodes it, reporting every failure at once. A second test asserts the catalogue is `complete` with no null media/audio; it is skipped (JUnit assumption, with a clear message listing counts of missing items) while `version.json` says `complete: false`, so it turns into the M2 gate automatically once the media are finished. A manual checklist `docs/designs/m2-content-check.md` lists all 15 tasks per variant with pass/notes columns.

## Boundaries & Constraints

**Always:** Iterate steps from `CatalogRepository` (no hard-coded IDs). Every non-null asset path must exist and decode (WebP via `ImageDecoder`, mp3 via `MediaExtractor`/`MediaMetadataRetriever` duration > 0). Failures are collected and reported together (step id, variant, asset). The strict test skips only when `complete == false` and prints how many steps lack audio / visuals per variant. The checklist has one row per task × variant (30 rows) with columns: task, variant, all steps shown, audio heard, demonstration clip from attempt 2, end screen, pass, notes.

**Never:** No new content, no catalogue changes. No failing the normal device run just because media are still missing (that is what the skipped strict test is for).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Present assets | non-null paths | all open and decode | N/A |
| Broken asset | path listed but file missing or corrupt | test fails naming step, variant, asset | collected, reported together |
| Dev catalogue | complete=false | strict test skipped with counts | N/A |
| Complete catalogue | complete=true | strict test requires no null media/audio | fails listing gaps |

</frozen-after-approval>

## Code Map

- `android/app/src/main/java/com/aitherapist/dls/catalog/CatalogRepository.kt`, `CatalogModels.kt`, `data/AssetCatalogRepository.kt` -- load the real catalogue in an instrumented test (construct the asset-backed repository with the instrumentation context).
- `android/app/src/androidTest/java/com/aitherapist/dls/` -- existing instrumented tests (DatabaseTest etc.) for patterns.
- `android/app/src/main/assets/catalog/version.json` -- `complete` flag.
- `docs/designs/` -- add `m2-content-check.md`; link it from `docs/README.md`.

## Tasks & Acceptance

**Execution:**
- [x] `androidTest/.../catalog/ContentCompletenessTest.kt` -- present-assets test + strict test (assumption-skipped while incomplete).
- [x] `docs/designs/m2-content-check.md` -- 30-row checklist + how to run; link from `docs/README.md`.

**Acceptance Criteria:**
- Given the phone, when `./gradlew connectedDebugAndroidTest` runs, then the present-assets test passes and the strict test is reported as skipped with the missing counts.
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
- `cd android && ./gradlew connectedDebugAndroidTest` -- expected: pass (strict test skipped)

## Implementation Notes

Phone run 2026-11-13: connectedDebugAndroidTest 20 tests, 0 failures, 1 skipped (strict completeness gate, complete=false). Strict gate turns on when media are finished.
