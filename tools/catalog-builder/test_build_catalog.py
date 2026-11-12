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
