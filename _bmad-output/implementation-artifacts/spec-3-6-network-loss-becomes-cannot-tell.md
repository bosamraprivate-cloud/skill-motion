---
title: 'Story 3.6: Network loss becomes "cannot tell"'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: 'af469c66d3385aaa964fa74e756b1e3a02c42823'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A cloud step during Wi-Fi loss must never lower a level; today error reasons are free text, the cannot-tell repeat numbering has a bug (two rows both `try=1` seen on the phone for `drink.cup.02`), and the therapist sees no network state.

**Approach:** CloudVerifier maps every failure to `CANNOT_TELL` with a fixed reason code: `network` (no connectivity / IO error), `timeout` (8 s), `refused` (model refusal / HTTP 4xx-5xx), `bad_answer` (malformed JSON); the model's free-text reason is kept separately only when the verdict came from the model. Fix attempt numbering so the cannot-tell repeat is attempt n+1. Add a connectivity monitor; therapist mode shows a calm network banner when offline or when the last proxy call failed; the child sees only the neutral retry (no error text). Add `docs/designs/m3-field-tests.md` with the Wi-Fi-off field test procedure and a results table.

## Boundaries & Constraints

**Always:** Doubt is never failure. Body steps unaffected by network. Cannot tell → repeat the same attempt once → second cannot tell → not scored → next step (design rule; keep). Reason codes are an enum-like set of strings in one place. No child data in logs. Banner uses therapist tokens, not red; strings ar/en.

**Never:** No offline queue/retry of cloud requests after the attempt closes. No child-facing error UI.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Wi-Fi off | IOException | CANNOT_TELL `network`, repeat once | N/A |
| Slow proxy | > 8 s | CANNOT_TELL `timeout` | N/A |
| HTTP 500 / refusal | error | CANNOT_TELL `refused` | N/A |
| Garbage JSON | malformed | CANNOT_TELL `bad_answer` | N/A |
| Repeat numbering | 1st cannot tell | next row is attempt 2 | N/A |
| Second cannot tell | repeat also cannot tell | step not scored, next step | N/A |
| Offline in therapist mode | no connectivity | banner shown; hides when back | N/A |
| Body step offline | pose step | judged locally | N/A |

</frozen-after-approval>

## Code Map

- `android/.../vision/CloudVerifier.kt`, `VerifierRouter.kt`, `Verifier.kt` (`VerdictReason`).
- `android/.../session/AssessmentRunner.kt` -- attempt numbering on the cannot-tell repeat; Rule 1 in `scoring/RulesEngine`.
- `android/.../ui/therapist/TherapistShell.kt` -- banner; new `data/Connectivity.kt` (ConnectivityManager callback) behind an interface.
- `docs/designs/m3-field-tests.md` (+ link in `docs/README.md`).

## Tasks & Acceptance

**Execution:**
- [x] CloudVerifier reason codes + tests (MockWebServer or fake client).
- [x] Runner numbering fix + test reproducing two `try=1` rows.
- [x] Connectivity monitor + therapist banner + strings.
- [x] m3-field-tests.md.

**Acceptance Criteria:**
- Given the gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.
- Given the phone with Wi-Fi off mid-task, when a cloud step closes, then it repeats once and continues; the therapist banner shows offline.

## Implementation Notes

- Reason codes live in `VerdictReason` (`network`, `timeout`, `refused`, `bad_answer`, plus `model` for model verdicts; `CLOUD_FAILURES` groups the four). `invalid_model_output` and `http_<status>` are gone from verdicts (`http()` stays for the Settings probe only).
- `Verdict.modelReason` carries the model's free text. The attempt `reason` column always holds a code (`model` for model verdicts); the text goes to the new nullable `attempt.modelReason` (max 300 chars, whitespace collapsed, never logged). Room v1 -> v2 with `MIGRATION_1_2`, exported `schemas/.../2.json`, JVM `MigrationSqlTest` and on-device `MigrationTest` (room-testing).
- Numbering: the cannot-tell repeat is attempt n+1 at the same prompt level. Rule 1 changed so this never lowers a level: two consecutive `cannot_tell` rows -> not scored; `done` at prompt level 1 with no earlier `not_done` -> THREE. Old same-number rows still score the same. `RULES_VERSION` bumped to "2".
- `data/Connectivity.kt`: `NetworkStatus` interface, pure `NetworkStatusState`, `AndroidNetworkStatus` (default-network callback; `ACCESS_NETWORK_STATE` added). `vision/ReportingVerifier` wraps the cloud verifier to report each proxy call. Online = INTERNET + VALIDATED. Reconnecting clears the failed-call flag and bumps a generation; a failure from a call started in an older generation is ignored; the flag expires after 2 min without new failures. Banner in `TherapistShell` uses `secondaryContainer`.

## Spec Change Log

- Coordinator review: model text moved to its own `modelReason` column (schema v2); banner generation counter, 2-minute expiry, validated network. Left as is: v1 resume numbering, runner timeout bypassing reporting, config errors mapping to `network`.

## Review Triage Log

RulesEngine verified against the design for all repeat sequences. Patched: model free text moved to new column modelReason (migration 1→2, capped, never logged); banner generation counter + 2 min expiry; online requires VALIDATED. Left as is: v1 resume numbering, runner timeout bypassing banner, config errors map to network. Migration androidTest not yet run on device.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
