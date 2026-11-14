import csv
import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent))

from clip_manifest import COLUMNS, DEFAULT_CATALOG, DEFAULT_MANIFEST, load_camera_steps  # noqa: E402
from generate_clips import build_rows, render  # noqa: E402
from validate_clips import main, validate  # noqa: E402


@pytest.fixture
def catalog(tmp_path):
    def step(sid, method):
        return {
            "id": sid,
            "task": "drink.cup",
            "number": int(sid[-2:]),
            "detector": {"method": method, "framing": "table", "check": None},
        }

    path = tmp_path / "assessment.json"
    steps = [step("drink.cup.01", "none"), step("drink.cup.02", "pose"), step("drink.cup.03", "cloud")]
    path.write_text(json.dumps({"steps": steps}), encoding="utf-8")
    return path


def write(tmp_path, rows):
    path = tmp_path / "clips.csv"
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=COLUMNS, lineterminator="\n")
        w.writeheader()
        w.writerows(rows)
    return path


def row(file="drink.cup.02__1__done__v01.mp4", **over):
    base = {
        "file": file,
        "task": "drink.cup",
        "step": "drink.cup.02",
        "attempt": "1",
        "outcome": "done",
        "volunteer": "v01",
        "framing": "table",
        "method": "pose",
        "scenario": "done_attempt_1",
    }
    return base | over


def test_generated_manifest_is_valid(tmp_path, catalog):
    rows = build_rows(load_camera_steps(catalog))
    assert len(rows) == 2 * 2 * 3
    assert validate(write(tmp_path, rows), catalog) == []


def test_committed_manifest_is_valid_and_up_to_date():
    assert validate(DEFAULT_MANIFEST, DEFAULT_CATALOG) == []
    expected = render(build_rows(load_camera_steps(DEFAULT_CATALOG)))
    assert DEFAULT_MANIFEST.read_text(encoding="utf-8") == expected


def test_bad_name_names_the_row(tmp_path, catalog):
    errors = validate(write(tmp_path, [row(), row(file="drink.cup.02_1_done_v01.mp4")]), catalog)
    assert len(errors) == 1
    assert errors[0].startswith("row 3:")


@pytest.mark.parametrize(
    "name",
    [
        "drink.cup.02__0__done__v01.mp4",
        "drink.cup.02__1__maybe__v01.mp4",
        "drink.cup.02__1__done__v01.mov",
        "drink.cup.02__1__done.mp4",
    ],
)
def test_rule_violations(tmp_path, catalog, name):
    assert validate(write(tmp_path, [row(file=name)]), catalog)


def test_step_mismatch_fails(tmp_path, catalog):
    errors = validate(write(tmp_path, [row(step="drink.cup.03")]), catalog)
    assert any("step 'drink.cup.03' does not match" in e for e in errors)


def test_none_and_unknown_steps_fail(tmp_path, catalog):
    rows = [
        row(file="drink.cup.01__1__done__v01.mp4", step="drink.cup.01"),
        row(file="x.y.09__1__done__v01.mp4", step="x.y.09"),
    ]
    errors = validate(write(tmp_path, rows), catalog)
    assert sum("not a camera-scored step" in e for e in errors) == 2


def test_missing_field_fails(tmp_path, catalog):
    assert validate(write(tmp_path, [row(volunteer="")]), catalog) == ["row 2: empty volunteer"]


def test_clips_dir_missing_and_extra(tmp_path, catalog):
    clips = tmp_path / "clips"
    clips.mkdir()
    (clips / "drink.cup.03__1__done__v01.mp4").write_bytes(b"")
    errors = validate(write(tmp_path, [row()]), catalog, clips)
    assert "missing clip: drink.cup.02__1__done__v01.mp4" in errors
    assert "extra clip not in manifest: drink.cup.03__1__done__v01.mp4" in errors
    (clips / "drink.cup.03__1__done__v01.mp4").unlink()
    (clips / "drink.cup.02__1__done__v01.mp4").write_bytes(b"")
    assert validate(write(tmp_path, [row()]), catalog, clips) == []


def test_main_exit_codes(tmp_path, catalog):
    assert main([str(write(tmp_path, [row()])), "--catalog", str(catalog)]) == 0
    assert main([str(write(tmp_path, [row(outcome="not_done")])), "--catalog", str(catalog)]) == 1
