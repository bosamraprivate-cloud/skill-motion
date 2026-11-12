# Adversarial Review: ARCHITECTURE-SPINE.md

- **Target:** `ARCHITECTURE-SPINE.md` (Daily Living Skills App, 2026-11-11)
- **Primary source:** `docs/designs/daily-living-skills-app.md`
- **Method:** for each finding, two units that each obey every AD to the letter, yet build incompatibly; plus ADs that contradict the design.
- **Verdict:** **Revise before stories.** The spine is strong on the proxy, encryption, and pure-core boundaries. It has real gaps in five areas: which sessions feed the report and plan, the evidence that replay and eval depend on, how check JSON is encoded, who owns rewards, and how version stamps are stored. Each gap lets two compliant units diverge.

Severity: **H** = units will clash during normal development; **M** = likely clash or design contradiction; **L** = minor ambiguity.

---

## H1. Which sessions feed the report, plan, and comparison is undefined

- **Clash:** `ReportScreen` passes `domainAverages` the step levels of the session just opened (one task). `PlanScreen` passes the latest assessment session of each task, so it covers several tasks. Both call `RulesEngine` and neither caches results, so both comply with AD-5. The same child gets different plan levels, and so different activity lists, on two screens.
- **Variants:** whether replay sessions (`kind=replay`) and `abandoned` or `running` sessions count; whether `compare` may mix `replay` and `assessment` sessions.
- **Design:** Goal 4 says "after an assessment". Treatment plan is "for a child … across all weak domains". The design does not say how levels combine across tasks or sessions.
- **Fix:** add a `RulesEngine.selectSessions(child, sessions)` rule (or a rule in AD-5). The input set is the latest `done` session with `kind=assessment` for each `taskId`. Replay, running, and abandoned sessions are excluded. The report for one session uses only that session. `compare` needs two sessions with the same `taskId` and `kind=assessment`.

## H2. Replay and export evidence has no column: `clipFile`, clip attempt number, and replay child

- **Clash:** the export (Conventions, "columns exactly as the design") requires `clipFile`. No table in AD-8's data model stores it. `ReplaySource` may put it in `attempt.reason`. The exporter may rebuild it from `stepId`, `number`, and `verdict`, but that cannot recover the volunteer. Both comply.
- The clip name carries `<attemptNumber>`. AD-6 says the runner sets `number` from its own state. A replay of `…__2__done__v1.mp4` through a fresh runner records `number=1`, which gives level 3 where the script expects 2. This silently corrupts Goal 8.
- `session.childId` is a non-null FK, but volunteer clips have no child. One implementer creates a fake "volunteer" child (then rewards, stickers and the report pick it up). Another uses a sentinel ID that breaks the FK.
- **Fix:** add `attempt.clipFile` (nullable) through a v1 schema change. When a session is replayed, the runner takes `number` and `promptLevel` from the clip name. Replay sessions belong to one reserved eval child that is hidden from child lists and earns no rewards. State all three in AD-9 and AD-10.

## H3. Check JSON encoding is not actually closed (AD-4)

AD-4 says the fields are "exactly the design's Check types table". But the design's own examples break the table, so the builder and `CatalogModels` can each pick a different encoding:
- The catalogue example `{"type":"angleAbove","joint":"rightShoulder","deg":150,...}` uses a `joint` field and a camelCase name. The table uses `a`, `vertex`, `b`, MediaPipe `UPPER_SNAKE` names, and `side`.
- The step table uses `either WRIST`, `midpoint of shoulders`, `MOUTH midpoint`, and `midpoint of hips`. These are not MediaPipe landmark names, and the spine gives no encoding for a midpoint. A Python author writes `"b":"SHOULDER_MID"` and a Kotlin author expects `{"mid":["LEFT_SHOULDER","RIGHT_SHOULDER"]}`.
- `side: either` in a field (table) or as a prefix in the name (`either WRIST`)? `handsRubbing` has no `side`.
- Activity criteria use the discriminator `kind` (`reps|hold`) plus `target`, `perSide`, `low`, `high`. AD-4 keys only on `type`. `RepCounter` hysteresis uses `low`/`high`, but the embedded `Check` carries `deg`, so there are two threshold sources.
- **Fix:** add a JSON Schema (`tools/catalog-builder/schema/check.schema.json`) as the single contract. Define landmark references as `{"lm":"LEFT_WRIST"}` or `{"mid":["LEFT_HIP","RIGHT_HIP"]}`, keep `side` as a field, and make `MOUTH` a named alias for the midpoint of `MOUTH_LEFT` and `MOUTH_RIGHT`. Define `Criterion` as a separate sealed type keyed by `kind`. State that `low`/`high` override `deg` for counting. Fix the design's `joint` example.

## H4. `inZone` needs therapist-drawn zones that the pure core cannot reach

