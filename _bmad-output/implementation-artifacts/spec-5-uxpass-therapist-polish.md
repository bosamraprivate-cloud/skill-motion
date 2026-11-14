---
title: 'Epic 5 UI/UX pass: therapist screens polish and in-context actions'
type: 'feature'
created: '2026-11-14'
status: 'done'
route: 'dispatch'
context:
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/DESIGN.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

## Intent (owner direction, 2026-11-14)

**Problem:** On the tablet (landscape, 2560x1600) therapist screens waste space and spread related actions apart. Example: the New session dialog lists all domains as long stacked sections with small cards in one corner; Settings cards are wide with uneven spacing.

**Approach:** Use the space well, use professional spacing, and keep every action next to what it acts on.
1. **New session dialog:** domain tabs across the top (one tab per catalogue domain group, e.g. Eating, Drinking, Dressing, Hygiene; names from the catalogue, never hard-coded) with the tasks of the selected tab in an adaptive grid below that fills the dialog width. Selected task clearly marked (border + check, not colour alone). Start/Cancel pinned at the bottom. The dialog uses most of the screen on expanded width.
2. **Settings:** a two-pane layout on expanded width: a section list on the start side (Security, Connection, Zones, Diagnostics, Evaluation, Advanced (debug), About) and the selected section's content on the end side. Single column on compact. Consistent 8dp-grid spacing (section gap 24, card padding 24, row height ≥ 56, item gap 12). Long hints wrap with a max text width (≈ 640dp) instead of spanning the screen.
3. **In-context actions:** each place offers what belongs to it, for example:
   - Session detail: header actions "Open report" and, for an open/paused session, "Continue"; a step's Correct button stays on its card.
   - Child profile overview: last session card links straight to its detail; quick "Start session" stays primary.
   - Empty states always carry their action (no sessions → "Start session"; Accuracy with no blind ratings → "Open blind review"; no zones → "Draw zones").
   - Diagnostics: "Open connection settings" next to proxy errors (existing) and "Draw zones" next to zone status if zones are missing.

## Boundaries

**Always:** Therapist design tokens from `TherapistUi.kt`/DESIGN.md; RTL; Western digits; strings ar/en; TalkBack labels; colour never alone; behaviour unchanged except navigation shortcuts listed above. Unit tests updated/added for any ViewModel change.
**Never:** No edits to `ui/child/`, scoring, data schema, vision. No hard-coded domain/task IDs. Agents do not run the full Gradle gate (coordinator does).

## Work split (parallel, non-overlapping files)

- **A — New session:** `NewSessionDialog.kt`, `NewSessionViewModel.kt` (+ test), strings.
- **B — Settings:** `SettingsScreen.kt`, `SettingsUiState.kt`/`SettingsViewModel.kt` if needed, strings.
- **C — In-context actions:** `SessionDetailScreen.kt`, `ChildProfileScreen.kt`, `SessionsTab.kt`, `MetricsScreen.kt`, `DiagnosticsScreen.kt` (+ VMs/tests if touched), navigation wiring in `ChildrenRoute.kt`/`TherapistShell.kt`, strings.
- strings.xml / values-ar/strings.xml are shared: each agent appends its own keys in a block prefixed with its letter comment; no edits to other keys.

## Review Triage Log

Coordinator gate passed first try; device check at tablet size: domain tabs + full-width grid, two-pane Settings with section kept on back, empty-state actions (blind review), last-assessment shortcut. Session detail kept single column (expanding cards would make a grid jump); per-section scroll shares one state (minor, accepted).

## Verification
- Coordinator: `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug`, then device screenshots at tablet size.
