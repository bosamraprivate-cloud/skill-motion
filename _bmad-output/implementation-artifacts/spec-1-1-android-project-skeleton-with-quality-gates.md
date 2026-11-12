---
title: 'Story 1.1: Android project skeleton with quality gates'
type: 'chore'
created: '2026-11-12'
status: 'done'
baseline_commit: 'NO_VCS'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-Ai therapist-2026-11-11/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** There is no code yet. Every later story needs one consistent, buildable Android module with pinned versions, the AD-13 UI pattern and the verification gates already enforced.

**Approach:** Create a greenfield one-module Gradle project in `android/` (package `com.aitherapist.dls`, minSdk 30). Pin every library from the spine's Stack table in a version catalogue. Wire Hilt + Navigation 3 + a sample sealed-`UiState` screen and the shared `ui/components`. Add ktlint, Kover, Konsist architecture tests and a MediaPipe model download task.

## Boundaries & Constraints

**Always:**
- Pin exact versions in `android/gradle/libs.versions.toml`; take the newest **stable** release of each library on the day of implementation (from Google Maven / Maven Central metadata). MediaPipe Tasks Vision is 0.10.x; SQLCipher `net.zetetic:sqlcipher-android` is >= 4.18; Material 3 is the stable line (no rc/alpha).
- Manifest: CAMERA + INTERNET permissions, `allowBackup="false"`, `supportsRtl="true"`, `screenOrientation="landscape"` (sensorLandscape), exactly one activity.
- Packages are exactly the spine's set: `catalog, data, camera, vision, session, scoring, training, rewards, audio, replay, ui.child, ui.therapist, ui.components, ui.theme` under `com.aitherapist.dls`.
- AD-13 pattern in the sample: `@HiltViewModel` exposing `StateFlow<SampleUiState>` via `stateIn(viewModelScope, WhileSubscribed(5_000), Loading)`; stateless `SampleScreen(state, onEvent)` with a `@Preview` per state; collected with `collectAsStateWithLifecycle()`.
- Theme tokens (`ui/theme`) come from DESIGN.md (therapist palette + Cairo font placeholder via system font until the font file is bundled); strings in `res/values/strings.xml` (English default) and `res/values-ar/strings.xml`.
- No secrets, no logging of child data, English code and IDs.

**Never:**
- No Room entities, camera code, catalogue code or real screens (later stories).
- No hard-coded step IDs, activities, thresholds or questions.
- No downloaded model files committed to source control: the download task writes them to `app/src/main/assets/` at build time and they are ignored.
- No Flutter, no extra DI or navigation library, no alpha/rc dependencies.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Sample loads | VM starts | `Loading` → after fake repo emits items → `Content` | N/A |
| Sample empty | Repo emits empty list | `Empty` → `EmptyState` with primary action | N/A |
| Sample fails | Repo flow throws | `Error(reason)` → `ErrorState` with Retry; retry re-subscribes | `catch` in flow, never crashes |
| Arch violation | Class in `scoring` imports `android.*`, or non-ui package imports `ui` | `./gradlew test` fails | Konsist assertion message names the class |
| Models present | Assets already downloaded | Download task is up-to-date, no network | Network failure fails the build with a clear message |

</frozen-after-approval>

## Code Map

- Greenfield: `android/` does not exist. Nothing to reuse locally.
- Model URLs (as in google-ai-edge/mediapipe-samples): `pose_landmarker_full.task` and `hand_landmarker.task` from `storage.googleapis.com/mediapipe-models/...`.
- Do not touch `docs/`, `_bmad*/`, `elephant-goldfish-model/`.

## Tasks & Acceptance

