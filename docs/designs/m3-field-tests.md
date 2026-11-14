# M3 field tests: network loss becomes "cannot tell"

Story 3.6. These tests check on a real phone or tablet that losing Wi-Fi during a camera-scored task never lowers a level. A cloud (object) step that cannot reach the proxy is saved as `cannot_tell`, repeated once as the next attempt number, and then left not scored. Body (pose or hand) steps are still judged on the device.

## Reason codes

Every cloud failure is saved as `cannot_tell` with one of these fixed codes (defined in `vision/Verifier.kt`, `VerdictReason`):

| Code | Cause |
|------|-------|
| `network` | No connectivity, connection dropped, or proxy not set up |
| `timeout` | No answer within 8 s |
| `refused` | Model refusal or proxy HTTP error (4xx/5xx) |
| `bad_answer` | Malformed or incomplete JSON from the proxy |

When the model itself answers, the attempt reason is the model's short text and `modelId` is set.

## Setup

1. Debug build installed, proxy URL and shared secret set in Settings, zones drawn.
2. A test child profile. Run `adb logcat -s DlsVerdict` to see the debug verdict trace (step, try, level, verdict and reason only).
3. Pick a task that has both cloud and body steps (for example drinking from a cup).

## Procedure

1. Start the task in child mode with Wi-Fi on. Do the first cloud step and confirm it is judged (reason is model text).
2. Turn Wi-Fi off from the quick settings (or airplane mode) while the next cloud step's attempt window is open.
3. Close the attempt (Done or go still). Expect: the character shows the neutral retry; no error text for the child; the trace shows `try=1 ... verdict=CANNOT_TELL reason=network` (or `timeout`).
4. Do the repeat. Expect `try=2` at the same prompt level, again `cannot_tell`, then the runner moves to the next step (step not scored).
5. Do a body step with Wi-Fi still off. Expect it to be judged normally (`check_passed` or `check_failed`).
6. Exit to therapist mode (PIN). Expect the calm banner "No internet ..." at the top of the shell.
7. Turn Wi-Fi back on. Expect the banner to disappear within a few seconds.
8. Optional: stop the proxy or set a wrong secret, run one cloud step, and expect the "last cloud check did not answer" banner and reason `refused`.

## Results

| Date | Device | Build | Step | Wi-Fi state | Attempt rows (try, verdict, reason) | Step level | Banner shown/hidden | Pass |
|------|--------|-------|------|-------------|-------------------------------------|------------|---------------------|------|
| | | | | | | | | |
