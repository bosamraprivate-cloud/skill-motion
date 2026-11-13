import json
import re
from dataclasses import replace

import build_catalog as bc
import pytest

HEADER = (
    "| Step No. | Step | Functional domain | Functional sub-skills | Score | Performance |\n"
    "|---:|---|---|---|:---:|:---:|\n"
)


def table(heading: str, rows: list[tuple[str, str, str]]) -> str:
    body = "".join(f"| {n} | {text} | {dom} | subs |  |  |\n" for n, text, dom in rows)
    return f"### {heading}\n\n{HEADER}{body}\n"


def one(heading="Handwashing", n="1", text="Wash", dom="Hand skills"):
    return table(heading, [(n, text, dom)])


@pytest.fixture(scope="module")
def real_steps():
    return bc.parse_steps()


# --- real sources -------------------------------------------------------------


def test_real_sources_counts_and_unique_ids(real_steps):
    assert len(real_steps) == 147
    assert {s.task for s in real_steps} == set(bc.TASK_IDS.values())
    counts = {a: sum(s.area == a for s in real_steps) for a in ("eat", "drink", "hygiene", "dress")}
    assert counts == {"eat": 42, "drink": 38, "hygiene": 27, "dress": 40}
    assert len({s.id for s in real_steps}) == 147


def test_spot_check_drink_cup_06(real_steps):
    step = {s.id: s for s in real_steps}["drink.cup.06"]
    assert step.textEn == "Place the cup on your lips"
    assert step.tags == ("Motor planning", "Motor coordination")
    assert step.source == "eating-drinking-assessment-scale.md#Drinking from a cup"


def test_id_format(real_steps):
    pattern = re.compile(r"^[a-z]+(\.[a-z]+)+\.\d{2}$")
    assert pattern.match("drink.cup.06")
    assert all(pattern.match(s.id) for s in real_steps)


def test_jacket_off_untagged_rows(real_steps):
    steps = {s.id: s for s in real_steps}
    assert steps["dress.jacket.off.01"].tags == ()
    assert sum(s.tags == () for s in real_steps) == 8


# --- parsing --------------------------------------------------------------------


def test_two_tags_and_numeric_prefix():
    (step,) = bc.parse_text(one("1.1 Drinking from a cup", dom="Motor planning + Hand skills"), "f.md")
    assert step.id == "drink.cup.01"
    assert step.tags == ("Motor planning", "Hand skills")


def test_tag_spacing_variants():
    (step,) = bc.parse_text(one(dom="Motor planning+Hand skills"), "f.md")
    assert step.tags == ("Motor planning", "Hand skills")


def test_untagged_becomes_empty():
    (step,) = bc.parse_text(one(dom="Not tagged in source"), "f.md")
    assert step.tags == ()


def test_escaped_pipe_parsed():
    (step,) = bc.parse_text(one(text=r"Open a \| close"), "f.md")
    assert step.textEn == "Open a | close"


def test_other_tables_ignored():
    md = "## Legend\n\n| Score | Meaning |\n|---|---|\n| 1 | x |\n\n"
    assert bc.parse_text(md, "f.md") == []


# --- parse errors -----------------------------------------------------------------


def test_unknown_tag_names_location():
    md = table("Handwashing", [("1", "Wash", "Hand skills"), ("2", "Stand", "Balance")])
    with pytest.raises(bc.CatalogError, match=r"f\.md#Handwashing row 2.*Balance"):
        bc.parse_text(md, "f.md")


def test_duplicate_tag():
    with pytest.raises(bc.CatalogError, match="duplicate domain tag"):
        bc.parse_text(one(dom="Hand skills + Hand skills"), "f.md")


def test_unknown_heading():
    with pytest.raises(bc.CatalogError, match="Wearing a hat"):
        bc.parse_text(one("Wearing a hat"), "f.md")


def test_step_gap():
    md = table("Handwashing", [(n, "a", "Hand skills") for n in ("1", "2", "4")])
    with pytest.raises(bc.CatalogError, match="row 3"):
        bc.parse_text(md, "f.md")


def test_mismatched_header_under_task():
    md = "### Handwashing\n\n" + HEADER.replace("Functional domain", "Domain")
    with pytest.raises(bc.CatalogError, match=r"f\.md#Handwashing.*header"):
        bc.parse_text(md, "f.md")


