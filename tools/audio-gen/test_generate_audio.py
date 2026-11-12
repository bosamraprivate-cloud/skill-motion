"""Tests for generate_audio.py with a mocked ElevenLabs client (no network)."""

import csv
import importlib.util
import json
import sys
from pathlib import Path

import pytest
from elevenlabs.core.api_error import ApiError

_SCRIPT = Path(__file__).with_name("generate_audio.py")
_spec = importlib.util.spec_from_file_location("generate_audio", _SCRIPT)
ga = importlib.util.module_from_spec(_spec)
sys.modules["generate_audio"] = ga
_spec.loader.exec_module(ga)

STEP_IDS = ["drink.cup.01", "drink.cup.02"]
ACTIVITY_IDS = ["mild.rom.01"]


class FakeTTS:
    def __init__(self, fail_after=None, error=None):
        self.calls = []
        self.fail_after = fail_after
        self.error = error

    def convert(self, voice_id, **kwargs):
        if self.fail_after is not None and len(self.calls) >= self.fail_after:
            raise self.error
        self.calls.append((voice_id, kwargs))
        return iter([b"ID3", kwargs["text"].encode("utf-8")])


class FakeClient:
    def __init__(self, **kw):
        self.text_to_speech = FakeTTS(**kw)


@pytest.fixture
def env(tmp_path):
    strings = {
        "steps": {"drink.cup.01": "بص على الكوباية.", "drink.cup.02": "مد إيدك للكوباية."},
        "activities": {"mild.rom.01": "ارفع دراعاتك فوق راسك."},
    }
    (tmp_path / "strings.json").write_text(json.dumps(strings, ensure_ascii=False), encoding="utf-8")
    paths = {
        "strings_file": tmp_path / "strings.json",
        "config_file": ga.CONFIG_FILE,
        "manifest_file": tmp_path / "manifest.json",
        "review_file": tmp_path / "review.csv",
        "output_dir": tmp_path / "out",
        "ids": (STEP_IDS, ACTIVITY_IDS),
    }
    return tmp_path, paths


def run(paths, argv=(), client=None):
    return ga.main(list(argv), client=client, **paths)


def test_first_run_writes_all_files_and_manifest(env):
    _, paths = env
    client = FakeClient()
    assert run(paths, client=client) == 0
    out = paths["output_dir"]
    for item_id in STEP_IDS + ACTIVITY_IDS:
        assert (out / f"{item_id}.mp3").exists()
        assert (out / f"{item_id}.slow.mp3").exists()
    assert len(client.text_to_speech.calls) == 6
    manifest = json.loads(paths["manifest_file"].read_text(encoding="utf-8"))
    entry = manifest["files"]["drink.cup.01.slow.mp3"]
    assert entry["voiceId"] == "I3u6waC588j43py1kDN2"
    assert entry["voiceSettings"]["speed"] < 1.0
    speeds = {kw["voice_settings"].speed for _, kw in client.text_to_speech.calls}
    assert speeds == {1.0, 0.8}
    rows = list(csv.DictReader(paths["review_file"].open(encoding="utf-8")))
    assert len(rows) == 6 and {r["status"] for r in rows} == {"pending"}


def test_rerun_unchanged_makes_no_calls(env):
    _, paths = env
    run(paths, client=FakeClient())
    client = FakeClient()
    assert run(paths, client=client) == 0
    assert client.text_to_speech.calls == []


def test_changed_text_regenerates_only_that_id(env):
    _, paths = env
    run(paths, client=FakeClient())
    data = json.loads(paths["strings_file"].read_text(encoding="utf-8"))
    data["steps"]["drink.cup.02"] = "امسك الكوباية."
    paths["strings_file"].write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    client = FakeClient()
    run(paths, client=client)
    assert {kw["text"] for _, kw in client.text_to_speech.calls} == {"امسك الكوباية."}
    assert len(client.text_to_speech.calls) == 2


