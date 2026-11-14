import csv
import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent))

from compute_metrics import (  # noqa: E402
    CANNOT_TELL,
    COLUMNS,
    DONE,
    END,
    NOT_DONE,
    NOT_SCORED,
    START,
    RuleAttempt,
    build_report,
    compute,
    main,
    quadratic_kappa,
    read_clips,
    read_results,
    rule1_level,
)

FIX = Path(__file__).parent / "fixtures"


def row(number, level, outcome, none=False):
    return RuleAttempt(number, level, outcome, none)


# Rule 1: mirrors RulesEngineTest stepLevel cases.
@pytest.mark.parametrize(
    ("rows", "expected"),
    [
        ([row(1, 1, DONE)], 3),
        ([row(1, 2, DONE)], 2),
        ([row(1, 1, NOT_DONE), row(2, 2, DONE)], 2),
        ([row(1, 1, NOT_DONE), row(2, 2, NOT_DONE), row(3, 3, DONE)], 2),
        ([row(1, 1, NOT_DONE), row(2, 2, NOT_DONE), row(3, 3, NOT_DONE)], 1),
        ([row(1, 1, CANNOT_TELL), row(2, 1, CANNOT_TELL)], NOT_SCORED),
        ([row(1, 1, CANNOT_TELL), row(1, 1, CANNOT_TELL)], NOT_SCORED),
        ([row(1, 1, CANNOT_TELL), row(2, 1, NOT_DONE), row(3, 2, CANNOT_TELL)], None),
        ([row(1, 1, CANNOT_TELL), row(2, 1, DONE)], 3),
        ([row(1, 1, NOT_DONE), row(2, 1, DONE)], 2),
        ([row(1, 1, CANNOT_TELL, none=True)], NOT_SCORED),
        ([], None),
        ([row(1, 1, NOT_DONE), row(2, 2, NOT_DONE)], None),
    ],
)
def test_rule1_mirrors_kotlin(rows, expected):
    assert rule1_level(rows) == expected


def test_cannot_tell_is_never_not_done():
    assert (
        rule1_level(
            [row(1, 1, CANNOT_TELL), row(2, 1, NOT_DONE), row(3, 2, CANNOT_TELL), row(4, 2, NOT_DONE)]
        )
        is None
    )


def test_kappa_basics():
    assert quadratic_kappa([]) is None
    assert quadratic_kappa([(3, 3), (2, 2), (1, 1)]) == pytest.approx(1.0)
    assert quadratic_kappa([(3, 3), (3, 3)]) is None  # no expected disagreement
    assert quadratic_kappa([(1, 3), (3, 1)]) == pytest.approx(-1.0)


def test_fixture_matches_hand_computed_values():
    expected = json.loads((FIX / "metrics_fixture.expected.json").read_text(encoding="utf-8"))
    got = compute(read_results(FIX / "metrics_fixture.csv"), read_clips(FIX / "metrics_clips.csv")).as_dict()
    assert got.keys() == expected.keys()
    for key, value in expected.items():
        assert got[key] == (pytest.approx(value) if isinstance(value, float) else value), key


def test_corrected_level_does_not_change_app_accuracy(tmp_path):
    rows = read_results(FIX / "metrics_fixture.csv")
    for r in rows:
        r["correctedLevel"] = ""
    clips = read_clips(FIX / "metrics_clips.csv")
    plain, base = compute(rows, clips), compute(read_results(FIX / "metrics_fixture.csv"), clips)
    assert plain.kappa == base.kappa and plain.agreement == base.agreement
    assert plain.corrected_steps == 0


def test_report_flags_targets_and_label():
    m = compute(read_results(FIX / "metrics_fixture.csv"), read_clips(FIX / "metrics_clips.csv"))
    text = build_report(m)
    assert "Volunteer accuracy" in text
    assert "80.0 % (8/10)" in text and "| meets |" in text
    assert "0.583" in text and text.count("**below target**") == 2
    assert "`missing.mp4`" in text
    assert "| `fixture.c` | 0 | 1 | 0 | 0 | 2 | 0 | 0 | 0 | 0 |" in text


def test_main_writes_between_markers(tmp_path):
    out = tmp_path / "m.md"
    out.write_text(f"# t\n{START}\nold\n{END}\nkeep\n", encoding="utf-8")
    code = main(
        [str(FIX / "metrics_fixture.csv"), "--clips", str(FIX / "metrics_clips.csv"), "--out", str(out)]
    )
    text = out.read_text(encoding="utf-8")
    assert code == 0 and "old" not in text and text.endswith("keep\n") and "Goal 8" in text


def test_main_creates_template(tmp_path):
    out = tmp_path / "new.md"
    assert (
        main([str(FIX / "metrics_fixture.csv"), "--clips", str(FIX / "metrics_clips.csv"), "--out", str(out)])
        == 0
    )
    assert "Matched clip attempts: 3" in out.read_text(encoding="utf-8")


@pytest.mark.parametrize(
    "content",
    ["", "a,b\n1,2\n", ",".join(COLUMNS) + "\n"],
    ids=["empty", "bad-header", "no-rows"],
)
def test_bad_input_exits_non_zero(tmp_path, content, capsys):
    path = tmp_path / "r.csv"
    path.write_text(content, encoding="utf-8")
    assert main([str(path), "--out", str(tmp_path / "o.md")]) == 1
    assert "error:" in capsys.readouterr().err
    assert not (tmp_path / "o.md").exists()