def test_missing_separator():
    md = "### Handwashing\n\n" + HEADER.splitlines()[0] + "\n| 1 | a | Hand skills | s |  |  |\n"
    with pytest.raises(bc.CatalogError, match="separator"):
        bc.parse_text(md, "f.md")


def test_separator_wrong_cell_count():
    md = "### Handwashing\n\n" + HEADER.splitlines()[0] + "\n|---|---|\n| 1 | a | Hand skills | s |  |  |\n"
    with pytest.raises(bc.CatalogError, match="separator"):
        bc.parse_text(md, "f.md")


def test_rows_after_blank_line():
    md = one() + "| 2 | b | Hand skills | s |  |  |\n"
    with pytest.raises(bc.CatalogError, match="blank line"):
        bc.parse_text(md, "f.md")


def test_wrong_cell_count():
    md = "### Handwashing\n\n" + HEADER + "| 1 | a | Hand skills |\n"
    with pytest.raises(bc.CatalogError, match="cells"):
        bc.parse_text(md, "f.md")


@pytest.mark.parametrize("number", ["x", "١", "²"])
def test_non_numeric_step_no(number):
    with pytest.raises(bc.CatalogError, match="not a number"):
        bc.parse_text(one(n=number), "f.md")


def test_empty_step_cell():
    with pytest.raises(bc.CatalogError, match="empty Step"):
        bc.parse_text(one(text=""), "f.md")


def test_empty_table():
    with pytest.raises(bc.CatalogError, match="no rows"):
        bc.parse_text("### Handwashing\n\n" + HEADER, "f.md")


# --- validation ---------------------------------------------------------------------


def test_duplicate_id_names_sources():
    steps = bc.parse_text(one(), "a.md") + bc.parse_text(one(), "b.md")
    with pytest.raises(bc.CatalogError, match=r"handwash\.01 in a\.md#Handwashing, b\.md#Handwashing"):
        bc.validate(steps, check_totals=False)


def test_validate_wrong_total(real_steps):
    with pytest.raises(bc.CatalogError):
        bc.validate(real_steps[:-1])


def test_validate_wrong_area_count(real_steps):
    moved = [replace(s, area="eat") if s.task == "drink.cup" else s for s in real_steps]
    with pytest.raises(bc.CatalogError, match="area"):
        bc.validate(moved)


def test_validate_wrong_task_set(real_steps):
    renamed = [replace(s, task="eat.other", id=s.id.replace("eat.fork", "eat.other"))
               if s.task == "eat.fork" else s for s in real_steps]
    with pytest.raises(bc.CatalogError, match="task set"):
        bc.validate(renamed)


def test_validate_unknown_area(real_steps):
    odd = [replace(real_steps[0], area="play")] + real_steps[1:]
    with pytest.raises(bc.CatalogError, match="unknown area"):
        bc.validate(odd)


def test_missing_source_file(tmp_path):
    with pytest.raises(bc.CatalogError, match="not found"):
        bc.parse_steps(tmp_path)


# --- CLI ----------------------------------------------------------------------------------


def test_cli_check(capsys):
    assert bc.main(["--check"]) == 0
    out = capsys.readouterr().out
    expected = ("dress: 40", "eat: 42", "drink: 38", "hygiene: 27", "total: 147", "tasks: 15", "untagged: 8")
    for line in expected:
        assert line in out


def test_cli_error_exits_nonzero(tmp_path, capsys):
    for name in bc.SOURCE_FILES:
        (tmp_path / name).write_text(one(dom="Balance"), "utf-8")
    assert bc.main(["--check", "--reference-dir", str(tmp_path)]) == 1
    assert "Balance" in capsys.readouterr().err


# --- activities (Story 2.2) ------------------------------------------------------------


ALL = ("Tools", "How to perform", "Goal", "Measurement criterion")
DOMAINS = {"rom", "coord", "hand", "midline", "planning"}


def activity(n=1, title="Act", fields=ALL):
    body = "".join(f"- **{label}:** {label} text.\n" for label in fields)
    return f"##### {n}. {title}\n\n{body}\n"


