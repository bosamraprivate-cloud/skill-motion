"""Export app-ready character clips into Android assets (Story 2.3).

- Reaction clips: copies art/animations/<variant>/react/<state>.webp (already matted, transparent).
- Step clips: cuts each step's segment of art/animations/<variant>/steps/raw/<clip>.mp4, as mapped by
  tools/art/step_clips.json, into an opaque animated WebP plus a still frame (first frame of the segment).
  Still-only steps get just the still. Null seconds on a shared clip split it evenly among its steps.
- Writes android/app/src/main/assets/character/index.json, which the app lists in the preview screen.

Usage:
    python tools/art/export_clips.py
"""

from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art" / "animations"
STEP_CLIPS = Path(__file__).resolve().parent / "step_clips.json"
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
OUT_DIR = "character"

REACTION_STATES = ("idle", "talk", "point", "encourage", "celebrateSmall", "celebrate", "celebrateBig")
STEP_VARIANTS = ("boy",)  # pilot: only the boy has step clips so far
STEP_SIZE = 720
STEP_SPEED = 1.25
STEP_QUALITY = 75


class ExportError(Exception):
    """A source file is missing or the mapping is inconsistent."""


@dataclass(frozen=True)
class Segment:
    step_id: str
    clip: str | None  # clip file stem; None for a still-only step
    start: float
    end: float | None  # None for a still-only step
    still_clip: str
    still_at: float


def plan_task(task_id: str, task: dict, duration: Callable[[str], float]) -> list[Segment]:
    """Turns one task of step_clips.json into ordered segments.

    Steps sharing a clip with null (or absent) seconds split the clip evenly in step order.
    """
    clips: dict[str, str] = task["clips"]
    steps: dict[str, dict] = task["steps"]
    by_clip: dict[str, list[str]] = {}
    for step_id in sorted(steps):
        entry = steps[step_id]
        # Only steps with null seconds share the even split; explicit seconds are kept as given.
        if "clip" in entry and (entry.get("startSec") is None or entry.get("endSec") is None):
            by_clip.setdefault(entry["clip"], []).append(step_id)
    warned = False
    out: list[Segment] = []
    for step_id in sorted(steps):
        entry = steps[step_id]
        if "still" in entry:
            key = entry["still"]
            if key not in clips:
                raise ExportError(f"{step_id}: unknown clip key {key!r}")
            out.append(Segment(step_id, None, 0.0, None, clips[key], float(entry.get("atSec") or 0)))
            continue
        key = entry.get("clip")
        if key not in clips:
            raise ExportError(f"{step_id}: unknown clip key {key!r}")
        stem = clips[key]
        start, end = entry.get("startSec"), entry.get("endSec")
        if start is None or end is None:
            group = by_clip[key]
            total = duration(stem)
            if len(group) > 1 and not warned:
                print(f"warning: {task_id}: null seconds, splitting shared clips evenly", file=sys.stderr)
                warned = True
            i = group.index(step_id)
            start, end = total * i / len(group), total * (i + 1) / len(group)
        out.append(Segment(step_id, stem, float(start), float(end), stem, float(start)))
    return out


def probe_duration(path: Path) -> float:
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
        capture_output=True, text=True, check=True,
    )
    return float(result.stdout.strip())


def probe_fps(path: Path) -> float:
    rate = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=r_frame_rate",
         "-of", "csv=p=0", str(path)],
        capture_output=True, text=True, check=True,
    ).stdout.strip()
    num, den = rate.split("/")
    return int(num) / int(den)


SCALE = f"scale='if(gt(iw,ih),{STEP_SIZE},-2)':'if(gt(iw,ih),-2,{STEP_SIZE})'"


def write_still(src: Path, at: float, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-ss", f"{at:.3f}", "-i", str(src), "-frames:v", "1",
         "-vf", SCALE, "-c:v", "libwebp", "-q:v", str(STEP_QUALITY), str(out)],
        check=True,
    )


