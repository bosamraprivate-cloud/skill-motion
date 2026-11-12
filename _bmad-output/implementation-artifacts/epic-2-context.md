# Epic 2 Context: Every task runs for a child, safely stored (M2)

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

The therapist can set up the tablet (PIN, proxy address and secret, consent), add children, and run any of the 15 tasks / 147 steps in locked child mode with an illustration, one Egyptian Arabic sentence and audio. Every fact is saved encrypted on the tablet, and an interrupted session can continue. The epic also covers the content pipeline (catalogue builder, art, audio, stickers, sound effects). Exit: all 147 steps play with illustration, Arabic text and audio, and the builder tests pass. Camera judging of real steps comes in Epic 3. Here, verifiers are fakes, or `none` for steps with no detector.

## Stories

- Story 2.1: Catalogue builder parses the 147 assessment steps
- Story 2.2: Catalogue builder parses the 148 activities
- Story 2.3: Character sheet and Rive character (art pipeline)
- Story 2.4: Step and activity illustrations with export script
- Story 2.5: Arabic strings and ElevenLabs audio generation
- Story 2.6: Stickers and sound effects
- Story 2.7: Catalogue validation, merge and version stamp
- Story 2.8: Catalogue repository in the app
- Story 2.9: Encrypted database, secure settings and frame store
- Story 2.10: First-run setup, PIN gate and consent
- Story 2.11: Child profiles with effect level and cascade delete
- Story 2.12: Attempt window and prompt ladder (pure)
- Story 2.13: Assessment runner state machine with Rule 1
- Story 2.14: Child step screen with audio, prompts and locked mode
- Story 2.15: Pause, resume and continue after interruption
- Story 2.16: Start assessment, content-only tasks and character walkthrough
- Story 2.17: M2 content completeness check

## Requirements & Constraints

- **Catalogue source:** content comes only from `docs/reference/`. Expected counts: 147 steps in 15 tasks (eating 42, drinking 38, hygiene 27, dressing 40) and 148 activities (mild 48, moderate 50, severe 50). Domain tags are copied verbatim; "A + B" becomes two tags, and "Not tagged in source" becomes `[]`.
- **Build failures:** the build fails, naming the row or ID, on any of these: an unknown tag, a duplicate ID, a detector that fails the schema, or a step missing Arabic text, normal audio, slow audio or its illustration. A missing activity audio file does not fail the build. Never invent clinical content.
- **Setup and PIN:** therapist mode cannot open before a PIN exists. The PIN is stored only as a salted PBKDF2 hash, and changing it requires the current PIN. The secret is always masked. The consent text names DeepSeek as the receiver of cropped frames and is acknowledged at setup and per child profile.
- **Child profiles:** each child has a name, a unique code and an age. Effect level is `full`, `calm` or `off` (default `calm`). Deleting a child cascades to every child-owned row and to the child's frame folders in the same operation. The hidden eval child is never listed.
- **Attempt window:** opens when the audio ends. It closes on Done, at 45 s, or once at least 20 s have passed with no movement for 2 s. Movement means the maximum wrist or elbow displacement over the last 0.5 s is strictly greater than 0.03 x shoulder width.
- **Prompt ladder:** levels 1 to 3. Help moves to the next level, counts as a prompt and sets `helpPressed`. Repeat replays the current level and does not count as a prompt.
- **Rule 1:** done on attempt 1 at prompt level 1 gives level 3. Done later, or after Help, gives level 2. Three not-done attempts give level 1. Cannot tell repeats the attempt once, and a second cannot tell means not scored. Method `none` gives not scored. A failed step never stops the task, and doubt is never `not_done`.
- **Saving and resume:** every state change is saved immediately. Pause or resume restarts the current attempt. A Continue card after the app closes discards the interrupted attempt. Content-only tasks run fully, with every step not scored. A walkthrough of the whole task plays at the end.
- **Privacy:** all data is encrypted with Keystore keys, backup is disabled, and there is no external store. Logs never contain a child name, code, frame or request body.
- **Child UI:** RTL Egyptian Arabic, no red or orange, and no scores, levels, countdowns, errors or network states. Touch targets are at least 120 dp, and Done is at least 220x160 dp.

