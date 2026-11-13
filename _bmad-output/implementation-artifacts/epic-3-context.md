# Epic 3 Context: The app scores the four camera-scored tasks by itself (M3)

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Turn the Epic 2 runner, which only records attempts, into one that judges them itself. Body steps are judged on the tablet from MediaPipe pose and hand landmarks. Object steps go to the stateless proxy (DeepSeek `deepseek-flash`) as cropped frames. Every attempt keeps encrypted evidence frames. The epic ends when all 41 steps of the four camera-scored tasks have been scored on volunteer clips through replay mode, deepseek-flash accuracy and latency have been measured, and the detector settings are frozen under a recorded `catalogVersion`.

## Stories

- Story 3.1: All body Check evaluators
- Story 3.2: Hand verifier and hands rubbing
- Story 3.3: Detector settings for the 41 steps and setup zones
- Story 3.4: Framing check screen
- Story 3.5: Live verifiers in the runner with evidence frames
- Story 3.6: Network loss becomes "cannot tell"
- Story 3.7: Replay mode through the same runner
- Story 3.8: Volunteer clip recording and manifest
- Story 3.9: Threshold tuning and first deepseek-flash measurement

## Requirements & Constraints

- Four camera-scored tasks, 41 steps: 5 `none`, 6 local (pose/hand), 30 cloud. Questions and checks come verbatim from the design's camera-scored step table. Never invent clinical content.
- Body steps are judged on the tablet within 300 ms of window close, and no frame leaves the device. Cloud steps must answer within 8 s or become `cannot_tell`.
- Object steps send at most 3 cropped JPEG frames (start/middle/end) plus the step question, with a random request ID and the shared secret. Never send a name, child code, profile field or session ID.
- Doubt is never failure. Timeout, network error, non-200, refusal, malformed answer and missing zone all become `cannot_tell` with a reason, never `not_done`. Network loss never lowers a level, and everything except object judging works offline.
- Judging order at window close: the visibility gate first (`out_of_frame` gives cannot tell), then the matching verifier. A cannot tell repeats the attempt once; a second one makes the step not scored. `method: none` gives not scored.
- Each attempt records number, prompt level, helpPressed, verdict, reason, method, latency and frame refs. Cloud attempts also record `modelId` and `promptVersion`.
- Evidence frames (3 per attempt) are saved encrypted, kept until the child is deleted, and shown in session detail.
- Replay: one clip is one attempt, the window is the whole clip, Done is implied. Attempt number and prompt level come from the file name. Re-scoring creates a new session under the current versions. Replay sessions never feed reports, plans or rewards.
- Never log child name, code, frame or request body.

## Technical Decisions

