---
title: 'Story 5.5: Diagnostics screen'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '0f9818c5e018eaf919776552f78904b197b8882e'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Diagnostics is still the Epic 1 spike; a therapist cannot check camera, landmark and proxy health before a session.

**Approach:** Rebuild `DiagnosticsScreen` as a therapist health page (Settings → Diagnostics, visible in release too, behind the PIN like all therapist screens): live front-camera fps; per-landmark-group visibility (head, shoulders, arms/wrists, hips, hands) as calm bars with words; proxy reachability + round-trip latency via a `CloudVerifier`/`ProxyProbe` health ping (no frames, no child data); the cannot-tell rate over saved attempts excluding verifier `none` (assessment sessions, non-eval child); clear messages: wrong secret → exactly "Proxy rejected the secret (401). Re-enter it in Settings." (Arabic equivalent in values-ar), proxy unreachable / not configured / timeout each with a short fix hint. The spike's developer tools stay in a debug-only "Developer" section.

## Boundaries & Constraints

**Always:** Camera and landmarkers released when leaving the screen; ping only on open and on a "Check again" button (no polling loop). No secret or URL printed in logs. Western digits, RTL, strings ar/en, therapist tokens (no red; status by word + icon + colour). Tests for the ping result mapping (200 / 401 / timeout / IO / not configured) and the cannot-tell rate query.

**Never:** No edits to ui/child, Progress tab, export or blind review files (parallel stories). No schema change.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Healthy | camera + proxy ok | fps, visibility bars, "reachable · 420 ms" | N/A |
| Wrong secret | 401 | exact rejected-secret message | N/A |
| No proxy set | missing settings | "Proxy not set up" + go to Settings | N/A |
| Offline | IO error | "No internet" hint | N/A |
| Timeout | > 8 s | timeout hint | N/A |
| Cannot-tell rate | attempts | % excluding `none`, with counts | none → "No attempts yet" |
| Camera denied | permission | explanation + retry | N/A |

</frozen-after-approval>

## Code Map

- `android/.../ui/therapist/DiagnosticsScreen.kt`, `DiagnosticsViewModel.kt`, `DiagnosticsUiState.kt`, `SettingsScreen.kt` (entry).
- `android/.../vision/CloudVerifier.kt`, `ProxyProbe.kt`, `FpsMeter.kt`, `Landmarkers.kt`; `data/Daos.kt`/`Repositories.kt` (cannot-tell rate query).

## Tasks & Acceptance

**Execution:**
- [x] Health ping mapping + tests.
- [x] Cannot-tell rate query + test.
- [x] Diagnostics UI (fps, visibility groups, proxy, rate), developer section debug-only; strings.

**Acceptance Criteria:**
- Given the coordinator's gate, when `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` runs, then it passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Ping verified never to reach DeepSeek (401 before body; empty body → 400 malformed_body before any upstream call). 6 findings patched: OK only on 400 malformed_body, 2xx unexpected, rate counts done sessions only, unreadable settings state, cancellable ping, ping and query in parallel. AttemptStatsTest pending a device run.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL (run by the coordinator)