def plan_md(*parts, plan="## 1. Mild plan", domain="### 3. Hand skills"):
    return f"# Plans\n\n{plan}\n\n{domain}\n\n**General goal:** Goal G.\n\n" + "".join(parts)


@pytest.fixture(scope="module")
def real_activities():
    return bc.parse_activities()


def test_real_activities_totals(real_activities):
    assert len(real_activities) == 148
    counts = {p: sum(a.plan == p for a in real_activities) for p in ("mild", "moderate", "severe")}
    assert counts == {"mild": 48, "moderate": 50, "severe": 50}
    assert len({a.id for a in real_activities}) == 148
    for plan in counts:
        assert {a.domain for a in real_activities if a.plan == plan} == DOMAINS
    pattern = re.compile(r"^(mild|moderate|severe)\.(rom|coord|hand|midline|planning)\.\d{2}$")
    assert all(pattern.match(a.id) for a in real_activities)
    assert all(a.generalGoal for a in real_activities)


def test_real_activities_spot_check(real_activities):
    act = {a.id: a for a in real_activities}["mild.hand.04"]
    assert act.title == "Opening and closing clothespins"
    assert act.criterion == "Attaching and removing 10 clothespins with control of the movement."
    assert act.generalGoal.startswith("To improve control of hand and finger movements")


def test_level4_only_domain_is_rom(real_activities):
    act = {a.id: a for a in real_activities}["mild.rom.01"]
    assert act.title == "Raising the arms overhead"
    assert act.tools == "A light stick, or no tools."
    assert act.generalGoal.startswith("To improve the range of motion")


def test_inline_parse_and_level4_domain():
    md = "## 2. Moderate\n\n#### Activities for range of motion\n\n" + activity(1) + activity(2)
    acts = bc.parse_activities_text(md)
    assert [a.id for a in acts] == ["moderate.rom.01", "moderate.rom.02"]
    assert acts[0].goal == "Goal text." and acts[0].generalGoal == ""


def test_general_goal_attached():
    acts = bc.parse_activities_text(plan_md(activity(1)))
    assert acts[0].id == "mild.hand.01" and acts[0].generalGoal == "Goal G."


@pytest.mark.parametrize(
    ("md", "needle"),
    [
        (plan_md(activity(1, fields=("Tools", "How to perform", "Measurement criterion"))), "Goal"),
        (plan_md(activity(1, fields=("Tools", *ALL))), "duplicate"),
        (plan_md(activity(1, fields=("Notes", *ALL))), "Notes"),
        ("## 1. Mild\n\n" + activity(1), "no domain"),
        (plan_md(activity(1), "## 4. Something\n"), "Something"),
        (plan_md(activity(1), activity(2), activity(4)), "not 1.."),
        (plan_md(activity(1), activity(1)), "duplicate"),
        (plan_md(activity(1), domain="### Hand skills and motor planning"), "2 domains"),
        (plan_md(activity(1), domain="### Balance"), "0 domains"),
        (plan_md("##### Unnumbered\n"), "no leading number"),
        (plan_md("##### 1. Act\n\n- **Tools:**\n"), "empty"),
    ],
)
def test_activity_errors(md, needle):
    with pytest.raises(bc.CatalogError, match=re.escape(needle)):
        bc.parse_activities_text(md)


def test_missing_field_names_heading_and_line():
    md = plan_md(activity(1, title="Clothespins", fields=("Tools", "Tools")))
    with pytest.raises(bc.CatalogError, match=r"line \d+.*Clothespins"):
        bc.parse_activities_text(md)


def test_validate_activities_wrong_total(real_activities):
    with pytest.raises(bc.CatalogError, match="mild"):
        bc.validate_activities(real_activities[1:])


def test_missing_activities_file(tmp_path):
    with pytest.raises(bc.CatalogError, match="treatment-plans.md"):
        bc.parse_activities(tmp_path)


def test_cli_check_prints_activities(capsys):
    assert bc.main(["--check"]) == 0
    out = capsys.readouterr().out
    for line in ("mild: 48", "moderate: 50", "severe: 50", "activities total: 148"):
        assert line in out
    assert "activities domain hand:" in out


# --- activities: review fixes -------------------------------------------------------------


