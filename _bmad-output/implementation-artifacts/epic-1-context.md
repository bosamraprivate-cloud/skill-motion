# Epic 1 Context: Camera and online-model feasibility on the tablet (M1)

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Prove the two riskiest technologies on real tablet hardware before any content work starts: MediaPipe Pose + Hand landmarkers running at >= 15 fps combined (or a recorded fallback that runs only the needed landmarker), a body step (`drink.cup.05`) judged locally within 300 ms, and an object step (`drink.cup.06`) judged by DeepSeek through a stateless Cloudflare Worker proxy in under 8 s. The epic also lays down the Android project skeleton and quality gates every later story builds on. Exit: fps and cloud latency recorded in `docs/designs/m1-spike-results.md`.

## Stories

- Story 1.1: Android project skeleton with quality gates
- Story 1.2: Camera feed and landmarkers with frame-rate probe
- Story 1.3: Geometry, Check model and first local verdict
- Story 1.4: Stateless verify proxy on Cloudflare Workers
- Story 1.5: CloudVerifier and first cloud verdict for a cup step

## Requirements & Constraints

- Body steps are judged on the tablet from pose/hand landmarks; verdict within 300 ms of window close; no frames leave the tablet for body steps.
- Object steps send exactly 3 cropped JPEGs (start/middle/end, cropped around hand landmarks and the object zone) plus the step question to the proxy, with a fresh UUID `requestId`. Never a child name, code, profile field or session ID.
- Cloud judging: 8 s timeout. Timeout, network error, refusal, malformed answer or non-200 all become `cannot_tell` with a reason. Doubt is never `not_done`, and network loss never lowers a level.
- The front camera image is un-mirrored before any left/right logic (a raised right hand reads as `RIGHT_WRIST`).
- Diagnostics show per-landmarker and combined fps. Proxy reachability and latency get probed and recorded.
- Secrets: `DEEPSEEK_API_KEY` and `SHARED_SECRET` are Worker secrets only. The API key never reaches the tablet. Set a monthly DeepSeek spend limit. Nothing gets committed.
- Never log child name, code, frame or request body (Logcat or Worker).
- No clinical content gets invented. Step IDs and thresholds may appear in Kotlin only inside tests and spike screens, never in production logic (content comes from the catalogue later).
- Record the M1 decisions in `m1-spike-results.md`: tablet model and fps, the DeepSeek endpoint, model ID and image limits confirmed from official docs, and the measured cloud latency.

## Technical Decisions