- **Verifier contract:** `suspend fun verify(step, frames, landmarks, ctx: VerifyContext): Verdict`, where `Verdict(outcome DONE|NOT_DONE|CANNOT_TELL, reason, modelId?, promptVersion?, latencyMs)`. A verifier never throws to the runner. `NOT_DONE` may come only from a completed check evaluation or a model answer of `not_done`. `VerifyContext` holds the setup zones as normalised, un-mirrored rectangles.
- **Check sealed type:** kotlinx-serialization with discriminator `type`. The only allowed types are `angleAbove`, `angleBelow`, `pointAbove`, `distanceBelow`, `distanceAbove`, `inZone`, `handsRubbing` and `question`. Landmark refs are `{"lm": ...}` or `{"mid": [..]}`. A side-less name combined with `side` resolves to LEFT_/RIGHT_; `side: either` passes if either side passes. Distances are ratios of shoulder width in the same frame, and `holdSec` must hold continuously. An unknown type fails both the catalogue build and the catalogue load. A new type changes the JSON schema, `CatalogModels`, the evaluator and its tests in one change.
- **Landmarkers:** `Detector.landmarkers` (pose, hand, or both; both for cloud) decides which landmarkers run. Each frame is paired with the nearest landmark result within 100 ms, otherwise that frame counts as not visible. The front image is un-mirrored before any left/right rule.
- **Content is data:** `detectors.json` is the hand-written input for detection settings only. `assessment.json` is generated and never hand-edited. Kotlin outside tests has no step ID, threshold or question literal. Any detector change changes `catalogVersion`.
- **Proxy contract (fixed):** `POST /verify`, Bearer shared secret, multipart with `requestId`, `stepId`, `question` and up to 3 `image` parts (longest side ≤ 512 px, quality 80, body ≤ 3 MB). The app maps errors to reasons `http_<status>`, `network` and `timeout`. Test the mapping with MockWebServer for 200, 401, 413, 502 and timeout.
- **Attempt facts:** each closed window writes exactly one `attempt` row, and a cannot-tell repeat writes a new row with the same `number`. The runner advances by calling `RulesEngine.stepLevel` on the facts it just saved. Only `AssessmentRunner` writes `session` and `attempt`; replay writes through the same runner. Replay sessions belong to the hidden eval child (`child.isEval`), and `attempt.clipFile` is set.
- **Frames:** buffered in memory and written only when their attempt row is saved. Only `data/FrameStore` writes them, as `IV(12) || AES-GCM(JPEG)` at `files/frames/<sessionId>/<attemptId>-<n>.bin`, with relative paths in `attempt.framePaths`. Zones, proxy URL and the secret are stored in `SecureSettings`. Only `data/` touches DAOs. Any schema change needs an explicit Migration plus test.
- **Runner:** the explicit state machine SHOW → LISTEN → ATTEMPT → JUDGE. `VerdictReady` is the event at the end of judging. Every transition is saved before the next state is emitted.
- **Verdict reasons:** `out_of_frame`, `no_zone`, `not_judgeable`, `timeout`, `network`, `http_<status>`, `invalid_model_output`, or the model's short reason.
- **Clip naming:** `<stepId>__<attemptNumber>__<outcome>__<volunteer>.mp4`, joined to `eval/clips.csv` by file name. Clip videos stay outside the repo.
- **Tests:** each Check subtype is tested on synthetic landmarks. The Konsist dependency test applies (`replay` → `session`/`vision`/`data`; `vision` → `camera`/`catalog`).

## UX & Interaction Patterns

- **Framing check:** shown only for camera-scored tasks. It shows the preview with a body silhouette, grey until the task's needed landmarks are visible, then sage; "Hand to child" starts screen pinning. The camera preview is never shown during steps.
- **Judging:** the character shows the idle "thinking" pose with soft dots and no text.
- **Hidden from the child:** cannot tell and network loss are invisible to the child, who sees only the neutral retry line. Therapist mode shows a network-loss banner. The error text says what happened and what to do.
- **Therapist IA:** the rail has only Children and Settings. Zones are drawn in Settings. Replay & evaluation and Diagnostics sit under Settings → "Advanced tools", in debug builds only. After a session, the PIN leads to a session summary (levels, Correct). Evidence frames appear in session detail under the child's Sessions tab.
- **Prompting:** level 1 shows the still frame, sentence and audio. Level 2 (second attempt or Help) and level 3 play the step's demonstration clip. There is no walkthrough.
- **Effect levels:** `full` and `calm` only.

## Cross-Story Dependencies

- 3.1 → 3.2 (the evaluator is extended with `handsRubbing`) → 3.3 (detectors reference the checks) → 3.4 and 3.5 (the visibility gate and the router use the detector settings).
- 3.5 builds on the Epic 2 runner, attempt facts and SecureSettings, and on the Epic 1 CloudVerifier and proxy. 3.6 hardens CloudVerifier and adds the shell banner.
- 3.7 reuses the 3.5 runner path. 3.8 supplies the clips. 3.9 needs 3.7 and 3.8 and freezes `detectors.json`.
- Epic 5 (blind review, kappa, export) depends on replay sessions and the attempt provenance from this epic.
