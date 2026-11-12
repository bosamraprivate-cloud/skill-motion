# Goldfish Review Report - Stage 9 Implementation Readiness

**Target:** `docs/designs/daily-living-skills-app.md` (lines 322-495)

## Reviewer Status

| Reviewer | Status | Persona read | Standard read |
| --- | --- | --- | --- |
| `egm-implementation-readiness` | completed | yes (`agents/implementation_readiness.md`) | yes (`implementation-readiness-standard.md`) |
| `egm-codebase-details` | completed | yes (`agents/codebase_details.md`) | yes (`implementation-readiness-standard.md`) |
| `egm-style` | completed | yes (`agents/style.md`) | persona default (no separate file) |

Caveats: codebase-details did not read the earlier reports, and style read only `critic_report.md`, so a few of their findings may repeat earlier ones. Implementation-readiness read all the earlier reports and found nothing repeated.

**Count verification (two reviewers checked the counts independently against the reference files):** all counts match. There are 147 steps (eating 42, drinking 38, hygiene 27, dressing 40) and 41 camera-scored steps (jacket on 5, jacket off 10, cup 13, handwashing 13). There are 148 activities: mild 48 (8+10+10+10+10), moderate 50, severe 50.

**Totals: 2 Blocker, 12 Major, 15 Nit (29 findings).**

## Blockers

### B1. The per-step detector content and the `Check` schema are undefined
* **Reviewer:** implementation-readiness, codebase-details
* **Lines:** 356-357, 422 (also 167, 343)
* **Problem:** The plan never says which of the 41 camera-scored steps use `pose`, `hand`, `cloud` or `none`, and it does not give their framing, joints, thresholds, hold times or cloud question text. Only `angleAbove` has a schema, and `"joint": "rightShoulder"` cannot define an angle, which needs three landmarks. There is no mapping from landmark names to MediaPipe indices. No check type covers the rubbing and reciprocal hand movements in handwashing steps 7 and 8. The implementer would have to design the scoring itself.
* **Fix:** Add a table with one row per step ID (41 rows) giving method, framing, check type and parameters, or the exact cloud question. Give the full schema for each check type: `angleAbove`/`angleBelow {a,b,c,deg,holdSec}`, `distanceBelow {p,q,normBy,ratio,holdSec}`, `pointAbove {p,q,holdSec}`, `question {text}`, and a new `oscillation {p,q,axis,minCycles,minAmp}`. Add a table mapping the landmark-name enum to the Pose 33 and Hand 21 indices.

### B2. The Room entities cannot be coded, and blind ratings would corrupt the levels
* **Reviewer:** implementation-readiness, codebase-details
* **Lines:** 404-407 (also 143, 457, 467)
* **Problem:** Entities are listed without primary keys or foreign keys:
  * `Attempt` has no `sessionId`.
  * `Correction` has no `sessionId` or `stepId`, and no stored automatic level, although CUJ 4 keeps both.
  * `Child` and `TrainingResult` have no fields.
  * `Session` has no child, task, type, timestamps or state enum.
  * Nothing numbers the "cannot tell, repeat once" retry.
  * Line 143 has a `frames` table, but the entity uses `Attempt.frameRefs`.
  * Saving the blind review as `Correction(blind=true)` lets it overwrite `stepLevel`, because a correction wins (line 208). It also stores one level per step, while Goal 8 needs per-attempt agreement.
* **Fix:** Specify every entity with its column types, primary key, foreign keys, cascade behaviour and enum values. Add `Attempt.retryIndex` and `Correction(sessionId, stepId, autoLevel, level, ...)`. Add a separate `BlindRating(attemptId, verdict, stepLevel, reviewer, createdAt)` that `stepLevel` ignores. Choose either a `Frame` entity or `frameRefs`, and use the same choice on line 143. Define `TrainingResult(activityId, count, durationSec, criterionMet)`.

## Major

### M1. Activity parsing rule is ambiguous
* **Reviewer:** codebase-details, implementation-readiness (N6)
* **Lines:** 352, 364-367
* **Problem:** "Activities from numbered headings" also matches the numbered domain headings in the mild plan, for example `### 3. Hand skills` (treatment-plans.md lines 143, 219, 297). The plan-level match is also loose.
* **Fix:** Anchor the rules to heading depth and text:
  * Plan level is a `##` heading matching `^\d\. Treatment plan .*(mild|moderate|severe)`.
  * Domain is the most recent depth 3 or 4 heading that names a domain.
  * An activity is a heading at the activity depth matching `^\d+\. `.
  * A field is a bullet matching `^- \*\*(Tools|How to perform|Goal|Measurement criterion):\*\*`.

  Also assert 15 tasks and the per-task counts.

