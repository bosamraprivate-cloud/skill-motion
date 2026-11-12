---
title: 'Story 2.9: Encrypted database, secure settings and frame store'
type: 'feature'
created: '2026-11-12'
status: 'draft'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The app has no storage yet; every later story (profiles, assessment runner, reports) needs encrypted child, session and attempt data, secure settings and encrypted frames, and AD-8/AD-9 forbid anything readable outside the app.

**Approach:** Room on SQLCipher with a Keystore-protected passphrase, a DataStore-backed `SecureSettings`, and an AES-GCM `FrameStore`, all in `data/`, with schema v1 exported and instrumented tests on the device.

## Boundaries & Constraints

**Always:** Keystore AES-256-GCM key alias `dls_master`, non-exportable; 32 random-byte SQLCipher passphrase generated once, stored encrypted in app-private DataStore. `exportSchema = true`, v1 JSON committed in `android/app/schemas/`; no destructive migration. Enums stored lower_snake (`not_done`, `cannot_tell`, `full`/`calm`/`off`, `boy`/`girl`). Only `data/` touches DAOs (Konsist rule). Never log child name, code, frame bytes or settings values. Newest stable versions of any added library.

**Never:** No tables beyond `child`, `session`, `attempt` (reward, correction, blind_rating, training_result come with their stories). No UI. No cloud or backup of data (`allowBackup` stays off). No plain-text secret anywhere on disk.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| First run | no DataStore entry | passphrase generated, encrypted, stored; DB opens | N/A |
| Second run | stored passphrase | same DB opens, earlier rows readable | N/A |
| Wrong key | DB opened with another passphrase | open fails | exception, no data |
| Frame round trip | `write(s, a, n, jpeg)` then `read` | identical bytes; file `files/frames/<s>/<a>-<n>.bin` = IV(12) ‖ GCM | N/A |
| Tampered frame | one byte flipped | read fails | `AEADBadTagException` surfaced, no partial bytes |
| Child delete | child with sessions and attempts | cascade removes them | N/A |
| Settings | PIN hash, proxy URL, secret, zones | round-trip equal; secret stored encrypted | N/A |

</frozen-after-approval>

## Code Map

- `android/gradle/libs.versions.toml` -- Room 2.8.5, SQLCipher 4.20.0, KSP already present; add DataStore (preferences) and androidx.test (runner, ext-junit) for instrumented tests.
- `android/app/build.gradle.kts` -- `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` exists (line ~223); Room deps present; add `androidTestImplementation` deps.
- `android/app/src/main/java/com/aitherapist/dls/data/PackageMarker.kt` -- delete when real code lands.
- `docs/designs/daily-living-skills-app.md` §Data model (child/session/attempt columns) and §Encryption -- column and crypto source of truth.
- `android/app/src/test/.../ArchitectureTest.kt` (Konsist) -- must keep passing; DAOs only in `data/`.
- `android/app/src/main/java/com/aitherapist/dls/ui/child/CharacterView.kt` -- `EffectLevel` enum lives here today; move it to `data/` (or a shared model) so the entity can use it, keeping UI imports working.

## Tasks & Acceptance

**Execution:**
- [ ] `libs.versions.toml`, `app/build.gradle.kts` -- add DataStore preferences and androidx.test runner/ext-junit (latest stable).
- [ ] `data/Entities.kt` -- `child` (design columns + `characterVariant` `boy`/`girl`, required, and `isEval` bool default false), `session`, `attempt` (design columns + nullable `clipFile`); FKs with CASCADE; index (`sessionId`,`stepId`); unique `child.code`.
- [ ] `data/Converters.kt` -- lower_snake enum converters, `framePaths` JSON list.
- [ ] `data/Daos.kt`, `data/AppDatabase.kt` -- DAOs (insert/query/delete child, session, attempt); DB v1, `exportSchema = true`, SQLCipher `SupportOpenHelperFactory`.
- [ ] `data/KeyManager.kt` -- Keystore `dls_master` create/get, encrypt/decrypt (IV ‖ ciphertext).
- [ ] `data/SecureSettings.kt` -- DataStore: passphrase (encrypted), PIN hash+salt, proxy URL, shared secret (encrypted), zones.
- [ ] `data/FrameStore.kt` -- write/read/deleteSession per the matrix.
- [ ] `data/Repositories.kt`, Hilt module -- provide DB, DAOs (internal to `data/`), repositories.
- [ ] `android/app/schemas/` -- commit generated v1 JSON.
- [ ] `<itest>/DatabaseTest.kt`, `SecureSettingsTest.kt`, `FrameStoreTest.kt` -- every matrix row; unit tests for converters.

**Acceptance Criteria:**
- Given the phone connected, when `./gradlew connectedDebugAndroidTest` runs, then all three instrumented test classes pass.
- Given the full gate, when run, then `test lint ktlintCheck koverVerifyCore assembleDebug` passes and schema v1 JSON is in git.
- Given the app data folder, when inspected, then the DB file is not readable as SQLite without the key.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

`characterVariant` is added now because AD-11 stores the chosen character on the child profile; making it required (no default) forces Story 2.11 to ask the therapist. `KeyManager` is shared by the passphrase, the secret and frames so there is one key path to test.

## Verification

**Commands:**
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
- `cd android && ./gradlew connectedDebugAndroidTest` -- expected: all instrumented tests pass on the phone