def test_regenerate_flag_forces_call_and_resets_to_pending(env):
    _, paths = env
    run(paths, client=FakeClient())
    review = paths["review_file"]
    rows = list(csv.DictReader(review.open(encoding="utf-8")))
    for r in rows:
        r["status"] = "regenerate" if r["file"] == "drink.cup.01.mp3" else "approved"
    with review.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=ga.REVIEW_FIELDS)
        w.writeheader()
        w.writerows(rows)
    client = FakeClient()
    run(paths, client=client)
    assert len(client.text_to_speech.calls) == 1
    status = {r["file"]: r["status"] for r in csv.DictReader(review.open(encoding="utf-8"))}
    assert status["drink.cup.01.mp3"] == "pending"
    assert status["drink.cup.02.mp3"] == "approved"


@pytest.mark.parametrize(
    "error",
    [
        ApiError(status_code=429, body={"detail": {"status": "too_many_concurrent_requests"}}),
        ApiError(status_code=401, body={"detail": {"status": "quota_exceeded", "message": "quota"}}),
    ],
)
def test_quota_hit_stops_saves_manifest_and_exits_3(env, capsys, error):
    _, paths = env
    client = FakeClient(fail_after=3, error=error)
    assert run(paths, client=client) == 3
    manifest = json.loads(paths["manifest_file"].read_text(encoding="utf-8"))
    assert len(manifest["files"]) == 3
    out = paths["output_dir"]
    assert not list(out.glob("*.part"))
    assert len(list(out.glob("*.mp3"))) == 3
    assert "3 file(s) remain" in capsys.readouterr().err
    # a later run continues with the rest only
    client2 = FakeClient()
    assert run(paths, client=client2) == 0
    assert len(client2.text_to_speech.calls) == 3


@pytest.mark.parametrize(
    "error",
    [
        ApiError(status_code=401, body={"detail": "invalid_api_key"}),
        ApiError(
            status_code=402,
            body={"detail": {"status": "payment_required", "message": "No library voices."}},
        ),
    ],
)
def test_other_api_error_exits_4_with_one_line(env, capsys, error):
    _, paths = env
    client = FakeClient(fail_after=1, error=error)
    assert run(paths, client=client) == 4
    err = capsys.readouterr().err.strip()
    assert len(err.splitlines()) == 1
    assert str(error.status_code) in err and "Traceback" not in err
    manifest = json.loads(paths["manifest_file"].read_text(encoding="utf-8"))
    assert len(manifest["files"]) == 1


def test_missing_key_exits_2_without_echo(env, monkeypatch, capsys):
    _, paths = env
    monkeypatch.delenv(ga.KEY_ENV, raising=False)
    assert run(paths) == 2
    err = capsys.readouterr().err
    assert ga.KEY_ENV in err


def test_missing_string_exits_1_listing_ids(env, capsys):
    _, paths = env
    paths["ids"] = (STEP_IDS + ["drink.cup.03"], ACTIVITY_IDS)
    assert run(paths, client=FakeClient()) == 1
    assert "drink.cup.03" in capsys.readouterr().err


def test_dry_run_prints_totals_without_network(env, capsys):
    _, paths = env
    client = FakeClient()
    assert run(paths, ["--dry-run"], client=client) == 0
    out = capsys.readouterr().out
    total = sum(len(t) for t in ["بص على الكوباية.", "مد إيدك للكوباية.", "ارفع دراعاتك فوق راسك."])
    assert f"normal: {total} characters" in out
    assert f"slow: {total} characters" in out
    assert client.text_to_speech.calls == []


def test_only_generates_single_pair(env):
    _, paths = env
    client = FakeClient()
    assert run(paths, ["--only", "drink.cup.01"], client=client) == 0
    files = sorted(p.name for p in paths["output_dir"].iterdir())
    assert files == ["drink.cup.01.mp3", "drink.cup.01.slow.mp3"]
    rows = list(csv.DictReader(paths["review_file"].open(encoding="utf-8")))
    assert len(rows) == 6
