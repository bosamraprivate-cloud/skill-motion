"""Goal 8 volunteer accuracy metrics from the therapist `results.csv` export (Story 5.8, part A).

Usage: compute_metrics.py RESULTS.csv [--clips eval/clips.csv] [--out docs/designs/evaluation-metrics.md]

Primary baseline is the blind therapist rating (`blindVerdict`); the scripted outcome from `eval/clips.csv`
(joined by `clipFile`) is a secondary table. Rules:

- Agreement is per attempt with a blind rating. Attempts where the app or the blind rating says cannot
  tell are counted separately and are never disagreement.
- Kappa (quadratic weighted, levels 1..3) pairs the exported automatic `level` with the blind level. The
  blind level is Rule 1 (`rule1_level`, a mirror of Kotlin `RulesEngine.stepLevel`) on the blind verdicts
  of a step, only when every attempt of that step is rated. Partially rated, open and not scored steps
  are excluded and counted.
- `correctedLevel` never feeds app accuracy; corrections are reported separately. It is 1/2/3, the text
  `not_scored` for a not-scored correction, or empty for no correction.
- `attemptNumber` must never decrease within a step (saved order is what Rule 1 reads).

Only the part of the output file between the report markers is replaced. Exits 1 on a missing file, a bad
header or no rows.
"""

import argparse
import csv
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CLIPS = ROOT / "eval" / "clips.csv"
DEFAULT_OUT = ROOT / "docs" / "designs" / "evaluation-metrics.md"

COLUMNS = (
    "sessionId",
    "childCode",
    "taskId",
    "stepId",
    "attemptId",
    "attemptNumber",
    "promptLevel",
    "verdict",
    "reason",
    "verifier",
    "modelId",
    "latencyMs",
    "level",
    "correctedLevel",
    "blindVerdict",
    "clipFile",
)
START = "<!-- report:start -->"
END = "<!-- report:end -->"
AGREEMENT_TARGET = 0.80
KAPPA_TARGET = 0.6
CANNOT_TELL_MAX = 0.15

DONE, NOT_DONE, CANNOT_TELL = "done", "not_done", "cannot_tell"
VERDICTS = (DONE, NOT_DONE, CANNOT_TELL)
DECIDED = (DONE, NOT_DONE)
NOT_SCORED = "not_scored"
LEVELS = (1, 2, 3)
NOT_DONE_LIMIT = 3


# ---------- Rule 1 mirror ----------


@dataclass(frozen=True)
class RuleAttempt:
    number: int
    prompt_level: int
    outcome: str
    verifier_none: bool = False


def rule1_level(rows: list[RuleAttempt]) -> int | str | None:
    """Mirror of Kotlin `RulesEngine.stepLevel` (no correction): 3/2/1, NOT_SCORED, or None while open."""
    not_done_numbers: set[int] = set()
    previous: RuleAttempt | None = None
    for row in rows:
        if row.verifier_none:
            return NOT_SCORED
        if row.outcome == DONE:
            return 3 if not not_done_numbers and row.prompt_level == 1 else 2
        if row.outcome == CANNOT_TELL:
            if previous is not None and previous.outcome == CANNOT_TELL:
                return NOT_SCORED
        elif row.outcome == NOT_DONE:
            not_done_numbers.add(row.number)
            if len(not_done_numbers) >= NOT_DONE_LIMIT:
                return 1
        else:
            raise ValueError(f"unknown outcome {row.outcome!r}")
        previous = row
    return None


# ---------- kappa ----------


def quadratic_kappa(pairs: list[tuple[int, int]], categories: tuple[int, ...] = LEVELS) -> float | None:
    """Quadratic-weighted Cohen's kappa; None when there are no pairs or no expected disagreement."""
    n = len(pairs)
    if not n:
        return None
    k = len(categories)
    idx = {c: i for i, c in enumerate(categories)}
    rows, cols = Counter(a for a, _ in pairs), Counter(b for _, b in pairs)

    def weight(a: int, b: int) -> float:
        return (idx[a] - idx[b]) ** 2 / (k - 1) ** 2

    observed = sum(weight(a, b) for a, b in pairs)
    expected = sum(weight(a, b) * rows[a] * cols[b] / n for a in categories for b in categories)
    if expected == 0:
        return None
    return 1 - observed / expected


