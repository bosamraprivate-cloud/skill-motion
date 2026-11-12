# Addendum: PRD Daily Living Skills App

Technical detail kept out of the PRD; the source of truth is `docs/designs/daily-living-skills-app.md`.

- Stack: Kotlin + Jetpack Compose, CameraX, MediaPipe Tasks Vision (Pose 33 + Hand 21 landmarks), Room + SQLCipher (Keystore AES-256-GCM key), Vico charts, Rive Android runtime, Konfetti, OkHttp. Package `com.aitherapist.dls`, minSdk 30.
- Proxy: Cloudflare Worker (TypeScript), `openai` npm SDK pointed at DeepSeek, model `deepseek-flash` via `MODEL_ID`; `POST /verify` with bearer shared secret; 401/413/502 errors mapped to cannot tell on the tablet.
- Catalogue: `tools/catalog-builder` builds `assessment.json` and `activities.json` from `docs/reference/` plus `detectors.json`, `activity_counters.json`, `strings.ar-eg.json`; `catalogVersion` = first 12 hex of SHA-256.
- The 41 camera-scored steps with method and check: design section "Camera-scored steps".
- Rejected alternatives (therapist button scoring, single photo per step, adapting one app, Flutter/React Native, on-device object detector, extra clinical content): design section "Alternatives Considered".
- Data model, encryption, replay file naming, CSV columns, milestone exit criteria: design "Detailed Implementation Plan".
