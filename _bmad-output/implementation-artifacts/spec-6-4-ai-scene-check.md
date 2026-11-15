---
title: 'Story 6.4: AI scene check before each session (auto zones, task-aware messages); remove manual zones and developer tools'
type: 'feature'
created: '2026-11-15'
status: 'in-progress'
route: 'dispatch'
context:
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-Ai therapist-2026-11-11/ARCHITECTURE-SPINE.md'
  - '{project-root}/docs/flows/therapist-flow.md'
  - '{project-root}/docs/flows/child-flow.md'
---

## Intent (owner decision 2026-11-15)

**Problem:** Zones (table, sink, jacket area…) are drawn by hand in Settings; the room changes between sessions (only the table/chair setup is constant). The owner wants the system to be "digital": when a session starts, the AI photographs the scene, checks it fits the chosen task, and finds the zones itself; it refuses to start until the scene is right and tells the therapist exactly what is missing, per task. Manual zones and the developer tools leave Settings (keep only the camera check and body tracking).

**Approach:**
1. **Proxy (`proxy/`):** new `POST /scene` endpoint, same auth (shared secret), same vision model call as `/verify`. Input: one JPEG of the empty scene + the task's required objects (zone kinds from the catalogue, e.g. `table`, `chair`, `cup`, `sink`, `jacket_hook`) + locale. Output (strict JSON, validated): `{ ok: boolean, objects: [{kind, box:{x,y,w,h} normalized 0..1, confidence}], issues: [{code, kind?}] }` with fixed issue codes (`missing_object`, `partly_visible`, `too_dark`, `person_in_view`, `camera_tilted`, `too_far`, `too_close`, `unclear`). Any invalid model output → `{ok:false, issues:[{code:"unclear"}]}`. Never log the body or image. Prompt versioned like `/verify`. Tests (vitest) for parsing/validation and auth; `npm test`, `npm run build`, `npx tsc --noEmit`.
2. **Android scene check** (replaces the framing pre-check's zone part, before "Hand to child"):
   - Capture one frame from the camera at the scene-check screen. If the on-device pose model sees a person, ask the therapist to clear the view first (no child images leave the tablet).
   - Required kinds come from the catalogue (the zone kinds the task's steps already use; never hard-code task IDs).
   - Call `/scene`; map returned boxes to the existing zone model (`ZoneStore`, normalized frame coordinates) for this session/orientation, so scoring code that reads zones keeps working unchanged.
   - Show task-aware Arabic messages for each issue code (e.g. "مش لاقي ترابيزة — حطّ الترابيزة قدام الكاميرا"), a "Check again" button, and enable "Hand to child" only when `ok` and every required kind has a box. Show the found boxes over the preview (labelled) so the therapist sees what the AI found.
   - Offline / proxy error: calm message + "Start anyway"; zone-dependent steps then end as cannot_tell (doubt is never failure).
   - Keep the existing body-framing guidance (person in the silhouette) as the second phase after the scene is accepted (child sits, framing ok, hand over).
3. **Remove:** manual zone editor UI (ZoneEditor*, its Settings entry and the "draw zones" links from framing/diagnostics) and the Developer tools section (replay, try-step, character preview, advanced tools) from Settings. Keep Settings → Camera & body tracking (diagnostics). Update `ReleaseGatingTest` accordingly (developer tools no longer exist; character preview route may stay debug-only or be removed). Keep `ZoneStore` and zone math used by scoring.
4. **Docs:** therapist-flow.md, child-flow.md, EXPERIENCE.md; record the architecture change (AI scene check replaces manual zones) in the spine's decision log or an addendum.

## Boundaries

**Always:** no child image to the proxy (empty scene only, person check on device); no logging of frames/bodies; strings ar+en; Western digits in therapist UI; portrait + landscape; tests for parsing, mapping boxes→zones, required-kind derivation, issue→message mapping.
**Never:** no hard-coded task/step IDs; no scoring-rule change; do not deploy the proxy (coordinator asks the owner); agents do not run the full Gradle gate.

## Ownership
- Agent P: `proxy/**` only.
- Agent A: `android/**` and docs.

## Review Triage Log

- 2026-11-15 Agent P: `POST /scene` added (`proxy/src/scene.ts`, route in `src/index.ts`, tests in `proxy/test/scene.test.ts`, contract in `proxy/README.md`). Request: multipart `requestId`, `kinds` (comma-separated), `locale`, one `image` JPEG. `partly_visible` treated as non-blocking (box still usable); all other issues block `ok`. Confidence threshold 0.6. npm test (69), build, tsc pass. Not deployed.
- 2026-11-15 Agent P (owner update): child may be in the scene (faces blurred on device). `person` accepted as a kind; prompt states faces are blurred on purpose, never describe the person, locate occluded objects; `partly_visible` only when the needed part is hidden and now blocks; `person_in_view` kept as accepted code but non-blocking. Prompt bumped to `scene-v2`.

## Verification
- `proxy`: npm test, build, tsc. Android: coordinator gate; device check with a real table scene; deploy proxy after owner approval.

- 2026-11-15, agent A (android + docs): Built `vision/SceneChecker` (POST /scene, multipart `requestId`/`kinds` comma-separated/`locale`=ar-EG/`image`, Bearer secret, constants in one block, matches proxy README; errors/timeouts/bad JSON -> `SceneResult.Unavailable`; logs nothing), `vision/ScenePlan` (zone kinds from catalogue `inZone` checks + `person`; client-side acceptance ignores person issues and needs a box per zone kind; boxes -> ZoneStore zones) and `FaceBlur` (owner update: no blocking on a person; face pixelated on device from pose face points 0..10 padded, else top 25% of the body box). Framing check now runs scene phase -> body framing -> hand-off; accepted boxes (incl. `person`) replace the saved zones; Start anyway clears zones (zone steps end cannot_tell). `/verify` crops now include the saved zones + person box (existing union) and are face-pixelated via `JpegEncoder.encode(..., blur)`. Removed: ZoneEditor*, setup zone step, Settings zones section + Developer tools (replay UI, try step, character preview + route), Diagnostics zones card; `replay/` infrastructure and its tests kept (no in-app entry). Docs: therapist-flow, child-flow, EXPERIENCE, spine decision-log addendum. Tests: SceneCheckTest, FramingCheckViewModelTest scene phases, Setup/ReleaseGating/AppGate updated. Only compile + targeted unit tests + ktlint run; full gate left to the coordinator. Open: the blur on /verify crops also pixelates the mouth area for eating steps, which may raise cannot_tell there (owner asked for it; watch on device).
