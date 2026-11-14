"""Write the planned volunteer shot list `eval/clips.csv` from the built catalogue (deterministic).

One row per camera-scored step x scripted scenario x volunteer slot. Scenarios follow the design's evaluation:
done on attempt 1, done on a later attempt (attempt 2), not done (attempt 1).
"""

import argparse
import csv
import io
import sys
from pathlib import Path

from clip_manifest import COLUMNS, DEFAULT_CATALOG, DEFAULT_MANIFEST, clip_name, load_camera_steps

SCENARIOS = [("done_attempt_1", 1, "done"), ("done_later", 2, "done"), ("not_done", 1, "not_done")]
VOLUNTEERS = ["v01", "v02"]


def build_rows(steps: dict[str, dict]) -> list[dict[str, str]]:
    rows = []
    for volunteer in VOLUNTEERS:
        for step in sorted(steps.values(), key=lambda s: (s["task"], s["number"])):
            for scenario, attempt, outcome in SCENARIOS:
                rows.append(
                    {
                        "file": clip_name(step["id"], attempt, outcome, volunteer),
                        "task": step["task"],
                        "step": step["id"],
                        "attempt": str(attempt),
                        "outcome": outcome,
                        "volunteer": volunteer,
                        "framing": step["detector"]["framing"],
                        "method": step["detector"]["method"],
                        "scenario": scenario,
                    }
                )
    return rows


def render(rows: list[dict[str, str]]) -> str:
    buf = io.StringIO()
    writer = csv.DictWriter(buf, fieldnames=COLUMNS, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return buf.getvalue()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--out", type=Path, default=DEFAULT_MANIFEST)
    args = parser.parse_args(argv)
    rows = build_rows(load_camera_steps(args.catalog))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(render(rows), encoding="utf-8", newline="")
    print(f"wrote {args.out}: {len(rows)} rows")
    return 0


if __name__ == "__main__":
    sys.exit(main())
