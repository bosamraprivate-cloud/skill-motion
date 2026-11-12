---
status: final
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-Ai therapist-2026-11-11/prd.md
  - _bmad-output/planning-artifacts/prds/prd-Ai therapist-2026-11-11/addendum.md
  - _bmad-output/planning-artifacts/architecture/architecture-Ai therapist-2026-11-11/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md
  - _bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md
  - _bmad-output/planning-artifacts/briefs/brief-ai-therapist-2026-11-11/brief.md
  - docs/designs/daily-living-skills-app.md
---

# Ai therapist - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for Ai therapist (Daily Living Skills Assessment and Training App), decomposing the requirements from the PRD, the UX design contract (DESIGN.md + EXPERIENCE.md) and the Architecture Spine into implementable stories. Epics follow the design's milestones M1-M5 so the riskiest work (camera on the tablet, online model) comes first.

**Conventions used in stories**

- Kotlin paths are abbreviated: `<pkg>/x/Y.kt` = `android/app/src/main/java/com/aitherapist/dls/x/Y.kt`; `<test>/` = `android/app/src/test/java/com/aitherapist/dls/`; `<itest>/` = `android/app/src/androidTest/java/com/aitherapist/dls/`. Other paths are relative to the repository root.
- AD-n refers to the binding decisions in ARCHITECTURE-SPINE.md. A story is done only when the Build and verification commands of the spine relevant to the files it touches pass (AD-12).
- No story adds clinical content. Steps, tags, rules, questions and activities come only from `docs/reference/` and the design's camera-scored steps table.

## Requirements Inventory

### Functional Requirements

FR-1: First-run setup: set PIN, proxy address and shared secret (stored encrypted, never shown in clear), diagnostics show proxy reachability; therapist mode cannot open before a PIN exists.
FR-2: PIN-guarded therapist mode on every entry; wrong PIN rejected; PIN stored only as salted hash; PIN changeable after entering current one.
FR-3: Locked child mode via Android screen pinning; leaving requires the therapist; adult pause control freezes the attempt window.
FR-4: Framing check before each camera-scored task: preview with outline green only when needed landmarks visible; therapist draws named zones (mat, sink, place) once per setup; front image un-mirrored before left/right rules.
FR-5: Create, edit, delete child profile (name, unique child code, age); delete cascades to all sessions, attempts, frames, corrections, blind ratings, training results, rewards.
FR-6: Effect level per child: full, calm, off; default calm; applies to all reward effects.
FR-7: Catalogue built only from `docs/reference/`: 147 steps / 15 tasks (42/38/27/40), 148 activities (48/50/50); permanent IDs; tags copied verbatim; build fails on unknown tag, duplicate ID, missing Arabic text, step audio or illustration.
FR-8: Catalogue version derived from content; any change to step, tag, detector setting or activity changes it.
FR-9: Every one of the 147 steps runs in child mode with character illustration, one Egyptian Arabic sentence and audio, plus slow audio for prompt level 3.
FR-10: Start an assessment on any of 15 tasks and hand over to child mode; session records child, task, catalogue and rules version; jacket: on, off or both.
FR-11: Step screen: large illustration, animated character, one sentence, large Done, Help, Repeat; RTL Egyptian Arabic, calm colours; Done also via Bluetooth button.
FR-12: Prompt ladder levels 1-3; Help moves to next level and counts as a prompt; Repeat replays current level and does not.
FR-13: Attempt window opens at audio end; closes on Done, or >=20 s elapsed and no movement for 2 s, or at 45 s; movement = max wrist/elbow displacement over last 0.5 s > 0.03 x shoulder width.
FR-14: Judging at window close: visibility first (out_of_frame -> cannot tell), then body or object judging; done -> Rule 1 level; not done -> next attempt or level 1 after attempt 3; cannot tell -> repeat once, second -> not scored; method none -> not scored; a failed step never stops the task.
FR-15: Every state change saved immediately; pause/resume restarts current attempt; "Continue" card after app closed mid-task discards interrupted attempt's frames.
FR-16: Evidence frames saved per attempt, linked, viewable in session detail.
FR-17: Character walks through the whole task once at the end (one illustration per step with audio).
FR-18: Body steps judged on tablet from pose/hand landmarks with typed check; verdict within 300 ms; no frames leave the tablet.
FR-19: Object steps: 3 cropped frames (start/middle/end) + question to proxy; no name/code; random request ID; max 3 images; shared secret required; proxy stores nothing.
FR-20: Timeout (8 s), network error, refusal, malformed answer, non-200 -> cannot tell with reason; never not done.
FR-21: Attempt provenance: number, prompt level, helpPressed, verdict, reason, method, model ID, prompt version, latency, frame refs; session stores catalogue and rules versions.
FR-22: The 11 content-only tasks run fully in child mode with all steps not scored.
FR-23: Rule 1 step level (3 / 2 / 1 / not scored); a correction wins.
FR-24: Rule 2 domain average and plan level (3 strength; 2.5-<3 mild; 1.5-<2.5 moderate; <1.5 severe); visual-motor = motor coordination, counted once; multi-domain step counts in each; no scored steps -> no average.
FR-25: Levels, averages, plan levels, strengths, needs, plan always recomputed from facts; a correction updates every dependent screen.
FR-26: Session list and detail per child: steps, levels, attempts, evidence frames, verdict reasons.
FR-27: Correct a step level to 3/2/1/not scored; both kept and shown; step marked reviewed; latest wins.
FR-28: Compare two assessments of the same task using steps with a level in both: per-domain averages and difference.
FR-29: Diagnostics: camera fps, landmark visibility, proxy reachability and latency, cannot-tell rate.
FR-30: Training history per child: activity, count, duration, criterion met.
FR-31: Report: strengths (level 3), needs (2 or 1), per domain average, plan level, scored-step count; "may be related to" wording; no diagnosis.
FR-32: Plan: for each weak domain every activity of matching plan level and domain section, weakest first; multiple plan levels allowed; strengths contribute none; activity shows tools, how to perform, goal, criterion.
FR-33: Therapist picks plan activities for a training session.
FR-34: Camera-counted activities count reps or hold time, show count, complete at criterion; reps < 0.4 s ignored; list fixed in data.
FR-35: Guided activities: instructions, illustration, timer and Done.
FR-36: Training results (count, duration, criterion met) saved per child and activity.
FR-37: Step rewards: 1 star for done, 1 star for finished with Done even if not done, bonus star for done on attempt 1; full level: star flight, particles, chime, celebration; not done after attempt 3 -> encouraging line, no negative sound/image.
FR-38: Task reward screen (stars, big celebration, sticker progress); sticker book and progress path; new sticker every 3 tasks.
FR-39: Effect levels full / calm / off; never > 3 flashes per second; never during instruction audio.
FR-40: Rewards only after verdict saved; never change levels; training stars follow the same rules per repetition set.
FR-41: All data encrypted on tablet with Keystore keys; frames encrypted in app-private storage; wrong key cannot open DB; backup disabled; no external data store.
FR-42: Only cropped frames + step question for object steps leave the tablet; no name, code or profile data.
FR-43: Consent text naming DeepSeek as receiver of cropped frames.
FR-44: Anonymised `results.csv` export with the defined per-attempt columns; names never exported.
FR-45: Evidence frames kept until the child's profile is deleted.
FR-46: Replay mode: import clips with file picker; one clip = one attempt; window = whole clip; Done implied; file name links to `eval/clips.csv`; re-score with new model or catalogue version.
FR-47: Blind review: rate attempts with verdict hidden; stored separately; never changes levels.
FR-48: Dashboard and evaluation script report per-attempt agreement, quadratic-weighted kappa on levels, cannot-tell rate, confusion matrix per step; not scored / cannot tell excluded from kappa.

### NonFunctional Requirements

NFR-1: Latency: local verdicts within 300 ms of window close; cloud within 8 s or cannot tell.
NFR-2: Performance: pose + hand at >= 15 fps on the chosen tablet (fallback: only the needed landmarker).
NFR-3: Security and privacy as FR-41, FR-42; API key never on the tablet.
NFR-4: Versioning and reproducibility: model ID and prompt version per attempt; catalogue and rules version per session; results reproducible from facts.
NFR-5: Offline tolerance: everything except object-step judging works offline; network loss never lowers a level.
NFR-6: Sensory safety: FR-39 limits in every mode.
NFR-7: Quality: >= 90% line coverage for RulesEngine, Geometry, RepCounter.
NFR-8: Localisation: RTL; all child-facing text and audio in Egyptian Arabic.

### Additional Requirements

- No starter template: greenfield one-module Android app (`com.aitherapist.dls`, minSdk 30), Cloudflare Worker, Python tools; the MediaPipe pose and hand samples (mediapipe-samples) are adapted for camera and landmarkers (open-source reuse; credits in README/About).
- AD-1 content-as-data: build order audio-gen -> export_illustrations -> catalog-builder; no step/activity ID, threshold or question literal in Kotlin outside tests.
- AD-2 ID formats; `catalogVersion` in `assets/catalog/version.json`; `RULES_VERSION` constant; `promptVersion` owned by proxy.
- AD-3 single `Verifier` contract; verifiers never throw; VisibilityGate first; landmark pairing within 100 ms.
- AD-4 sealed `Check` (8 types) and `Criterion` (reps|hold); JSON Schema in `tools/catalog-builder/schema/`.
- AD-5 pure RulesEngine (`stepLevel`, `domainAverages`, `planLevel`, `activitiesFor`, `strengthsAndNeeds`, `compare`); only finished assessment sessions feed reports.
- AD-6 attempt-row semantics; runner advances via `stepLevel`.
- AD-7 fixed proxy contract (`POST /verify`, 401/413/502, stateless, `MODEL_ID` default `deepseek-flash`, JSON mode, schema validation).
- AD-8 Room + SQLCipher, Keystore key `dls_master`, DataStore `SecureSettings`, `FrameStore` encrypted frames, `exportSchema = true`, explicit migrations, `allowBackup="false"`, hidden eval child for replay.
- AD-9 one writer per facts table.
- AD-10 runner state machine with persisted resume point, injected clock; Bluetooth key -> Done.
- AD-11 single `RewardEffects` entry point; `CharacterView(variant, state)` states `idle, talk, point, celebrate, celebrateSmall, celebrateBig, encourage` (boy/girl pose images, Compose motion).
- AD-12 JVM tests for core, instrumented tests for Keystore/SQLCipher/migrations, MockWebServer, Vitest, Kover >= 90%, Konsist dependency test.
- Secrets: `DEEPSEEK_API_KEY`, `SHARED_SECRET` as Worker secrets; `ELEVENLABS_API_KEY` env var only; monthly DeepSeek spend limit.
- Logging: no child name, code, frame or request body in Logcat or Worker logs.
- M1 decisions to record: tablet model and fps, DeepSeek endpoint/model ID/image limits confirmed from official docs, pinned library versions.