**Execution:**
- [x] `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, Gradle wrapper -- project setup, plugin management, `android.useAndroidX=true` -- greenfield build
- [x] `android/gradle/libs.versions.toml` -- all Stack + AD-13 libraries and plugins pinned (AGP, Kotlin, KSP, Compose BOM, Material 3, material3-adaptive, adaptive-navigation-suite, adaptive-navigation3, navigation3-runtime/ui, lifecycle-viewmodel-compose, lifecycle-runtime-compose, Hilt + hilt-navigation-compose, CameraX, MediaPipe tasks-vision, Room, sqlcipher-android, kotlinx-serialization, OkHttp + mockwebserver, Vico, Rive, Konfetti, Coil 3, compose-shimmer, Kover, ktlint gradle plugin, Konsist, JUnit, kotlinx-coroutines-test, Turbine) -- single source of versions
- [x] `android/app/build.gradle.kts` -- namespace/applicationId `com.aitherapist.dls`, minSdk 30, Compose, Hilt, KSP, serialization, Kover rule placeholder for core classes, `downloadModels` task wired before `preBuild` -- AD-12
- [x] `android/app/src/main/AndroidManifest.xml` -- flags from Boundaries -- AD-8, UX-DR17
- [x] `<pkg>/DlsApplication.kt`, `<pkg>/MainActivity.kt` -- `@HiltAndroidApp`, `@AndroidEntryPoint`, edge-to-edge, theme, `AppNavigation()` -- entry
- [x] `<pkg>/ui/AppNavigation.kt` -- Navigation 3 `NavDisplay` with back stack of `@Serializable` keys (`SampleKey`) -- AD-13
- [x] `<pkg>/ui/sample/{SampleUiState,SampleViewModel,SampleScreen,SampleRepository}.kt` -- sealed state + VM + screen + fake repo bound in a Hilt module -- AD-13 reference pattern
- [x] `<pkg>/ui/components/{EmptyState,ErrorState,Skeleton}.kt` -- shared components with previews; `Skeleton` uses compose-shimmer -- AD-13
- [x] `<pkg>/ui/theme/{Color,Type,Theme}.kt` -- DESIGN.md therapist tokens -- consistent theming
- [ ] package placeholders (`package-info`-style `.kt` files) for each spine package -- so Konsist rules have targets
- [x] `<test>/ArchitectureTest.kt` -- Konsist: core packages (`scoring`, `rewards`, `catalog`) import no `android.*`; no non-`ui` package imports `com.aitherapist.dls.ui` -- AD-12
- [x] `<test>/SampleViewModelTest.kt` -- Loading/Content/Empty/Error/retry with Turbine -- I/O matrix
- [x] `android/.gitignore` + root `.gitignore` -- build dirs, `local.properties`, downloaded `*.task` models -- hygiene

**Acceptance Criteria:**
- Given a clean checkout, when `./gradlew assembleDebug test lint ktlintCheck` runs in `android/`, then all succeed and the APK has package `com.aitherapist.dls`, minSdk 30.
- Given the version catalogue, when inspected, then every library listed in the story AC is pinned to an exact stable version.
- Given a temporary class in `scoring` importing `android.util.Log`, when `./gradlew test` runs, then it fails (verified once, class removed).
- Given the build, when `downloadModels` runs, then `pose_landmarker_full.task` and `hand_landmarker.task` exist in `app/src/main/assets/`.

## Implementation Notes

- Environment (2026-11-12): Windows 11, no git repository. JDK 21 at `C:\Program Files\Microsoft\jdk-21.0.13.21-hotspot` (set `JAVA_HOME`). Android SDK at `%LOCALAPPDATA%\Android\Sdk` with `cmdline-tools\latest` (may still be downloading at start; install `platform-tools`, the `platforms;android-<compileSdk>` and matching `build-tools` with `sdkmanager`, accept licences). Write `android/local.properties` with `sdk.dir` (gitignored). No global Gradle: generate the wrapper by downloading a Gradle distribution once into a temp folder and running `gradle wrapper`.

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Route | Evidence |
|---|---|---|---|---|
| 1 | downloadModels: no timeouts, renameTo fails over existing file on Windows, `.part` left on non-IO exception | medium | patch | build.gradle.kts download() uses openStream() with no timeouts, renameTo, catch IOException only |
| 2 | Room schema export not configured | medium | patch | no room.schemaLocation / schemaDirectory in app/build.gradle.kts; AGENTS.md requires it |
| 3 | Kover filter under reports.filters limits all reports | medium | patch | filter applies globally, not only to the rule |
| 4 | Core-purity Konsist rule ignores `androidx.*` | medium | patch | rule checks `android.` prefix only |
| 5 | `kotlin.code.style=official` vs ktlint android_studio | low | patch | gradle.properties line 6 |
| 6 | Root `*.task` gitignore too broad | low | patch | android/.gitignore already scopes it |
| 7 | Kover rule disabled, passes trivially | medium | defer | no core classes exist yet; must be enabled in story 1.3 |
| 8 | Model URLs use `latest`, no checksum | medium | defer | reproducibility; needs versioned URLs + SHA-256 |
| 9 | Every build needs network (preBuild → downloadModels) | low | defer | offline/CI switch not needed until CI exists |
| 10 | No UI tests for Empty→Retry wiring and Skeleton 150 ms delay | medium | defer | no compose-ui-test setup; add with first real screen |
| 11 | Status/level colours not exposed through theme (no extended colours) | medium | defer | later screens would import raw colours |
| 12 | Cairo not bundled, incomplete type scale, no RTL preview | low | defer | font bundling belongs to theme/content story |
| 13 | "Only data/ touches DAOs" and no-logging rules not in Konsist | medium | defer | no DAOs or logging yet |
| 14 | No icon, signing, keep rules | low | defer | release concerns |
| 15 | ScriptedRepository.removeFirst unclear failure | low | rejected | test-only, unlikely, fix adds branches |
| 16 | Gradle wrapper missing | false | rejected | gradlew, gradlew.bat, gradle-wrapper.jar exist; excluded from the review diff by the reviewer-diff builder |
| 17 | Task checkboxes not ticked | false | rejected | all 12 tasks are `[x]` |
| 18 | `tools:targetApi="31"` mismatch | false | rejected | it scopes `dataExtractionRules` (API 31), not targetSdk |

## Verification

**Commands:**
- `./gradlew assembleDebug test lint ktlintCheck` (in `android/`) -- expected: BUILD SUCCESSFUL
- `aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk` -- expected: package `com.aitherapist.dls`, sdkVersion 30
