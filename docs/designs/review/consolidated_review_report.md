# Consolidated Review Report

Target: [`docs/designs/daily-living-skills-app.md`](docs/designs/daily-living-skills-app.md). Date: 2026-11-11. All five stages ran with fresh reviewers; the author delegated every decision to the drafting agent, so findings were resolved without checkpoints.

| Stage | Report | Findings (Blocker / Major / Nit) |
|---|---|---|
| 5 Initial Zoom Out | [zoom_out_report.md](zoom_out_report.md) | 2 / 10 / 6 |
| 6 Comprehension | [comprehension_report.md](comprehension_report.md) | 0 / 6 / 9 |
| 7 Critic | [critic_report.md](critic_report.md) | 3 / 13 / 9 |
| 8 Intermediate Zoom Out | [intermediate_zoom_out_report.md](intermediate_zoom_out_report.md) | 3 / 13 / 6 |
| 9 Implementation Readiness | [implementation_readiness_report.md](implementation_readiness_report.md) | 2 / 12 / 15 |

The design was revised once after Stage 8 (feature range, following Stage 8's smallest set of changes) and once after Stage 9 (implementation range). It was not re-reviewed after the revisions.

## Blockers and how they were resolved

| Blocker | Source | Resolution |
|---|---|---|
| Sixth domain "Visual-motor coordination" has no plan section | S5 B1, S6 #1, S7 M1, S8 B2 | Counted as Motor coordination, as `project-details.md` section "Fourth" lists it there; added to `scoring-rules.md` and the Terms section |
| Catalogue builder breaks on real headings and untagged steps | S5 B2, S8 B3, S9 M1 | Activity parsing rule by heading level and text (148 level-5 headings verified); "Not tagged in source" = empty tags |
| "First 3 activities" rule not in the documents | S7 B1, S8 B1 | Removed; the plan lists every activity of the matching plan and domain section, and the therapist picks |
| Alternatives rejected without trade-offs | S7 B2 | Each alternative now states its technical or scope reason |
| No risks section | S7 B3 | Risks and Mitigations added (5 items) |
| Per-step detectors undefined | S9 B1 | Table of all 41 camera-scored steps with method and typed check; `Check` schema with 7 types and zones |
| Data model not codable; blind ratings would overwrite levels | S9 B2 | Data model table with keys; separate `blind_rating` table that never changes levels |

## Majors resolved

*   Removed: ExtraHandDetector, `pose+cloud`, the string rule grammar, per-step animations, tablet-to-tablet import, frame retention setting (S5 M3-M7, S8).
*   Rejected as added scope (S8): durable cloud retry queue, second high-resolution stream, per-device token system, cost arithmetic, minimum steps per domain.
*   Added: Terms section (domains, levels, assistance meaning, plan, character, cited stages); first-setup CUJ; "cannot tell" ceiling of 15% in Goal 8; model chosen by measuring Opus 5.5 and Haiku 5.5; catalogue input/output table; precise attempt-window rule; resume design; proxy API; encryption; replay mode; export columns; tooling; milestone exit criteria; added tests.
*   Softened overstated claims ("never penalised", "only catalogue settings").

## Residual items accepted

*   Thresholds in the detector table are starting values to be tuned in M3.
*   Which activities are camera-counted is decided in M4 by a stated rule.
*   Accuracy is measured on adult volunteers first; results with children are reported separately.
*   Clinical rules (`scoring-rules.md`) and the assembled dressing scale have not been reviewed by an occupational therapist.
*   Stage 9 Nits on sentence length and minor wording were not all applied.
