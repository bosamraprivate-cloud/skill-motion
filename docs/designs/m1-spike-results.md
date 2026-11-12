# M1 spike results

Status: **provisional** until the target tablet is chosen. Numbers below come from the development
test device, not the production tablet.

## Frame-rate probe (Story 1.2)

Measured with Diagnostics > "Run 60 s" (front camera, analysis about 640x480, Pose full + Hand, LIVE_STREAM).
Hand and combined fps are **inference rates**: a hand result is counted for every processed frame,
even when zero hands are detected, so they measure landmarker throughput, not tracking quality.
The debug build also writes one numbers-only Logcat line, tag `DlsFps`: `<pose> <hand> <combined>`.

| Field | Value |
|---|---|
| Device model | Xiaomi 21061110AG (test device) |
| Android version | 13 (API 33) |
| Delegate | GPU |
| Pose fps (60 s avg) | 10.2 |
| Hand fps (60 s avg) | 10.2 |
| Combined fps (60 s avg) | 10.2 |
| Date | 2026-11-12 |
| Conditions | Dim room lighting |

### Decision rule (NFR-2)

- Combined >= 15 fps: run Pose and Hand together.
- Combined < 15 fps: fallback, run only the landmarker the current step needs.

Decision: **fallback**. Combined 10.2 fps < 15 on this device, so the NFR-2 fallback applies: run
only the landmarker the current step needs. Provisional until the target tablet is chosen and measured.

### Un-mirroring correction

The first build flipped front-camera frames horizontally before inference. The device test showed
left/right swapped (a raised right hand got the LEFT_WRIST dot): CameraX front-camera analysis frames are
already un-mirrored (as others see the subject). The flip was removed; frames are only rotated upright
before inference, and the overlay flips x only to match the mirrored preview.

## Cloud latency (Story 1.5)

Not measured yet.

## Device verification after the un-mirroring fix (2026-11-12)

- Overlay alignment checked on the Xiaomi: eye, nose, mouth, shoulder and all 21 hand points sit on the subject in the preview, including with a tilted head.
- Subject raised the right hand: the large red dot (`RIGHT_WRIST`, index 16) sat on the raised right wrist. Left/right is correct.
- Second 60 s run in better lighting: pose 10.0, hand 10.0, combined 10.0 fps (GPU). Decision unchanged: fallback on this device; provisional until the tablet is chosen.
- Landmarks estimated outside the frame (x or y outside 0..1) are no longer drawn.

## First local verdict, drink.cup.05 (Story 1.3, 2026-11-12)

Debug "Try drink.cup.05" attempt (5 s window) on the Xiaomi, GPU, about 10 fps. Latency = pairing wait + evaluation.

| Case | Verdict | Latency (wait + eval = total) |
|---|---|---|
| Camera covered | `cannot_tell` / `out_of_frame` | 101 + 2 = 103 ms |
| Wrist held at the mouth | `done` / `check_passed` | 103 + 12 = 115 ms |
| Hands below the frame (wrists not visible) | `cannot_tell` / `out_of_frame` | 102 + 3 = 105 ms |
| Hand visible on the chest, away from the mouth | `not_done` / `check_failed` | 103 + 22 = 125 ms |

NFR-1 (local verdict within 300 ms) met on this device: worst case 125 ms. The "hands below the frame" case confirms AD-3: what the camera cannot see becomes `cannot_tell`, never `not_done`.

## DeepSeek endpoint for the verify proxy (Story 1.4, 2026-11-12)

Confirmed from api-docs.deepseek.com (guides/vision) on 2026-11-12:

- Base URL: `https://api.deepseek.com` (OpenAI-compatible chat completions).
- Model ID: `deepseek-flash` (V4.1-Flash-1017), accepts images.
- Images: inline `image_url` with a base64 data URL; up to 600 images per request, 32 MiB per image, 48 MiB inline body, 8192 px per side; JPEG, PNG, GIF, WebP; `detail` `low` (512x512) or `auto` (original).
- JSON mode together with images is not documented, so the Worker validates the output regardless.

Cloud latency is not measured yet: the Worker is not deployed (deployment and secrets are a user-run step).

## Proxy deployment and live check (Story 1.4, 2026-11-12)

- Worker `verify-proxy` deployed to the author's Cloudflare account (workers.dev). Secrets `DEEPSEEK_API_KEY` and `SHARED_SECRET` set with `wrangler secret put`; local copies only in git-ignored `proxy/.dev.vars`. Observability off; no storage bindings.
- Sources for the DeepSeek facts above: https://api-docs.deepseek.com/ and https://api-docs.deepseek.com/guides/vision/ (checked 2026-11-12).
- Live checks with a generated test-pattern JPEG (no person):
  - Wrong secret: 401.
  - 1 image: 200, `cannot_tell` ("color bar test pattern; no person or cup is visible"), 1.8 s.
  - 3 images, three runs: 200 in 2.2 s, 1.8 s, 2.3 s (all well under the 8 s cloud budget).
- JSON output mode together with image input works with `deepseek-flash`.
- `modelId` as returned by DeepSeek is the alias `deepseek-flash`, not a dated snapshot. The served snapshot (V4.1-Flash-1017 per the docs page) is therefore recorded here with the date; if DeepSeek changes the snapshot behind the alias during evaluation, clips are re-scored via replay (design).
- Action for the author: set a monthly spending limit on the DeepSeek account, and rotate the API key before sharing the project (it appeared in a chat transcript).