## Technical Decisions

- **Content as data (AD-1):** build order is audio-gen, then `tools/art/export_illustrations.py`, then `tools/catalog-builder/build_catalog.py`. The generated `assets/catalog/*.json` are never hand-edited. Kotlin outside tests has no step or activity ID literals, and a test enforces this.
- **IDs and versions (AD-2):**
  - Step IDs are `<area>.<task>.<NN>`, with areas `dress|eat|drink|hygiene` and the task IDs listed in the spine's Conventions.
  - Activity IDs are `<plan>.<domain>.<NN>`, with domains `rom|coord|hand|midline|planning`.
  - `catalogVersion` is the first 12 hex characters of SHA-256 over `assessment.json` bytes followed by `activities.json` bytes. It lives in a separate `version.json`.
  - `RULES_VERSION` is a constant in `RulesEngine`.
  - Each session stores `catalogVersion` and `rulesVersion`.
- **Check model (AD-4):** `Check` is a sealed type with discriminator `type`, and `Criterion` uses `kind` (`reps|hold`). The JSON Schema in `tools/catalog-builder/schema/` is the contract. An unknown type fails both the Python build and the Kotlin load. `detectors.json` defaults every step to `none` in this epic.
- **Data (AD-8):**
  - Room over SQLCipher (`SupportOpenHelperFactory`).
  - The passphrase is 32 random bytes, encrypted with the Keystore AES-256-GCM key `dls_master` and kept in DataStore `SecureSettings`, together with the PIN hash and salt, proxy URL, encrypted secret and zones.
  - Frame files are `IV(12) || AES-GCM(JPEG)` at `files/frames/<sessionId>/<attemptId>-<n>.bin`. They are written only together with their attempt row.
  - Tables `child`, `session` and `attempt` start at version 1, with nullable `attempt.clipFile` and `child.isEval`.
  - `exportSchema = true` with committed schemas, explicit migrations and no destructive migration.
  - Enums serialise as lower_snake. `allowBackup="false"`. Only `data/` touches DAOs.
- **One writer per table (AD-9):** only `AssessmentRunner` writes `session` and `attempt`, including `session.state`. There is at most one running or paused session per child, and starting a new one sets the old one to `abandoned`. Only the therapist profile screens write `child`.
- **Runner (AD-10):** `StateFlow<RunnerState>` with the events `AudioEnded`, `Done`, `Help`, `Repeat`, `Pause`, `Resume`, `WindowTimeout` and `VerdictReady`. It runs SHOW → LISTEN → ATTEMPT → JUDGE with an injected clock. `stepIndex`, `attemptNumber` and `promptLevel` are persisted before each emit. The runner advances only by calling `stepLevel` on saved rows. A Bluetooth key press maps to `Done`, debounced 1 s.
- **Attempt row fields (AD-6):** number, promptLevel, helpPressed, verdict, reason, verifier, modelId, promptVersion, latencyMs, framePaths, createdAt.
- **Rewards in this epic (AD-11):** the Rive state machine inputs are `idle, talk, point, celebrate, celebrateSmall, celebrateBig, encourage`. Reward effects themselves are Epic 4; in this epic, StarBar is display only.
- **UI (AD-13):** one ViewModel per screen exposing a sealed UiState (`Loading`, `Empty`, `Content`, `Error`) via `stateIn(WhileSubscribed(5_000))`. One-off events go through a Channel. Navigation 3 uses typed keys. The therapist shell uses a NavigationSuiteScaffold rail and list-detail panes. Child mode is a single destination driven by `RunnerState`.
- **Tools:** Python 3.11 with `pytest tools/` and `ruff check tools`. `ELEVENLABS_API_KEY` comes from the environment only. Audio is regenerated only when the text hash changes.