### M2. Activity mode and typed criteria have no input file
* **Reviewer:** both
* **Lines:** 168, 349-362, 368, 439-442
* **Problem:** Nothing says which of the 148 activities are camera-counted; this is deferred to M4. `RepCounter` gets no joint triple, high or low thresholds, or smoothing. The `hold` kind has no schema. Hand-based activities need Hand landmarks, but `RepCounter` tracks Pose angles only.
* **Fix:** Add `tools/catalog-builder/activity_modes.json` (Create): `{mode, criterion:{kind:"reps"|"hold", target, perSide, holdSec?}, tracker:{landmarker, joint:[a,b,c], high, low, minRepSec}}`. IDs not listed default to guided. Smoothing is an EMA with alpha = 0.3. Add a builder test that every listed ID exists.

### M3. File paths are not repository-relative, and files are missing from the list
* **Reviewer:** both
* **Lines:** 349-488, 372, 377
* **Problem:** Bare paths such as `catalog/CatalogModels.kt` have no source root. Several files the plan uses are not in the list:
  * `tools/pictograms/pictograms.csv`, the CREDITS file and `review.csv`.
  * The ViewModels and the Bluetooth key handler.
  * The `.gitignore` entry for the models.
  * `strings.xml` and `values-ar/strings.xml`.
  * `proxy/tsconfig.json`.
* **Fix:** Prefix the source files with `android/app/src/main/java/com/aitherapist/dls/` and the test files with `android/app/src/test/java/...`, and add each missing file with Create.

### M4. The verification commands cannot run from the dependency list
* **Reviewer:** both
* **Lines:** 382, 491-494
* **Problem:**
  * `ktlintCheck` needs the ktlint Gradle plugin, which is not listed.
  * The 90% coverage target needs Kover or JaCoCo, and no command is given.
  * navigation-compose, coroutines and the serialization plugin are not listed.
  * There is no Python requirements file.
  * The `build` and `test` scripts in `proxy/package.json` are not defined.
* **Fix:** Add the plugins and libraries to `libs.versions.toml`. Add `tools/requirements.txt` (pytest, ruff, requests, TTS). Define `build` (`wrangler deploy --dry-run`) and `test` (`vitest run`). Add `./gradlew koverVerify` with a 90% lines rule on `scoring.*`, `vision.Geometry` and `training.RepCounter`.

### M5. Milestones have no mapped items or exit criteria
* **Reviewer:** implementation-readiness
* **Lines:** 339-345 vs 347-488
* **Problem:** No item is assigned to a milestone. M1 needs a cloud verdict, which uses items 16 and 26, but the catalogue only arrives in M2.
* **Fix:** Map each item to a milestone. Give each milestone the commands and tests that must pass to exit it. State that M1 uses a stub step hard-coded in a test harness.

### M6. The app-to-proxy contract is unspecified
* **Reviewer:** implementation-readiness, codebase-details (N1), style (3)
* **Lines:** 424-427, 474-477 (also 197)
* **Problem:**
  * No secret header name.
  * No status codes.
  * No maximum image size or image encoding.
  * No structured-output schema.
  * "Low effort" is not an exact parameter.
  * No prompt template and no rule for `promptVersion`.
  * The crop margin is not given.
  * Line 427 crops around the hands only; line 197 says hands and object.
* **Fix:** Use `X-Shared-Secret`, return 401 or 413, and return 200 with `cannot_tell` when the upstream call fails. Send images as `{mime:"image/jpeg", data:base64}` with a maximum byte size per image. Give the request and response JSON schemas, the exact effort parameter, the prompt template with `PROMPT_VERSION = "1"`, and the crop margin as a percent of the hands-and-object bounding box.

### M7. Resume after the app closes, and attempt-end thresholds, are not designed
* **Reviewer:** both
* **Lines:** 429-432, 495 (also 176, 195)
* **Problem:** The E2E test expects resume after the app is closed and reopened, but item 17 covers only in-app pause. "Has not moved for 2 s" and "most frames" have no numeric values. There is no abandon action.
* **Fix:** On launch, reload the session whose state is running and restart its current attempt. Define the abandon action. Stillness = mean wrist displacement < 0.02 of image width over 2 s. "Most frames" = at least 60%.

### M8. Replay does not fit the landmarker mode or the attempt model
* **Reviewer:** codebase-details
* **Lines:** 414-417, 459-462
* **Problem:** MP4 replay needs MediaPipe `RunningMode.VIDEO`, not live-stream mode. The plan does not say how a clip maps to attempts.
* **Fix:** Landmarkers support both `LIVE_STREAM` and `VIDEO`, with frames decoded by MediaCodec at 15 fps. Each clip is one attempt. `clips.csv` has the columns `file, task, stepId, attemptNo, scriptedOutcome`.

