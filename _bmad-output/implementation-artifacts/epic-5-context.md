# Epic 5 Context: Therapist review, export and measured accuracy (M5)

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Give the therapist the final say and measure how far the app can be trusted. The therapist reviews every session with its evidence and corrects step levels, compares two assessments and sees training history. Diagnostics surfaces camera, landmark and proxy health. An anonymised `results.csv`, blind ratings and a metrics script and dashboard measure Goal 8 on the volunteer clips, and the README plus all gates make the project reproducible. Exit (design M5): Goal 8 measured and reported.

## Stories

- Story 5.1: Session list and session detail with evidence
- Story 5.2: Correct a step level
- Story 5.3: Compare two assessments
- Story 5.4: Training history
- Story 5.5: Diagnostics screen
- Story 5.6: Anonymised CSV export
- Story 5.7: Blind review
- Story 5.8: Accuracy metrics script and dashboard
- Story 5.9: Evaluation run and Goal 8 report
- Story 5.10: README and final verification gate

## Requirements & Constraints

- Goal 8 (design): app verdict agrees with the blind therapist rating on at least 80% of attempts; quadratic-weighted kappa on levels at least 0.6; "cannot tell" on at most 15% of attempts. Not scored steps and cannot-tell attempts are excluded from kappa and reported as the cannot-tell rate.
- Doubt is never failure: a target that is not met is reported as measured; no cannot tell is ever turned into not done to improve the numbers (5.9).
- Export has exactly the columns `sessionId, childCode, taskId, stepId, attemptId, attemptNumber, promptLevel, verdict, reason, verifier, modelId, latencyMs, level, correctedLevel, blindVerdict, clipFile`; levels come from `RulesEngine`, never stored. No child name anywhere in the file (test that searches for names). Only `childCode` identifies a child. Never the model's free text (`modelReason`), never frames.
- Blind ratings never change levels, reports, plans or compare; only `correction` rows do (test in 5.7). Blind review shows frames/clip only, never verdict, reason or level.
- Correction: upsert on (`sessionId`,`stepId`), latest wins; delete restores the automatic level; `level` null = not scored; valid values null or 1..3 (`RulesEngine.isValidCorrection`). Report, plan and compare update with no other action.
- Compare: two finished assessments of the same `taskId`; only steps with a level in both; per-domain averages for each and their difference. The second picker offers only the same task.
- Diagnostics cannot-tell rate is over saved attempts excluding verifier `none`. A 401 from the proxy shows "Proxy rejected the secret (401). Re-enter it in Settings."
- Metrics dashboard is labelled "volunteer accuracy"; Python script and Kotlin `Metrics` give identical numbers on a hand-computed fixture.
- Never log child name, code, frame or request body. All child data stays on the tablet; export goes to Downloads only by the therapist's action.
- Every schema change ships an explicit Migration plus instrumented test; destructive migration is forbidden. Kover >= 90% on `RulesEngine` (adding `compare` must keep it).

## Technical Decisions

- **AD-5 RulesEngine:** pure; `compare` is still missing and must be added (5.3) along with tests at domain-boundary cases. Levels are never written to the DB; export computes them. Bump `RULES_VERSION` (now "3") on any output change.
- **AD-9 one writer per table:** `correction` only from the therapist Session detail screen (via `CorrectionStore`); `blind_rating` only from `BlindReviewScreen`'s ViewModel through a `data/` repository. Only `data/` touches DAOs (Konsist).
- **Data model (design):** `blind_rating(id, attemptId, verdict done|not_done|cannot_tell, createdAt)`, FK `attemptId` -> `attempt.id` CASCADE, unique `attemptId` (insert-or-replace keeps one row per attempt).
- **Versions frozen for 5.9:** `catalogVersion`, `rulesVersion`, `modelId`, `promptVersion` are stored per session/attempt and must be reported (SM-5) with latency and fps.
- **UI state:** sealed `Loading/Empty/Content/Error`; shimmer skeleton after 150 ms in therapist lists and charts.
- **AD-12 / NFR-7:** README covers AD-1 build order (audio, illustrations, catalogue), app install, proxy deploy with secrets and spend limit, Bluetooth button pairing, physical setup, credits and licenses (also in an About screen).

## UX & Interaction Patterns

