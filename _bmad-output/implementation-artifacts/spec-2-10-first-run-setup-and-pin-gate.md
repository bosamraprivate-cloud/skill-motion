---
title: 'Story 2.10: First-run setup and PIN gate'
type: 'feature'
created: '2026-11-12'
status: 'draft'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The app opens straight into a debug Diagnostics screen: no PIN, no setup, proxy URL and secret come from `local.properties` at build time, and there is no therapist shell or design theme. A real tablet needs a protected therapist mode and a one-time setup.

**Approach:** On first launch a setup flow (welcome → PIN twice → proxy URL + secret) stores everything through `SecureSettings`; afterwards every app start shows the PIN screen before therapist mode. Therapist mode is an RTL shell with a start-side rail (Children, Replay & evaluation, Diagnostics, Settings) using DESIGN tokens and the bundled Cairo font. Settings can change the PIN and proxy. No consent screen (FR-43 removed).

## Boundaries & Constraints

**Always:** PIN is numeric, 4–6 digits, stored only as PBKDF2 (salt, iterations stored with the hash). Therapist mode is unreachable without a PIN. The secret is never shown in clear (masked field, "change" only). Proxy URL + secret come from `SecureSettings` at runtime for `CloudVerifier`/`ProxyProbe`; debug builds may prefill setup fields from the existing `BuildConfig` values. Landscape only. All UI strings in `values/strings.xml` and `values-ar/strings.xml` (Egyptian Arabic, RTL). AD-13 UiState pattern; repositories/settings accessed off the main thread.

**Never:** No consent text or acknowledgement. No zones or framing check (Epic 3, Stories 3.3/3.4). No child list content (Story 2.11 — Children shows an empty-state placeholder). No accounts or network login. No PIN lockout/timer (not in the plan).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Fresh install | no PIN stored | setup flow opens; shell unreachable | N/A |
| PIN mismatch | second entry differs | stays on PIN step, message shown | inline error, no save |
| PIN too short | 3 digits | Continue disabled | N/A |
| Setup done | PIN + URL + secret | lands in shell on Children (empty state) | N/A |
| Relaunch | PIN stored | PIN screen first | N/A |
| Wrong PIN | wrong digits | stays on PIN screen, field cleared, message | no hint of the right PIN |
| Change PIN | current wrong | rejected | inline error |
| Change PIN | current right, new twice | new PIN works, old fails | N/A |
| Secret view | Settings | masked value, never the text | N/A |
| Invalid URL | not https | Continue disabled with hint | N/A |

</frozen-after-approval>

## Code Map

- `android/app/src/main/java/com/aitherapist/dls/data/SecureSettings.kt` -- has PIN hash+salt, proxy URL, encrypted secret; add PIN hashing helper (`PinHasher`, PBKDF2WithHmacSHA256, iterations stored) and `hasPin()`.
- `.../ui/AppNavigation.kt` -- Navigation 3 keys (Diagnostics start, Sample, CharacterPreview); new start logic: Setup or Pin, then Shell.
- `.../ui/therapist/DiagnosticsScreen.kt`, `DiagnosticsViewModel.kt` -- keep; move into the shell's Diagnostics destination; switch `BuildConfig.PROXY_URL/SHARED_SECRET` (lines ~58-67) to `SecureSettings`.
- `.../ui/therapist/CharacterPreview*` -- keep reachable from Diagnostics.
- `.../ui/sample/*` -- AD-13 reference; may stay reachable from Diagnostics.
- `.../ui/theme/{Color,Theme,Type}.kt` -- replace with DESIGN tokens (`Tokens.kt`: child + therapist palettes, level ramp) and Cairo typography (therapist 28/20/16/14sp; child sizes defined for later stories).
- `android/app/build.gradle.kts` lines 38-45 -- `BuildConfig.PROXY_URL/SHARED_SECRET` from local.properties; keep for debug prefill only.
- `AndroidManifest.xml` -- confirm `screenOrientation` landscape and RTL support.

## Tasks & Acceptance

**Execution:**
- [ ] `android/app/src/main/res/font/cairo_*.ttf` + `OFL.txt` -- bundle Cairo (Google Fonts, OFL) regular/semibold/bold; keep the license notice.
- [ ] `ui/theme/Tokens.kt`, `Theme.kt`, `Type.kt`, `Color.kt` -- DESIGN tokens and Cairo type scale; light only.
- [ ] `data/PinHasher.kt` + `SecureSettings.kt` -- hash/verify, `hasPin()`, `setPin`, `verifyPin`.
- [ ] `ui/therapist/Setup{Screen,ViewModel,UiState}.kt` -- welcome, PIN twice, proxy URL + secret; saves and enters the shell.
- [ ] `ui/therapist/Pin{Screen,ViewModel,UiState}.kt` -- numeric pad, verify, clear on wrong.
- [ ] `ui/therapist/TherapistShell.kt` -- RTL start-side `NavigationRail` (Children placeholder empty state, Replay & evaluation placeholder, Diagnostics, Settings).
- [ ] `ui/therapist/Settings{Screen,ViewModel,UiState}.kt` -- change PIN (current + new twice), edit proxy URL, replace secret (masked).
- [ ] `ui/AppNavigation.kt` -- start: Setup if no PIN, else Pin; then Shell; inner destinations as before.
- [ ] `DiagnosticsViewModel.kt` -- proxy URL/secret from `SecureSettings`.
- [ ] strings (en + ar) for all new UI.
- [ ] tests: `PinHasherTest` (salt differs, verify right/wrong, iterations stored), `SetupViewModelTest`, `PinViewModelTest`, `SettingsViewModelTest` covering every matrix row.

**Acceptance Criteria:**
- Given a fresh install on the phone, when the app opens, then setup appears in Egyptian Arabic, RTL, Cairo font, and after it the shell with the rail.
- Given the full gate, when run, then `test lint ktlintCheck koverVerifyCore assembleDebug` pass and `connectedDebugAndroidTest` still passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

PIN length 4–6 digits and "no lockout" are choices not stated in the plan; kept minimal because the tablet stays with the therapist. Debug prefill keeps device testing fast without putting the secret in release builds.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
- `cd android && ./gradlew connectedDebugAndroidTest` -- expected: pass on the phone

**Manual checks (if no CLI):**
- Clear app data, open: setup → shell; relaunch → PIN; wrong PIN rejected; Settings change PIN works.
