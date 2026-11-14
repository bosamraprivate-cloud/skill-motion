"""Write the M3 accuracy report from a replay export CSV (Story 3.9).

Usage: accuracy_report.py RESULTS.csv [--manifest eval/clips.csv] [--out docs/designs/m3-accuracy.md]

RESULTS.csv is the file written by "Export results" on the Replay screen (debug builds). Only the part of the
report between the report markers is replaced; the threshold change log and the frozen catalogVersion line are
kept. Agreement: scripted `done` vs verdict `done`, scripted `not_done` vs verdict `not_done`. Cannot tell and
not scored are counted separately and never as disagreement. Exits 1 when the CSV has no rows.
"""

import argparse
import csv
import math
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / "eval" / "clips.csv"
DEFAULT_OUT = ROOT / "docs" / "designs" / "m3-accuracy.md"

COLUMNS = (
    "clip_file",
    "step",
    "scripted_outcome",
    "verdict",
    "reason",
    "verifier",
    "model_id",
    "prompt_version",
    "latency_ms",
    "catalog_version",
    "rules_version",
)
START = "<!-- report:start -->"
END = "<!-- report:end -->"
AGREEMENT_TARGET = 0.80
CANNOT_TELL_MAX = 0.15
DECIDED = ("done", "not_done")


@dataclass
class StepStats:
    attempts: int = 0
    verdicts: Counter = field(default_factory=Counter)
    # (scripted, verdict) for decided verdicts only
    confusion: Counter = field(default_factory=Counter)

    @property
    def agree(self) -> int:
        return self.confusion[("done", "done")] + self.confusion[("not_done", "not_done")]

    @property
    def decided(self) -> int:
        return sum(self.confusion.values())

    @property
    def agreement(self) -> float | None:
        return self.agree / self.decided if self.decided else None

    @property
    def cannot_tell_rate(self) -> float | None:
        return self.verdicts["cannot_tell"] / self.attempts if self.attempts else None


def read_results(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        missing = [c for c in COLUMNS if c not in (reader.fieldnames or [])]
        if missing:
            raise ValueError(f"{path}: missing columns {', '.join(missing)}")
        return list(reader)


def read_manifest(path: Path) -> dict[str, str]:
    """Clip file -> scripted outcome from eval/clips.csv (empty when the file is missing)."""
    if not path.is_file():
        return {}
    with path.open(encoding="utf-8", newline="") as fh:
        return {r["file"]: r["outcome"] for r in csv.DictReader(fh) if r.get("file")}


def percentile(values: list[int], p: float) -> int | None:
    """Nearest-rank percentile; None for no values."""
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(p * len(ordered)) - 1)]


def pct(value: float | None) -> str:
    return "n/a" if value is None else f"{value * 100:.1f} %"


def flags(stats: StepStats) -> str:
    out = []
    if stats.agreement is not None and stats.agreement < AGREEMENT_TARGET:
        out.append("agreement below 80 %")
    if stats.cannot_tell_rate is not None and stats.cannot_tell_rate > CANNOT_TELL_MAX:
        out.append("cannot tell above 15 %")
    return "; ".join(out)


def versions(rows: list[dict[str, str]], column: str) -> str:
    values = sorted({r[column] for r in rows if r[column]})
    return ", ".join(f"`{v}`" for v in values) if values else "none"


