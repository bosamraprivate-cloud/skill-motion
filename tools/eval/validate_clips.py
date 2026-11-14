"""Validate the volunteer clip manifest `eval/clips.csv` against the clip name rule and the built catalogue.

Usage: validate_clips.py [eval/clips.csv] [--catalog PATH] [--clips-dir DIR]
With --clips-dir, every listed file must exist there and no other .mp4 may be present.
"""

import argparse
import csv
import sys
from pathlib import Path

from clip_manifest import (
    DEFAULT_CATALOG,
    DEFAULT_MANIFEST,
    EXTENSION,
    REQUIRED,
    load_camera_steps,
    parse_clip_name,
)

RULE = "<stepId>__<attemptNumber>__<outcome>__<volunteer>.mp4"


def validate(manifest: Path, catalog: Path, clips_dir: Path | None = None) -> list[str]:
    """Return a list of problems (empty when the manifest is valid)."""
    steps = load_camera_steps(catalog)
    with manifest.open(encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        missing_cols = [c for c in REQUIRED if c not in (reader.fieldnames or [])]
        if missing_cols:
            return [f"{manifest}: missing columns {', '.join(missing_cols)}"]
        rows = list(reader)
    errors: list[str] = []
    seen: set[str] = set()
    for line, row in enumerate(rows, start=2):
        where = f"row {line}"
        empty = [c for c in REQUIRED if not (row.get(c) or "").strip()]
        if empty:
            errors.append(f"{where}: empty {', '.join(empty)}")
            continue
        name = row["file"]
        if name in seen:
            errors.append(f"{where}: duplicate file {name}")
        seen.add(name)
        parsed = parse_clip_name(name)
        if parsed is None:
            errors.append(f"{where}: file name {name} does not match {RULE}")
            continue
        step, attempt, outcome, volunteer = parsed
        expected = {"step": step, "attempt": str(attempt), "outcome": outcome, "volunteer": volunteer}
        for column, value in expected.items():
            if row[column] != value:
                errors.append(f"{where}: {column} {row[column]!r} does not match file name {name}"
                    f" ({value!r})")
        info = steps.get(row["step"])
        if info is None:
            errors.append(f"{where}: step {row['step']} is not a camera-scored step in the catalogue")
        elif row["task"] != info["task"]:
            errors.append(f"{where}: task {row['task']!r} does not match step "
                f"{row['step']} ({info['task']!r})")
    if clips_dir is not None:
        present: set[str] = set()
        if clips_dir.is_dir():
            present = {
                p.name for p in clips_dir.iterdir() if p.is_file() and p.name.lower().endswith(EXTENSION)
            }
        errors += [f"missing clip: {n}" for n in sorted(seen - present)]
        errors += [f"extra clip not in manifest: {n}" for n in sorted(present - seen)]
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, nargs="?", default=DEFAULT_MANIFEST)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--clips-dir", type=Path)
    args = parser.parse_args(argv)
    errors = validate(args.manifest, args.catalog, args.clips_dir)
    for e in errors:
        print(e, file=sys.stderr)
    if errors:
        print(f"FAILED: {len(errors)} problem(s)", file=sys.stderr)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
