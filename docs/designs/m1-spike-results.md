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
| Delegate | NOT MEASURED YET |
| Pose fps (60 s avg) | NOT MEASURED YET |
| Hand fps (60 s avg) | NOT MEASURED YET |
| Combined fps (60 s avg) | NOT MEASURED YET |
| Date | NOT MEASURED YET |

### Decision rule (NFR-2)

- Combined >= 15 fps: run Pose and Hand together.
- Combined < 15 fps: fallback, run only the landmarker the current step needs.

Decision: **pending measurement** (the device was not connected over adb when Story 1.2 was implemented).

## Cloud latency (Story 1.5)

Not measured yet.