def build_report(rows: list[dict[str, str]], manifest: dict[str, str]) -> str:
    """The markdown between the report markers. Deterministic for the same input."""
    if not rows:
        return "**Status:** no data. The results CSV has no rows.\n"
    per_step: dict[str, StepStats] = defaultdict(StepStats)
    total = StepStats()
    unmatched: list[str] = []
    latencies: list[int] = []
    for row in rows:
        clip = row["clip_file"]
        if clip in manifest:
            scripted = manifest[clip]
        else:
            unmatched.append(clip)
            scripted = row["scripted_outcome"]
        verdict = row["verdict"]
        for stats in (per_step[row["step"]], total):
            stats.attempts += 1
            stats.verdicts[verdict] += 1
            if verdict in DECIDED and scripted in DECIDED:
                stats.confusion[(scripted, verdict)] += 1
        if row["verifier"] == "cloud" and row["latency_ms"].strip().isdigit():
            latencies.append(int(row["latency_ms"]))

    other = total.attempts - sum(total.verdicts[v] for v in (*DECIDED, "cannot_tell"))
    lines = [
        "**Status:** measured from a replay export.",
        "",
        f"- catalogVersion: {versions(rows, 'catalog_version')}",
        f"- rulesVersion: {versions(rows, 'rules_version')}",
        f"- modelId: {versions(rows, 'model_id')}",
        f"- promptVersion: {versions(rows, 'prompt_version')}",
        "",
        "### Overall",
        "",
        "| Attempts | Done | Not done | Cannot tell | Not scored / other | Agreement | Cannot tell rate |",
        "|---|---|---|---|---|---|---|",
        f"| {total.attempts} | {total.verdicts['done']} | {total.verdicts['not_done']} "
        f"| {total.verdicts['cannot_tell']} | {other} | {pct(total.agreement)} "
        f"| {pct(total.cannot_tell_rate)} |",
        "",
        f"Targets (design Goal 8): agreement at least {AGREEMENT_TARGET * 100:.0f} %, cannot tell at most "
        f"{CANNOT_TELL_MAX * 100:.0f} %. Agreement excludes cannot tell and not scored.",
        "",
        "### Per step",
        "",
        "Confusion columns are scripted -> verdict (d = done, n = not done).",
        "",
        "| Step | Attempts | Done | Not done | Cannot tell | d->d | d->n | n->d | n->n | Agreement "
        "| Cannot tell rate | Flag |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for step in sorted(per_step):
        s = per_step[step]
        c = s.confusion
        lines.append(
            f"| `{step}` | {s.attempts} | {s.verdicts['done']} | {s.verdicts['not_done']} "
            f"| {s.verdicts['cannot_tell']} | {c[('done', 'done')]} | {c[('done', 'not_done')]} "
            f"| {c[('not_done', 'done')]} | {c[('not_done', 'not_done')]} | {pct(s.agreement)} "
            f"| {pct(s.cannot_tell_rate)} | {flags(s)} |"
        )
    p50, p90 = percentile(latencies, 0.5), percentile(latencies, 0.9)
    lines += [
        "",
        "### Cloud latency",
        "",
        f"- Cloud attempts: {len(latencies)}",
        f"- p50: {'n/a' if p50 is None else f'{p50} ms'}",
        f"- p90: {'n/a' if p90 is None else f'{p90} ms'}",
        "",
        "### Unmatched clips",
        "",
    ]
    if unmatched:
        lines += ["Not in `eval/clips.csv` (scripted outcome taken from the file name):", ""]
        lines += [f"- `{u}`" for u in sorted(set(unmatched))]
    else:
        lines.append("None.")
    return "\n".join(lines) + "\n"


def write_report(out: Path, body: str) -> None:
    """Replace the text between the markers in `out`; append a marked section when they are missing."""
    block = f"{START}\n{body}{END}"
    text = out.read_text(encoding="utf-8") if out.is_file() else "# M3 accuracy\n\n"
    if START in text and END in text:
        head, rest = text.split(START, 1)
        _, tail = rest.split(END, 1)
        text = head + block + tail
    else:
        text = text.rstrip("\n") + "\n\n" + block + "\n"
    out.write_text(text, encoding="utf-8", newline="\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Write the M3 accuracy report from a replay export CSV.")
    parser.add_argument("results", type=Path)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args(argv)
    try:
        rows = read_results(args.results)
    except (OSError, ValueError) as exc:
        print(exc, file=sys.stderr)
        return 1
    write_report(args.out, build_report(rows, read_manifest(args.manifest)))
    if not rows:
        print("No data: the results CSV has no rows.", file=sys.stderr)
        return 1
    print(f"Wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