@pytest.mark.parametrize(
    ("md", "needle"),
    [
        (plan_md(activity(1), plan="## 10. Ten plans"), "unknown plan heading"),
        (plan_md(activity(1), plan="## 1.5 Mild"), "unknown plan heading"),
        (plan_md(activity(1), "## 1. Mild again\n"), "duplicate plan"),
        (plan_md(activity(1) + "  continued text\n"), "unrecognised line"),
        (plan_md("##### 1. Act\n\n- **Tools:** x\n  - sub bullet\n"), "unrecognised line"),
        (plan_md(activity(1), "**General goal:** Late.\n"), "after an activity"),
        (plan_md("**General goal:** Again.\n", activity(1)), "second general goal"),
        (plan_md(activity(1), domain="### Two-hand skills"), "0 domains"),
        (plan_md(activity(1), "###### Deep\n"), "level-6"),
        (plan_md("- **Tools:** stray\n"), "outside an activity"),
        (activity(1), "outside a plan"),
        (plan_md(activity(100)), "1..99"),
        ("# A\n\n## 1. Mild\n\n### Hand skills\n\n# B\n\n" + activity(1), "outside a plan"),
    ],
)
def test_activity_review_errors(md, needle):
    with pytest.raises(bc.CatalogError, match=re.escape(needle)):
        bc.parse_activities_text(md)


def test_missing_fields_named():
    with pytest.raises(bc.CatalogError, match="How to perform, Goal, Measurement criterion"):
        bc.parse_activities_text(plan_md(activity(1, fields=("Tools",))))


def test_goal_reset_on_level4_domain_change():
    md = plan_md(activity(1), "#### Motor planning\n\n", activity(1))
    acts = bc.parse_activities_text(md)
    assert [(a.id, a.generalGoal) for a in acts] == [("mild.hand.01", "Goal G."), ("mild.planning.01", "")]


def test_whole_word_domain_match():
    acts = bc.parse_activities_text(plan_md(activity(1), domain="### Activities: HAND SKILLS"))
    assert acts[0].domain == "hand"


def test_validate_missing_domain_with_correct_plan_counts(real_activities):
    rewritten = [
        replace(a, domain="coord", id=f"mild.coord.{50 + a.number:02d}")
        if a.plan == "mild" and a.domain == "midline"
        else a
        for a in real_activities
    ]
    with pytest.raises(bc.CatalogError, match=r"missing domain\(s\) \['midline'\]"):
        bc.validate_activities(rewritten)


def test_validate_per_domain_counts(real_activities):
    moved = [
        replace(a, domain="coord", id="moderate.coord.11")
        if a.id == "moderate.hand.10"
        else a
        for a in real_activities
    ]
    with pytest.raises(bc.CatalogError, match="moderate.coord: expected 10"):
        bc.validate_activities(moved)


SNAPSHOT_DIR = bc.Path(__file__).resolve().parent


def test_activity_id_snapshot(real_activities):
    expected = (SNAPSHOT_DIR / "activity_ids.snapshot.txt").read_text("utf-8").split()
    assert sorted(a.id for a in real_activities) == expected


def test_step_id_snapshot(real_steps):
    expected = (SNAPSHOT_DIR / "step_ids.snapshot.txt").read_text("utf-8").split()
    assert sorted(s.id for s in real_steps) == expected


# --- Story 2.7: merge, validate, write, version stamp --------------------------


STEP_IDS = [s.id for s in bc.parse_steps()]
ACTIVITY_IDS = [a.id for a in bc.parse_activities()]
TASK_IDS = list(dict.fromkeys(s.task for s in bc.parse_steps()))


