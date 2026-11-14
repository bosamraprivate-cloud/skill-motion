# Skillmotion

Skillmotion is a tablet app that assesses and trains daily living skills (dressing, eating and drinking, hygiene)
for children, with all child-facing speech and text in Egyptian Arabic. It is a native Android app (Kotlin,
Jetpack Compose). The camera scores each step on the tablet with MediaPipe. Steps the camera cannot judge on its
own go to a stateless Cloudflare Worker that asks DeepSeek, and anything uncertain is recorded as "cannot tell",
never as "not done". All child data stays on the tablet, in Room encrypted with SQLCipher. There is no data backend.

The repository folder is still called `Ai therapist`, and the Kotlin package is `com.aitherapist.dls`.

Planning documents:

- Architecture (binding decisions AD-1 to AD-13): `_bmad-output/planning-artifacts/architecture/architecture-Ai therapist-2026-11-11/ARCHITECTURE-SPINE.md`
- PRD: `_bmad-output/planning-artifacts/prds/prd-Ai therapist-2026-11-11/`
- Epics and stories: `_bmad-output/planning-artifacts/epics.md`
- Design: `docs/designs/daily-living-skills-app.md`
- Docs index: `docs/README.md`

> **Status markers.** Items marked **[PENDING]** do not exist yet. They are described here so the README stays
> complete, but they cannot be followed until the item is delivered.

## Repository layout

| Path | Contents |
|---|---|
| `android/` | The Android app (Gradle, version catalogue in `android/gradle/libs.versions.toml`, Room schemas in `android/app/schemas/`) |
| `android/app/src/main/assets/` | Generated content: `catalog/` (assessment and activities JSON), `character/` (clips), MediaPipe `.task` models, `audio/ar-eg/` **[PENDING: no audio files yet]** |
| `proxy/` | `verify-proxy` Cloudflare Worker (TypeScript, `openai` SDK pointed at DeepSeek) |
| `tools/catalog-builder/` | Builds `assets/catalog/*.json` from `docs/reference/` plus `strings.ar-eg.json` and `detectors.json` |
| `tools/audio-gen/` | ElevenLabs one-time audio generation (resumable, with `manifest.json` and `review.csv`) |
| `tools/art/` | Character clip export (`export_clips.py`) and background removal (`cutout_video.py`) |
| `tools/eval/` | Clip manifest tools, the M3 accuracy report and the Goal 8 metrics (`compute_metrics.py`) |
| `art/` | Original character sheets and animation sources (see `art/README.md`) |
| `eval/` | Evaluation data and protocol only: `clips.csv`, `recording-protocol.md` (clips and consent forms are git-ignored) |
| `docs/` | Design, reviews, wireframes, clinical sources (`docs/reference/`) |
| `THIRD_PARTY_NOTICES.md` | Licence notices for adapted open-source code |

## Prerequisites

- Python 3.14, with a virtual environment at `tools/.venv`:
  `python -m venv tools/.venv`, then `tools/.venv/Scripts/python.exe -m pip install -r tools/requirements.txt -r tools/audio-gen/requirements.txt`
  (on macOS or Linux use `tools/.venv/bin/python`).
- JDK 17 or newer and the Android SDK (Android Studio). The Gradle wrapper is in `android/`.
- Node.js (current LTS) and npm for `proxy/`.
- A Cloudflare account (Wrangler login) and a DeepSeek API account.
- An ElevenLabs account, needed only to generate audio.
- For `tools/art/cutout_video.py` only: ffmpeg, plus a CUDA GPU for Robust Video Matting.
- An Android tablet (11-inch landscape is the target, Medium width the minimum) with a front camera, a stand, and optionally a Bluetooth button.

## Build order (AD-1)

Run from the repository root in this order: audio, then character art, then the catalogue.

```sh
# 1. Audio (Egyptian Arabic, ElevenLabs). The key comes only from the environment, never from a file.
export ELEVENLABS_API_KEY=...            # PowerShell: $env:ELEVENLABS_API_KEY = "..."
tools/.venv/Scripts/python.exe tools/audio-gen/generate_audio.py --dry-run   # character totals, no API calls
tools/.venv/Scripts/python.exe tools/audio-gen/generate_audio.py             # writes assets/audio/ar-eg/<id>.mp3 and <id>.slow.mp3
#    Exit 3 means the quota ran out. Run again later, possibly with another account's key: it resumes.
#    Listen to the files and mark any that need regenerating in tools/audio-gen/review.csv.

# 2. Character clips (into android/app/src/main/assets/character/, plus index.json)
tools/.venv/Scripts/python.exe tools/art/export_clips.py

# 3. Catalogue (assessment.json and activities.json). Never hand-edit the output.
tools/.venv/Scripts/python.exe tools/catalog-builder/build_catalog.py      # must exit 0
#    --check parses, validates and prints counts without writing.
```

