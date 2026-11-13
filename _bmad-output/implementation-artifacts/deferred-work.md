- source_spec: `spec-1-1-android-project-skeleton-with-quality-gates.md`
  summary: Enable the Kover 90% rule for Geometry/RulesEngine/RepCounter (remove disabled = true) in story 1.3, and make the gate run `koverVerifyCore` (custom `core` variant) instead of `koverVerify`.
  evidence: Rule is disabled and koverVerify passes trivially (review of story 1.1).
- source_spec: `spec-1-1-android-project-skeleton-with-quality-gates.md`
  summary: Pin MediaPipe model URLs to versioned paths and verify SHA-256 in downloadModels.
  evidence: URLs use `latest`; corrupt or changed models go undetected.
- source_spec: `spec-1-1-android-project-skeleton-with-quality-gates.md`
  summary: Add an offline/CI switch so test/lint do not require downloading models.
  evidence: preBuild depends on downloadModels.
- source_spec: `spec-1-1-android-project-skeleton-with-quality-gates.md`
  summary: Add Compose UI tests: Empty/Error actions send Retry; Skeleton hidden before 150 ms.
  evidence: No UI test setup in story 1.1.
- source_spec: `spec-1-1-android-project-skeleton-with-quality-gates.md`
  summary: Expose level/status colours via an extended colour scheme CompositionLocal in DlsTheme.
  evidence: Level3/2/1, NotScored, CannotTell, Corrected, Warning are raw top-level vals.
- source_spec: `spec-1-1-android-project-skeleton-with-quality-gates.md`
  summary: Bundle Cairo font, complete the type scale, add RTL/ar previews.
  evidence: Theme uses FontFamily.Default.
- source_spec: `spec-1-1-android-project-skeleton-with-quality-gates.md`
  summary: Add Konsist rules: only data/ uses DAOs; no android.util.Log/println in production code.
  evidence: Required by AGENTS.md; nothing to check yet.
- source_spec: `spec-1-1-android-project-skeleton-with-quality-gates.md`
  summary: Add app icon, release signing config and keep rules before minify.
  evidence: Lint warning; release not configured.
- source_spec: `spec-1-2-camera-feed-and-landmarkers-with-frame-rate-probe.md`
  summary: Add DiagnosticsViewModel, CameraController and GPU-to-CPU fallback tests (needs a Landmarkers seam).
  evidence: No tests for the spike screen logic (review of 1.2).
- source_spec: `spec-1-2-camera-feed-and-landmarkers-with-frame-rate-probe.md`
  summary: Make the landmark overlay follow the real analysis frame ratio and PreviewView transform.
  evidence: Overlay assumes 4:3.
- source_spec: `spec-1-2-camera-feed-and-landmarkers-with-frame-rate-probe.md`
  summary: Reuse bitmaps or use MediaPipe rotation to avoid two allocations per frame.
  evidence: Per-frame allocation may lower measured fps.
- source_spec: `spec-1-2-camera-feed-and-landmarkers-with-frame-rate-probe.md`
  summary: Move Diagnostics behind the therapist PIN/shell; child mode must never open on it.
  evidence: Diagnostics is currently the start destination.
- source_spec: `spec-1-2-camera-feed-and-landmarkers-with-frame-rate-probe.md`
  summary: Handle first camera denial with re-prompt (rationale) before sending to settings.
  evidence: Currently goes straight to settings.
- source_spec: `spec-1-3-geometry-check-model-and-first-local-verdict.md`
  summary: Shared JSON fixtures validated by both check.schema.json (pytest) and Kotlin decoding; add Step to schema; align zone enum and reps target type.
  evidence: Schema and CatalogModels can drift (review 1.3).
- source_spec: `spec-1-3-geometry-check-model-and-first-local-verdict.md`
  summary: Reject side-less landmarks without side, unknown landmark names and method/check mismatches at catalogue build/decode time.
  evidence: Currently surface only as not_judgeable at runtime.