@pytest.fixture
def full(tmp_path):
    """Complete tmp inputs (real docs/reference, fake strings/detectors/media/audio)."""
    assets = tmp_path / "assets"
    audio = assets / "audio" / "ar-eg"
    audio.mkdir(parents=True)
    for sid in STEP_IDS:
        (audio / f"{sid}.mp3").write_bytes(b"x")
        (audio / f"{sid}.slow.mp3").write_bytes(b"x")
    variants = []
    for v in bc.VARIANTS:
        steps = []
        for sid in STEP_IDS:
            still = f"character/{v}/steps/{sid}.still.webp"
            (assets / still).parent.mkdir(parents=True, exist_ok=True)
            (assets / still).write_bytes(b"x")
            steps.append({"id": sid, "clip": None, "still": still})
        variants.append({"id": v, "reactions": [], "tasks": [{"id": "all", "steps": steps}]})
    (assets / "character" / "index.json").write_text(json.dumps({"variants": variants}), encoding="utf-8")
    strings = {
        "steps": {i: "نص" for i in STEP_IDS},
        "activities": {i: "نص" for i in ACTIVITY_IDS},
        "tasks": {t: "مهمة" for t in TASK_IDS},
        "areas": {a: "مجال" for a in bc.EXPECTED_AREA_COUNTS},
    }
    (tmp_path / "strings.json").write_text(json.dumps(strings, ensure_ascii=False), encoding="utf-8")
    detectors = {i: {"method": "none", "framing": None, "check": None} for i in STEP_IDS}
    (tmp_path / "detectors.json").write_text(json.dumps(detectors), encoding="utf-8")
    (tmp_path / "counters.json").write_text("[]", encoding="utf-8")
    return bc.Paths(
        strings=tmp_path / "strings.json",
        detectors=tmp_path / "detectors.json",
        activity_counters=tmp_path / "counters.json",
        assets_dir=assets,
        out_dir=tmp_path / "out",
    )


def edit_json(path, fn):
    data = json.loads(path.read_text(encoding="utf-8"))
    fn(data)
    path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")


def version_of(paths):
    files, _ = bc.build(paths)
    return json.loads(files["version.json"])["catalogVersion"]


def test_all_present_writes_three_files(full):
    assert bc.main([], paths=full) == 0
    names = sorted(p.name for p in full.out_dir.iterdir())
    assert names == ["activities.json", "assessment.json", "version.json"]
    version = json.loads((full.out_dir / "version.json").read_text(encoding="utf-8"))
    assert re.fullmatch(r"[0-9a-f]{12}", version["catalogVersion"])
    assert version["complete"] is True
    step = json.loads((full.out_dir / "assessment.json").read_text(encoding="utf-8"))["steps"][0]
    assert step["detector"] == {"method": "none", "framing": None, "check": None}
    assert step["audio"]["slow"].endswith(".slow.mp3")
    assert step["media"]["girl"]["still"].endswith(".still.webp")


def test_missing_arabic_fails_naming_id(full, capsys):
    edit_json(full.strings, lambda d: d["steps"].pop("drink.cup.06"))
    assert bc.main([], paths=full) == 1
    assert "drink.cup.06" in capsys.readouterr().err
    assert not full.out_dir.exists()


def test_missing_slow_audio_strict_and_dev(full, capsys):
    (full.assets_dir / "audio" / "ar-eg" / "eat.spoon.03.slow.mp3").unlink()
    assert bc.main([], paths=full) == 1
    assert "eat.spoon.03" in capsys.readouterr().err
    assert bc.main(["--allow-missing-media"], paths=full) == 0
    assert "eat.spoon.03" in capsys.readouterr().err
    assert json.loads((full.out_dir / "version.json").read_text())["complete"] is False


def test_missing_girl_mapping_names_id_and_variant(full):
    index = full.assets_dir / "character" / "index.json"
    edit_json(index, lambda d: d["variants"][1]["tasks"][0]["steps"].pop(0))
    expected = rf"{re.escape(STEP_IDS[0])}: no media mapping for variant girl"
    with pytest.raises(bc.CatalogError, match=expected):
        bc.build(full)
    files, warnings = bc.build(full, allow_missing_media=True)
    assert warnings and json.loads(files["assessment.json"])["steps"][0]["media"]["girl"] is None


def test_missing_activity_audio_never_fails(full):
    files, warnings = bc.build(full)
    assert not warnings
    assert json.loads(files["activities.json"])["activities"][0]["audio"] is None


def test_bad_detector_check_fails(full):
    bad = {"method": "pose", "framing": "upper", "check": {"type": "angleAbove", "deg": 500}}
    edit_json(full.detectors, lambda d: d.update({"eat.fork.02": bad}))
    with pytest.raises(bc.CatalogError, match="eat.fork.02"):
        bc.build(full)


def test_valid_detector_check_passes(full):
    check = {"type": "question", "text": "q"}
    detector = {"method": "ai", "framing": None, "check": check}
    edit_json(full.detectors, lambda d: d.update({"eat.fork.02": detector}))
    files, _ = bc.build(full)
    step = next(s for s in json.loads(files["assessment.json"])["steps"] if s["id"] == "eat.fork.02")
    assert step["detector"]["check"] == check