- **Paradigm:** functional core and imperative shell in one module, `com.aitherapist.dls`, minSdk 30. The pure core has no `android.*` imports (`vision/Geometry`, check evaluation, `catalog/CatalogModels`) and is tested on the JVM. The shell holds `camera/`, `vision/Landmarkers`, `vision/CloudVerifier`, `ui/`.
- **Package dependencies:** enforced by a Konsist test. Nothing depends on `ui`. `vision` may depend on `camera` and `catalog`. `scoring`/`rewards`/`catalog` stay Android-free.
- **Verifier contract:** `suspend fun verify(step, frames, landmarks, ctx: VerifyContext): Verdict`. `Verdict(outcome DONE|NOT_DONE|CANNOT_TELL, reason, modelId?, promptVersion?, latencyMs)`. Verifiers never throw: every exception becomes `CANNOT_TELL`. `NOT_DONE` comes only from a completed check evaluation or a model answer of `not_done`. `VisibilityGate` runs first and returns `out_of_frame` on failure. A missing zone gives `no_zone`. A frame is paired with the nearest landmark result within 100 ms, otherwise it counts as not visible. Enums serialise lower_snake via `@SerialName`.
- **Check model:** a sealed `Check` decoded by kotlinx-serialization with discriminator `type`. The allowed types are exactly: `angleAbove, angleBelow, pointAbove, distanceBelow, distanceAbove, inZone, handsRubbing, question`. An unknown type fails. Landmark refs are `{"lm": ...}` or `{"mid": [a, b]}`, and a side-less name plus `side` (`left|right|either`) resolves to LEFT_/RIGHT_. Distances are ratios of shoulder width in the same frame. `holdSec` must hold continuously. The JSON Schema in `tools/catalog-builder/schema/` is the contract. `Criterion` (`kind` reps|hold) is a separate sealed type.
- **Verdict reasons:** `out_of_frame, no_zone, not_judgeable, timeout, network, http_<status>, invalid_model_output`, or the model's short reason text.
- **Proxy contract (fixed):** `POST /verify` with `Authorization: Bearer <secret>` and `multipart/form-data` body: fields `requestId`, `stepId`, `question` plus up to 3 binary JPEG parts named `image` (crops, longest side <= 768 px), at most 3 MB; the Worker base64-encodes images only for the DeepSeek call. 200 returns `{verdict, reason, modelId, promptVersion}`. 401 means a wrong secret, 413 means too many or too large images, 502 means the DeepSeek call failed. The Worker uses the `openai` SDK with DeepSeek `baseURL`, `MODEL_ID` (default `deepseek-flash`) and JSON mode, and validates against the verdict schema. Refusal or invalid output returns 200 `cannot_tell`/`invalid_model_output`. `promptVersion` lives in `proxy/src/prompt.ts`. No storage bindings (KV/D1/R2) and no body logging. `modelId` is the value DeepSeek returns. A contract change bumps `promptVersion` and updates the Vitest and MockWebServer tests together.
- **App-side mapping:** non-200 becomes `http_<status>`, a dropped connection becomes `network`, and anything over 8 s becomes `timeout`.
- **Manifest:** camera and internet permissions, `allowBackup="false"`, `supportsRtl="true"`, landscape, one activity.
- **Stack:** pin exact versions in `libs.versions.toml`: Kotlin, Compose BOM, CameraX, MediaPipe Tasks Vision (latest 1.x), Room + KSP, SQLCipher >= 4.18, kotlinx-serialization, OkHttp, Vico 3, Rive 11, Konfetti 2, Kover, ktlint, Konsist, Material 3 + adaptive, Navigation 3, Hilt, Lifecycle Compose, Coil 3, compose-shimmer. The proxy uses TypeScript, wrangler, vitest and `openai`, pinned in `package-lock.json`.
- **UI pattern:** one `@HiltViewModel` per screen exposing a sealed `UiState` (Loading/Empty/Content/Error) via `stateIn(WhileSubscribed(5_000))`. Navigation 3 with typed `@Serializable` keys. Shared `EmptyState`/`ErrorState`/skeleton live in `ui/components` with previews. RTL-safe modifiers only. Pure core classes take no Hilt annotations.
- **Reuse:** adapt the camera and landmarker code from mediapipe-samples, keep the upstream license headers and list the credit for README/About. A Gradle task downloads the pose and hand model files into assets.
- **Verification gates:** `./gradlew assembleDebug test lint ktlintCheck koverVerify` (Kover >= 90% line coverage on `Geometry`). Every `Check` subtype used gets a synthetic-landmark test. `CloudVerifier` is tested with OkHttp MockWebServer (200/401/413/502/timeout/drop). Proxy: `npm test`, `npm run build` (wrangler dry-run), `npx tsc --noEmit`.

## Cross-Story Dependencies

- 1.1 comes before every Android story (1.2, 1.3, 1.5).
- 1.2 (`FrameBuffer`, landmark pairing, un-mirroring) feeds 1.3 (`VisibilityGate`, `PoseVerifier`) and 1.5 (crops around hand landmarks).
- 1.3 defines `Verifier`, `Verdict`, `Check`/`Step`/`Detector` models used by 1.5's `CloudVerifier`.
- 1.4 (deployed proxy) is required for 1.5's live end-to-end verdict. 1.5's MockWebServer tests can proceed independently.
- Epics 2 and 3 build on these contracts: the catalogue builder emits the `Check` schema and the runner calls the verifiers. Keep the contracts stable.
