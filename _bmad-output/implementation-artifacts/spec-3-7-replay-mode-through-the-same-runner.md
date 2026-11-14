---
title: 'Story 3.7: Replay mode through the same runner'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '48daef057ff1a3ae968eef46f6ca93129bf0b009'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/docs/designs/daily-living-skills-app.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Accuracy can only be measured by live runs; volunteer clips cannot be scored, and a new catalogue/model version cannot be re-measured on the same clips.

**Approach:** A debug-only Replay screen (inside Settings → Advanced tools) imports clips via the system file picker (SAF, multiple). Each file name `<stepId>__<attemptNumber>__<outcome>__<volunteer>.mp4` is parsed by `ClipName`; malformed names are skipped with a message. A `ReplaySource` decodes the clip (MediaMetadataRetriever / MediaExtractor frames at the live analysis rate, downscaled like live), runs the step's landmarkers in MediaPipe VIDEO mode, and feeds frames + landmarks into the same `VerifierRouter` and evidence path as live — window = whole clip, Done implied, `number`/`promptLevel` from the name, `clipFile` saved. All clips of one import go into one `replay` session for a hidden eval child (created once, never listed). "Re-score" creates a new replay session over the previously imported clips with current catalogue/rules/model versions. Replay sessions never feed reports, plans, rewards or the Continue card. A results list shows per clip: step, scripted outcome, app verdict, reason, agreement.

## Boundaries & Constraints

**Always:** Same router, gate, verifiers and frame/evidence code as live (no parallel scoring path). Clips are copied into app-private storage (encrypted like frames, or at least app-private) so Re-score works offline. The eval child is hidden from the Children list and from every report query (filter on session kind `replay` and the eval child flag). Outcome token in names is one of `done`, `not_done` (validate). Step ID must exist in the current catalogue, else skipped with a message. Cloud steps follow the normal network rules. Debug builds only. Strings ar/en.

**Never:** No release-build entry. No editing of clinical content. No new scoring rules.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid import | 3 named clips | 1 replay session, 3 attempts, clipFile saved | N/A |
| Malformed name | `foo.mp4` | skipped, message lists it | N/A |
| Unknown step | stepId not in catalogue | skipped with message | N/A |
| Bad outcome token | `__maybe__` | skipped | N/A |
| Undecodable video | corrupt | that clip CANNOT_TELL `not_judgeable` or skipped with message | N/A |
| Re-score | after catalogue change | new replay session, current versions | N/A |
| Reports | replay sessions exist | not in Children, Continue, reports | N/A |

</frozen-after-approval>

## Code Map

- New `android/.../replay/ClipName.kt`, `ReplaySource.kt`, `ReplayRepository`/use-case; `ui/therapist/ReplayScreen.kt` + ViewModel; entry in Settings Advanced tools (debug only).
- `android/.../vision/VerifierRouter.kt`, `Landmarkers.kt` (VIDEO mode), `session/FrameStoreEvidence.kt`, `data/*` (SessionKind.REPLAY, eval child, queries filtering it).
- `android/.../ui/therapist/ChildList*`, Continue query -- exclude eval child / replay.

## Tasks & Acceptance

**Execution:**
- [x] ClipName parser + tests.
- [x] ReplaySource (decode + VIDEO landmarks) behind an interface; JVM tests with a fake decoder.
- [x] Replay use-case: session per import/re-score, attempts via the shared router + evidence; clip copy.
- [x] Eval child hidden everywhere; tests on the queries.
- [x] ReplayScreen (import, results with agreement, Re-score) debug-only.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.
- Given the phone, when two named clips are imported, then the results list shows their verdicts and the eval child appears nowhere else.

## Implementation Notes

## Spec Change Log

## Review Triage Log

6 findings + 2 coordinator items patched: replay router without live zones (zone steps no_zone), replay cloud off the live banner, scaled decode + 90 s cap, duplicate names + atomic clip replace, session always ends (done/abandoned), <80% decode coverage → not_judgeable, replay-only runner calls guarded, mirrored-clips import switch. Not yet run: androidTest DatabaseTest replay case.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