# ---------- input ----------


def read_results(path: Path) -> list[dict[str, str]]:
    if not path.is_file():
        raise ValueError(f"{path}: file not found")
    with path.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        if not reader.fieldnames:
            raise ValueError(f"{path}: empty file (no header)")
        missing = [c for c in COLUMNS if c not in reader.fieldnames]
        if missing:
            raise ValueError(f"{path}: missing columns {', '.join(missing)}")
        rows = list(reader)
    if not rows:
        raise ValueError(f"{path}: no rows")
    last_number: dict[tuple[str, str], int] = {}
    for i, row in enumerate(rows, start=2):
        if row["verdict"] not in VERDICTS:
            raise ValueError(f"{path}:{i}: bad verdict {row['verdict']!r}")
        if row["blindVerdict"] not in (*VERDICTS, ""):
            raise ValueError(f"{path}:{i}: bad blindVerdict {row['blindVerdict']!r}")
        for column in ("attemptNumber", "promptLevel"):
            value = row[column].strip()
            if not value.isdigit() or int(value) < 1:
                raise ValueError(f"{path}:{i}: bad {column} {row[column]!r} (expected a positive integer)")
        for column in ("level", "correctedLevel"):
            try:
                parse_level(row[column])
            except ValueError as exc:
                raise ValueError(f"{path}:{i}: bad {column}: {exc}") from None
        key = (row["sessionId"], row["stepId"])
        number = int(row["attemptNumber"])
        if number < last_number.get(key, 0):
            raise ValueError(
                f"{path}:{i}: attemptNumber {number} decreases within step {row['stepId']} "
                f"of session {row['sessionId']}"
            )
        last_number[key] = number
    return rows


