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
