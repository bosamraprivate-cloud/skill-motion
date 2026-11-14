import csv
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from accuracy_report import COLUMNS, END, START, build_report, main, percentile  # noqa: E402


def write_results(path: Path, rows: list[tuple]) -> Path:
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(COLUMNS)
        for clip, verdict, verifier, latency in rows:
            step, _, outcome, _ = clip.removesuffix(".mp4").split("__")
            model = "deepseek-flash" if verifier == "cloud" else ""
            prompt = "p1" if model else ""
            w.writerow([clip, step, outcome, verdict, "x", verifier, model, prompt, latency, "cv1", "rv1"])
    return path


def write_manifest(path: Path, clips: list[str]) -> Path:
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["file", "outcome"])
        for c in clips:
            w.writerow([c, c.split("__")[2]])
    return path


ROWS = [
    ("a.s1__1__done__v01.mp4", "done", "pose", 10),
    ("a.s1__2__done__v01.mp4", "done", "pose", 12),
    ("a.s1__1__not_done__v01.mp4", "not_done", "pose", 11),
    ("a.s1__1__done__v02.mp4", "cannot_tell", "pose", 9),
    ("b.s2__1__done__v01.mp4", "not_done", "cloud", 1000),
    ("b.s2__1__not_done__v01.mp4", "not_done", "cloud", 3000),
    ("b.s2__2__done__v01.mp4", "done", "cloud", 2000),
    ("b.s2__1__done__v02.mp4", "not_done", "cloud", 4000),
]


def test_report_has_per_step_rates_flags_latency_and_unmatched(tmp_path):
    results = write_results(tmp_path / "r.csv", ROWS)
    manifest = write_manifest(tmp_path / "clips.csv", [r[0] for r in ROWS[:-1]])
    out = tmp_path / "m3.md"
    seed = f"# M3\n\nintro\n\n{START}\nOLDBODY\n{END}\n\n## Threshold change log\n\nkept\n"
    out.write_text(seed, encoding="utf-8")
    assert main([str(results), "--manifest", str(manifest), "--out", str(out)]) == 0
    text = out.read_text(encoding="utf-8")
    assert "OLDBODY" not in text and "kept" in text and "intro" in text
    # a.s1: 3 decided, all agree; 1 cannot tell of 4 = 25 % -> flagged for cannot tell only.
    assert "| `a.s1` | 4 | 2 | 1 | 1 | 2 | 0 | 0 | 1 | 100.0 % | 25.0 % | cannot tell above 15 % |" in text
    # b.s2: 2 of 4 agree = 50 % -> flagged.
    assert "| `b.s2` | 4 | 1 | 3 | 0 | 1 | 2 | 0 | 1 | 50.0 % | 0.0 % | agreement below 80 % |" in text
    assert "- p50: 2000 ms" in text and "- p90: 4000 ms" in text
    assert "`b.s2__1__done__v02.mp4`" in text.split("### Unmatched clips")[1]
    assert "`deepseek-flash`" in text and "`cv1`" in text


def test_cannot_tell_is_never_disagreement():
    rows = [
        dict(
            zip(
                COLUMNS,
                [f"s__1__done__v{i}.mp4", "s", "done", "cannot_tell", "x", "pose", "", "", "1", "cv", "rv"],
                strict=True,
            )
        )
        for i in range(3)
    ]
    report = build_report(rows, {})
    assert "| `s` | 3 | 0 | 0 | 3 | 0 | 0 | 0 | 0 | n/a | 100.0 % |" in report


def test_empty_csv_says_no_data_and_fails(tmp_path):
    results = write_results(tmp_path / "r.csv", [])
    out = tmp_path / "m3.md"
    assert main([str(results), "--manifest", str(tmp_path / "none.csv"), "--out", str(out)]) == 1
    assert "no data" in out.read_text(encoding="utf-8")


def test_deterministic(tmp_path):
    results = write_results(tmp_path / "r.csv", ROWS)
    a, b = tmp_path / "a.md", tmp_path / "b.md"
    for o in (a, b):
        main([str(results), "--manifest", str(tmp_path / "none.csv"), "--out", str(o)])
    assert a.read_text(encoding="utf-8") == b.read_text(encoding="utf-8")


def test_percentile_nearest_rank():
    assert percentile([], 0.5) is None
    assert percentile([5], 0.9) == 5
    assert percentile([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 0.9) == 9