## UX & Interaction Patterns

- **Tokens:** child palette is sage Done #5E9C8A, sky Help #7FA7C9, lavender Repeat #B9A6D3, star #E8C26A, sand, and ink #2E3A40 on warm off-white. The therapist palette has a level ramp and a corrected purple. Cairo font is bundled. Type sizes are 40 / 32 / 96 sp for the child and 28 / 20 / 16 / 14 sp for the therapist. 8 dp grid, landscape only, light theme only.
- **Step screen:**
  - A 64 dp top band holds the star bar, the schedule dots and the adult corner.
  - The illustration takes about 60% of the width, with the character beside it and the sentence below.
  - The bottom band has Done at the start side and Help and Repeat at the end side, labelled خلصت / ساعدني / تاني.
  - Done is dimmed, not hidden, while audio plays.
- **Prompt levels:** level 1 is the character in `talk`. Level 2 replays the audio, switches the character to `point` and enlarges the illustration by 15%. Level 3 shows the illustration full screen with the movement animation and slow audio.
- **Child-facing states:** judging shows the idle "thinking" pose with no text. Cannot tell and network loss appear only as a neutral retry line. Paused means the character sits and the screen dims.
- **Starting and pausing:** the task start screen shows the character greeting and يلا نبدأ. A 2 s long-press on the adult corner opens the pause sheet (pause, resume, or exit with PIN). Screen pinning locks child mode.
- **Therapist shell:** RTL, with a start-side rail (Children, Replay & evaluation, Diagnostics, Settings). Empty states show one message and one primary action. The Continue card appears in the child list.
- **Copy:** child copy never says wrong, no or try harder.

## Cross-Story Dependencies

- **Content chain:**
  - 2.1 and 2.2 come before 2.7.
  - 2.3 comes before 2.4.
  - 2.4, 2.5 and 2.6 provide the assets 2.7 validates.
  - 2.7 comes before 2.8, which uses the existing `catalog/CatalogModels.kt`.
  - 2.17 needs everything.
- **App chain:**
  - 2.9 comes before 2.10, 2.11 and 2.13.
  - 2.12 comes before 2.13, which comes before 2.14, 2.15 and 2.16.
  - 2.10 provides the PIN used by the 2.15 exit and by gating Diagnostics.
- **Epic 1 code reused:** Hilt, Navigation 3, the UiState pattern, `vision/` (VisibilityGate and CloudVerifier, wired into the runner only in Epic 3), and the Diagnostics screen.
- **Deferred Epic 1 work to absorb:**
  - **2.10 (setup and shell):**
    - Move the proxy URL and shared secret from debug BuildConfig into encrypted settings entered at first-run setup.
    - Bundle the Cairo font, complete the type scale and add RTL/ar previews.
    - Expose the level and status colours through an extended colour scheme CompositionLocal.
    - Move Diagnostics behind the therapist PIN and shell; it is currently the start destination, and child mode must never open on it.
    - Optionally handle a first camera denial with a rationale before sending the user to settings.
  - **2.9 (data):** add the Konsist rules that only `data/` uses DAOs and that production code has no `android.util.Log` or `println`.
  - **2.7 and 2.8 (catalogue):**
    - Use shared JSON fixtures validated by both `check.schema.json` (pytest) and Kotlin decoding. Add `Step` to the schema, and align the zone enum and the reps target type.
    - At build and decode time, reject side-less landmarks without `side`, unknown landmark names and method/check mismatches.
  - **2.12 and 2.13 (runner):** optionally move the pure vision code into a dedicated package so the Konsist purity rule does not rely on file names.
  - **Any story touching Compose:** add Compose UI test setup (Empty/Error states send Retry; the skeleton stays hidden before 150 ms).
  - **Left for Epic 3:** model URL pinning and SHA-256 checks, an offline switch for model downloads, overlay ratio, bitmap reuse, Diagnostics ViewModel tests and proxy prompt tuning.