def test_unknown_detector_id_fails(full):
    detector = {"method": "none", "framing": None, "check": None}
    edit_json(full.detectors, lambda d: d.update({"eat.nope.01": detector}))
    assert bc.main([], paths=full) == 1


def test_version_changes_on_detector_activity_and_tag(full, tmp_path):
    base = version_of(full)
    detector = {"method": "x", "framing": None, "check": None}
    edit_json(full.detectors, lambda d: d.update({"eat.fork.02": detector}))
    v_detector = version_of(full)
    edit_json(full.activity_counters, lambda d: d.append({"activityId": ACTIVITY_IDS[0], "kind": "reps"}))
    v_activity = version_of(full)
    ref = tmp_path / "ref"
    ref.mkdir()
    for f in (*bc.SOURCE_FILES, bc.ACTIVITIES_FILE):
        (ref / f).write_text((bc.REFERENCE_DIR / f).read_text(encoding="utf-8-sig"), encoding="utf-8")
    scale = ref / "eating-drinking-assessment-scale.md"
    text = scale.read_text(encoding="utf-8")
    row = next(line for line in text.splitlines() if line.startswith("| 6 | Place the cup on your lips"))
    new_row = row.replace("Motor planning + Motor coordination", "Motor planning")
    assert new_row != row
    scale.write_text(text.replace(row, new_row), encoding="utf-8")
    v_tag = version_of(replace(full, reference_dir=ref))
    assert len({base, v_detector, v_activity, v_tag}) == 4


def test_build_is_deterministic(full):
    assert bc.main([], paths=full) == 0
    first = {p.name: p.read_bytes() for p in full.out_dir.iterdir()}
    assert bc.main([], paths=full) == 0
    assert first == {p.name: p.read_bytes() for p in full.out_dir.iterdir()}


def test_dev_mode_missing_step_audio_is_null_and_changes_version(full):
    base = version_of(full)
    (full.assets_dir / "audio" / "ar-eg" / f"{STEP_IDS[0]}.mp3").unlink()
    files, _ = bc.build(full, allow_missing_media=True)
    assert json.loads(files["assessment.json"])["steps"][0]["audio"] is None
    assert json.loads(files["version.json"])["catalogVersion"] != base


def test_clip_present_missing_and_still_missing(full):
    index = full.assets_dir / "character" / "index.json"
    clip = f"character/boy/steps/{STEP_IDS[0]}.webp"
    edit_json(index, lambda d: d["variants"][0]["tasks"][0]["steps"][0].update({"clip": clip}))
    with pytest.raises(bc.CatalogError, match="missing clip"):
        bc.build(full)
    files, _ = bc.build(full, allow_missing_media=True)
    assert json.loads(files["assessment.json"])["steps"][0]["media"]["boy"] is None
    (full.assets_dir / clip).write_bytes(b"x")
    files, _ = bc.build(full)
    assert json.loads(files["assessment.json"])["steps"][0]["media"]["boy"]["clip"] == clip
    (full.assets_dir / f"character/girl/steps/{STEP_IDS[1]}.still.webp").unlink()
    with pytest.raises(bc.CatalogError, match=rf"{re.escape(STEP_IDS[1])}: missing still for variant girl"):
        bc.build(full)


def test_activity_audio_present_sets_paths(full):
    audio = full.assets_dir / "audio" / "ar-eg"
    (audio / f"{ACTIVITY_IDS[0]}.mp3").write_bytes(b"x")
    (audio / f"{ACTIVITY_IDS[0]}.slow.mp3").write_bytes(b"x")
    files, _ = bc.build(full)
    entry = json.loads(files["activities.json"])["activities"][0]
    assert entry["audio"] == {
        "normal": f"audio/ar-eg/{ACTIVITY_IDS[0]}.mp3",
        "slow": f"audio/ar-eg/{ACTIVITY_IDS[0]}.slow.mp3",
    }