- source_spec: `spec-1-3-geometry-check-model-and-first-local-verdict.md`
  summary: Add DiagnosticsViewModel attempt-flow tests with a Landmarkers seam and fake clock.
  evidence: No tests for spike attempt flow.
- source_spec: `spec-1-3-geometry-check-model-and-first-local-verdict.md`
  summary: Move pure vision code into a dedicated package so the Konsist purity rule does not depend on file names.
  evidence: PURE_VISION_FILES list is name-based.
- source_spec: `spec-1-5-cloudverifier-and-first-cloud-verdict-for-a-cup-step.md`
  summary: Extract attempt assembly from DiagnosticsViewModel into a testable helper and test cloud routing, frame release and model fields.
  evidence: No ViewModel tests for the cloud spike path (review 1.5).
- source_spec: `spec-1-5-cloudverifier-and-first-cloud-verdict-for-a-cup-step.md`
  summary: Move proxy URL and shared secret from debug BuildConfig into encrypted settings entered at first-run setup.
  evidence: Secret is compiled into debug APKs for the spike (review 1.5).
- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-cloudverifier-and-first-cloud-verdict-for-a-cup-step.md`
  summary: Tune the proxy system prompt (age-neutral subject, judge only the action) and the mouth-region crop margin on volunteer clips in M3; bump promptVersion when changed.
  evidence: drink.cup.06 with an adult bearded subject returned cannot_tell three times; model cited "person appears adult" and lips obscured.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-character-clips-in-the-app.md`
  summary: Define what effect level CALM changes in CharacterView (currently same as FULL).
  evidence: EffectLevel has FULL/CALM/OFF but only OFF changes rendering; the per-child setting arrives in Story 2.11.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-character-clips-in-the-app.md`
  summary: Accessibility labels for character clips and readable step names in the preview chips.
  evidence: contentDescription is null and chips show raw step IDs.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-9-encrypted-database-secure-settings-and-frame-store.md`
  summary: Recovery path when the Keystore key or the settings file is lost (DB can no longer open).
  evidence: databasePassphrase() throws on decrypt failure every launch; Story 2.10 first-run/reset flow should offer a guarded "reset all data" path.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-9-encrypted-database-secure-settings-and-frame-store.md`
  summary: Sweep orphan frame folders with no session row at startup; Hilt test pinning DataModule wiring (filesDir/frames, dls_master).
  evidence: frames are deleted after the DB transaction; a crash in between leaves encrypted orphans; no test uses the real module yet (first injection comes in 2.10/2.11).
- source_spec: `_bmad-output/implementation-artifacts/spec-2-9-encrypted-database-secure-settings-and-frame-store.md`
  summary: Add connectedDebugAndroidTest to the AGENTS.md verification table.
  evidence: encryption behaviour is only asserted in instrumented tests, which the documented gate does not run.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-10-first-run-setup-and-pin-gate.md`
  summary: Unit-test that DiagnosticsViewModel uses the stored proxy URL and secret (extract a small proxySettings helper).
  evidence: no test covers the settings source; regression to BuildConfig values would go unnoticed.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-14-child-step-screen-with-audio-prompts-and-locked-mode.md`
  summary: Surface screen-pinning failure to the adult and plan device-owner/lock-task allowlisting for the tablet.
  evidence: without device owner, startLockTask shows a prompt each time and can be declined; failure is only logged.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-14-child-step-screen-with-audio-prompts-and-locked-mode.md`
  summary: Show readable task names in the Try step screen picker; neutral a11y label for the adult corner.
  evidence: picker shows raw task ids; TalkBack announces "Adult corner".
- source_spec: `_bmad-output/implementation-artifacts/spec-2-16-start-assessment-content-only-tasks-and-character-walkthrough.md`
  summary: Persist the planned second task of a jacket "both" run so Continue (or exiting during the chain delay) still offers jacket-off.
  evidence: thenTaskId lives only on the navigation key; needs a session column and migration.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-16-start-assessment-content-only-tasks-and-character-walkthrough.md`
  summary: Task-name audio on the child task start screen (with the audio generation at the end of the project).
  evidence: start screen shows the task name as text only.