- **Profile tabs:** Sessions (history -> session detail with per-step levels, evidence, Correct), Progress (compare, training history; currently an empty state).
- **Level chip (UX-DR13):** a corrected level shows the automatic level struck through and the new one in corrected purple with "reviewed".
- **Evidence strip (UX-DR12):** per attempt: number, prompt level, Help, verdict, reason, method, latency, 3 decrypted thumbnails that enlarge on tap.
- **Replay & evaluation (rail):** import clips, replay sessions, blind review, accuracy metrics. Diagnostics is its own rail item.
- Therapist wording: "may be related to", never diagnostic; calm empty states.

## Current Code Facts

- DB **version 5** (`data/AppDatabase.kt`), schemas 1-5 committed. Entities: `child`, `session`, `attempt`, `correction` (`id, sessionId, stepId, level?, reason?, createdAt`, index on sessionId+stepId, **not unique**), `reward`, `training_result`. **No `blind_rating`**: 5.7 adds migration 5->6 (decide whether to also make the correction index unique in the same migration).
- `data/Results.kt`: `AssessmentResults.results/latestResults`, `SessionResult(session, levels)`, `sessionLevels`, `countsForResults` (assessment + done only), `CorrectionStore { addCorrection, clearCorrection }` implemented by `ResultsRepository`. **No UI calls `CorrectionStore` yet**; `addCorrection` inserts rows (latest wins on read), not a true upsert.
- `scoring/RulesEngine.kt`: `stepLevel`, `effectiveStepLevel`, `domainAverages`, `strengthsAndNeeds`, `planLevel`, `planCode`, `activitiesFor`, `isValidCorrection`, `correctionLevel`; **no `compare`**, no `Metrics.kt`.
- `ui/therapist/SessionsTab.kt`: lists assessments and training runs newest first, read-only; a done assessment opens `SessionSummary.kt` (per-step levels, "read-only; corrections come in Epic 5"). **No `SessionDetailScreen`, `EvidenceStrip`, `LevelChip`, `CorrectSheet`, `CompareScreen`, `TrainingHistoryScreen`, `BlindReviewScreen`, `MetricsScreen`, `AboutScreen`, `CsvExporter`.** 5.1 can extend SessionSummary into session detail rather than build a parallel screen.
- `data/FrameStore.kt` already decrypts frames (`files/frames/<sessionId>/<attemptId>-<n>.bin`); `attempt.framePaths` holds them.
- `ui/therapist/DiagnosticsScreen.kt` (+ ViewModel) is the M1 spike screen: delegate, pose/hand/combined fps, visibility %, a timed fps run and a try-step attempt with model/prompt version. **Missing:** per-landmark-group visibility, proxy reachability/latency ping, 401 message, cannot-tell rate. `vision/ProxyProbe.kt` exists and is the base for the health ping.
- `TrainingResultDao.forChild` exists; no repository method or screen exposes training history yet.
- `replay/ReplayExport.kt` + `ReplayService.exportCsv()` (debug only, Replay screen) write a different CSV (`clip_file, step, scripted_outcome, verdict, reason, verifier, model_id, prompt_version, latency_ms, catalog_version, rules_version`). `SettingsScreen.kt` has no export.
- `tools/eval/` (not `eval/` as the epic says): `accuracy_report.py` (scripted vs verdict agreement, cannot-tell rate, writes `docs/designs/m3-accuracy.md` between markers), `validate_clips.py`, `clip_manifest.py`, `generate_clips.py`, tests. **No kappa, no blind-rating join, no `compute_metrics.py`.** `eval/clips.csv` has 216 clip rows; `eval/recording-protocol.md` exists.
- `docs/designs/m3-accuracy.md`: status "pending clips"; no thresholds tuned; no frozen catalogVersion. Story 3.9 measurement is a prerequisite for 5.9.
- `childCode` is auto-generated hidden ("C-<digits>", `nextChildCode`), intended for the export.

## Deferred Items Relevant Here

