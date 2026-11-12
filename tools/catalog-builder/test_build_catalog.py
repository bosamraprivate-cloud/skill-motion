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