def test_bad_verdict_rejected(tmp_path):
    path = tmp_path / "r.csv"
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(COLUMNS)
        w.writerow(["s", "C-1", "t", "x", "a", "1", "1", "maybe", "", "local", "", "", "", "", "", ""])
    with pytest.raises(ValueError, match="bad verdict"):
        read_results(path)


def write_rows(path, overrides):
    """Fixture rows with per-row overrides {row_index: {column: value}}."""
    rows = read_results(FIX / "metrics_fixture.csv")
    for i, changes in overrides.items():
        rows[i].update(changes)
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=COLUMNS)
        w.writeheader()
        w.writerows(rows)
    return path


@pytest.mark.parametrize(
    ("overrides", "message"),
    [
        ({0: {"level": "4"}}, ":2: bad level"),
        ({0: {"level": "x"}}, ":2: bad level"),
        ({1: {"correctedLevel": "0"}}, ":3: bad correctedLevel"),
        ({2: {"attemptNumber": "a"}}, ":4: bad attemptNumber"),
        ({2: {"promptLevel": "0"}}, ":4: bad promptLevel"),
        ({1: {"attemptNumber": "2"}, 2: {"attemptNumber": "1"}}, ":4: attemptNumber 1 decreases"),
    ],
)
def test_bad_cells_exit_non_zero_with_row(tmp_path, capsys, overrides, message):
    path = write_rows(tmp_path / "r.csv", overrides)
    assert main([str(path), "--out", str(tmp_path / "o.md")]) == 1
    assert message in capsys.readouterr().err


def test_not_scored_correction_text(tmp_path):
    path = write_rows(
        tmp_path / "r.csv", {13: {"correctedLevel": "not_scored"}, 14: {"correctedLevel": "not_scored"}}
    )
    m = compute(read_results(path), {})
    assert m.corrected_steps == 1 and m.corrected_changed == 1
    assert m.kappa == pytest.approx(7 / 12)


def test_clips_header_error(tmp_path, capsys):
    clips = tmp_path / "c.csv"
    clips.write_text("file,step\nx.mp4,s\n", encoding="utf-8")
    assert (
        main([str(FIX / "metrics_fixture.csv"), "--clips", str(clips), "--out", str(tmp_path / "o.md")]) == 1
    )
    assert "missing columns outcome" in capsys.readouterr().err


def test_explicit_missing_clips_is_error(tmp_path, capsys):
    args = [
        str(FIX / "metrics_fixture.csv"),
        "--clips",
        str(tmp_path / "none.csv"),
        "--out",
        str(tmp_path / "o.md"),
    ]
    assert main(args) == 1
    assert "clips file not found" in capsys.readouterr().err
    assert read_clips(tmp_path / "none.csv") == {}


def test_report_uses_lf_newlines(tmp_path):
    out = tmp_path / "o.md"
    assert (
        main([str(FIX / "metrics_fixture.csv"), "--clips", str(FIX / "metrics_clips.csv"), "--out", str(out)])
        == 0
    )
    assert b"\r\n" not in out.read_bytes()


def test_kotlin_export_round_trip():
    """The Kotlin `CsvExporterTest` sample (Story 5.6) passes column validation with the exact header."""
    path = FIX / "results_export_sample.csv"
    raw = path.read_bytes()
    text = raw.decode("utf-8")
    assert not raw.startswith(b"\xef\xbb\xbf") and b"\r" not in raw
    assert text.splitlines()[0] == ",".join(COLUMNS)
    assert "Youssef" not in text
    rows = read_results(path)
    assert len(rows) == 8
    assert rows[2]["reason"] == "hand_far, wrist low"
    # Quote, newline and formula guard survive the round trip (leading ' stripped).
    assert rows[5]["reason"] == '-say "hi"\nthen -1'
    assert [r["level"] for r in rows if r["sessionId"] == "s3"] == [""]
    only = [r for r in rows if not r["attemptId"]]
    assert [(r["stepId"], r["correctedLevel"], r["level"]) for r in only] == [("eat-04", "2", "")]
    m = compute(rows, {})
    assert m.attempts == 7
    assert m.corrected_steps == 3  # eat-02, eat-03 and the attempt-less eat-04
    assert m.rated == 3


def _write(tmp_path, lines):
    path = tmp_path / "r.csv"
    path.write_text(",".join(COLUMNS) + "\n" + "\n".join(lines) + "\n", encoding="utf-8")
    return path


def test_correction_only_row_counts_only_for_corrections(tmp_path):
    path = _write(
        tmp_path,
        [
            "s,C-1,t,a,a1,1,1,done,ok,pose,,1,3,,done,",
            "s,C-1,t,b,,,,,,,,,,2,,",
        ],
    )
    m = compute(read_results(path), {})
    assert (m.attempts, m.decided, m.agree, len(m.kappa_pairs)) == (1, 1, 1, 1)
    assert (m.corrected_steps, m.corrected_changed, m.steps) == (1, 1, 1)


def test_correction_only_row_needs_corrected_level(tmp_path):
    path = _write(tmp_path, ["s,C-1,t,b,,,,,,,,,,,,"])
    with pytest.raises(ValueError, match="correction-only"):
        read_results(path)


def test_formula_guard_is_stripped(tmp_path):
    path = _write(tmp_path, ["s,C-1,t,a,a1,1,1,done,'=1+1,pose,,1,3,,done,'@x.mp4"])
    row = read_results(path)[0]
    assert (row["reason"], row["clipFile"]) == ("=1+1", "@x.mp4")