- Kover 90% rule: confirm it is enabled for `RulesEngine`/`Geometry`/`RepCounter` and the gate runs the right variant (1.1 review).
- Konsist rules: only `data/` uses DAOs; no `android.util.Log`/`println` in production code (1.1).
- Release readiness: app icon, release signing config, keep rules before minify (1.1); pin MediaPipe model URLs with SHA-256 and an offline/CI switch for `downloadModels` (1.1).
- Add `connectedDebugAndroidTest` to the AGENTS.md verification table (2.9); 5.10 must run it.
- Move Diagnostics behind the PIN/shell; child mode must never open on it (1.2). Diagnostics ViewModel tests with a Landmarkers seam and fake clock; proxy settings helper test (1.2, 1.5, 2.9).
- Proxy prompt and mouth-crop tuning on volunteer clips, bump `promptVersion` (1.5): must happen before the frozen 5.9 run.
- Keystore/settings loss recovery ("reset all data") and orphan frame sweep (2.9).
- Screen-pinning failure surfaced to the adult; device-owner lock-task plan (2.14).
- Readable task names in pickers (Try step, and the new compare picker) (2.14).
- Jacket "both" chain persistence (`thenTaskId` needs a session column): could ride on the 5->6 migration.
- Audio run at the end of the project (task-name audio, activity details translation with owner review, Arabic domain/level labels review) (2.16, Epic 4 notes); ElevenLabs currently blocked.
- PIN lock should return to the last profile tab (4.3 review). Design doc still mentions Rive (clean up before release, 5.10).
- Debug-only result strip must never ship in release; `DlsVerdict` log tag debug only (3.5 notes).
- Cairo font, extended colour scheme for level colours (needed for the corrected-purple chip), Compose UI tests for Empty/Error (1.1).

## Cross-Story Dependencies

- 5.1 (session detail, evidence, LevelChip) -> 5.2 (CorrectSheet on that screen; the first `CorrectionStore` caller).
- 5.3 (`RulesEngine.compare`) and 5.4 (training history) fill the Progress tab; independent of 5.1/5.2 except that compare must reflect corrections.
- 5.7 (`blind_rating` + migration 5->6) -> 5.6 (export `blindVerdict` column); build 5.7's table before or with 5.6, or export an empty column first.
- 5.6 (export) + 5.7 -> 5.8 (script and `Metrics`) -> 5.9 (run on all clips, frozen versions, report). 5.9 also needs recorded volunteer clips and Story 3.9 tuning.
- 5.5 is independent (uses `ProxyProbe`, attempts for the cannot-tell rate).
- 5.10 last: README, About credits, every gate including `connectedAndroidTest` and logged E2E checks (Wi-Fi off, close/reopen mid-task).
- An Epic 5 UI/UX pass follows (owner rule: polish after each epic).

## Open Questions

- Paths: epics say `eval/compute_metrics.py`; existing tooling is `tools/eval/`. Put 5.8 in `tools/eval/` (reusing `accuracy_report.py`/`clip_manifest.py`) and update the epic, or follow the epic literally?
- Two CSVs: keep the debug `ReplayExport` for M3 tuning, or replace it with the 5.6 `results.csv` (which has no `scripted_outcome`; the script joins `eval/clips.csv` by `clipFile`)?
- Agreement baseline: Goal 8 compares the app with the blind therapist rating, while M3 compared with the scripted outcome. Report both?
- Correction uniqueness: make (`sessionId`,`stepId`) a unique index with a real upsert in the 5->6 migration, or keep insert + latest-wins read? The 5.2 AC says upsert.
- Kappa on levels needs level pairs: is the blind "level" derived by running Rule 1 on blind verdicts per step, and what happens when a step has blind ratings for only some attempts?
- Which sessions does blind review list: replay only (AC) or also live sessions? Should export include replay and live sessions together?
- Does `correctedLevel` in export repeat per attempt row of the step, and how is "not scored" correction encoded (empty vs a token)?
- Does the Diagnostics redesign keep the spike's timed run and try-step tools, or move them to a debug-only section?
- Where should export go on Android 10+ (MediaStore Downloads) and is a share sheet acceptable? Is `childCode` acceptable in a file leaving the tablet (it is not a name, per FR-44)?

## Coordinator decisions on the open questions (2026-11-14)

- Metrics script lives in `tools/eval/` (existing package); `eval/` holds data/protocol only.
- Keep the debug replay CSV; `results.csv` is the new release-visible export.
- Goal 8 agreement is measured against the blind therapist rating (primary, as the design states); scripted-outcome agreement from replay is reported as a secondary table.
- 5→6 migration makes `correction (sessionId, stepId)` unique: dedupe keeping the latest row, then a true upsert.
- Blind verdicts → levels for kappa: Rule 1 applied to the blind verdicts of a step only when every attempt of that step is rated; partially rated steps are excluded and counted.
- Blind review covers every attempt with frames (live assessment and replay), eval child included; never the child's name.
- Export: `correctedLevel` = 1/2/3, `not_scored`, or empty when no correction.
- Diagnostics keeps the spike tools in a debug-only section.
- Export via the system file picker (SAF); `childCode` may leave the tablet, names never.
- Jacket "both" chain persistence already shipped in 4.1 (nextTaskId); not needed in 5→6.
