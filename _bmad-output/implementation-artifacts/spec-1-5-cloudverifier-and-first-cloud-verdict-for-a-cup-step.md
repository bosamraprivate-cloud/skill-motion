---
title: 'Story 1.5: CloudVerifier and first cloud verdict for a cup step'
type: 'feature'
created: '2026-11-12'
status: 'draft'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-1-3-geometry-check-model-and-first-local-verdict.md'
  - '{project-root}/proxy/README.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Object steps (cup on lips, soap, zipper) cannot be judged from landmarks; the tablet must send three cropped frames to the deployed proxy and turn every answer or failure into a safe `Verdict`. This closes M1 (cloud verdict under 8 s).

**Approach:** Add `FrameCropper` (pure crop-box math + Android JPEG encoding) and `CloudVerifier` (OkHttp, multipart per AD-7, 8 s timeout, fixed error mapping), keep the attempt's frames in memory only, add a proxy reachability probe, and extend the debug Diagnostics attempt to run cloud steps. Prove it on the phone with `drink.cup.06` and a real cup.

## Boundaries & Constraints

**Always:**
- Request per AD-7 (updated): `POST {proxyUrl}/verify`, `Authorization: Bearer <secret>`, `multipart/form-data` with `requestId` (fresh UUID v4 per call), `stepId`, `question` (the step's `question` check text) and exactly 3 `image` JPEG parts: start, middle and end frames of the attempt window (by timestamp; if fewer than 3 visible frames exist, `CANNOT_TELL out_of_frame` without calling the proxy). Nothing else: no child name, code, profile field or session ID.
- Crop: union box of all visible hand landmarks of both hands plus pose face landmarks (nose, mouth L/R) plus the step's zone if one exists in `VerifyContext`; expand by 25% per side, clamp to the frame, make it at least 20% of the frame's shorter side; downscale so the longest side ≤ 768 px; JPEG quality 80. Crop math is pure Kotlin (JVM-tested); encoding is shell code.
- Mapping (AD-3/AD-7): 200 + valid body → that verdict with `modelId`, `promptVersion`, `latencyMs`; 200 + unparseable/unknown verdict → `CANNOT_TELL invalid_model_output`; non-200 → `CANNOT_TELL http_<status>`; call timeout 8 s → `CANNOT_TELL timeout`; IO/connection error → `CANNOT_TELL network`; any other exception → `CANNOT_TELL not_judgeable`. `NOT_DONE` only from a 200 `not_done`. Never throws.
- Frames for the attempt (bitmaps) are held in memory only for the attempt and released after the verdict; never written to disk; never logged.
- Proxy URL and shared secret come from `android/local.properties` (`dls.proxyUrl`, `dls.sharedSecret`, git-ignored) into **debug-only** BuildConfig fields; release builds get empty values (real settings storage comes in Epic 2). No secret in source control.
- Reachability probe: `GET {proxyUrl}/verify` → 405 means reachable; shown on Diagnostics with round-trip ms (FR-1).
- `drink.cup.06` spike asset copied verbatim from the design's camera-scored step table (method `cloud`, question "Is the rim of the cup touching the child's lips?"), debug assets only; Diagnostics lets the user pick among the spike assets (05 local, 06 cloud).

**Never:** No Room/DataStore, no runner, no retries (one proxy call per attempt), no base64 on the tablet, no Files API, no logging of images, questions or headers.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 200 done | MockWebServer `{"verdict":"done",...}` | `DONE`, modelId, promptVersion | N/A |
| 200 not_done | `{"verdict":"not_done",...}` | `NOT_DONE` | N/A |
| 200 garbage | invalid JSON / verdict "maybe" | `CANNOT_TELL invalid_model_output` | N/A |
| 401 / 413 / 502 | status codes | `CANNOT_TELL http_401` / `http_413` / `http_502` | N/A |
| Slow | response delayed > 8 s | `CANNOT_TELL timeout` | call cancelled |
| Drop | connection reset / no server | `CANNOT_TELL network` | N/A |
| Request shape | any attempt | multipart with fresh UUID, stepId, question, exactly 3 JPEG parts (FF D8 FF), Bearer header, no other fields | N/A |
| Too few frames | < 3 visible frames | `CANNOT_TELL out_of_frame`, no request | N/A |
| Crop | hands + face landmarks | box = union + 25% margin, clamped, min size, longest side ≤ 768 after scale | N/A |
| No config | empty proxy URL or secret | `CANNOT_TELL network`, no request | N/A |

</frozen-after-approval>

## Code Map

- `vision/Verifier.kt` -- `Verifier`, `Verdict`, `Frame` (`image: Any?` placeholder — give it a real type, e.g. an `AttemptImage` wrapper holding a `Bitmap` in shell code), `VerifyContext` (zones), `VerdictReason`.
- `vision/PoseVerifier.kt`, `VisibilityGate.kt` -- reuse the gate before cloud calls (AD-3).
- `ui/therapist/DiagnosticsViewModel.kt` -- spike attempt flow (loads `assets/spike/`, records 5 s window, `DlsVerdict` log); extend to keep the window's bitmaps and route `method: cloud` steps to `CloudVerifier`.
- `camera/CameraController.kt` -- produces upright bitmaps per frame (`uprightBitmap`).
- `catalog/CatalogModels.kt` -- `Check.Question(text)`, `Method.CLOUD`.
- OkHttp + mockwebserver3 already pinned in `libs.versions.toml`.
- Deployed proxy: see `proxy/README.md`; live values in git-ignored `proxy/.dev.vars` (`PROXY_URL`, `SHARED_SECRET`) — copy into `android/local.properties` as `dls.proxyUrl` / `dls.sharedSecret`.

## Tasks & Acceptance

**Execution:**
- [ ] `<pkg>/vision/FrameCropper.kt` -- pure `cropBox(...)` + shell JPEG encode/scale -- crop
- [ ] `<pkg>/vision/CloudVerifier.kt` -- gate, frame pick, multipart request, timeout, mapping -- FR-19/20
- [ ] `<pkg>/vision/ProxyProbe.kt` -- reachability GET -- FR-1
- [ ] `app/build.gradle.kts` -- debug BuildConfig fields from local.properties; release empty -- config
- [ ] `src/debug/assets/spike/drink.cup.06.json` + Diagnostics: step picker, cloud route, probe row, verdict + modelId + latency -- spike
- [ ] `<test>/vision/CloudVerifierTest.kt` (MockWebServer), `<test>/vision/FrameCropperTest.kt`, extend `SpikeAssetDecodingTest` -- matrix
- [ ] `docs/designs/m1-spike-results.md` -- live drink.cup.06 results and latency (orchestrator fills after the device run)

**Acceptance Criteria:**
- Given the deployed proxy and the phone on Wi-Fi, when the drink.cup.06 spike runs with a real cup at the lips, then a verdict with `modelId` and `promptVersion` returns in under 8 s.
- Given `./gradlew assembleDebug test lint ktlintCheck koverVerifyCore`, then all pass.

## Implementation Notes

- Environment as earlier stories; device Xiaomi over adb. Do not commit. You may write `android/local.properties` entries from `proxy/.dev.vars` but never print the secret.

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `./gradlew assembleDebug test lint ktlintCheck koverVerifyCore` (in `android/`) -- expected: BUILD SUCCESSFUL

**Manual checks:**
- Phone: probe shows reachable; drink.cup.06 with cup at lips → verdict with modelId, latency < 8 s; airplane mode → `cannot_tell network`.
