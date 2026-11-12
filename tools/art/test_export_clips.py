import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent))

from export_clips import REACTION_STATES, ExportError, export, plan_task  # noqa: E402

TASK = {
    "clips": {"look": "t.look", "bite": "t.bite", "sit": "t.sit"},
    "steps": {
        "t.task.01": {"clip": "look"},
        "t.task.02": {"still": "sit", "atSec": 0},
        "t.task.03": {"clip": "bite", "startSec": None, "endSec": None},
        "t.task.04": {"clip": "bite", "startSec": None, "endSec": None},
        "t.task.05": {"clip": "bite", "startSec": None, "endSec": None},
    },
}


def test_null_seconds_split_shared_clip_evenly(capsys):
    segs = {s.step_id: s for s in plan_task("t.task", TASK, lambda stem: 3.0)}
    assert [(segs[i].start, segs[i].end) for i in ("t.task.03", "t.task.04", "t.task.05")] == [
        (0.0, 1.0), (1.0, 2.0), (2.0, 3.0)
    ]
    assert segs["t.task.01"].start == 0.0 and segs["t.task.01"].end == 3.0
    assert "warning" in capsys.readouterr().err


def test_even_split_follows_step_order_not_mapping_order():
    task = {
        "clips": {"b": "c.b"},
        "steps": {
            "x.y.02": {"clip": "b", "startSec": None, "endSec": None},
            "x.y.01": {"clip": "b", "startSec": None, "endSec": None},
        },
    }
    segs = {s.step_id: s for s in plan_task("x.y", task, lambda stem: 2.0)}
    assert (segs["x.y.01"].start, segs["x.y.01"].end) == (0.0, 1.0)
    assert (segs["x.y.02"].start, segs["x.y.02"].end) == (1.0, 2.0)


def test_mixed_explicit_and_null_seconds_split_only_null_steps():
    task = {
        "clips": {"b": "c.b"},
        "steps": {
            "x.y.01": {"clip": "b", "startSec": 0.2, "endSec": 0.8},
            "x.y.02": {"clip": "b", "startSec": None, "endSec": None},
            "x.y.03": {"clip": "b", "startSec": None, "endSec": None},
        },
    }
    segs = {s.step_id: s for s in plan_task("x.y", task, lambda stem: 4.0)}
    assert (segs["x.y.01"].start, segs["x.y.01"].end) == (0.2, 0.8)
    assert (segs["x.y.02"].start, segs["x.y.02"].end) == (0.0, 2.0)
    assert (segs["x.y.03"].start, segs["x.y.03"].end) == (2.0, 4.0)


def test_stale_output_is_removed(tmp_path):
    stale = tmp_path / "assets/character/boy/steps/old.webp"
    stale.parent.mkdir(parents=True)
    stale.write_bytes(b"x")
    _run(tmp_path, _art(tmp_path))
    assert not stale.exists()


def test_explicit_seconds_are_kept():
    task = {"clips": {"a": "c.a"}, "steps": {"x.y.01": {"clip": "a", "startSec": 0.5, "endSec": 1.5}}}
    (seg,) = plan_task("x.y", task, lambda stem: 9.0)
    assert (seg.start, seg.end, seg.still_at) == (0.5, 1.5, 0.5)


def test_still_only_step_has_no_clip():
    segs = {s.step_id: s for s in plan_task("t.task", TASK, lambda stem: 3.0)}
    still = segs["t.task.02"]
    assert still.clip is None and still.end is None and still.still_clip == "t.sit"


def _art(tmp: Path, variants=("boy",), raw=("t.look", "t.bite", "t.sit")) -> Path:
    art = tmp / "art"
    for v in variants:
        (art / v / "react").mkdir(parents=True)
        for s in REACTION_STATES:
            (art / v / "react" / f"{s}.webp").write_bytes(b"RIFF")
    (art / "boy" / "steps" / "raw").mkdir(parents=True, exist_ok=True)
    for stem in raw:
        (art / "boy" / "steps" / "raw" / f"{stem}.mp4").write_bytes(b"")
    return art


def _run(tmp: Path, art: Path):
    mapping = tmp / "step_clips.json"
    mapping.write_text(json.dumps({"_doc": "x", "t.task": TASK}), encoding="utf-8")
    calls = []
    index = export(
        art, mapping, tmp / "assets",
        duration=lambda p: 3.0,
        segment=lambda src, a, b, out: calls.append(("seg", src.stem, a, b, out.name)),
        still=lambda src, at, out: calls.append(("still", src.stem, at, out.name)),
    )
    return index, calls


def test_index_content(tmp_path):
    index, calls = _run(tmp_path, _art(tmp_path, variants=("boy", "girl")))
    assert [v["id"] for v in index["variants"]] == ["boy", "girl"]
    boy, girl = index["variants"]
    assert boy["reactions"][0] == {"state": "idle", "file": "character/boy/react/idle.webp"}
    assert girl["tasks"] == []
    steps = boy["tasks"][0]["steps"]
    assert [s["id"] for s in steps] == [f"t.task.0{i}" for i in range(1, 6)]
    assert steps[1] == {"id": "t.task.02", "clip": None, "still": "character/boy/steps/t.task.02.still.webp"}
    assert steps[2]["clip"] == "character/boy/steps/t.task.03.webp"
    assert ("seg", "t.bite", 1.0, 2.0, "t.task.04.webp") in calls
    assert (tmp_path / "assets/character/girl/react/celebrate.webp").is_file()
    written = json.loads((tmp_path / "assets/character/index.json").read_text(encoding="utf-8"))
    assert written == index


def test_missing_step_source_fails(tmp_path):
    with pytest.raises(ExportError, match="t.bite"):
        _run(tmp_path, _art(tmp_path, raw=("t.look", "t.sit")))


def test_missing_reaction_fails(tmp_path):
    art = _art(tmp_path)
    (art / "boy" / "react" / "talk.webp").unlink()
    with pytest.raises(ExportError, match="talk"):
        _run(tmp_path, art)
