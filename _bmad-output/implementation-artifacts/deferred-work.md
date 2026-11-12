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