### UX Design Requirements

UX-DR1: Design tokens for child palette (sage Done #5E9C8A, sky Help #7FA7C9, lavender Repeat #B9A6D3, star #E8C26A, sand, child-ink #2E3A40, warm off-white canvas; no red/orange in child mode) and therapist palette (t-primary, level-3/2/1/not-scored ramp, corrected purple); Cairo font bundled; type scale child-instruction 40sp, child-button 32sp, child-count 96sp, therapist 28/20/16/14sp; 8dp grid; shapes rounded.lg 28dp / md / sm; light theme only.
UX-DR2: Child step screen fixed zones: 64dp top band (star bar start, schedule centre, adult corner end), illustration ~60% width with character beside, sentence below, bottom band Done at start, Help/Repeat at end; 32dp margins; targets >= 120dp; Done >= 220x160dp; RTL mirrored.
UX-DR3: Done area: tap or Bluetooth key, debounced 1 s, dimmed (not hidden) while instruction audio plays; labels خلصت / ساعدني / تاني with icons.
UX-DR4: Prompt ladder presentation: L1 talk; L2 audio replay + point + illustration +15%; L3 full-screen illustration + movement animation + slow audio, buttons still visible.
UX-DR5: Step schedule dots (done light sage, current larger sage, upcoming sand; no outcome marking) and star bar (only increases).
UX-DR6: Character state mapping: idle default, talk during audio, point at L2, celebrateSmall on step reward, encourage after not done at attempt 3, celebrateBig on task reward; effect off keeps idle/talk/point only.
UX-DR7: Child state patterns: judging shows idle "thinking" pose with soft dots, no text; cannot tell and network loss invisible to the child (neutral retry line); paused = character sits, screen dims.
UX-DR8: Task start screen (character greets, task picture, يلا نبدأ) and adult pause sheet via 2 s long-press on adult corner (pause / resume / exit with PIN).
UX-DR9: Framing outline: body silhouette over preview, grey until landmarks visible, then sage; camera preview never shown during steps.
UX-DR10: Rewards table per effect level (full: star flight + Konfetti + chime; calm: slow fade + soft sound; off: counter only), task reward screen with 3-slot sticker path, sticker book grid with soft-outline locked slots (no lock icons).
UX-DR11: Training counter: big count and target, sage progress ring; hold activities show filling ring.
UX-DR12: Therapist shell: start-side navigation rail (Children, Replay & evaluation, Diagnostics, Settings), list-detail panes, cards with 1dp border, modal depth one, empty states with single message + primary action, network-loss banner, "Continue" card.
UX-DR13: Level chip (number + word; corrected shows struck automatic level and corrected level in purple, "reviewed"), evidence frame strip (3 thumbnails per attempt, tap to enlarge), Correct sheet (3/2/1/not scored, shows automatic level).
UX-DR14: Domain chart: Vico bars of domain averages with plan-level colour bands and step-count labels; "No scored steps" when empty; colour never the only signal; TalkBack labels on chips and charts.
UX-DR15: Activity picker: checklist grouped by domain then plan level; each activity expands to tools, how to perform, goal, criterion verbatim.
UX-DR16: Voice and tone: child copy never says wrong/no/try harder, shows no scores, levels or countdowns; therapist errors say what happened and what to do; report uses "may be related to".
UX-DR17: Landscape-only, portrait locked, 8-11 inch tablets, medium/expanded width classes; Material 3 AA contrast in therapist mode.

### FR Coverage Map

FR-1: Epic 2 - first-run setup (2.10); diagnostics reachability in Epic 1 (1.5) and Epic 5 (5.5)
FR-2: Epic 2 - PIN gate (2.10)
FR-3: Epic 2 - screen pinning, adult pause (2.14, 2.15)
FR-4: Epic 3 - zones and framing check (3.3, 3.4); un-mirroring in Epic 1 (1.2)
FR-5: Epic 2 - profiles and cascade delete (2.11)
FR-6: Epic 2 - effect level setting (2.11); applied in Epic 4 (4.8)
FR-7: Epic 2 - catalogue builder (2.1, 2.2, 2.7)
FR-8: Epic 2 - catalogue version (2.7)
FR-9: Epic 2 - step content and audio (2.4, 2.5, 2.6, 2.14, 2.17)
FR-10: Epic 2 - start assessment (2.16)
FR-11: Epic 2 - step screen (2.14)
FR-12: Epic 2 - prompt ladder (2.12, 2.14)
FR-13: Epic 2 - attempt window (2.12)
FR-14: Epic 2 runner (2.13); Epic 3 visibility/verifier wiring (3.5)
FR-15: Epic 2 - pause, resume, continue (2.15)
FR-16: Epic 3 - evidence frames (3.5)
FR-17: Epic 2 - walkthrough (2.16)
FR-18: Epic 1 (1.3) and Epic 3 (3.1, 3.2, 3.5)
FR-19: Epic 1 (1.4, 1.5) and Epic 3 (3.5)
FR-20: Epic 1 (1.4, 1.5) and Epic 3 (3.6)
FR-21: Epic 2 (2.13) and Epic 3 (3.5)
FR-22: Epic 2 - content-only tasks (2.16)
FR-23: Epic 2 stepLevel (2.13); corrections honoured in Epic 4 (4.1)
FR-24: Epic 4 - Rule 2 (4.1)
FR-25: Epic 4 (4.1, 4.2) and Epic 5 (5.2)
FR-26: Epic 5 - session list/detail (5.1)
FR-27: Epic 5 - corrections (5.2)
FR-28: Epic 5 - compare (5.3)
FR-29: Epic 1 fps probe (1.2); Epic 5 diagnostics screen (5.5)
FR-30: Epic 5 - training history (5.4)
FR-31: Epic 4 - report (4.2)
FR-32: Epic 4 - plan (4.3)
FR-33: Epic 4 - activity picker (4.3)
FR-34: Epic 4 (4.4, 4.5, 4.6)
FR-35: Epic 4 (4.6)
FR-36: Epic 4 (4.6)
FR-37: Epic 4 (4.7, 4.8)
FR-38: Epic 4 (4.9)
FR-39: Epic 4 (4.8)
FR-40: Epic 4 (4.7)
FR-41: Epic 2 (2.9)
FR-42: Epic 1 (1.4, 1.5) and Epic 3 (3.5)
FR-43: Epic 2 (2.10)
FR-44: Epic 5 (5.6)
FR-45: Epic 2 (2.9, 2.11)
FR-46: Epic 3 (3.7)
FR-47: Epic 5 (5.7)
FR-48: Epic 5 (5.8, 5.9)

## Epic List

### Epic 1: Camera and online-model feasibility on the tablet (M1)
The team can prove on the chosen tablet that pose and hand landmarks run at >= 15 fps, that a body step is judged locally, and that an object step gets a verdict from DeepSeek through the stateless proxy in under 8 s. Retires the two biggest risks first.
**FRs covered:** FR-18, FR-19, FR-20, FR-42 (first slice), FR-4 (un-mirroring), FR-29 (fps probe), FR-1 (reachability probe)

### Epic 2: Every task runs for a child, safely stored (M2)
The therapist can set up the tablet, add children, and run any of the 15 tasks / 147 steps in locked child mode with illustration, Egyptian Arabic text and audio; every fact is saved encrypted and an interrupted session can continue. Includes the art and audio production pipeline.
**FRs covered:** FR-1, FR-2, FR-3, FR-5, FR-6, FR-7, FR-8, FR-9, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, FR-17, FR-21, FR-22, FR-23, FR-41, FR-43, FR-45

### Epic 3: The app scores the four camera-scored tasks by itself (M3)
During an assessment the app judges all 41 camera-scored steps (6 local, 30 cloud, 5 none) with framing check, zones and evidence frames; network loss becomes "cannot tell"; volunteer clips can be replayed through the same runner and first accuracy is measured.
**FRs covered:** FR-4, FR-14, FR-16, FR-18, FR-19, FR-20, FR-21, FR-42, FR-46

### Epic 4: Results, plan, training and rewards (M4)
The therapist sees a functional report and treatment plan computed by the pure rules engine, picks activities, and the child trains with camera-counted and guided activities; children receive calm stars, stickers and effects that never touch scoring.
**FRs covered:** FR-6, FR-23, FR-24, FR-25, FR-31, FR-32, FR-33, FR-34, FR-35, FR-36, FR-37, FR-38, FR-39, FR-40

### Epic 5: Therapist review, export and measured accuracy (M5)
The therapist reviews every session with evidence, corrects levels, compares assessments, sees diagnostics and training history, exports anonymised CSV, rates clips blind, and the team measures Goal 8 (agreement >= 80%, kappa >= 0.6, cannot tell <= 15%).
**FRs covered:** FR-25, FR-26, FR-27, FR-28, FR-29, FR-30, FR-44, FR-47, FR-48

## Epic 1: Camera and online-model feasibility on the tablet (M1)

Prove the riskiest technology on real hardware before content work starts. Exit: Pose and Hand at >= 15 fps on the tablet; one cloud verdict returned in under 8 s (design M1 exit).

### Story 1.1: Android project skeleton with quality gates

As the developer,
I want a buildable one-module Android app with pinned versions and the verification gates in place,
So that every later story lands in a consistent, tested project.

Implements: AD-8 (manifest flags), AD-12, AD-13; NFR-7, NFR-8, UX-DR17.
Files: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `<pkg>/MainActivity.kt`, `<pkg>/ui/AppNavigation.kt`, `<test>/ArchitectureTest.kt`.

**Acceptance Criteria:**

**Given** a clean checkout
**When** `./gradlew assembleDebug test lint ktlintCheck` runs in `android/`
**Then** all succeed and the APK has package `com.aitherapist.dls`, minSdk 30
**And** the manifest declares camera and internet permissions, `allowBackup="false"`, `supportsRtl="true"`, landscape orientation, one activity.

**Given** the version catalogue
**When** it is inspected
**Then** Kotlin, Compose BOM, CameraX, MediaPipe Tasks Vision (latest stable), Room + KSP, SQLCipher 4.x (>= 4.18), kotlinx-serialization, OkHttp, Vico, Konfetti, Kover, ktlint, Konsist, Material 3 + adaptive (navigation suite, navigation3), Navigation 3, Hilt, Lifecycle Compose, Coil 3 and compose-shimmer are pinned to exact versions (AD-13).

**Given** the `ui/components` package
**When** the project builds
**Then** shared `EmptyState`, `ErrorState` and a `Skeleton` helper exist with `@Preview`s, and `AppNavigation.kt` uses Navigation 3 with a typed key and a Hilt-injected sample ViewModel exposing a sealed `UiState` via `stateIn(WhileSubscribed(5_000))`.

**Given** the Konsist test encoding the spine's package dependency diagram
**When** a class in `scoring` imports `android.*` or any package depends on `ui`
**Then** `./gradlew test` fails.

**Given** the app build
**When** the Gradle task that downloads MediaPipe pose and hand model files runs
**Then** the models are present in assets (as in mediapipe-samples).

### Story 1.2: Camera feed and landmarkers with frame-rate probe

As the developer,
I want the front camera feeding MediaPipe Pose and Hand landmarkers with un-mirrored output and a live fps readout,
So that we can decide the tablet and the fallback (NFR-2) with numbers.

Implements: FR-4 (un-mirroring), FR-29 (fps measure), NFR-2; AD-3 (landmark pairing), AD-10 (injected clock not yet needed).
Files: `<pkg>/camera/CameraController.kt`, `<pkg>/camera/FrameBuffer.kt`, `<pkg>/vision/Landmarkers.kt`, `<pkg>/ui/therapist/DiagnosticsScreen.kt` (minimal fps panel), `docs/designs/m1-spike-results.md`.

**Acceptance Criteria:**

**Given** the app on the candidate tablet with camera permission granted
**When** the diagnostics panel runs pose + hand in LIVE_STREAM mode for 60 s
**Then** it shows the measured fps of each landmarker and combined
**And** the result (tablet model, fps, combined >= 15 or fallback chosen) is written to `docs/designs/m1-spike-results.md`.

**Given** a person raising the right hand in front of the front camera
**When** landmarks are produced
**Then** the raised wrist is reported as `RIGHT_WRIST` (image un-mirrored before any left/right logic).

**Given** `FrameBuffer` holding frames and landmark results with timestamps
**When** a frame has no landmark result within 100 ms
**Then** that frame is marked not visible (AD-3).

**Given** code adapted from mediapipe-samples
**When** the files are inspected
**Then** each carries the upstream license header and the credit is listed for the README.

### Story 1.3: Geometry, Check model and first local verdict

As the developer,
I want pure geometry, the sealed `Check` type, `VisibilityGate` and one `PoseVerifier` check,
So that a body step (`drink.cup.05`, `distanceBelow`) gets a verdict on the tablet within 300 ms.

Implements: FR-18, NFR-1, NFR-7; AD-3, AD-4.
Files: `<pkg>/vision/Geometry.kt`, `<pkg>/catalog/CatalogModels.kt` (Check, Criterion, Step, Detector), `<pkg>/vision/Verifier.kt`, `<pkg>/vision/VisibilityGate.kt`, `<pkg>/vision/PoseVerifier.kt`, `tools/catalog-builder/schema/check.schema.json`, `<test>/GeometryTest.kt`, `<test>/CheckEvaluationTest.kt`.

**Acceptance Criteria:**

**Given** the JSON `{"type":"distanceBelow","a":{"lm":"WRIST"},"side":"either","b":{"mid":["MOUTH_LEFT","MOUTH_RIGHT"]},"ratio":0.6,"holdSec":0.3}`
**When** it is decoded with kotlinx-serialization (discriminator `type`)
**Then** it yields a `Check.DistanceBelow`; an unknown `type` fails decoding.

**Given** synthetic landmark series where the wrist stays within 0.6 shoulder widths of the mouth midpoint for 0.3 s continuously
**When** `PoseVerifier.verify` runs
**Then** outcome is `DONE`; for 0.2 s it is `NOT_DONE`.

**Given** frames where needed landmarks are not visible
**When** verification runs
**Then** `VisibilityGate` returns `CANNOT_TELL` with reason `out_of_frame` and the check is not evaluated.

**Given** any exception inside a verifier
**When** `verify` is called
**Then** it returns `CANNOT_TELL` and never throws (AD-3).

**Given** the tablet
**When** a live attempt of drink.cup.05 closes
**Then** the verdict is available within 300 ms (latency logged)
**And** `GeometryTest` covers angle, distance-ratio and midpoint math with >= 90% line coverage on `Geometry`.

### Story 1.4: Stateless verify proxy on Cloudflare Workers

As the developer,
I want the `POST /verify` Worker that calls DeepSeek and returns a validated verdict,
So that object steps can be judged without the API key ever reaching the tablet.

Implements: FR-19, FR-20, FR-42, NFR-3; AD-2 (promptVersion), AD-7.
Files: `proxy/package.json`, `proxy/package-lock.json`, `proxy/wrangler.toml`, `proxy/tsconfig.json`, `proxy/src/index.ts`, `proxy/src/prompt.ts`, `proxy/test/index.test.ts`.

**Acceptance Criteria:**

**Given** a request without the correct `Authorization: Bearer <SHARED_SECRET>`
**When** it hits `/verify`
**Then** the response is 401 and DeepSeek is not called.

**Given** a body with 4 images or larger than 3 MB
**When** it hits `/verify`
**Then** the response is 413.

**Given** a valid request and a mocked `openai` SDK (baseURL DeepSeek, model from `MODEL_ID`, default `deepseek-flash`, JSON output mode, 3 image content parts)
**When** the model answers valid JSON
**Then** the response is 200 `{verdict, reason, modelId, promptVersion}` with `modelId` as returned by DeepSeek.

**Given** a refusal or JSON not matching the verdict schema
**When** it is returned by the model
**Then** the proxy returns 200 with `verdict: "cannot_tell"`, `reason: "invalid_model_output"`; never `not_done`.

**Given** the DeepSeek call fails (HTTP or network)
**When** the proxy handles it
**Then** it returns 502.

**Given** `wrangler.toml`
**When** inspected
**Then** there are no KV, D1, R2 or other storage bindings and no logging of bodies
**And** `npm test`, `npm run build`, `npx tsc --noEmit` pass.

**Given** DeepSeek's official documentation
**When** the Worker is deployed with secrets `DEEPSEEK_API_KEY` and `SHARED_SECRET` and a monthly spend limit is set
**Then** the confirmed endpoint, model ID and image limits are recorded in `docs/designs/m1-spike-results.md`.

### Story 1.5: CloudVerifier and first cloud verdict for a cup step

As the developer,
I want the tablet-side `CloudVerifier` with crop, timeout and error mapping,
So that `drink.cup.06` returns a verdict through the proxy in under 8 s and every failure becomes "cannot tell".

Implements: FR-19, FR-20, FR-42, NFR-1, NFR-5, FR-1 (reachability probe); AD-3, AD-7.
Files: `<pkg>/vision/CloudVerifier.kt`, `<pkg>/vision/FrameCropper.kt`, `<test>/CloudVerifierTest.kt`, `docs/designs/m1-spike-results.md`.

**Acceptance Criteria:**

**Given** OkHttp MockWebServer
**When** it answers 200, 401, 413, 502, or delays beyond 8 s, or the connection drops
**Then** `CloudVerifier` returns respectively the parsed verdict, `CANNOT_TELL http_401`, `http_413`, `http_502`, `timeout`, `network`; never `NOT_DONE` except from a 200 `not_done`.

**Given** an attempt's frames
**When** the request is built
**Then** it contains a fresh UUID `requestId`, `stepId`, the question, and exactly 3 JPEGs (start, middle, end) cropped around hand landmarks and the object zone; no child name, code, profile field or session ID.

**Given** the deployed proxy and the tablet on Wi-Fi
**When** a spike screen runs drink.cup.06 with a real cup
**Then** a verdict with `modelId` and `promptVersion` returns in under 8 s and the latency is recorded in `m1-spike-results.md` (M1 exit).

## Epic 2: Every task runs for a child, safely stored (M2)

Exit: all 147 steps play with illustration, Arabic text and audio; builder tests pass (design M2 exit).

### Story 2.1: Catalogue builder parses the 147 assessment steps

As the developer,
I want `build_catalog.py` to parse the assessment scales into steps with permanent IDs and tags,
So that the app's steps are exactly those in `docs/reference/`.

Implements: FR-7; AD-1, AD-2.
Files: `tools/catalog-builder/build_catalog.py`, `tools/catalog-builder/test_build_catalog.py`, `tools/requirements.txt`, `pyproject.toml` (ruff config).

**Acceptance Criteria:**

**Given** the scale tables in `docs/reference/*-assessment-scale.md` (columns Step No., Step, Functional domain, Functional sub-skills)
**When** the parser runs
**Then** it yields 147 steps over 15 tasks with counts eating 42, drinking 38, hygiene 27, dressing 40, and IDs `<area>.<task>.<NN>` using the spine's task IDs.

**Given** a domain cell "A + B"
**When** parsed
**Then** tags are `[A, B]` copied verbatim; "Not tagged in source" yields `[]`.

**Given** a tag not among the six domain tags, or a duplicate ID
**When** the build runs
**Then** it exits non-zero with a message naming the row
**And** `pytest tools/` and `ruff check tools` pass.

### Story 2.2: Catalogue builder parses the 148 activities

As the developer,
I want treatment-plan activities parsed by the design's activity parsing rule,
So that plans list exactly the source activities.

Implements: FR-7, FR-32 (data); AD-1, AD-2.
Files: `tools/catalog-builder/build_catalog.py`, `tools/catalog-builder/test_build_catalog.py`.

**Acceptance Criteria:**

**Given** `treatment-plans.md`
**When** parsed
**Then** level-2 headings starting `1.`/`2.`/`3.` set plan mild/moderate/severe, every `#####` heading is one activity, 148 in total (48/50/50)
**And** each activity's domain comes from the nearest preceding level-3/4 heading containing a domain name (covering the mild range-of-motion level-4 heading).

**Given** an activity
**When** parsed
**Then** it has ID `<plan>.<domain>.<NN>` and verbatim fields tools, how to perform, goal, measurement criterion.

### Story 2.3: Boy and girl characters with animated poses

As the author,
I want two original characters (boy and girl) with model sheets and transparent pose images, animated in Compose,
So that every screen and illustration shares one polished look and the therapist can pick the character per child.

Implements: FR-9, FR-11, FR-12 (level-3 movement), UX-DR6; AD-11 (updated 2026-11-12: pose images + Compose, no Rive).
Files: `art/characters/{boy,girl}-sheet.png`, `art/poses/<variant>/<state>.png`, `art/README.md`, `android/app/src/main/assets/character/<variant>/<state>.webp`, `<pkg>/ui/components/CharacterView.kt`, `<test>/CharacterStateTest.kt`.

**Acceptance Criteria:**

**Given** the model sheets
**When** reviewed
**Then** each character shows front, 3/4, side and back views, hands and five expressions in the DESIGN palette (done: `035db9d`).

**Given** each variant
**When** the pose set is inspected
**Then** there is one transparent image per state `idle, talk, point, celebrate, celebrateSmall, celebrateBig, encourage` plus `talk-open` and `blink`, identical in character, scale and framing.

**Given** `CharacterView(variant, state)`
**When** the state changes or audio plays
**Then** poses crossfade ≤ 300 ms, idle breathes and blinks, talk toggles the mouth only while audio plays, celebrate hops gently, effect level `off` shows a still pose, and nothing flashes more than 3 times per second.

**Given** any generated image
**When** recorded
**Then** the prompt and tool are noted in `art/README.md`, and no third-party character or brand is used.

### Story 2.4: Step and activity illustrations with export script

As the author,
I want one corrected illustration per step and activity, exported to the app,
So that every step shows the character doing exactly the step text.

Implements: FR-9, FR-35 (activity illustration), SM-4; AD-1.
Files: `art/steps/<stepId>.png` (147), `art/activities/<activityId>.png` (148), `tools/art/export_illustrations.py`, `tools/art/test_export_illustrations.py`, `android/app/src/main/assets/illustr/*.webp`, `android/app/src/main/assets/character/<variant>/*.webp`.

**Acceptance Criteria:**

**Given** the 41 camera-scored steps
**When** illustrations are produced
**Then** they are finished first, and each is checked by the team against the step text (action matches exactly) with the check recorded in `art/review.csv`.

**Given** all PNGs
**When** `python tools/art/export_illustrations.py` runs
**Then** each is resized to 1280 px WebP into `assets/illustr/<variant>/<id>.webp` and the pose PNGs into `assets/character/<variant>/<state>.webp`
**And** a missing or misnamed file is reported with its ID.

### Story 2.5: Arabic strings and ElevenLabs audio generation

As the author,
I want one Egyptian Arabic sentence per step and activity and generated normal and slow audio,
So that every step can be spoken to the child.

Implements: FR-9, NFR-8, UX-DR16; AD-1.
Files: `tools/catalog-builder/strings.ar-eg.json`, `tools/audio-gen/generate_audio.py`, `tools/audio-gen/config.json`, `tools/audio-gen/manifest.json`, `tools/audio-gen/requirements.txt`, `tools/audio-gen/test_generate_audio.py`, `tools/audio-gen/review.csv`, `android/app/src/main/assets/audio/ar-eg/`.

**Acceptance Criteria:**

**Given** `strings.ar-eg.json`
**When** reviewed
**Then** every step ID has one Egyptian Arabic sentence translating the source step (no added clinical content), reviewed by the author; activity sentences likewise; UI labels from EXPERIENCE.md are included.

**Given** `ELEVENLABS_API_KEY` set in the environment and a fixed voice ID and model ID in `config.json`
**When** `generate_audio.py` runs
**Then** it writes `<id>.mp3` and `<id>.slow.mp3` (lower speaking rate, not slowed playback) for every step and records text hashes in `manifest.json`.

**Given** a second run with unchanged text
**When** it runs
**Then** no API call is made for those IDs (unit-tested with a mocked SDK)
**And** `review.csv` lists each file as approved or regenerated; the key is never written to any file.

### Story 2.6: Stickers and sound effects

As the author,
I want the sticker set and the three reward sounds,
So that rewards can be built in Epic 4.

Implements: FR-37, FR-38, FR-39 (sound assets).
Files: `art/stickers/*.png` (>= 30), `art/sfx/star_chime.mp3`, `art/sfx/task_fanfare.mp3`, `art/sfx/calm_soft.mp3`, `tools/art/export_illustrations.py` (copy stickers and sfx to assets).

**Acceptance Criteria:**

**Given** the sticker folder
**When** exported
**Then** at least 30 stickers in the character style are in `assets/stickers/` as WebP.

**Given** the sfx generated once with ElevenLabs sound effects
**When** reviewed
**Then** none is harsh or negative-sounding, and each is under 2 s; files are in `assets/sfx/`.

### Story 2.7: Catalogue validation, merge and version stamp

As the developer,
I want the builder to merge detector defaults, strings and asset checks, and stamp `catalogVersion`,
So that a broken or incomplete catalogue never ships.

Implements: FR-7, FR-8, NFR-4; AD-1, AD-2, AD-4.
Files: `tools/catalog-builder/build_catalog.py`, `tools/catalog-builder/detectors.json` (all steps default `none`, structure only), `tools/catalog-builder/activity_counters.json` (empty list), `tools/catalog-builder/schema/criterion.schema.json`, `tools/catalog-builder/test_build_catalog.py`, `android/app/src/main/assets/catalog/assessment.json`, `activities.json`, `version.json`.

**Acceptance Criteria:**

**Given** all inputs present
**When** `python tools/catalog-builder/build_catalog.py` runs
**Then** it exits 0 and writes `assessment.json`, `activities.json` and `version.json` with `catalogVersion` = first 12 hex of SHA-256 over the two files' bytes.

**Given** a step without Arabic text, normal or slow audio, or illustration, or an activity without text or illustration
**When** the build runs
**Then** it fails naming the ID; missing activity audio does not fail.

**Given** a `detectors.json` entry whose check fails the JSON Schema
**When** the build runs
**Then** it fails.

**Given** any single change to a step, tag, detector setting or activity
**When** rebuilt
**Then** `catalogVersion` changes (test).

### Story 2.8: Catalogue repository in the app

As the developer,
I want the app to load the catalogue JSON into typed models,
So that runners and screens never hard-code content.

Implements: FR-7, FR-9; AD-1, AD-4.
Files: `<pkg>/catalog/CatalogModels.kt`, `<pkg>/catalog/CatalogRepository.kt`, `<test>/CatalogRepositoryTest.kt`.

**Acceptance Criteria:**

**Given** the built assets
**When** `CatalogRepository` loads
**Then** it exposes 15 tasks, 147 steps in order, 148 activities and `catalogVersion`, with each step's `Detector.landmarkers`.

**Given** a catalogue with an unknown check `type`
**When** loaded
**Then** loading fails with a clear error (no silent skip).

**Given** the Kotlin sources outside tests
**When** scanned by a test
**Then** no step ID or activity ID literal is found.

### Story 2.9: Encrypted database, secure settings and frame store

As a guardian,
I want all child data and frames encrypted on the tablet,
So that nothing readable exists outside the app.

Implements: FR-41, FR-45, NFR-3; AD-8, AD-9.
Files: `<pkg>/data/AppDatabase.kt`, `<pkg>/data/Entities.kt` (child, session, attempt), `<pkg>/data/Daos.kt`, `<pkg>/data/Converters.kt`, `<pkg>/data/Repositories.kt`, `<pkg>/data/SecureSettings.kt`, `<pkg>/data/FrameStore.kt`, `android/app/schemas/`, `<itest>/DatabaseTest.kt`, `<itest>/SecureSettingsTest.kt`, `<itest>/FrameStoreTest.kt`.

**Acceptance Criteria:**

**Given** first run
**When** the database opens
**Then** a 32-byte passphrase is generated, encrypted with Keystore AES-256-GCM key `dls_master` (non-exportable) and stored in DataStore; Room opens via SQLCipher `SupportOpenHelperFactory`.

**Given** the database file
**When** opened with a wrong key
**Then** it fails (instrumented test).

**Given** tables `child`, `session`, `attempt` (columns per design Data model plus nullable `attempt.clipFile`, `child.isEval`)
**When** schema is exported
**Then** version 1 JSON is committed under `android/app/schemas/`; destructive migration is not enabled.

**Given** `FrameStore.write(sessionId, attemptId, n, jpeg)`
**When** called
**Then** the file at `files/frames/<sessionId>/<attemptId>-<n>.bin` is `IV(12) || AES-GCM(JPEG)` and reads back identical; `SecureSettings` round-trips PIN hash, proxy URL, encrypted secret and zones.

**Given** enums
**When** stored
**Then** they serialise as lower_snake (`not_done`, `cannot_tell`).

### Story 2.10: First-run setup, PIN gate and consent

As a therapist,
I want to set a PIN, enter the proxy address and secret, and acknowledge consent text once,
So that the tablet is ready and therapist mode is protected.

Implements: FR-1, FR-2, FR-43, UX-DR1, UX-DR12, UX-DR17; AD-8.
Files: `<pkg>/ui/theme/Theme.kt`, `<pkg>/ui/theme/Tokens.kt`, `android/app/src/main/res/font/cairo_*.ttf`, `<pkg>/ui/therapist/SetupScreen.kt`, `<pkg>/ui/therapist/PinScreen.kt`, `<pkg>/ui/therapist/SettingsScreen.kt`, `<pkg>/ui/therapist/TherapistShell.kt`, `<pkg>/ui/AppNavigation.kt`, `<pkg>/data/SecureSettings.kt`, `<test>/PinHashTest.kt`.

**Acceptance Criteria:**

**Given** a fresh install
**When** the app starts
**Then** setup opens: PIN entered twice, proxy URL and secret, consent text naming DeepSeek as receiver of cropped frames (must be acknowledged), then the empty child list; therapist mode is unreachable before a PIN is set.

**Given** a saved secret
**When** settings are viewed
**Then** the secret is masked and never shown in clear.

**Given** the PIN screen
**When** a wrong PIN is entered
**Then** therapist mode does not open; the PIN is stored only as PBKDF2 hash with salt (unit test).

**Given** Settings
**When** the therapist enters the current PIN and a new one twice
**Then** the PIN changes.

**Given** the therapist shell
**When** shown
**Then** it is RTL with a start-side rail (Children, Replay & evaluation, Diagnostics, Settings), Cairo font and DESIGN tokens.

[ASSUMPTION per PRD FR-43] consent acknowledgement is recorded at setup and per child profile (Story 2.11).

### Story 2.11: Child profiles with effect level and cascade delete

As a therapist,
I want to add, edit and delete children with an effect level,
So that sessions belong to a child and deletion removes everything.

Implements: FR-5, FR-6, FR-43, FR-45; AD-8, AD-9.
Files: `<pkg>/ui/therapist/ChildListScreen.kt`, `<pkg>/ui/therapist/ChildProfileScreen.kt`, `<pkg>/data/Repositories.kt`, `<pkg>/data/Entities.kt`, `<itest>/ChildDeleteTest.kt`.

**Acceptance Criteria:**

**Given** the add form
**When** name, code or age is missing, or the code exists
**Then** save is refused with a message; otherwise the child appears in the list (searchable by name/code), effect level default `calm`.

**Given** a profile
**When** the effect level is set to full/calm/off
**Then** it persists.

**Given** a child with sessions, attempts and frame files
**When** the child is deleted after confirmation
**Then** all child-owned rows cascade and `FrameStore` deletes `files/frames/<sessionId>/` folders in the same operation (instrumented test); frames otherwise persist until deletion.

**Given** no children
**When** the list shows
**Then** an empty state with "Add child" appears; the hidden eval child is never listed.

### Story 2.12: Attempt window and prompt ladder (pure)

As the developer,
I want the window-close rules, movement test and prompt ladder as pure functions with an injected clock,
So that timing is testable and identical in live and replay.

Implements: FR-12, FR-13; AD-10.
Files: `<pkg>/session/AttemptWindow.kt`, `<pkg>/session/PromptLadder.kt`, `<test>/AttemptWindowTest.kt`, `<test>/PromptLadderTest.kt`.

**Acceptance Criteria:**

**Given** a fake clock and landmark series
**When** Done is pressed / 20 s elapsed with no movement for the last 2 s / 45 s reached
**Then** each closes the window independently with its reason; movement at 25 s keeps it open until 2 s still.

**Given** wrist/elbow displacement over 0.5 s of exactly 0.03 and 0.031 x shoulder width
**When** evaluated
**Then** the first is not movement, the second is.

**Given** prompt level 1 and Help
**When** applied
**Then** level becomes 2, `helpPressed` true, attempt number unchanged; Help at 3 stays 3; Repeat changes nothing.

### Story 2.13: Assessment runner state machine with Rule 1

As the developer,
I want `AssessmentRunner` driving SHOW -> LISTEN -> ATTEMPT -> JUDGE and saving each attempt fact,
So that steps advance by Rule 1 and nothing is lost.

Implements: FR-14, FR-21, FR-23; AD-5 (`stepLevel`), AD-6, AD-9, AD-10, NFR-4.
Files: `<pkg>/session/AssessmentRunner.kt`, `<pkg>/session/RunnerState.kt`, `<pkg>/scoring/RulesEngine.kt` (`RULES_VERSION`, `stepLevel`), `<pkg>/vision/NoneVerifier.kt`, `<test>/AssessmentRunnerTest.kt`, `<test>/RulesEngineTest.kt`.

**Acceptance Criteria:**

**Given** a fake verifier returning done on attempt 1 at prompt 1
**When** the step runs
**Then** one attempt row is saved and `stepLevel` = 3; done on attempt 2 or 3 (or after Help on 1) = 2; three not_done numbers = 1.

**Given** cannot_tell then cannot_tell on the same number
**When** saved
**Then** two rows share the number and the step is not scored; the task moves on.

**Given** a step with method `none`
**When** Done is pressed
**Then** a row with verifier `none`, outcome `cannot_tell`, reason `not_judgeable` is saved and the step is not scored.

**Given** any transition
**When** the next state is emitted
**Then** `session.stepIndex/attemptNumber/promptLevel` were saved first; the runner advances only by calling `stepLevel` on saved rows.

**Given** each attempt row
**When** inspected
**Then** it has number, promptLevel, helpPressed, verdict, reason, verifier, modelId, promptVersion, latencyMs, framePaths, createdAt; the session has catalogVersion and rulesVersion.

### Story 2.14: Child step screen with audio, prompts and locked mode

As a child,
I want one calm step screen with picture, sentence, audio and big buttons,
So that I always know what to do next.

Implements: FR-3, FR-9, FR-11, FR-12, NFR-8, UX-DR2, UX-DR3, UX-DR4, UX-DR5, UX-DR6, UX-DR7, UX-DR16.
Files: `<pkg>/audio/AudioPlayer.kt`, `<pkg>/ui/child/StepScreen.kt`, `<pkg>/ui/child/CharacterView.kt`, `<pkg>/ui/child/StepSchedule.kt`, `<pkg>/ui/child/StarBar.kt` (display only), `<pkg>/ui/child/ChildModeHost.kt`, `<pkg>/MainActivity.kt` (key events, screen pinning).

**Acceptance Criteria:**

**Given** a step at prompt level 1
**When** shown
**Then** the fixed zones appear (illustration, character `talk`, sentence, Done, Help, Repeat) and the audio plays; Done is dimmed until `AudioEnded`.

**Given** prompt level 2 or 3
**When** shown
**Then** level 2 replays audio, character `point`, illustration +15%; level 3 shows the illustration full screen, the movement animation where it exists and the `.slow.mp3` audio, buttons still visible.

**Given** a paired Bluetooth HID button
**When** any key is pressed during an open window
**Then** the runner receives `Done` (debounced 1 s).

**Given** child mode starts
**When** the child tries to leave
**Then** screen pinning prevents it; no error, cannot-tell or network state is visible to the child.

**Given** the child UI
**When** inspected
**Then** no red/orange, no scores, levels or countdowns; child targets >= 120dp, Done >= 220x160dp.

### Story 2.15: Pause, resume and continue after interruption

As a therapist,
I want to pause safely and continue an interrupted session,
So that no progress is lost and no half attempt is scored.

Implements: FR-3 (adult pause), FR-15, UX-DR8, UX-DR12; AD-8, AD-9, AD-10.
Files: `<pkg>/ui/child/AdultPauseSheet.kt`, `<pkg>/session/AssessmentRunner.kt`, `<pkg>/ui/therapist/ChildListScreen.kt` (Continue card), `<test>/AssessmentRunnerTest.kt`.

**Acceptance Criteria:**

**Given** a running attempt
**When** the adult long-presses the corner 2 s and picks Pause
**Then** the window freezes, the screen dims, character sits; Resume restarts the current attempt from its beginning.

**Given** the app killed mid-attempt
**When** reopened
**Then** the child list shows a "Continue" card; continuing restarts the saved attempt, and no frames of the interrupted attempt exist (frames are written only with the attempt row).

**Given** a child with a running or paused session
**When** a new assessment starts
**Then** the old session becomes `abandoned`.

**Given** Exit on the pause sheet
**When** chosen
**Then** the PIN is required.

### Story 2.16: Start assessment, content-only tasks and character walkthrough

As a therapist,
I want to start any of the 15 tasks for a child and hand over,
So that the child runs the whole task and sees the closing walkthrough.

Implements: FR-10, FR-17, FR-22, UX-DR8.
Files: `<pkg>/ui/therapist/NewSessionDialog.kt`, `<pkg>/ui/child/TaskPickerScreen.kt` (task start screen), `<pkg>/ui/child/WalkthroughScreen.kt`, `<pkg>/session/AssessmentRunner.kt`.

**Acceptance Criteria:**

**Given** a child profile
**When** New session -> a task is chosen
**Then** a session row with child, task, catalogVersion, rulesVersion, kind `assessment`, state `running` is created and child mode opens on the task start screen.

**Given** the jacket
**When** chosen
**Then** the therapist picks on, off, or both (both runs on then off as two sessions).

**Given** a content-only task
**When** run
**Then** every step plays and all are saved not scored; the session appears in lists.

**Given** the last step finished
**When** the task ends
**Then** the walkthrough shows one illustration per step with its audio, then the session becomes `done`.

### Story 2.17: M2 content completeness check

As the author,
I want an automated and a manual pass over all 147 steps,
So that SM-4 is demonstrated.

Implements: FR-9, SM-4.
Files: `<itest>/ContentCompletenessTest.kt`, `docs/designs/m2-content-check.md`.

**Acceptance Criteria:**

**Given** the installed APK
**When** the instrumented test iterates every step
**Then** each step's illustration, normal and slow audio load without error.

**Given** a manual run of all 15 tasks on the tablet
**When** completed
**Then** the checklist in `m2-content-check.md` records pass per task.

## Epic 3: The app scores the four camera-scored tasks by itself (M3)

Exit: all 41 steps scored on volunteer clips; deepseek-flash accuracy measured (design M3 exit).

### Story 3.1: All body Check evaluators

As the developer,
I want every `Check` subtype evaluated on synthetic landmarks,
So that any body step can be judged from data.

Implements: FR-18; AD-4.
Files: `<pkg>/vision/CheckEvaluator.kt`, `<pkg>/vision/PoseVerifier.kt`, `<test>/CheckEvaluationTest.kt`.

**Acceptance Criteria:**

**Given** synthetic landmarks
**When** each of `angleAbove`, `angleBelow`, `pointAbove`, `distanceBelow`, `distanceAbove`, `inZone` is evaluated
**Then** a passing and a failing series each give `DONE` / `NOT_DONE`, `side: either` passes if either side passes, `holdSec` requires continuity.

**Given** `inZone` with a missing zone
**When** evaluated
**Then** `CANNOT_TELL` reason `no_zone`.

### Story 3.2: Hand verifier and hands rubbing

As the developer,
I want `HandVerifier` with `handsRubbing`,
So that `hygiene.handwash.07` and `dress.jacket.off.02` are judged locally.

Implements: FR-18; AD-3, AD-4.
Files: `<pkg>/vision/HandVerifier.kt`, `<pkg>/vision/CheckEvaluator.kt`, `<test>/HandsRubbingTest.kt`.

**Acceptance Criteria:**

**Given** two hands within 0.6 wrist ratio and motion >= 0.05 for 2.0 s
**When** evaluated
**Then** `DONE`; still hands or 1.5 s gives `NOT_DONE`; one hand missing gives `CANNOT_TELL out_of_frame`.

### Story 3.3: Detector settings for the 41 steps and setup zones

As the developer,
I want `detectors.json` filled from the design's camera-scored steps table and zones drawable in setup,
So that each step uses its method, framing and question.

Implements: FR-4, FR-7, FR-8; AD-1, AD-4.
Files: `tools/catalog-builder/detectors.json`, `tools/catalog-builder/test_build_catalog.py`, `<pkg>/ui/therapist/ZoneEditor.kt`, `<pkg>/ui/therapist/SetupScreen.kt`, `<pkg>/ui/therapist/SettingsScreen.kt`.

**Acceptance Criteria:**

**Given** the built catalogue
**When** counted
**Then** the four camera-scored tasks have 41 steps: 5 none, 6 local (pose/hand), 30 cloud, with questions and checks verbatim from the design table.

**Given** the setup or settings preview
**When** the therapist draws mat, sink and place rectangles
**Then** they are stored normalised and un-mirrored in `SecureSettings`.

### Story 3.4: Framing check screen

As a therapist,
I want a pre-task preview that turns sage when the needed landmarks are visible,
So that the child is placed correctly before scoring.

Implements: FR-4, UX-DR9.
Files: `<pkg>/ui/child/FramingCheckScreen.kt`, `<pkg>/vision/VisibilityGate.kt`.

**Acceptance Criteria:**

**Given** a camera-scored task
**When** started
**Then** the framing check shows the preview with a silhouette outline, grey until the task's needed landmarks are visible, then sage; "Hand to child" starts pinning.

**Given** a content-only task
**When** started
**Then** no framing check appears; the preview is never shown during steps.

### Story 3.5: Live verifiers in the runner with evidence frames

As a therapist,
I want each camera-scored attempt judged at window close and its frames saved,
So that sessions are scored without my input and I can see the evidence.

Implements: FR-14, FR-16, FR-18, FR-19, FR-21, FR-42, NFR-1; AD-3, AD-6, AD-8, AD-10.
Files: `<pkg>/session/AssessmentRunner.kt`, `<pkg>/vision/VerifierRouter.kt`, `<pkg>/vision/CloudVerifier.kt`, `<pkg>/data/FrameStore.kt`, `<pkg>/data/SecureSettings.kt`, `<test>/AssessmentRunnerTest.kt`.

**Acceptance Criteria:**

**Given** a step with `method` pose/hand/cloud
**When** the window closes
**Then** VisibilityGate runs first, then the matching verifier; only landmarkers in `Detector.landmarkers` run.

**Given** a body step
**When** judged
**Then** no network request is made (test with a failing HTTP client).

**Given** a saved attempt
**When** inspected
**Then** 3 encrypted evidence frames (start/middle/end) are in FrameStore, their paths in `framePaths`, and cloud attempts store `modelId` and `promptVersion`.

**Given** the request log
**When** inspected
**Then** no child name, code or session ID appears in Logcat or request bodies.

### Story 3.6: Network loss becomes "cannot tell"

As a therapist,
I want Wi-Fi loss to never lower a level,
So that poor internet does not change results.

Implements: FR-20, NFR-5, UX-DR7, UX-DR12.
Files: `<pkg>/vision/CloudVerifier.kt`, `<pkg>/ui/therapist/TherapistShell.kt` (network banner), `<test>/AssessmentRunnerTest.kt`, `docs/designs/m3-field-tests.md`.

**Acceptance Criteria:**

**Given** a cloud step and Wi-Fi switched off mid-task on the tablet
**When** the attempt closes
**Then** it is saved `cannot_tell` reason `network` or `timeout`, the same attempt repeats once, the child sees only a neutral retry, and the task continues
**And** therapist mode shows a network banner; the test result is logged in `m3-field-tests.md`.

### Story 3.7: Replay mode through the same runner

As a researcher,
I want to import clips and score them exactly like live attempts,
So that accuracy can be measured and re-measured on new versions.

Implements: FR-46, NFR-4; AD-2, AD-6, AD-8, AD-9, AD-10.
Files: `<pkg>/replay/ReplaySource.kt`, `<pkg>/replay/ClipName.kt`, `<pkg>/ui/therapist/ReplayScreen.kt`, `<test>/ClipNameTest.kt`, `<test>/ReplaySourceTest.kt`.

**Acceptance Criteria:**

**Given** clips named `<stepId>__<attemptNumber>__<outcome>__<volunteer>.mp4`
**When** imported with the file picker
**Then** a `replay` session for the hidden eval child is created; each clip is one attempt in MediaPipe VIDEO mode, window = whole clip, Done implied; `number`/`promptLevel` come from the name and `clipFile` is saved.

**Given** a malformed file name
**When** imported
**Then** it is skipped with a message.

**Given** a new catalogue or model version
**When** "Re-score" runs
**Then** a new replay session is created with current versions; replay sessions never feed reports, plans or rewards.

### Story 3.8: Volunteer clip recording and manifest

As a researcher,
I want a scripted clip set for the 41 steps,
So that accuracy can be measured against known outcomes.

Implements: FR-46, FR-48 (data); SM-1..SM-3 preparation.
Files: `eval/recording-protocol.md`, `eval/clips.csv`, `eval/consent/` (signed volunteer consent, not committed if private; path recorded).

**Acceptance Criteria:**

**Given** the protocol
**When** volunteers record
**Then** each camera-scored step has clips for done on attempt 1, done later, and not done, under the physical setup in the design.

**Given** `eval/clips.csv`
**When** validated by a script check
**Then** every row has file, task, step, scripted outcome, and every file name matches the naming rule and a row.

[ASSUMPTION] Clip videos stay outside the repository; only the manifest is committed.

### Story 3.9: Threshold tuning and first deepseek-flash measurement

As a researcher,
I want detector thresholds tuned on the clips and first accuracy measured,
So that M3 exits with known numbers and frozen settings.

Implements: FR-18, FR-19, FR-46; AD-2 (freeze by catalogVersion).
Files: `tools/catalog-builder/detectors.json`, `docs/designs/m3-accuracy.md`.

**Acceptance Criteria:**

**Given** a tuning subset of clips
**When** thresholds are adjusted
**Then** each change and its reason is logged, no question text or step changes meaning, and the final `catalogVersion` is recorded as frozen.

**Given** replay of all clips
**When** finished
**Then** per-step done/not done/cannot tell counts and cloud latency are recorded in `m3-accuracy.md` with `modelId` and `promptVersion`.

## Epic 4: Results, plan, training and rewards (M4)

Exit: rules and counter tests pass; report and plan shown (design M4 exit).

### Story 4.1: Rule 2, corrections-aware levels and recalculation

As a therapist,
I want domain averages and plan levels computed purely from facts,
So that results always match `scoring-rules.md`.

Implements: FR-23, FR-24, FR-25, NFR-7, SM-6; AD-5.
Files: `<pkg>/scoring/RulesEngine.kt`, `<pkg>/data/Entities.kt` + migration 1->2 (`correction` table), `android/app/schemas/`, `<test>/RulesEngineTest.kt`, `<itest>/MigrationTest.kt`.

**Acceptance Criteria:**

**Given** averages exactly 3, 2.5, 1.5 and just below each
**When** `planLevel` runs
**Then** they map to strength, mild, moderate, severe per Rule 2.

**Given** a step tagged Visual-motor coordination and Motor coordination
**When** `domainAverages` runs
**Then** it counts once in Motor coordination; a multi-domain step counts once in each; not scored steps are excluded; an empty domain returns no average.

**Given** a correction row (including level null = not scored)
**When** `stepLevel` runs
**Then** the correction wins over the automatic level.

**Given** sessions in replay/running/paused/abandoned state
**When** results are requested
**Then** they are ignored (only finished assessment sessions).

**Given** `koverVerify`
**When** run
**Then** RulesEngine line coverage >= 90%; migration 1->2 has an instrumented test.

### Story 4.2: Functional report screen

As a therapist,
I want a report of strengths, needs and domain results after an assessment,
So that I see where difficulties appear.

Implements: FR-25, FR-31, UX-DR14, UX-DR16; AD-5.
Files: `<pkg>/scoring/RulesEngine.kt` (`strengthsAndNeeds`), `<pkg>/ui/therapist/ReportScreen.kt`, `<pkg>/ui/therapist/DomainChart.kt`, `<test>/RulesEngineTest.kt`.

**Acceptance Criteria:**

**Given** a finished assessment
**When** the report opens
**Then** strengths list level-3 steps, needs list level-2/1 steps "may be related to" their domains, and each domain shows average (one decimal), plan level and scored-step count, or "No scored steps".

**Given** the chart
**When** rendered
**Then** bars carry plan-level colour plus number/word labels and TalkBack descriptions.

### Story 4.3: Treatment plan and activity picker

As a therapist,
I want every matching activity for each weak domain and a way to pick some,
So that I can start a training session.

Implements: FR-32, FR-33, UX-DR15; AD-5.
Files: `<pkg>/scoring/RulesEngine.kt` (`activitiesFor`), `<pkg>/ui/therapist/PlanScreen.kt`, `<test>/RulesEngineTest.kt`.

**Acceptance Criteria:**

**Given** domains at mild and severe and one strength
**When** the plan is computed from the latest finished assessment per task
**Then** it lists all activities of the matching plan level and domain section, weakest domain first, both plan levels at once, none for the strength.

**Given** an activity row
**When** expanded
**Then** tools, how to perform, goal and criterion show verbatim.

**Given** selected activities
**When** "Start training" is pressed
**Then** the selection is passed to the training hand-off.

### Story 4.4: Camera-counted activity selection data

As the author,
I want `activity_counters.json` filled by the design's selection rule,
So that only qualifying activities are camera-counted.

Implements: FR-34; AD-1, AD-4.
Files: `tools/catalog-builder/activity_counters.json`, `tools/catalog-builder/test_build_catalog.py`, `docs/designs/activity-counter-selection.md`.

**Acceptance Criteria:**

**Given** each of the 148 criteria
**When** reviewed
**Then** an activity is listed only if its criterion is a count or hold of one body movement describable by one `angleAbove/angleBelow/pointAbove/distanceBelow` check; the decision per included activity is recorded with the source criterion quoted.

**Given** the builder
**When** run
**Then** each entry validates against `criterion.schema.json` (`kind`, `target`, `perSide`, `low`, `high`) and references an existing activity ID.

### Story 4.5: Repetition and hold counter

As the developer,
I want a pure `RepCounter` with hysteresis,
So that repetitions and holds are counted reliably.

Implements: FR-34, NFR-7; AD-4.
Files: `<pkg>/training/RepCounter.kt`, `<test>/RepCounterTest.kt`.

**Acceptance Criteria:**

**Given** a synthetic angle series crossing `high` then `low` 10 times
**When** counted
**Then** count = 10; a crossing shorter than 0.4 s is ignored; noise between thresholds is not counted.

**Given** `perSide` true
**When** counting
**Then** left and right are counted separately to target.

**Given** a hold criterion
**When** the check holds continuously
**Then** hold time accumulates and resets on break
**And** Kover line coverage on RepCounter >= 90%.

### Story 4.6: Training runner, screens and saved results

As a child,
I want to do the picked activities with a big counter or a timer,
So that I can practise and my therapist sees the results.

Implements: FR-34, FR-35, FR-36, UX-DR11; AD-9.
Files: `<pkg>/training/TrainingRunner.kt`, `<pkg>/ui/child/TrainingScreen.kt`, `<pkg>/data/Entities.kt` + migration (`training_result`), `android/app/schemas/`, `<test>/TrainingRunnerTest.kt`, `<itest>/MigrationTest.kt`.

**Acceptance Criteria:**

**Given** a camera-counted activity
**When** running
**Then** the counter shows count and target ("٣ من ١٠" style) with a sage ring and completes at the criterion.

**Given** a guided activity
**When** running
**Then** illustration, instruction audio when present, a timer and Done are shown.

**Given** an activity ends
**When** saved
**Then** one `training_result` row (count nullable, durationSec, criterionMet) is written only by `TrainingRunner`.

### Story 4.7: Reward rules and recorder

As a child,
I want stars after each step and training set,
So that I feel encouraged regardless of my score.

Implements: FR-37, FR-40, SM-C2; AD-9, AD-11.
Files: `<pkg>/rewards/RewardRules.kt`, `<pkg>/rewards/RewardRecorder.kt`, `<pkg>/data/Entities.kt` + migration (`reward`), `<pkg>/session/AssessmentRunner.kt`, `<pkg>/training/TrainingRunner.kt`, `<test>/RewardRulesTest.kt`.

**Acceptance Criteria:**

**Given** done on attempt 1 / done on attempt 2-3 / not done after attempt 3 with Done pressed
**When** `RewardRules` computes
**Then** stars are 2 / 1 / 1.

**Given** a step
**When** its reward is recorded
**Then** it happens only after the attempt facts are saved; replay (eval child) earns nothing; rewards never appear as RulesEngine input (Konsist/unit check).

**Given** a training repetition set completed
**When** recorded
**Then** stars follow the same rules.

### Story 4.8: Reward effects by effect level

As a child,
I want calm, safe celebrations,
So that rewards feel good without overload.

Implements: FR-6, FR-37, FR-39, NFR-6, UX-DR6, UX-DR10; AD-11.
Files: `<pkg>/ui/child/RewardEffects.kt`, `<pkg>/ui/child/StarBar.kt`, `<pkg>/ui/child/CharacterView.kt`, `<test>/RewardEffectsTest.kt`.

**Acceptance Criteria:**

**Given** effect level full / calm / off
**When** a step is done
**Then** respectively star flight + Konfetti + chime + `celebrateSmall`; slow fade + soft sound + `celebrateSmall`; counter increment only with no sound.

**Given** instruction audio playing
**When** an effect is requested
**Then** it waits for audio end.

**Given** any effect
**When** its flash schedule is checked
**Then** no more than 3 flashes per second.

**Given** not done after attempt 3
**When** the step ends
**Then** `encourage` and the encouraging line play; no negative sound or image.

### Story 4.9: Task reward screen and sticker book

As a child,
I want a reward screen after each task and a sticker book,
So that I want to continue.

Implements: FR-38, UX-DR10; AD-9.
Files: `<pkg>/ui/child/TaskRewardScreen.kt`, `<pkg>/ui/child/StickerBookScreen.kt`, `<pkg>/rewards/RewardRules.kt`, `<test>/RewardRulesTest.kt`.

**Acceptance Criteria:**

**Given** a finished task
**When** after the walkthrough
**Then** the screen shows stars earned, `celebrateBig` per effect level and a 3-slot sticker path.

**Given** 3, 6, 9 finished assessment tasks
**When** stickers are computed from finished sessions
**Then** 1, 2, 3 stickers are unlocked; the sticker book shows earned stickers in colour and locked ones as soft outlines.

## Epic 5: Therapist review, export and measured accuracy (M5)

Exit: Goal 8 measured and reported (design M5 exit).

### Story 5.1: Session list and session detail with evidence

As a therapist,
I want every session of a child with step levels, attempts, frames and reasons,
So that I can check what the app decided.

Implements: FR-16, FR-26, UX-DR12, UX-DR13.
Files: `<pkg>/ui/therapist/ChildProfileScreen.kt` (sessions tab), `<pkg>/ui/therapist/SessionDetailScreen.kt`, `<pkg>/ui/therapist/EvidenceStrip.kt`, `<pkg>/ui/therapist/LevelChip.kt`.

**Acceptance Criteria:**

**Given** a child with sessions
**When** the sessions tab opens
**Then** all sessions show task, date, kind and state.

**Given** session detail
**When** a step is opened
**Then** each attempt shows number, prompt level, Help, verdict, reason, method, latency and 3 decrypted frame thumbnails that enlarge on tap.

### Story 5.2: Correct a step level

As a therapist,
I want to correct any step level after the session,
So that I keep the final say.

Implements: FR-25, FR-27, UX-DR13; AD-5, AD-9.
Files: `<pkg>/ui/therapist/CorrectSheet.kt`, `<pkg>/ui/therapist/SessionDetailScreen.kt`, `<pkg>/data/Repositories.kt`, `<itest>/CorrectionTest.kt`.

**Acceptance Criteria:**

**Given** a step at automatic level 2
**When** corrected to 3
**Then** the chip shows 2 struck and 3 in corrected purple with "reviewed"; report, plan and compare update with no other action.

**Given** a second correction on the same step
**When** saved
**Then** it replaces the first (upsert on sessionId, stepId); deleting it restores the automatic level; "not scored" stores level null.

### Story 5.3: Compare two assessments

As a therapist,
I want to compare two assessments of the same task,
So that I can follow progress.

Implements: FR-28; AD-5.
Files: `<pkg>/scoring/RulesEngine.kt` (`compare`), `<pkg>/ui/therapist/CompareScreen.kt`, `<test>/RulesEngineTest.kt`.

**Acceptance Criteria:**

**Given** two finished assessments with the same taskId
**When** compared
**Then** only steps with a level in both are used, and per-domain averages for each and their difference are shown.

**Given** sessions of different tasks
**When** selection is attempted
**Then** the second picker offers only sessions of the same task.

### Story 5.4: Training history

As a therapist,
I want each child's training results,
So that I know what was practised.

Implements: FR-30.
Files: `<pkg>/ui/therapist/TrainingHistoryScreen.kt`, `<pkg>/data/Repositories.kt`.

**Acceptance Criteria:**

**Given** saved training results
**When** the history tab opens
**Then** each row shows activity, count, duration, criterion met, date, newest first; empty state when none.

### Story 5.5: Diagnostics screen

As a therapist,
I want to see camera, landmark and proxy health,
So that I can fix setup problems before a session.

Implements: FR-1, FR-29, NFR-1, NFR-2.
Files: `<pkg>/ui/therapist/DiagnosticsScreen.kt`, `<pkg>/vision/CloudVerifier.kt` (health ping).

**Acceptance Criteria:**

**Given** the diagnostics screen
**When** open
**Then** it shows camera fps, per-landmark-group visibility, proxy reachability and round-trip latency, and the cannot-tell rate over saved attempts excluding verifier `none`.

**Given** a wrong secret
**When** the proxy is pinged
**Then** the screen says "Proxy rejected the secret (401). Re-enter it in Settings."

### Story 5.6: Anonymised CSV export

As a researcher,
I want `results.csv` exported without names,
So that the evaluation script can run on it.

Implements: FR-44, NFR-4; AD-2.
Files: `<pkg>/data/CsvExporter.kt`, `<pkg>/ui/therapist/SettingsScreen.kt`, `<test>/CsvExporterTest.kt`.

**Acceptance Criteria:**

**Given** sessions with attempts, corrections and blind ratings
**When** Settings -> Export runs
**Then** `results.csv` is written to Downloads with exactly the columns `sessionId, childCode, taskId, stepId, attemptId, attemptNumber, promptLevel, verdict, reason, verifier, modelId, latencyMs, level, correctedLevel, blindVerdict, clipFile`, levels computed by RulesEngine.

**Given** the file
**When** searched for any child name
**Then** none appears.

### Story 5.7: Blind review

As a therapist,
I want to rate attempts with the app's verdict hidden,
So that agreement is measured fairly.

Implements: FR-47; AD-9.
Files: `<pkg>/ui/therapist/BlindReviewScreen.kt`, `<pkg>/data/Entities.kt` + migration (`blind_rating`), `android/app/schemas/`, `<itest>/MigrationTest.kt`, `<test>/RulesEngineTest.kt`.

**Acceptance Criteria:**

**Given** replay attempts
**When** blind review opens
**Then** one attempt at a time shows frames/clip only, no verdict, reason or level, with done / not done / cannot tell.

**Given** a rating
**When** saved
**Then** one `blind_rating` row per attempt (unique), and levels, report and plan are unchanged (test).

### Story 5.8: Accuracy metrics script and dashboard

As a researcher,
I want agreement, weighted kappa, cannot-tell rate and confusion matrices,
So that Goal 8 can be judged.

Implements: FR-48, SM-1, SM-2, SM-3, SM-C1, SM-C3.
Files: `eval/compute_metrics.py`, `eval/test_compute_metrics.py`, `<pkg>/ui/therapist/MetricsScreen.kt`, `<pkg>/scoring/Metrics.kt`, `<test>/MetricsTest.kt`.

**Acceptance Criteria:**

**Given** a `results.csv` and `eval/clips.csv` joined by `clipFile`
**When** `python eval/compute_metrics.py results.csv` runs
**Then** it prints per-attempt agreement (app vs blind), quadratic-weighted kappa on levels, cannot-tell rate, and a per-step confusion matrix; not scored steps and cannot-tell attempts are excluded from kappa.

**Given** a hand-computed fixture
**When** tested
**Then** script and Kotlin `Metrics` produce identical numbers; the dashboard labels results "volunteer accuracy".

### Story 5.9: Evaluation run and Goal 8 report

As the author,
I want the full evaluation executed and written up,
So that the research question is answered with numbers.

Implements: FR-46, FR-47, FR-48, SM-1..SM-5.
Files: `eval/results/` (CSV export, not containing names), `docs/designs/evaluation-report.md`.

**Acceptance Criteria:**

**Given** the frozen catalogVersion, rulesVersion, modelId and promptVersion
**When** all clips are replayed and blind-rated by the therapist
**Then** metrics are computed and the report states each Goal 8 threshold as met or not, with versions, latency and fps (SM-5).

**Given** a target is not met
**When** reported
**Then** results are reported as measured, with no doubt converted into "not done" to improve numbers.

### Story 5.10: README and final verification gate

As the committee,
I want build, deployment and setup documented and all gates passing,
So that the project can be reproduced.

Implements: AD-12, NFR-7; all FRs (verification).
Files: `README.md`, `<pkg>/ui/therapist/AboutScreen.kt` (open-source credits), `docs/permissions/` (existing proofs referenced).

**Acceptance Criteria:**

**Given** the README
**When** followed on a clean machine
**Then** it builds audio, illustrations and catalogue in AD-1 order, builds and installs the app, deploys the proxy with secrets and spend limit, pairs the Bluetooth button and explains the physical setup; credits and licenses listed (also in About).

**Given** the spine's Build and verification table
**When** every command runs
**Then** all pass, including `koverVerify` and `connectedAndroidTest`, and the E2E checks (Wi-Fi off, close/reopen mid-task) are logged.

## FR Coverage Table (by story)

| FR | Stories |
|---|---|
| FR-1 | 2.10, 1.5, 5.5 |
| FR-2 | 2.10 |
| FR-3 | 2.14, 2.15 |
| FR-4 | 1.2, 3.3, 3.4 |
| FR-5 | 2.11 |
| FR-6 | 2.11, 4.8 |
| FR-7 | 2.1, 2.2, 2.7, 2.8, 3.3 |
| FR-8 | 2.7, 3.3 |
| FR-9 | 2.3, 2.4, 2.5, 2.8, 2.14, 2.17 |
| FR-10 | 2.16 |
| FR-11 | 2.3, 2.14 |
| FR-12 | 2.12, 2.14 |
| FR-13 | 2.12 |
| FR-14 | 2.13, 3.5 |
| FR-15 | 2.15 |
| FR-16 | 3.5, 5.1 |
| FR-17 | 2.16 |
| FR-18 | 1.3, 3.1, 3.2, 3.5, 3.9 |
| FR-19 | 1.4, 1.5, 3.5, 3.9 |
| FR-20 | 1.4, 1.5, 3.6 |
| FR-21 | 2.13, 3.5 |
| FR-22 | 2.16 |
| FR-23 | 2.13, 4.1 |
| FR-24 | 4.1 |
| FR-25 | 4.1, 4.2, 5.2 |
| FR-26 | 5.1 |
| FR-27 | 5.2 |
| FR-28 | 5.3 |
| FR-29 | 1.2, 5.5 |
| FR-30 | 5.4 |
| FR-31 | 4.2 |
| FR-32 | 2.2, 4.3 |
| FR-33 | 4.3 |
| FR-34 | 4.4, 4.5, 4.6 |
| FR-35 | 2.4, 4.6 |
| FR-36 | 4.6 |
| FR-37 | 2.6, 4.7, 4.8 |
| FR-38 | 2.6, 4.9 |
| FR-39 | 2.6, 4.8 |
| FR-40 | 4.7 |
| FR-41 | 2.9 |
| FR-42 | 1.4, 1.5, 3.5 |
| FR-43 | 2.10, 2.11 |
| FR-44 | 5.6 |
| FR-45 | 2.9, 2.11 |
| FR-46 | 3.7, 3.8, 3.9, 5.9 |
| FR-47 | 5.7, 5.9 |
| FR-48 | 3.8, 5.8, 5.9 |

All 48 FRs are covered. UX-DR1-17 are covered by 2.10 (DR1, DR12, DR17), 2.14 (DR2-DR7, DR16), 2.15 (DR8), 2.16 (DR8), 3.4 (DR9), 4.8/4.9 (DR6, DR10), 4.6 (DR11), 5.1/5.2 (DR13), 4.2 (DR14), 4.3 (DR15).

## Validation Notes and Assumptions

- Run non-interactively at the author's request; every menu was answered "Continue" and no extra input documents were added.
- No starter template exists; Story 1.1 sets up the greenfield project; mediapipe-samples code is adapted in 1.2 (open-source reuse).
- Tables are created when first needed: child/session/attempt in 2.9, correction in 4.1, training_result in 4.6, reward in 4.7, blind_rating in 5.7, each with an explicit Room migration and test (AD-8). [ASSUMPTION] AD-8 lists all tables but does not require creating them at version 1.
- Epic 1 is a technical spike epic by design (author's guidance: riskiest first); its stories still deliver demonstrable verdicts on the tablet.
- `RulesEngine.stepLevel` is created in 2.13 because AD-6 requires the runner to advance through it; the rest of RulesEngine comes in Epic 4.
- Jacket "both" runs as two sessions (on then off). [ASSUMPTION]
- Consent acknowledgement is recorded at setup and per child profile (PRD FR-43 assumption).
- Sticker unlock every 3 finished assessment tasks (PRD FR-38 assumption).
- Volunteer clip videos are kept outside the repository; only `eval/clips.csv` and protocol are committed. [ASSUMPTION]
- Spike and milestone records (`docs/designs/m1-spike-results.md`, `m2-content-check.md`, `m3-field-tests.md`, `m3-accuracy.md`, `activity-counter-selection.md`, `evaluation-report.md`) are new documentation files. [ASSUMPTION] Added so exit criteria are evidenced.
- Helper files not named in the spine (`FrameCropper.kt`, `VerifierRouter.kt`, `CheckEvaluator.kt`, `NoneVerifier.kt`, `RunnerState.kt`, `StepSchedule.kt`, `ChildModeHost.kt`, `AdultPauseSheet.kt`, `WalkthroughScreen.kt`, `NewSessionDialog.kt`, `ZoneEditor.kt`, `TherapistShell.kt`, `LevelChip.kt`, `EvidenceStrip.kt`, `CorrectSheet.kt`, `DomainChart.kt`, `ReplayScreen.kt`, `ClipName.kt`, `MetricsScreen.kt`, `Metrics.kt`, `TrainingHistoryScreen.kt`, `CsvExporter.kt`, `AboutScreen.kt`, `ui/theme/*`, `data/Repositories.kt`, `data/Converters.kt`) stay inside the spine's packages and respect its dependency diagram. Navigation (Navigation 3 + Material 3 adaptive) and DI (Hilt) are fixed by AD-13 and set up in Story 1.1.
- Child UI labels and Arabic step sentences await author review (EXPERIENCE.md assumption).
