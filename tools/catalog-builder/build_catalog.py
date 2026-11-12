"""Catalogue builder: parses the assessment scales in docs/reference/ into step records.

Story 2.1 scope: parse and validate the 147 assessment steps (`--check`).
JSON output is added by Story 2.7, which consumes `parse_steps()`.
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
REFERENCE_DIR = REPO_ROOT / "docs" / "reference"

SOURCE_FILES = (
    "eating-drinking-assessment-scale.md",
    "personal-hygiene-assessment-scale.md",
    "dressing-assessment-scale.md",
)

HEADER_CELLS = (
    "Step No.",
    "Step",
    "Functional domain",
    "Functional sub-skills",
    "Score",
    "Performance",
)

TASK_IDS = {
    "Eating a sandwich": "eat.sandwich",
    "Eating with a spoon": "eat.spoon",
    "Eating with a fork": "eat.fork",
    "Drinking from a cup": "drink.cup",
    "Drinking from a bottle": "drink.bottle",
    "Drinking with a straw": "drink.straw",
    "Handwashing": "hygiene.handwash",
    "Toothbrushing": "hygiene.teeth",
    "Putting on the jacket": "dress.jacket.on",
    "Taking off the jacket": "dress.jacket.off",
    "T-shirt — Putting on": "dress.tshirt.on",
    "Shirt — Putting on": "dress.shirt.on",
    "Trousers — Putting on": "dress.trousers.on",
    "Socks — Putting on": "dress.socks.on",
    "Shoes — Putting on": "dress.shoes.on",
}

ALLOWED_TAGS = frozenset(
    {
        "Hand skills",
        "Midline crossing",
        "Motor coordination",
        "Motor planning",
        "Range of motion",
        "Visual-motor coordination",
    }
)
UNTAGGED = "Not tagged in source"

# Expected counts from the Story 2.1 spec and the PRD (FR-7):
# eating 42, drinking 38, hygiene 27, dressing 40 = 147 steps in 15 tasks.
EXPECTED_AREA_COUNTS = {"dress": 40, "eat": 42, "drink": 38, "hygiene": 27}
EXPECTED_TOTAL = 147
EXPECTED_TASKS = 15

_HEADING_RE = re.compile(r"^(#{1,6})\s+(.*?)\s*$")
_NUMBER_PREFIX_RE = re.compile(r"^\d+(?:\.\d+)*\.?\s+")
_SEPARATOR_CELL_RE = re.compile(r"^:?-{3,}:?$")
_STEP_NO_RE = re.compile(r"^[0-9]+$")
_PIPE_RE = re.compile(r"(?<!\\)\|")
_TAG_SPLIT_RE = re.compile(r"\s*\+\s*")


class CatalogError(Exception):
    """Raised on any unexpected content; the build stops with no output."""


@dataclass(frozen=True)
class Step:
    id: str
    area: str
    task: str
    number: int
    textEn: str
    tags: tuple[str, ...] = ()
    subSkills: str = ""
    source: str = ""


def _split_row(line: str) -> list[str]:
    """Split a table row on unescaped pipes and unescape `\\|` in cells."""
    body = line.strip()
    if body.startswith("|"):
        body = body[1:]
    if body.endswith("|") and not body.endswith("\\|"):
        body = body[:-1]
    return [cell.strip().replace("\\|", "|") for cell in _PIPE_RE.split(body)]


def _is_table_line(line: str) -> bool:
    return line.strip().startswith("|")


def _heading_title(text: str) -> str:
    return _NUMBER_PREFIX_RE.sub("", text).strip()


def _parse_tags(cell: str, where: str) -> tuple[str, ...]:
    if cell == UNTAGGED:
        return ()
    tags = _TAG_SPLIT_RE.split(cell.strip())
    for tag in tags:
        if tag not in ALLOWED_TAGS:
            raise CatalogError(f"{where}: unknown domain tag {tag!r}")
    if len(set(tags)) != len(tags):
        raise CatalogError(f"{where}: duplicate domain tag in {cell!r}")
    return tuple(tags)


def _skip_table(lines: list[str], i: int) -> int:
    while i < len(lines) and _is_table_line(lines[i]):
        i += 1
    return i


def parse_text(text: str, file_name: str) -> list[Step]:
    """Parse one scale file's Markdown into step records."""
    lines = text.splitlines()
    steps: list[Step] = []
    heading: str | None = None
    table_seen = False
    i = 0
    while i < len(lines):
        line = lines[i]
        match = _HEADING_RE.match(line)
        if match:
            heading = _heading_title(match.group(2))
            table_seen = False
            i += 1
            continue
        if not _is_table_line(line):
            i += 1
            continue
        cells = _split_row(line)
        task = TASK_IDS.get(heading) if heading is not None else None
        loc = f"{file_name}#{heading}"
        if task is None:
            if tuple(cells) == HEADER_CELLS:
                if heading is None:
                    raise CatalogError(f"{file_name}: line {i + 1}: step table without a heading")
                raise CatalogError(f"{loc}: unknown task heading with a step table")
            i = _skip_table(lines, i)  # not a step table (e.g. a legend)
            continue
        if table_seen:
            raise CatalogError(f"{loc}: line {i + 1}: table rows after a blank line or a second table")
        table_seen = True
        if tuple(cells) != HEADER_CELLS:
            raise CatalogError(f"{loc}: line {i + 1}: step table header does not match: {cells}")
        area = task.split(".", 1)[0]
        i += 1
        sep = _split_row(lines[i]) if i < len(lines) and _is_table_line(lines[i]) else []
        if len(sep) != len(HEADER_CELLS) or not all(_SEPARATOR_CELL_RE.match(c) for c in sep):
            raise CatalogError(f"{loc}: line {i + 1}: missing or malformed separator row")
        i += 1
        row_no = 0
        while i < len(lines) and _is_table_line(lines[i]):
            row_no += 1
            where = f"{loc} row {row_no}"
            cells = _split_row(lines[i])
            if len(cells) != len(HEADER_CELLS):
                raise CatalogError(f"{where}: expected {len(HEADER_CELLS)} cells, got {len(cells)}")
            number_cell, step_text, domain, sub_skills = cells[0], cells[1], cells[2], cells[3]
            if not _STEP_NO_RE.match(number_cell):
                raise CatalogError(f"{where}: Step No. {number_cell!r} is not a number")
            number = int(number_cell)
            if number != row_no:
                raise CatalogError(f"{where}: Step No. {number} breaks the sequence (expected {row_no})")
            if not step_text:
                raise CatalogError(f"{where}: empty Step cell")
            steps.append(
                Step(
                    id=f"{task}.{number:02d}",
                    area=area,
                    task=task,
                    number=number,
                    textEn=step_text,
                    tags=_parse_tags(domain, where),
                    subSkills=sub_skills,
                    source=loc,
                )
            )
            i += 1
        if row_no == 0:
            raise CatalogError(f"{loc}: step table has no rows")
    return steps


