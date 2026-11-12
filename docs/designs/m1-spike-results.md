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
