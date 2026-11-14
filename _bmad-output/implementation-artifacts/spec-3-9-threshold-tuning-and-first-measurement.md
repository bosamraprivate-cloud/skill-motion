---
title: 'Story 3.9: Threshold tuning and first deepseek-flash measurement'
type: 'feature'
created: '2026-11-14'
status: 'done'
baseline_commit: '2a19513b3accfad66fc9c43e0f27daf2903d66fe'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/docs/designs/daily-living-skills-app.md'
  - '{project-root}/eval/recording-protocol.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** M3 must exit with measured accuracy and frozen detector settings, but the volunteer clips (Story 3.8 protocol) are not recorded yet, so tuning cannot happen now; what can be built now is the full measurement pipeline so that, the day clips exist, one replay run + one script produce the numbers and the freeze.

**Approach:** (1) Replay screen gains "Export results" (debug only): writes a CSV via SAF with per attempt: clip file, step, scripted outcome, verdict, reason code, verifier, modelId, promptVersion, latencyMs, catalogVersion, rulesVersion — no child data, no modelReason. (2) `tools/eval/accuracy_report.py` reads that CSV (+ `eval/clips.csv`) and writes `docs/designs/m3-accuracy.md`: per-step done / not done / cannot tell counts, agreement (cannot tell excluded and reported separately as a rate), confusion per step, cloud latency p50/p90, modelId/promptVersion/catalogVersion; flags steps below targets (agreement 80 %, cannot tell ≤ 15 %, design Goal 8). (3) `docs/designs/m3-accuracy.md` committed now as the template with "pending clips" status, plus a "Threshold change log" table (date, step, field, old → new, reason, clips used) and a "Frozen catalogVersion" line, empty until tuning. (4) `tools/eval/README` section: the tuning procedure (tuning subset vs hold-out, never change question text or step meaning, every change logged, rebuild catalogue → new catalogVersion → replay all → record → freeze).

## Boundaries & Constraints

**Always:** No threshold is changed in this story (no clips yet) — the change log stays empty and says why. The report script is deterministic, pure Python 3.14, ruff clean, pytest with a synthetic CSV fixture. Export contains no identifiers. Agreement math: scripted `done` vs verdict DONE, `not_done` vs NOT_DONE; CANNOT_TELL and NOT_SCORED counted separately, never as disagreement.

**Never:** No invented accuracy numbers. No edits to questions or step meanings. No release-build export.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Export | replay session results | CSV with the listed columns | SAF cancel → nothing written |
| Report | synthetic CSV | md with per-step table, rates, latency | N/A |
| Missing manifest row | clip not in clips.csv | listed as unmatched | N/A |
| Empty CSV | header only | report says no data, exit non-zero | N/A |
| Below target | step agreement < 80 % | flagged | N/A |

</frozen-after-approval>

## Code Map

- `android/.../ui/therapist/ReplayScreen.kt`, `ReplayViewModel.kt`, `replay/ReplayService.kt`, `data/*` -- results query + CSV writer (pure formatter unit-tested).
- New `tools/eval/accuracy_report.py`, `tools/eval/test_accuracy_report.py`; `docs/designs/m3-accuracy.md`; `tools/eval/README.md` (or section in existing docs); link from `docs/README.md`.

## Tasks & Acceptance

**Execution:**
- [x] Replay export CSV (formatter + SAF) + tests.
- [x] accuracy_report.py + tests.
- [x] m3-accuracy.md template with change log + freeze line; tuning procedure doc.

**Acceptance Criteria:**
- Given `pytest tools/`, `ruff check tools` and `./gradlew test lint ktlintCheck koverVerifyCore assembleDebug`, when run, then all pass.

## Implementation Notes

## Spec Change Log

## Review Triage Log

Coordinator check: CSV escaping correct (Kotlin escape, Python csv module). Measurement itself pending volunteer clips (Story 3.8 protocol); no thresholds changed, no catalogVersion frozen yet.

## Verification

**Commands:**
- `tools/.venv/Scripts/python.exe -m pytest tools/ -q`; `tools/.venv/Scripts/python.exe -m ruff check tools` -- expected: pass
- `cd android && ./gradlew test lint ktlintCheck koverVerifyCore assembleDebug` -- expected: BUILD SUCCESSFUL