def validate(steps: list[Step], *, check_totals: bool = True) -> None:
    """Check ID uniqueness and, for the real sources, the expected task set and totals."""
    by_id: dict[str, list[str]] = {}
    for s in steps:
        by_id.setdefault(s.id, []).append(s.source)
    duplicates = {sid: srcs for sid, srcs in by_id.items() if len(srcs) > 1}
    if duplicates:
        detail = "; ".join(f"{sid} in {', '.join(srcs)}" for sid, srcs in sorted(duplicates.items()))
        raise CatalogError(f"duplicate step ID(s): {detail}")
    if not check_totals:
        return
    for s in steps:
        if s.area not in EXPECTED_AREA_COUNTS:
            raise CatalogError(f"{s.source}: unknown area {s.area!r}")
    tasks = {s.task for s in steps}
    expected_tasks = set(TASK_IDS.values())
    if tasks != expected_tasks:
        raise CatalogError(
            f"task set mismatch: missing {sorted(expected_tasks - tasks)}, "
            f"unexpected {sorted(tasks - expected_tasks)}"
        )
    counts = Counter(s.area for s in steps)
    for area, expected in EXPECTED_AREA_COUNTS.items():
        if counts.get(area, 0) != expected:
            sources = sorted({s.source for s in steps if s.area == area})
            raise CatalogError(
                f"area {area}: expected {expected} steps, found {counts.get(area, 0)} "
                f"(sources: {', '.join(sources)})"
            )
    if len(steps) != EXPECTED_TOTAL:
        raise CatalogError(f"expected {EXPECTED_TOTAL} steps, found {len(steps)}")


def parse_steps(reference_dir: Path = REFERENCE_DIR) -> list[Step]:
    """Parse and validate all assessment steps from the three scale files."""
    steps: list[Step] = []
    for name in SOURCE_FILES:
        path = reference_dir / name
        if not path.is_file():
            raise CatalogError(f"{name}: source file not found in {reference_dir}")
        steps.extend(parse_text(path.read_text(encoding="utf-8-sig"), name))
    validate(steps)
    return steps


def main(argv: list[str] | None = None, reference_dir: Path | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="parse, validate and print counts")
    parser.add_argument("--reference-dir", type=Path, default=None, help="folder with the scale files")
    args = parser.parse_args(argv)
    if not args.check:
        parser.error("only --check is available until Story 2.7 adds JSON output")
    try:
        steps = parse_steps(args.reference_dir or reference_dir or REFERENCE_DIR)
    except CatalogError as exc:
        print(f"catalog error: {exc}", file=sys.stderr)
        return 1
    counts = Counter(s.area for s in steps)
    for area in sorted(EXPECTED_AREA_COUNTS):
        print(f"{area}: {counts[area]}")
    print(f"total: {len(steps)}")
    print(f"tasks: {len({s.task for s in steps})}")
    print(f"untagged: {sum(not s.tags for s in steps)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