- **Clash:** AD-3 fixes the signature `verify(step, frames, landmarks)` and puts check evaluation in the pure core. Zones (`mat`, `sink`, `place`) are drawn in the framing check and "stored in settings" (design). AD-8's `SecureSettings` list does not include them. `PoseVerifier` A reads `SecureSettings` (shell I/O inside core evaluation, which breaks the paradigm). Verifier B expects the runner to inject zones and fails the check when none are given, which returns `NOT_DONE` (AD-3 forbids this, but only if B counts "no zone" as incomplete).
- The coordinate space is unspecified: zones are drawn on the mirrored preview, but rules run on un-mirrored, normalized landmarks.
- **Fix:** add `zones: Map<String, Rect>` (normalized and un-mirrored) to the verifier input, or make it a constructor parameter set by the runner. Store zones in `SecureSettings` under AD-8. A missing zone gives `CANNOT_TELL` reason `no_zone`; add that reason to Conventions.

## H5. Reward ownership and the "facts versus results" status of rewards are contradictory

- **Clash:** AD-9 names no writer for `reward`. It says only "after the verdict is saved". `AssessmentRunner` may write a row for each step, while `TaskRewardScreen` writes a row for each task. AD-11 says stickers are "computed by `RewardRules` from saved facts", yet `reward.stickerId` stores them. A sticker book that reads the stored `stickerId` and one that recomputes "every 3 finished tasks" will disagree after a session is abandoned or replayed.
- Training stars ("same rules per repetition set") have no verdict. AD-9's "only after the verdict is saved" leaves no legal writer for them.
- The dependency graph has no `session → rewards` or `training → rewards` edge. Only `ui` may call `rewards`, so the reward write must happen in UI code, which goes against the AD-10 rule against UI-driven logic.
- **Fix:** name one writer, `rewards/RewardRecorder`, called by `AssessmentRunner` after the verdict is saved and by `TrainingRunner` after the result is saved. Write one row per step or activity with `stars`. Stickers are derived, not stored: drop `stickerId`, or record it only as an audit fact computed by `RewardRules`. Define "finished task" as a session with `state=done` and `kind=assessment`. Add the missing graph edges.

---

## M1. Version stamps sit on `session`, but the design wants them on every attempt, and results are recomputed anyway

- The design NFR says "every **attempt** stores the catalogue version, rules version, model ID and prompt version". AD-2 puts `catalogVersion` and `rulesVersion` on `session` only. That contradicts the design, and the design's data model table does not include them on `attempt` either, so the design is inconsistent with itself.
- Under AD-5, results are always recomputed with the current `RULES_VERSION` and catalogue. A stored `session.rulesVersion` of "1" then sits next to levels computed by rules "2", and old sessions are re-tagged silently if the catalogue changes. Two dashboards (one that warns on a mismatch, one that ignores it) both comply.
- **Fix:** state that results always use the current rules and catalogue, and that the stored stamps record only provenance. The UI shows a "computed with newer rules" badge when they differ. Fix the design NFR to say "every session stores catalogue/rules version; every cloud attempt stores model ID/prompt version".

## M2. `catalogVersion` is self-referential

- The design says the builder writes `assessment.json` and `activities.json` "with a `catalogVersion` hash". AD-2 defines the hash over the bytes of those same files. If the builder embeds the value, the app can never recompute it, and an app that recomputes it gets a different value from the embedded one.
- **Fix:** the builder writes a separate `assets/catalog/version.json` (`{"catalogVersion":"…"}`) computed over the two files, which contain no version field. The app reads that file and never hashes the catalogue at runtime.

## M3. Help mid-attempt: does it close the window?

- AD-6 says "Help raises `promptLevel`" and "each closed attempt window writes exactly one row". Runner A treats Help as ending the attempt: it writes a `not_done` row and moves to `number+1`. Runner B keeps the same `number` and raises `promptLevel`. If Help is pressed during attempt 1 and the step is then done, A gives level 2 with 2 rows, while B gives level 2 with 1 row. If Help is pressed three times, A reaches level 1 with no movement judged, while B never does. Both comply with AD-6.
- **Fix:** Help keeps the current `number`, raises `promptLevel` (capped at 3), sets `helpPressed`, and restarts the window. It never writes a row on its own.

## M4. Cloud error mapping has two paths for the same event

- AD-7 lets the Worker return either "200 with `cannot_tell`" or "502" for invalid model output. A Worker that returns 200 makes the app record reason `malformed`. One that returns 502 makes the app record `http_502`. Diagnostics and the Goal 8 "cannot tell" breakdown then differ by deployment.
- AD-7 maps errors to `http_<status>`. The design says `reason` = "the status code" (`502`). This is a small contradiction that matters to `compute_metrics.py` if it parses reasons.
- **Fix:** refusal or invalid JSON gives 200 with `{verdict:"cannot_tell", reason:"refused"|"malformed"}`. 502 is used only for upstream HTTP or network failure. Update the design to say `http_<status>`.

