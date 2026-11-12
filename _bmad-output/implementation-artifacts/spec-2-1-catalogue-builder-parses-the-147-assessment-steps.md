---
title: 'Story 2.1: Catalogue builder parses the 147 assessment steps'
type: 'feature'
created: '2026-11-12'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The app must contain exactly the assessment steps in `docs/reference/`, with permanent IDs and verbatim domain tags; nothing may be typed in by hand.

**Approach:** A Python 3.14 module `tools/catalog-builder/build_catalog.py` that parses the three `*-assessment-scale.md` files into step records, assigns IDs `<area>.<task>.<NN>` from an explicit heading→task map, validates tags and IDs, and fails loudly on anything unexpected. pytest + ruff wired at the repo root.

## Boundaries & Constraints

**Always:**
- Inputs only: `docs/reference/eating-drinking-assessment-scale.md`, `personal-hygiene-assessment-scale.md`, `dressing-assessment-scale.md`. Parse only Markdown table rows under a task heading whose header row is `Step No. | Step | Functional domain | Functional sub-skills | Score | Performance`.
- Explicit heading→ID map (unknown heading with a table → error): Eating a sandwich `eat.sandwich`, Eating with a spoon `eat.spoon`, Eating with a fork `eat.fork`, Drinking from a cup `drink.cup`, Drinking from a bottle `drink.bottle`, Drinking with a straw `drink.straw`, Handwashing `hygiene.handwash`, Toothbrushing `hygiene.teeth`, Putting on the jacket `dress.jacket.on`, Taking off the jacket `dress.jacket.off`, T-shirt — Putting on `dress.tshirt.on`, Shirt — Putting on `dress.shirt.on`, Trousers — Putting on `dress.trousers.on`, Socks — Putting on `dress.socks.on`, Shoes — Putting on `dress.shoes.on`. Headings may carry a numeric prefix ("1.", "1.1").
- Step ID = task ID + "." + two-digit Step No. (`drink.cup.06`). Step No. must run 1..n without gaps per task.
- Step record: `id, area, task, number, textEn` (the Step cell verbatim, trimmed), `tags` (domain cell split on " + ", each trimmed, verbatim), `subSkills` (cell verbatim), `source` (`<file>#<heading>`).
- Allowed tags exactly: Hand skills, Midline crossing, Motor coordination, Motor planning, Range of motion, Visual-motor coordination. "Not tagged in source" → `[]`. Anything else → error.
- Expected totals asserted by the build: 147 steps, 15 tasks; eating 42, drinking 38, hygiene 27, dressing 40.
- Errors exit non-zero with a message naming file, heading and row number; no partial output.
- CLI `python tools/catalog-builder/build_catalog.py --check` parses, validates and prints per-area counts. (JSON output is written by Story 2.7; expose `parse_steps()` for it.)
- Python 3.14 (latest stable; spine Stack updated), `pytest`, `ruff` pinned in `tools/requirements.txt`; ruff config in root `pyproject.toml`.

**Never:** No hand-written step list, no edits to `docs/reference/`, no translation, no activity parsing (Story 2.2), no JSON writing.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Real sources | the three scales | 147 steps, counts per area as above, IDs unique | N/A |
| Two tags | "Motor planning + Hand skills" | `["Motor planning","Hand skills"]` | N/A |
| Untagged | "Not tagged in source" | `[]` | N/A |
| Unknown tag | "Balance" | exit ≠ 0, names file/heading/row | no output |
| Duplicate ID | two tables map to one task | exit ≠ 0, names the ID | no output |
| Unknown heading | table under "Wearing a hat" | exit ≠ 0 | no output |
| Step gap | numbers 1,2,4 | exit ≠ 0 | no output |
| Spot check | drink.cup.06 | textEn "Place the cup on your lips", tags from source row | N/A |

</frozen-after-approval>

## Code Map

- Greenfield `tools/`. Sources verified: 147 numbered rows (40 + 80 + 27); tags in sources are exactly the six allowed plus "Not tagged in source" (8 rows, all in jacket off).
- Dressing has the extra `##`/`####` levels (`### 1. Jacket` → `#### 1.1 Putting on the jacket`); garments 2–6 use `### 2. T-shirt — Putting on` (em dash).
- Spine Stack row "Python tools | 3.11" and AGENTS.md "Python 3.11 tools" → update to 3.14.

## Tasks & Acceptance

**Execution:**
- [ ] `tools/catalog-builder/build_catalog.py` -- parser, map, validation, `--check` CLI -- FR-7
- [ ] `tools/catalog-builder/test_build_catalog.py` -- real-source counts + every matrix row with small fixture strings -- verification
- [ ] `tools/requirements.txt`, root `pyproject.toml` -- pinned pytest/ruff, ruff config, `requires-python >= 3.14` -- tooling
- [ ] spine Stack + `AGENTS.md` -- Python 3.14 -- latest-version policy

**Acceptance Criteria:**
- Given the repo root, when `pytest tools/` and `ruff check tools` run, then both pass.
- Given `python tools/catalog-builder/build_catalog.py --check`, then it prints dressing 40, eating 42, drinking 38, hygiene 27, total 147 and exits 0.

## Implementation Notes

- Windows, Python 3.14 on PATH; use a venv at `tools/.venv` (git-ignored) or `uv`. Do not commit.

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `pytest tools/` -- expected: all pass
- `ruff check tools` -- expected: no findings
- `python tools/catalog-builder/build_catalog.py --check` -- expected: counts as above, exit 0
