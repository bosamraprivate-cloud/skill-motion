"""strings.ar-eg.json covers every step and activity ID exactly, in Arabic."""

import json
import re
from pathlib import Path

import build_catalog

STRINGS = json.loads(Path(__file__).with_name("strings.ar-eg.json").read_text(encoding="utf-8"))
ARABIC = re.compile(r"[؀-ۿ]")


def _check(section: str, ids: list[str]) -> None:
    table = STRINGS[section]
    assert set(table) == set(ids), (set(ids) - set(table), set(table) - set(ids))
    for item_id, text in table.items():
        assert text.strip(), item_id
        assert ARABIC.search(text), item_id


def test_every_step_has_arabic_sentence():
    _check("steps", [s.id for s in build_catalog.parse_steps()])


def test_every_activity_has_arabic_sentence():
    _check("activities", [a.id for a in build_catalog.parse_activities()])


def test_ui_labels_present():
    for key in ("done", "help", "repeat", "start"):
        assert ARABIC.search(STRINGS["ui"][key])


def test_notes_reference_known_ids():
    known = set(STRINGS["steps"]) | set(STRINGS["activities"]) | {"_all"}
    assert set(STRINGS["notes"]) <= known