## M5. `method: none` is stored as `cannot_tell`, which inflates the Goal 8 "cannot tell" rate

- AD-3 records `none` steps as outcome `cannot_tell` with reason `not_judgeable`. Goal 8 caps "cannot tell" at 15% of attempts. A metrics script that counts `verdict=cannot_tell` includes 5 of 41 steps by construction. One that filters `verifier=none` does not. Both scripts comply.
- **Fix:** state that `eval/compute_metrics.py` and the diagnostics rate exclude `verifier=none`. Better: also add outcome `not_scored` for `none` rows so the categories never overlap.

## M6. Frames before the attempt row: orphans and resume

- AD-6 writes the `attempt` row only when the window closes. AD-8 names frame files `<attemptId>-<n>.bin`. AD-10's resume "deletes the interrupted attempt's frames", but no row holds that `attemptId`. Runner A pre-generates the ID and keeps it in `session`. Runner B writes frames under a temporary ID. After a crash, B's frames are never found and are never deleted on child delete if they sit outside `<sessionId>/`.
- **Fix:** add `session.currentAttemptId` (pre-generated and saved with the resume point). Resume deletes `frames/<sessionId>/<currentAttemptId>-*`. All frames live under the session folder, so child delete always removes them.

## M7. The dependency graph is missing edges that the rules require

- `replay → vision` (VIDEO-mode landmarker), `replay → data` (creates the replay session), `training → catalog`, `training → camera`, `training → audio`, `training → rewards`, `session → rewards`, `ui → vision/camera` (diagnostics shows frame rate and visibility, and the framing check draws the outline). Without these, a lint or ArchUnit rule built from the graph rejects compliant code, or each unit routes around it in a different way.
- **Fix:** add the edges and enforce the graph with a Konsist or ArchUnit test under AD-12.

## M8. Landmarker selection versus the needs of cloud steps

- The design runs "only the landmarker(s) the current step needs". AD-3 requires `VisibilityGate` for `cloud` steps, and the CloudVerifier crops "around the child's hands". If one unit runs Pose only for cloud steps and the crop code expects Hand landmarks, the crops are wrong or empty, which gives `CANNOT_TELL` on every step.
- Frame-to-landmark alignment is also unspecified: `FrameBuffer` holds 5 fps, while landmarks run at about 15 fps.
- **Fix:** add `Detector.landmarkers: [pose|hand]` to the catalogue (derived by the builder from the check, with cloud defaulting to both). Pair frames to landmarks by nearest timestamp within 100 ms.

## L1. Verdict enum spellings

- AD-3 uses `DONE|NOT_DONE|CANNOT_TELL`. The design's interface uses `done|notDone|cannotTell`. Conventions say lower_snake. Fine as long as the Kotlin `@SerialName` and the Room converter both use `not_done`. Add that explicitly.

## L2. Correction removal and "both kept"

- Upsert on (`sessionId`, `stepId`) with `level=null` meaning "not scored" leaves no way to clear a correction and return to the automatic level. The design says that "latest wins" over the unique key, which loses history. **Fix:** add a delete path (or `cleared` flag), owned by the same screen.

## L3. Activity audio and illustration gates

- AD-1 makes the builder fail if a step has no audio, but it does not check activity audio. Training plays instructions ("hears the instruction", CUJ 5). The builder also depends on `audio-gen` and `art` running first, but no build step order is stated. **Fix:** require normal audio for activities. State the order: audio-gen, then art export, then catalog-builder.

## L4. Session `paused` and `abandoned` writers

- No rule says when a session becomes `abandoned` (for example, a new session started while another is `running`). The therapist "Continue" card and a new-assessment button could both act on the same child. **Fix:** allow at most one `running` or `paused` session per child. Starting a new one sets the old one to `abandoned` (AD-9 writer: `AssessmentRunner`).

---

## ADs that contradict the design

| AD | Contradiction | Resolution |
| --- | --- | --- |
| AD-2 | Version stamps on `session` versus the design NFR "every attempt" | Fix the design NFR (M1) |
| AD-7 | `http_<status>` versus the design's "reason = the status code"; 502 for invalid output versus the design's "refusals and errors become `cannot_tell`" | Update the design and make the mapping single-valued (M4) |
| AD-4 | "Exactly the table", but the design's own examples (`joint`, `either WRIST`, midpoints) do not fit the table | Add a schema (H3) |
| AD-1 / design | The design says the builder writes `catalogVersion` into the outputs, while AD-2 hashes those outputs | Use `version.json` (M2) |
| AD-8 | The design puts zones in settings, while the SecureSettings list in AD-8 leaves them out | H4 |
