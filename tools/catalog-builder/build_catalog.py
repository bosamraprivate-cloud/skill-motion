"""Catalogue builder: parses the assessment scales in docs/reference/ into step records.

Story 2.1 scope: parse and validate the 147 assessment steps (`--check`).
Story 2.2 scope: parse and validate the 148 activities in treatment-plans.md.
JSON output is added by Story 2.7, which consumes `parse_steps()` and `parse_activities()`.
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


ACTIVITIES_FILE = "treatment-plans.md"

PLAN_PREFIXES = {"1.": "mild", "2.": "moderate", "3.": "severe"}
DOMAIN_NAMES = {
    "range of motion": "rom",
    "motor coordination": "coord",
    "hand skills": "hand",
    "midline crossing": "midline",
    "motor planning": "planning",
}
FIELD_LABELS = {
    "Tools": "tools",
    "How to perform": "howToPerform",
    "Goal": "goal",
    "Measurement criterion": "criterion",
}
EXPECTED_PLAN_COUNTS = {"mild": 48, "moderate": 50, "severe": 50}
EXPECTED_ACTIVITY_TOTAL = 148

_ACTIVITY_NO_RE = re.compile(r"^(\d+)\.\s+(.*\S)\s*$")
_PLAN_RE = re.compile(r"^([123])\.\s")
_FIELD_RE = re.compile(r"^-\s+\*\*([^*]+?):\*\*\s*(.*?)\s*$")
_GENERAL_GOAL_RE = re.compile(r"^\*\*General goal:\*\*\s*(.*?)\s*$")
_DOMAIN_RES = {
    code: re.compile(rf"(?<![\w-])\b{re.escape(name)}\b(?![\w-])", re.IGNORECASE)
    for name, code in DOMAIN_NAMES.items()
}

# Per (plan, domain) activity counts, taken from docs/reference/treatment-plans.md as of
# Story 2.2 (mild range of motion has 8 activities, every other section 10).
EXPECTED_DOMAIN_COUNTS = {
    (plan, domain): (8 if (plan, domain) == ("mild", "rom") else 10)
    for plan in EXPECTED_PLAN_COUNTS
    for domain in DOMAIN_NAMES.values()
}


@dataclass(frozen=True)
class Activity:
    id: str
    plan: str
    domain: str
    number: int
    title: str
    tools: str
    howToPerform: str
    goal: str
    criterion: str
    generalGoal: str = ""
    source: str = ""


def _domains_in(text: str) -> tuple[str, ...]:
    return tuple(code for code, pattern in _DOMAIN_RES.items() if pattern.search(text))


@dataclass
class _Pending:
    where: str
    plan: str
    domain: str
    number: int
    title: str
    general_goal: str
    source: str
    fields: dict[str, str]

    def build(self) -> Activity:
        missing = [label for label, key in FIELD_LABELS.items() if key not in self.fields]
        if missing:
            raise CatalogError(f"{self.where}: missing field(s) {', '.join(missing)}")
        return Activity(
            id=f"{self.plan}.{self.domain}.{self.number:02d}",
            plan=self.plan,
            domain=self.domain,
            number=self.number,
            title=self.title,
            generalGoal=self.general_goal,
            source=self.source,
            **self.fields,
        )


def parse_activities_text(text: str, file_name: str = ACTIVITIES_FILE) -> list[Activity]:
    """Parse treatment-plans Markdown into activity records (numbering checked, totals not)."""
    activities: list[Activity] = []
    seen_plans: set[str] = set()
    plan: str | None = None
    deciding: tuple[str, int] | None = None  # nearest level-3/4 heading in the plan: (text, line)
    section: tuple[str, ...] | None = None  # domain codes named by the deciding heading
    general_goal = ""
    goal_seen = False  # a General goal paragraph was seen in the current section
    activity_seen = False  # an activity was seen in the current section
    current: _Pending | None = None

    def reset_section() -> None:
        nonlocal general_goal, goal_seen, activity_seen
        general_goal, goal_seen, activity_seen = "", False, False

    for line_no, line in enumerate(text.splitlines(), start=1):
        match = _HEADING_RE.match(line)
        if match:
            level, heading = len(match.group(1)), match.group(2)
            if current is not None:
                activities.append(current.build())
                current = None
            if level == 1:
                plan, deciding, section = None, None, None
                reset_section()
            elif level == 2:
                plan_match = _PLAN_RE.match(heading)
                if plan_match is None:
                    raise CatalogError(f"{file_name}: line {line_no}: unknown plan heading {heading!r}")
                plan = PLAN_PREFIXES[f"{plan_match.group(1)}."]
                if plan in seen_plans:
                    raise CatalogError(f"{file_name}: line {line_no}: duplicate plan heading {heading!r}")
                seen_plans.add(plan)
                deciding, section = None, None
                reset_section()
            elif level in (3, 4):
                codes = _domains_in(heading)
                if section is not None and codes != section:
                    reset_section()  # a new domain section; the plan-level goal is kept for the first one
                elif section is None:
                    activity_seen = False
                deciding, section = (heading, line_no), codes
            elif level == 5:
                where = f"{file_name}: line {line_no}: activity {heading!r}"
                if plan is None:
                    raise CatalogError(f"{where}: activity outside a plan")
                num = _ACTIVITY_NO_RE.match(heading)
                if num is None:
                    raise CatalogError(f"{where}: heading has no leading number")
                number = int(num.group(1))
                if not 1 <= number <= 99:
                    raise CatalogError(f"{where}: activity number {number} is outside 1..99 (AD-2)")
                if deciding is None or section is None:
                    raise CatalogError(f"{where}: no domain heading before the activity")
                if len(section) != 1:
                    raise CatalogError(
                        f"{where}: deciding heading {deciding[0]!r} (line {deciding[1]}) "
                        f"names {len(section)} domains"
                    )
                activity_seen = True
                current = _Pending(
                    where=where,
                    plan=plan,
                    domain=section[0],
                    number=number,
                    title=num.group(2),
                    general_goal=general_goal,
                    source=f"{file_name}#{heading}",
                    fields={},
                )
            else:
                raise CatalogError(f"{file_name}: line {line_no}: unexpected level-{level} heading")
            continue
        goal_match = _GENERAL_GOAL_RE.match(line)
        if goal_match:
            if activity_seen:
                raise CatalogError(f"{file_name}: line {line_no}: general goal after an activity")
            if goal_seen:
                raise CatalogError(f"{file_name}: line {line_no}: second general goal in one section")
            general_goal, goal_seen = goal_match.group(1), True
            continue
        field = _FIELD_RE.match(line)
        if field is None:
            if current is not None and line.strip():
                raise CatalogError(f"{current.where}, line {line_no}: unrecognised line {line.strip()!r}")
            continue
        label, value = field.group(1), field.group(2)
        if current is None:
            raise CatalogError(f"{file_name}: line {line_no}: field {label!r} outside an activity")
        where = f"{current.where}, line {line_no}"
        key = FIELD_LABELS.get(label)
        if key is None:
            raise CatalogError(f"{where}: unknown field {label!r}")
        if key in current.fields:
            raise CatalogError(f"{where}: duplicate field {label!r}")
        if not value:
            raise CatalogError(f"{where}: empty field {label!r}")
        current.fields[key] = value
    if current is not None:
        activities.append(current.build())
    _check_activity_numbers(activities)
    return activities


def _check_activity_numbers(activities: list[Activity]) -> None:
    groups: dict[tuple[str, str], list[Activity]] = {}
    for a in activities:
        groups.setdefault((a.plan, a.domain), []).append(a)
    for (plan, domain), group in groups.items():
        numbers = [a.number for a in group]
        dups = sorted({n for n in numbers if numbers.count(n) > 1})
        if dups:
            sources = ", ".join(a.source for a in group if a.number in dups)
            raise CatalogError(f"{plan}.{domain}: duplicate activity number(s) {dups} ({sources})")
        if sorted(numbers) != list(range(1, len(numbers) + 1)):
            raise CatalogError(f"{plan}.{domain}: activity numbers {numbers} are not 1..{len(numbers)}")


def validate_activities(activities: list[Activity]) -> None:
    """Check the real-source totals: 148 activities, 48/50/50, per-domain counts, five domains per plan."""
    ids = [a.id for a in activities]
    if len(set(ids)) != len(ids):
        raise CatalogError(f"duplicate activity ID(s): {sorted({i for i in ids if ids.count(i) > 1})}")
    counts = Counter(a.plan for a in activities)
    for plan, expected in EXPECTED_PLAN_COUNTS.items():
        if counts.get(plan, 0) != expected:
            raise CatalogError(f"plan {plan}: expected {expected} activities, found {counts.get(plan, 0)}")
        missing = sorted(set(DOMAIN_NAMES.values()) - {a.domain for a in activities if a.plan == plan})
        if missing:
            raise CatalogError(f"plan {plan}: missing domain(s) {missing}")
    domain_counts = Counter((a.plan, a.domain) for a in activities)
    for (plan, domain), expected in EXPECTED_DOMAIN_COUNTS.items():
        found = domain_counts.get((plan, domain), 0)
        if found != expected:
            raise CatalogError(
                f"{plan}.{domain}: expected {expected} activities, found {found}"
            )
    # Plans outside EXPECTED_PLAN_COUNTS cannot occur (the parser only yields mild/moderate/severe),
    # so the per-plan counts above already fix the total at EXPECTED_ACTIVITY_TOTAL.


def parse_activities(reference_dir: Path = REFERENCE_DIR) -> list[Activity]:
    """Parse and validate all activities from treatment-plans.md."""
    path = reference_dir / ACTIVITIES_FILE
    if not path.is_file():
        raise CatalogError(f"{ACTIVITIES_FILE}: source file not found in {reference_dir}")
    activities = parse_activities_text(path.read_text(encoding="utf-8-sig"), ACTIVITIES_FILE)
    validate_activities(activities)
    return activities


def main(argv: list[str] | None = None, reference_dir: Path | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="parse, validate and print counts")
    parser.add_argument("--reference-dir", type=Path, default=None, help="folder with the scale files")
    args = parser.parse_args(argv)
    if not args.check:
        parser.error("only --check is available until Story 2.7 adds JSON output")
    try:
        ref = args.reference_dir or reference_dir or REFERENCE_DIR
        steps = parse_steps(ref)
        activities = parse_activities(ref)
    except CatalogError as exc:
        print(f"catalog error: {exc}", file=sys.stderr)
        return 1
    counts = Counter(s.area for s in steps)
    for area in sorted(EXPECTED_AREA_COUNTS):
        print(f"{area}: {counts[area]}")
    print(f"total: {len(steps)}")
    print(f"tasks: {len({s.task for s in steps})}")
    print(f"untagged: {sum(not s.tags for s in steps)}")
    plan_counts = Counter(a.plan for a in activities)
    for plan in EXPECTED_PLAN_COUNTS:
        print(f"activities {plan}: {plan_counts[plan]}")
    domain_counts = Counter(a.domain for a in activities)
    for domain in DOMAIN_NAMES.values():
        print(f"activities domain {domain}: {domain_counts[domain]}")
    print(f"activities total: {len(activities)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