def write_segment(src: Path, start: float, end: float, out: Path) -> None:
    from cutout_video import encode

    tmp = Path(tempfile.mkdtemp(prefix="seg_"))
    try:
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-ss", f"{start:.3f}", "-to", f"{end:.3f}", "-i", str(src),
             "-vf", SCALE, "-start_number", "0", str(tmp / "f_%04d.png")],
            check=True,
        )
        # encode() writes loop=forever; the app plays segments once via Coil's repeatCount.
        encode(tmp, out, probe_fps(src) * STEP_SPEED, STEP_QUALITY)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def export(
    art: Path = ART,
    step_clips: Path = STEP_CLIPS,
    assets: Path = ASSETS,
    *,
    duration: Callable[[Path], float] = probe_duration,
    segment: Callable[[Path, float, float, Path], None] = write_segment,
    still: Callable[[Path, float, Path], None] = write_still,
) -> dict:
    """Exports all clips and returns the index written to assets/character/index.json."""
    mapping = json.loads(step_clips.read_text(encoding="utf-8"))
    out_root = assets / OUT_DIR
    variants = sorted(p.name for p in art.iterdir() if (p / "react").is_dir())
    if not variants:
        raise ExportError(f"no reaction clips under {art}")
    # Validate every source before clearing old output, so a failed run leaves the assets intact.
    for variant in variants:
        needed = [art / variant / "react" / f"{s}.webp" for s in REACTION_STATES]
        if variant in STEP_VARIANTS:
            needed += [
                art / variant / "steps" / "raw" / f"{stem}.mp4"
                for k, task in mapping.items() if not k.startswith("_")
                for stem in task["clips"].values()
            ]
        for path in needed:
            if not path.is_file():
                raise ExportError(f"missing source {path}")
    shutil.rmtree(out_root, ignore_errors=True)  # drop stale files from removed or renamed steps
    index: dict = {"variants": []}
    for variant in variants:
        reactions = []
        for state in REACTION_STATES:
            src = art / variant / "react" / f"{state}.webp"
            if not src.is_file():
                raise ExportError(f"missing reaction clip {src}")
            rel = f"{OUT_DIR}/{variant}/react/{state}.webp"
            (assets / rel).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, assets / rel)
            reactions.append({"state": state, "file": rel})
        tasks = []
        if variant in STEP_VARIANTS:
            raw = art / variant / "steps" / "raw"

            def source(stem: str, raw: Path = raw) -> Path:
                path = raw / f"{stem}.mp4"
                if not path.is_file():
                    raise ExportError(f"missing step clip {path}")
                return path

            for task_id in (k for k in mapping if not k.startswith("_")):
                steps = []
                for seg in plan_task(task_id, mapping[task_id], lambda s: duration(source(s))):
                    base = f"{OUT_DIR}/{variant}/steps/{seg.step_id}"
                    still(source(seg.still_clip), seg.still_at, assets / f"{base}.still.webp")
                    clip_rel = None
                    if seg.clip is not None and seg.end is not None:
                        clip_rel = f"{base}.webp"
                        segment(source(seg.clip), seg.start, seg.end, assets / clip_rel)
                    steps.append({"id": seg.step_id, "clip": clip_rel, "still": f"{base}.still.webp"})
                tasks.append({"id": task_id, "steps": steps})
        index["variants"].append({"id": variant, "reactions": reactions, "tasks": tasks})
    out_root.mkdir(parents=True, exist_ok=True)
    (out_root / "index.json").write_text(json.dumps(index, indent=2) + "\n", encoding="utf-8")
    return index


def main() -> None:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    try:
        index = export()
    except ExportError as e:
        sys.exit(f"export_clips: {e}")
    n = sum(len(t["steps"]) for v in index["variants"] for t in v["tasks"])
    print(f"exported {len(index['variants'])} variants, {n} steps -> {ASSETS / OUT_DIR}")


if __name__ == "__main__":
    main()