def read_clips(path: Path | None, required: bool = False) -> dict[str, str]:
    """Clip file -> scripted outcome from eval/clips.csv. A missing file is empty unless [required]."""
    if path is None:
        return {}
    if not path.is_file():
        if required:
            raise ValueError(f"{path}: clips file not found")
        return {}
    with path.open(encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        missing = [c for c in ("file", "outcome") if c not in (reader.fieldnames or [])]
        if missing:
            raise ValueError(f"{path}: clips file missing columns {', '.join(missing)}")
        return {r["file"]: r["outcome"] for r in reader}


def parse_level(value: str) -> int | str | None:
    """`level` / `correctedLevel` cell: "1".."3", "not_scored", or empty (open / no correction)."""
    value = value.strip()
    if not value:
        return None
    if value == NOT_SCORED:
        return NOT_SCORED
    if value in ("1", "2", "3"):
        return int(value)
    raise ValueError(f"{value!r} is not 1, 2, 3, {NOT_SCORED} or empty")


# ---------- metrics ----------


@dataclass
class Metrics:
    attempts: int = 0
    app_cannot_tell: int = 0
    rated: int = 0
    unrated: int = 0
    either_cannot_tell: int = 0
    decided: int = 0
    agree: int = 0
    steps: int = 0
    kappa_pairs: list[tuple[int, int]] = field(default_factory=list)
    partial_steps: int = 0
    not_scored_steps: int = 0
    app_open_steps: int = 0
    blind_open_steps: int = 0
    corrected_steps: int = 0
    corrected_changed: int = 0
    # stepId -> Counter[(blind, app)] over rated attempts
    confusion: dict[str, Counter] = field(default_factory=lambda: defaultdict(Counter))
    clip_decided: int = 0
    clip_agree: int = 0
    clip_cannot_tell: int = 0
    clip_matched: int = 0
    clip_confusion: Counter = field(default_factory=Counter)
    unmatched: list[str] = field(default_factory=list)

    @property
    def agreement(self) -> float | None:
        return self.agree / self.decided if self.decided else None

    @property
    def cannot_tell_rate(self) -> float | None:
        return self.app_cannot_tell / self.attempts if self.attempts else None

    @property
    def kappa(self) -> float | None:
        return quadratic_kappa(self.kappa_pairs)

    @property
    def clip_agreement(self) -> float | None:
        return self.clip_agree / self.clip_decided if self.clip_decided else None

    def as_dict(self) -> dict:
        return {
            "attempts": self.attempts,
            "app_cannot_tell": self.app_cannot_tell,
            "cannot_tell_rate": self.cannot_tell_rate,
            "rated": self.rated,
            "unrated": self.unrated,
            "either_cannot_tell": self.either_cannot_tell,
            "decided": self.decided,
            "agree": self.agree,
            "agreement": self.agreement,
            "steps": self.steps,
            "kappa_pairs": len(self.kappa_pairs),
            "kappa": self.kappa,
            "partial_steps": self.partial_steps,
            "not_scored_steps": self.not_scored_steps,
            "app_open_steps": self.app_open_steps,
            "blind_open_steps": self.blind_open_steps,
            "corrected_steps": self.corrected_steps,
            "corrected_changed": self.corrected_changed,
            "clip_matched": self.clip_matched,
            "clip_decided": self.clip_decided,
            "clip_agree": self.clip_agree,
            "clip_cannot_tell": self.clip_cannot_tell,
            "clip_agreement": self.clip_agreement,
            "unmatched": list(self.unmatched),
        }


def compute(rows: list[dict[str, str]], clips: dict[str, str]) -> Metrics:
    m = Metrics()
    groups: dict[tuple[str, str], list[dict[str, str]]] = {}
    for row in rows:
        groups.setdefault((row["sessionId"], row["stepId"]), []).append(row)
        app, blind = row["verdict"], row["blindVerdict"]
        m.attempts += 1
        m.app_cannot_tell += app == CANNOT_TELL
        if blind:
            m.rated += 1
            m.confusion[row["stepId"]][(blind, app)] += 1
            if CANNOT_TELL in (app, blind):
                m.either_cannot_tell += 1
            else:
                m.decided += 1
                m.agree += app == blind
        else:
            m.unrated += 1
        clip = row["clipFile"].strip()
        if clip:
            if clip in clips:
                m.clip_matched += 1
                scripted = clips[clip]
                m.clip_confusion[(scripted, app)] += 1
                if app == CANNOT_TELL:
                    m.clip_cannot_tell += 1
                elif scripted in DECIDED:
                    m.clip_decided += 1
                    m.clip_agree += app == scripted
            elif clip not in m.unmatched:
                m.unmatched.append(clip)

    for step_rows in groups.values():  # saved (file) order within a step, as Rule 1 reads it
        m.steps += 1
        app_level = next((parse_level(r["level"]) for r in step_rows if r["level"].strip()), None)
        corrected = next(
            (parse_level(r["correctedLevel"]) for r in step_rows if r["correctedLevel"].strip()), None
        )
        if any(r["correctedLevel"].strip() for r in step_rows):
            m.corrected_steps += 1
            m.corrected_changed += corrected != app_level
        if any(not r["blindVerdict"] for r in step_rows):
            m.partial_steps += 1
            continue
        blind_level = rule1_level(
            [
                RuleAttempt(int(r["attemptNumber"]), int(r["promptLevel"]), r["blindVerdict"])
                for r in step_rows
            ]
        )
        if NOT_SCORED in (app_level, blind_level):
            m.not_scored_steps += 1
        elif app_level is None:
            m.app_open_steps += 1
        elif blind_level is None:
            m.blind_open_steps += 1
        else:
            m.kappa_pairs.append((int(app_level), int(blind_level)))
    return m


# ---------- report ----------


def pct(value: float | None) -> str:
    return "n/a" if value is None else f"{value * 100:.1f} %"


def num(value: float | None) -> str:
    return "n/a" if value is None else f"{value:.3f}"


def mark(ok: bool | None) -> str:
    return "n/a" if ok is None else ("meets" if ok else "**below target**")


def build_report(m: Metrics) -> str:
    agreement, kappa, rate = m.agreement, m.kappa, m.cannot_tell_rate
    unmatched = ", ".join(f"`{u}`" for u in m.unmatched) if m.unmatched else "none"
    lines = [
        "**Volunteer accuracy** (replay of volunteer clips, eval child; not a clinical validation).",
        "",
        "### Goal 8",
        "",
        "| Metric | Value | Target | Result |",
        "|---|---|---|---|",
        f"| Agreement app vs blind (per attempt) | {pct(agreement)} ({m.agree}/{m.decided}) "
        f"| >= {AGREEMENT_TARGET * 100:.0f} % "
        f"| {mark(None if agreement is None else agreement >= AGREEMENT_TARGET)} |",
        f"| Quadratic-weighted kappa on levels | {num(kappa)} ({len(m.kappa_pairs)} steps) "
        f"| >= {KAPPA_TARGET} | {mark(None if kappa is None else kappa >= KAPPA_TARGET)} |",
        f"| Cannot tell rate (app) | {pct(rate)} ({m.app_cannot_tell}/{m.attempts}) "
        f"| <= {CANNOT_TELL_MAX * 100:.0f} % | {mark(None if rate is None else rate <= CANNOT_TELL_MAX)} |",
        "",
        "### Counts",
        "",
        f"- Attempts: {m.attempts}; blind rated: {m.rated}; not rated: {m.unrated}.",
        f"- Rated attempts with cannot tell on either side (not disagreement): {m.either_cannot_tell}.",
        f"- Steps: {m.steps}; in kappa: {len(m.kappa_pairs)}; excluded: partially rated {m.partial_steps}, "
        f"not scored {m.not_scored_steps}, app open {m.app_open_steps}, blind open {m.blind_open_steps}.",
        f"- Therapist corrections (not used for accuracy): {m.corrected_steps} steps, "
        f"{m.corrected_changed} differ from the automatic level.",
        "",
        "### Per-step confusion (blind -> app)",
        "",
        "d = done, n = not done, c = cannot tell.",
        "",
        "| Step | d->d | d->n | d->c | n->d | n->n | n->c | c->d | c->n | c->c |",
        "|---|---|---|---|---|---|---|---|---|---|",
    ]
    for step in sorted(m.confusion):
        c = m.confusion[step]
        cells = " | ".join(str(c[(b, a)]) for b in VERDICTS for a in VERDICTS)
        lines.append(f"| `{step}` | {cells} |")
    lines += [
        "",
        "### Secondary: app vs scripted outcome (joined by clipFile)",
        "",
        f"- Matched clip attempts: {m.clip_matched}; agreement {pct(m.clip_agreement)} "
        f"({m.clip_agree}/{m.clip_decided}); app cannot tell {m.clip_cannot_tell}.",
        f"- Unmatched clipFile values: {unmatched}.",
        "",
    ]
    return "\n".join(lines)


TEMPLATE = f"""# Evaluation metrics

Goal 8 volunteer accuracy from `results.csv`, produced by `tools/eval/compute_metrics.py`.

## Results

{START}
{END}
"""


def write_report(out: Path, body: str) -> None:
    text = out.read_text(encoding="utf-8") if out.is_file() else TEMPLATE
    if START not in text or END not in text:
        raise ValueError(f"{out}: report markers not found")
    head, rest = text.split(START, 1)
    _, tail = rest.split(END, 1)
    out.write_text(f"{head}{START}\n{body}{END}{tail}", encoding="utf-8", newline="\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("results", type=Path)
    parser.add_argument("--clips", type=Path, default=None, help=f"default {DEFAULT_CLIPS} (may be missing)")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args(argv)
    try:
        rows = read_results(args.results)
        metrics = compute(rows, read_clips(args.clips or DEFAULT_CLIPS, required=args.clips is not None))
        write_report(args.out, build_report(metrics))
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