### M9. The CSV export columns and join key are undefined
* **Reviewer:** both
* **Lines:** 457, 482
* **Fix:** Define the export columns: `sessionId, clipFile, stepId, attemptNo, retryIndex, verdict, reason, autoLevel, blindVerdict, blindLevel, modelId, promptVersion`. Join to `clips.csv` on `clipFile + stepId + attemptNo`. Report the attempt-level and step-level "not scored" rates separately.

### M10. Secret storage and frame encryption are not specified
* **Reviewer:** codebase-details
* **Lines:** 407 (`SecureSettings`, `FrameStore`)
* **Problem:** The Android Keystore stores keys, not strings. The plan does not say how the SQLCipher passphrase is created or protected, or which scheme encrypts the frames.
* **Fix:**
  * A Keystore AES-256-GCM key wraps a random 32-byte SQLCipher passphrase and the settings, stored in DataStore.
  * Frames are encrypted with AES-GCM.
  * The PIN is stored as a salted PBKDF2 hash.
  * Room uses `SupportOpenHelperFactory` from `net.zetetic:sqlcipher-android`.

### M11. Test plan gaps
* **Reviewer:** both
* **Lines:** 464-467, 472, 477
* **Problem:** The following have no tests:
  * `CloudVerifier` failure mapping (timeout, malformed JSON, refusal, network error).
  * `VisibilityGate`.
  * `strengthsAndNeeds`.
  * Recalculation after a correction.
  * Builder failures: unknown tag, duplicate ID, missing text or audio.
  * The 20 s / 2 s / 45 s timing.
  * Help advances the prompt level; Repeat does not.
  * "Cannot tell" twice gives "not scored".
  * `compute_metrics.py`.
  * Named proxy test cases.
* **Fix:** Add named cases with expected results. Add `CloudVerifierTest.kt` (MockWebServer), `VisibilityGateTest.kt` and `eval/test_compute_metrics.py`.

### M12. Pass conditions are unstated or uncalibrated
* **Reviewer:** style, implementation-readiness (M7)
* **Lines:** 362, 437, 452, 495
* **Problem:**
  * "Check Goal 8" (line 495) has no pass threshold.
  * "Reviewed line by line" (line 362) names no reviewer and no record.
  * "Calm colours" (line 452) cannot be tested.
  * `compare` and "weakest domain first" (line 437) have no signature and no tie-break.
* **Fix:**
  * Quote the Goal 8 agreement and kappa thresholds.
  * "A native Egyptian-Arabic therapist approves each line, recorded in `strings-review.csv`."
  * Name the palette in `ui/theme/Color.kt`.
  * `compare(a: SessionResult, b: SessionResult): List<StepDiff>` covers steps scored in both sessions. Sort domains by ascending average, break ties by the plan's section order, and keep source order within a section.

## Nits

1. **Line 412.** Ring buffer size and evidence frames per attempt are not stated. Fix: 45 s × 5 fps (225 frames); save the 3 cloud frames plus 1 frame per second. *(all three reviewers)*
2. **Line 417.** No rule picks the landmarker. Fix: `pose` runs Pose; `hand` runs Hand; `cloud` runs Hand (for the crop) plus Pose (for visibility). *(IR)*
3. **Line 352.** The activity ID scheme is undefined, and names repeat across plans. Fix: `act.mild.rom.01`. *(IR)*
4. **Line 352.** The `catalogVersion` hash inputs are undefined. Fix: SHA-256 of the canonical sorted JSON, first 12 hex characters. *(IR)*
5. **Lines 372, 447.** Audio file naming is not defined and slow playback has no speed. Fix: shared ID-based naming; `PlaybackParams.setSpeed(0.75f)`. *(IR, CD)*
6. **Line 432.** No Bluetooth keycodes are given. Fix: treat ENTER, SPACE and MEDIA_PLAY_PAUSE as Done. *(CD)*
7. **Line 397.** Screen pinning asks the user to confirm unless the app is device owner. Fix: say this prompt is accepted. *(CD)*
8. **Line 343.** "The two candidate models" is vague. Fix: name them and link to the Online model section. *(style)*
9. **Line 387.** "As the samples do" is vague. Fix: name the sample's Gradle download file. *(style)*
10. **Line 451.** "Informed by cboard" is imprecise. Fix: "tile grid layout adapted from cboard". *(style)*
11. **Line 422.** The rep counters are named by licence. Fix: name RepCounterSDK and Rep-Counter. *(style)*
12. **Lines 432, 457.** References to the Technical Plan have no anchor. Fix: link the subsections. *(style)*
13. **Line 467.** The 12-item run-on list is hard to read. Fix: one sub-bullet per test file. *(style)*
14. **Line 477.** One sentence of 60+ words. Fix: split it into auth/limits, request, response and failure mapping. *(style)*
15. **Lines 352, 482.** Overlong paragraph and parenthetical. Fix: split into Scales, Treatment plans and Output sub-bullets; make the exclusion rule its own sentence. *(style)*