**[PENDING]** Audio has not been generated yet (the ElevenLabs run is blocked), so
`android/app/src/main/assets/audio/ar-eg/` is empty and the app plays no instruction audio.
The script named in older notes, `tools/art/export_illustrations.py`, does not exist; the art step is
`tools/art/export_clips.py`.

To remove the background from a newly generated clip (only when adding art), run:
`tools/.venv/Scripts/python.exe tools/art/cutout_video.py <src.mp4> <out.webp> [--size 960 --speed 1.25 --quality 85]`.

## Build and install the app

```sh
cd android
./gradlew assembleDebug                      # the build downloads the MediaPipe models into assets
./gradlew installDebug                       # with the tablet connected over USB/ADB (or adb install the APK)
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.
### Release build

Release builds use R8 (minify + resource shrinking, rules in `android/app/proguard-rules.pro`) and strip debug-only tools (Replay, Try step, Character preview, the Developer section, the debug result strip and `DlsVerdict` logs).

Create the upload key once, outside the repo, and keep it backed up (losing it means you cannot update the app):

```
keytool -genkeypair -v -keystore skillmotion-release.jks -alias skillmotion -keyalg RSA -keysize 4096 -validity 10000
```

Then create the git-ignored `android/keystore.properties` (paths are relative to `android/`):

```
storeFile=../../keys/skillmotion-release.jks
storePassword=...
keyAlias=skillmotion
keyPassword=...
```

or set `DLS_KEYSTORE_FILE`, `DLS_KEYSTORE_PASSWORD`, `DLS_KEY_ALIAS` and `DLS_KEY_PASSWORD`. Build with `./gradlew :app:assembleRelease` (output in `android/app/build/outputs/apk/release/`). Without a keystore, Gradle prints a warning and signs the release with the debug key, which is only for local testing. `keystore.properties`, `*.jks` and `*.keystore` are git-ignored; never commit them.

## Deploy the proxy

The Worker is `proxy/` (`verify-proxy`). It has no storage bindings, logs nothing and has Workers observability
switched off. Its API is described in `proxy/README.md`.

```sh
cd proxy
npm ci
npm test && npx tsc --noEmit && npm run build    # build = wrangler deploy --dry-run
npx wrangler login
npx wrangler secret put DEEPSEEK_API_KEY         # the DeepSeek API key
npx wrangler secret put SHARED_SECRET            # a long random string; the same value is entered on the tablet
npx wrangler deploy                              # prints the https://verify-proxy.<account>.workers.dev URL
```

- Never put either secret in `wrangler.toml`, the repo or a log. For local `npm run dev`, use a git-ignored `proxy/.dev.vars`.
- **Spend limit:** set a monthly spending limit (or a small prepaid balance) in the DeepSeek console before
  first use. The Worker itself has no limit.
- `MODEL_ID` is a plain variable in `wrangler.toml` (`deepseek-flash`). A prompt or contract change bumps
  `PROMPT_VERSION` in `proxy/src/prompt.ts`.

## First-run setup on the tablet (CUJ 1)

1. Open Skillmotion. The setup screen asks for a therapist **PIN**. Therapist mode is behind it; child mode uses Android screen pinning.
2. Enter the **proxy URL** (from `wrangler deploy`) and the **shared secret** (the `SHARED_SECRET` value). The secret is stored encrypted with the Keystore key.
3. Open **Diagnostics** to check that the proxy can be reached. If it shows "Proxy rejected the secret (401)", re-enter the secret in Settings.
4. Before each task, run the **framing check**. Place the tablet as described under "Physical setup", then draw the **zones**
   (`mat` for the cup, `sink` for handwashing, `place` for the jacket) as rectangles on the camera preview. They are saved in settings.
   The check shows a green outline when the needed landmarks are visible.
5. Create a child profile (name and age; the child code is generated automatically and kept hidden).

## Bluetooth button

When the child is at a distance (jacket, sink), they can press Done with a Bluetooth button instead of touching the screen.

1. Put the button in pairing mode. Then on the tablet go to Android **Settings > Connected devices > Pair new device** and pair it.
2. No setting in the app is needed: the app treats the button's key press as the on-screen Done.
3. Try it on a training step before an assessment.

## Physical setup per framing

| Framing | Tasks | Tablet position | Scene |
|---|---|---|---|
| `table` | cup (and other eating/drinking tasks) | On a table stand about 60 cm from the child, at face height, front camera | Child seated on a chair at a table; cup on the printed placement mat (`mat` zone) |
| `standing` | jacket on/off and other dressing | On a floor stand about 1.8 m away, at chest height | Child standing; items ready (`place` zone for taking the jacket off) |
| `sink` | handwashing | On a shelf beside the sink, about 1 m away | Child standing at the sink; soap and towel ready (`sink` zone) |

Use even, bright light from the front or side, with no window behind the child. The front-camera image is un-mirrored before any left/right rule.

## Running the evaluation

Follow `eval/recording-protocol.md` for volunteers, consent (forms in the git-ignored `eval/consent/`), framing
and file names. The tuning procedure is in `tools/eval/README.md`.

```sh
tools/.venv/Scripts/python.exe tools/eval/generate_clips.py                                    # rebuild the shot list after a catalogue change
tools/.venv/Scripts/python.exe tools/eval/validate_clips.py eval/clips.csv --clips-dir eval/clips
```

1. **Replay:** in therapist mode, open Replay and import the clips from `eval/clips/` (turn on "Import mirrored clips" for a mirrored batch). Then replay or re-score.
2. **M3 tuning (optional):** on the Replay screen tap "Export results", then run
   `tools/.venv/Scripts/python.exe tools/eval/accuracy_report.py <exported.csv>`. This updates `docs/designs/m3-accuracy.md`.
3. **Blind review:** a therapist rates every attempt from its frames or clip only, without seeing the verdict or level.
4. **Export:** from Settings, export the anonymised `results.csv` through the system file picker. It contains no names.
5. **Metrics:** run `tools/.venv/Scripts/python.exe tools/eval/compute_metrics.py results.csv [--clips eval/clips.csv] [--out docs/designs/evaluation-metrics.md]`.
   Goal 8 targets: at least 80% agreement with the blind rating, quadratic-weighted kappa of at least 0.6, and at most 15% cannot tell.

**[PENDING]** Volunteer clips have not been recorded, and the Goal 8 run (Story 5.9) has not been done. `docs/designs/m3-accuracy.md` is still "pending clips".

## Verification

A change is done only when every command below passes (spine "Build and verification").

| Step | Command (directory) |
|---|---|
| Catalogue | `python tools/catalog-builder/build_catalog.py` (root), exits 0 |
| Build | `./gradlew assembleDebug` (android/); `npm run build` (proxy/, = `wrangler deploy --dry-run`) |
| Unit tests | `./gradlew test` (android/); `pytest tools/` (root); `npm test` (proxy/) |
| Instrumented | `./gradlew connectedAndroidTest` (android/, device attached) |
| Lint and types | `./gradlew lint ktlintCheck` (android/); `ruff check tools eval` (root); `npx tsc --noEmit` (proxy/) |
| Coverage | `./gradlew koverVerify` (android/; at least 90% on RulesEngine, Geometry, RepCounter) |
| Accuracy | `python tools/eval/compute_metrics.py results.csv` (root) against Goal 8 |

The final gate also needs two manual end-to-end checks, logged with the run: run a task with Wi-Fi off (camera
steps still score and proxy steps become cannot tell), and close then reopen the app in the middle of a task (it resumes).

## Open-source credits and licences

Android libraries (versions in `android/gradle/libs.versions.toml`):

| Library | Licence |
|---|---|
| AndroidX: Core, Activity, Compose (BOM), Material 3, Material 3 Adaptive and Navigation Suite, Navigation 3, Lifecycle, CameraX, Room, DataStore, Hilt Navigation, Test | Apache-2.0 |
| Kotlin, kotlinx.serialization, kotlinx.coroutines | Apache-2.0 |
| Hilt / Dagger (Google) | Apache-2.0 |
| MediaPipe Tasks Vision and the pose/hand landmarker models (Google) | Apache-2.0 |
| SQLCipher for Android (Zetetic) | BSD-3-Clause-style (Zetetic licence) |
| OkHttp and MockWebServer (Square) | Apache-2.0 |
| Vico (Patryk Goworowski and Patrick Michalik) | Apache-2.0 |
| Konfetti (Dion Segijn) | ISC |
| Coil 3 | Apache-2.0 |
| compose-shimmer (Valentin Ilk) | Apache-2.0 |
| Konsist, Turbine, JUnit 4 (tests only) | Apache-2.0 / Apache-2.0 / EPL-1.0 |
| Build plugins: Android Gradle Plugin, KSP, Kover, ktlint-gradle | Apache-2.0 / Apache-2.0 / Apache-2.0 / MIT |

Proxy (`proxy/package.json`): `openai` (Apache-2.0), plus the development tools `wrangler`, `@cloudflare/workers-types`
(MIT OR Apache-2.0), `typescript` (Apache-2.0) and `vitest` (MIT).
Python tools (`tools/requirements.txt`): pytest (MIT), ruff (MIT), jsonschema (MIT). Clip cutouts use
Robust Video Matting (GPL-3.0). It is used as an offline tool only and is not shipped in the app.

Reused code: `google-ai-edge/mediapipe-samples` (Apache-2.0). The adapted files are listed with their notices in `THIRD_PARTY_NOTICES.md`.

Content and services: instruction audio is generated with ElevenLabs (voice "Fatima", Egyptian Arabic). The characters are original art
(see `art/README.md`). Verification calls DeepSeek.

**Permissions:** `docs/permissions/` **[PENDING: the folder does not exist yet]**. No code has been copied from a
repository that needs an owner's written permission. Any such permission goes there before the code is used.

The same credits are shown in the app under Settings > About (`ui/therapist/AboutScreen.kt`).
