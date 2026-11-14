"""Shared rules for the volunteer clip manifest `eval/clips.csv` (Story 3.8).

The clip name rule mirrors the replay parser in the app:
`<stepId>__<attemptNumber>__<outcome>__<volunteer>.mp4`.
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CATALOG = ROOT / "android" / "app" / "src" / "main" / "assets" / "catalog" / "assessment.json"
DEFAULT_MANIFEST = ROOT / "eval" / "clips.csv"

COLUMNS = ["file", "task", "step", "attempt", "outcome", "volunteer", "framing", "method", "scenario"]
REQUIRED = ["file", "task", "step", "attempt", "outcome", "volunteer"]
OUTCOMES = ("done", "not_done")
EXTENSION = ".mp4"
SEPARATOR = "__"
PART = re.compile(r"[A-Za-z0-9._-]+")


def parse_clip_name(name: str) -> tuple[str, int, str, str] | None:
    """Return (step, attempt, outcome, volunteer), or None when the name breaks the rule."""
    if not name.lower().endswith(EXTENSION):
        return None
    parts = name[: -len(EXTENSION)].split(SEPARATOR)
    if len(parts) != 4 or any(not PART.fullmatch(p) or p.startswith("_") or p.endswith("_") for p in parts):
        return None
    step, attempt, outcome, volunteer = parts
    if not attempt.isdigit() or int(attempt) < 1 or outcome not in OUTCOMES:
        return None
    return step, int(attempt), outcome, volunteer


def clip_name(step: str, attempt: int, outcome: str, volunteer: str) -> str:
    return SEPARATOR.join([step, str(attempt), outcome, volunteer]) + EXTENSION


def load_camera_steps(catalog: Path) -> dict[str, dict]:
    """Camera-scored steps (method other than `none`) from the built catalogue, keyed by step ID."""
    data = json.loads(catalog.read_text(encoding="utf-8"))
    return {s["id"]: s for s in data["steps"] if (s.get("detector") or {}).get("method", "none") != "none"}
