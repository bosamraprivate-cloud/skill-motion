---
title: 'Story 2.2: Catalogue builder parses the 148 activities'
type: 'feature'
created: '2026-11-12'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-2-1-catalogue-builder-parses-the-147-assessment-steps.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Treatment plans must list exactly the activities in `docs/reference/treatment-plans.md`, per plan level and domain, with verbatim fields; nothing hand-written.

**Approach:** Extend `build_catalog.py` with `parse_activities()` that walks the Markdown headings by the design's activity parsing rule, assigns AD-2 IDs and validates strictly, mirroring Story 2.1's style and error handling.

## Boundaries & Constraints

**Always:**
- Plan level from level-2 headings whose text starts `1.` → `mild`, `2.` → `moderate`, `3.` → `severe`; any other level-2 heading inside the file → error.
- Every `#####` heading is exactly one activity; its title is the heading text with the leading `N.` removed, verbatim.
- Domain = nearest preceding level-3 or level-4 heading (within the current plan) whose text contains one of the domain names (case-insensitive): Range of motion → `rom`, Motor coordination → `coord`, Hand skills → `hand`, Midline crossing → `midline`, Motor planning → `planning`. This covers the mild plan's range-of-motion section, which has only a level-4 heading. No match or more than one domain name in the deciding heading → error.
- Activity ID `<plan>.<domain>.<NN>` (AD-2), `NN` = the `#####` heading's own number, two digits; numbers run 1..n without gaps per (plan, domain); duplicates → error.
- Fields, verbatim and trimmed, from list items `- **Tools:**`, `- **How to perform:**`, `- **Goal:**`, `- **Measurement criterion:**`; each exactly once per activity, non-empty; missing/duplicate/unknown labelled item → error naming file, heading and line.
- `generalGoal` per (plan, domain) from the `**General goal:**` paragraph of that section, verbatim, stored on each activity; missing → empty string (not an error).
- Totals asserted: 148 activities; mild 48, moderate 50, severe 50; each plan has all 5 domains.
- `--check` additionally prints activity counts per plan and per domain.

**Never:** No edits to `docs/reference/`, no translation, no JSON writing (Story 2.7), no "first N activities" selection (rejected in the design).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Real source | treatment-plans.md | 148 activities, 48/50/50, IDs unique, 5 domains per plan | N/A |
| Level-4-only domain | mild plan: "#### Therapeutic activities for improving range of motion" | activities get `mild.rom.NN` | N/A |
| Spot check | mild hand skills activity 4 | `mild.hand.04`, title "Opening and closing clothespins", criterion "Attaching and removing 10 clothespins with control of the movement." | N/A |
| Missing field | activity without Goal | exit ≠ 0 naming heading | no output |
| Duplicate field | two Tools items | exit ≠ 0 | no output |
| No domain | activity before any domain heading | exit ≠ 0 | no output |
| Bad plan heading | "## 4. Something" | exit ≠ 0 | no output |
| Number gap | activities 1, 2, 4 | exit ≠ 0 | no output |

</frozen-after-approval>

## Code Map

- `tools/catalog-builder/build_catalog.py` -- Story 2.1 parser: reuse `CatalogError`, `main(argv, reference_dir)`, `--check`, utf-8-sig reading, location-rich errors.
- Source verified: 148 `#####` headings (48/50/50); exactly 148 of each field label; 15 `**General goal:**` paragraphs (one per plan×domain); domain headings are level 3 except the mild range-of-motion section (level 4 only).

## Tasks & Acceptance

**Execution:**
- [ ] `tools/catalog-builder/build_catalog.py` -- `Activity` dataclass, `parse_activities()`, validation, `--check` output -- FR-7, FR-32
- [ ] `tools/catalog-builder/test_build_catalog.py` -- real-source totals and spot check + every matrix row with inline Markdown -- verification

**Acceptance Criteria:**
- Given the repo root, when `pytest tools/` and `ruff check tools` run, then both pass and `--check` prints 148 activities (mild 48, moderate 50, severe 50).

## Implementation Notes

- Use `tools/.venv`. Do not commit.

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `tools/.venv/Scripts/python -m pytest tools/` -- expected: all pass
- `tools/.venv/Scripts/python -m ruff check tools` -- expected: clean
- `tools/.venv/Scripts/python tools/catalog-builder/build_catalog.py --check` -- expected: steps 147 and activities 148 (48/50/50)