@pytest.mark.parametrize(
    "detector",
    [
        {"method": "none", "framing": None, "check": {"type": "question", "text": "q"}},
        {"method": "none", "framing": None, "check": None, "extra": 1},
        {"framing": None, "check": None},
        {"method": "pose", "framing": 3, "check": None},
        {"method": "pose", "framing": "", "check": None},
    ],
)
def test_detector_rejections(full, detector):
    edit_json(full.detectors, lambda d: d.update({"eat.fork.02": detector}))
    with pytest.raises(bc.CatalogError, match="eat.fork.02"):
        bc.build(full)


@pytest.mark.parametrize(
    "counters",
    [
        [{"activityId": "mild.rom.99"}],
        [{"activityId": ["unhashable"]}],
        [{"activityId": "mild.rom.01"}, {"activityId": "mild.rom.01"}],
    ],
)
def test_counter_rejections(full, counters):
    full.activity_counters.write_text(json.dumps(counters), encoding="utf-8")
    with pytest.raises(bc.CatalogError, match="activity_counters"):
        bc.build(full)


@pytest.mark.parametrize(
    ("mutate", "match"),
    [
        (lambda d: d["steps"].update({"eat.nope.01": "x"}), "unknown steps id"),
        (lambda d: d["activities"].update({"mild.rom.99": "x"}), "unknown activities id"),
        (lambda d: d.update({"steps": ["x"]}), "must be an object"),
    ],
)
def test_strings_rejections(full, mutate, match):
    edit_json(full.strings, mutate)
    with pytest.raises(bc.CatalogError, match=match):
        bc.build(full)


@pytest.mark.parametrize(
    ("mutate", "match"),
    [
        (lambda d: d["variants"][0]["tasks"][0]["steps"].append({"id": "eat.nope.01"}), "unknown step id"),
        (lambda d: d["variants"][0]["tasks"][0]["steps"][0].update({"still": 5}), "strings or null"),
        (lambda d: d.update({"variants": "x"}), "malformed"),
    ],
)
def test_index_rejections(full, mutate, match):
    edit_json(full.assets_dir / "character" / "index.json", mutate)
    with pytest.raises(bc.CatalogError, match=match):
        bc.build(full)


# --- Story 2.16: task names ------------------------------------------------------


def test_tasks_emitted_in_order_with_names(full):
    files, _ = bc.build(full)
    tasks = json.loads(files["assessment.json"])["tasks"]
    assert [t["id"] for t in tasks] == TASK_IDS
    assert len(tasks) == bc.EXPECTED_TASKS
    assert all(t["nameAr"] == "مهمة" for t in tasks)
    assert tasks[0]["source"].endswith("#Eating a sandwich")


def test_missing_task_name_strict_fails_dev_warns(full, capsys):
    edit_json(full.strings, lambda d: d["tasks"].pop("eat.fork"))
    with pytest.raises(bc.CatalogError, match="eat.fork: missing Arabic task name"):
        bc.build(full)
    files, warnings = bc.build(full, allow_missing_media=True)
    assert any("eat.fork" in w for w in warnings)
    task = next(t for t in json.loads(files["assessment.json"])["tasks"] if t["id"] == "eat.fork")
    assert task["nameAr"] is None


def test_unknown_task_name_fails(full):
    edit_json(full.strings, lambda d: d["tasks"].update({"eat.cake": "كيك"}))
    with pytest.raises(bc.CatalogError, match="unknown tasks id"):
        bc.build(full)


def test_areas_emitted_with_names_and_missing_name_strict_fails(full):
    files, _ = bc.build(full)
    areas = json.loads(files["assessment.json"])["areas"]
    assert [a["id"] for a in areas] == list(dict.fromkeys(s.split(".")[0] for s in STEP_IDS))
    assert all(a["nameAr"] == "مجال" for a in areas)
    edit_json(full.strings, lambda d: d["areas"].pop("eat"))
    with pytest.raises(bc.CatalogError, match="eat: missing Arabic area name"):
        bc.build(full)
    files, warnings = bc.build(full, allow_missing_media=True)
    assert any("eat" in w for w in warnings)


def test_non_contiguous_task_steps_fail(full, monkeypatch):
    real = bc.parse_steps()
    moved = real[1:] + real[:1]
    monkeypatch.setattr(bc, "parse_steps", lambda _dir=None: moved)
    with pytest.raises(bc.CatalogError, match="not contiguous"):
        bc.build(full)
