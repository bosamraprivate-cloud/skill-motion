---
title: 'Story 6.1: Task posture (seated/standing) and take-off-before-put-on order'
type: 'feature'
created: '2026-11-14'
status: 'done'
route: 'dispatch'
context:
  - '{project-root}/docs/reference/owner-posture-and-order.md'
  - '{project-root}/docs/reference/dressing-assessment-scale.md'
---

## Intent

**Problem:** The project owners specified which skills are tested seated and which standing, and that each garment is tested taking off first, then putting on. The catalogue has no posture per task, the framing check does not tell the therapist how to position the child, and the jacket "both" choice runs put-on before take-off.

**Approach:**
1. **Catalogue posture:** a new `tools/catalog-builder/task_posture.json` maps every task ID to `seated`, `standing` or `standing_or_seated` (trousers), per `docs/reference/owner-posture-and-order.md`; `build_catalog.py` validates it (every task present, known values only, no unknown IDs) and emits `"posture"` on each entry of `assessment.json` `tasks`; schema + tests updated; regenerate the catalogue.
2. **Android catalogue model:** read `posture` (nullable for old catalogues → treat as unknown).
3. **Framing check:** show a one-line positioning hint for the task's posture ("The child sits on a chair at the table" / "The child stands" / "The child stands; sitting is fine if needed"), Arabic + English strings; standing/seated never changes scoring.
4. **Order:** for put-on/take-off pairs, "both" runs **take off first, then put on**; the New session dialog defaults a pair to "both" and lists the choices as take off, put on, both. Assessment task order inside the dressing area: each garment's take-off before its put-on where both exist.
5. Docs: `docs/flows/therapist-flow.md`, `child-flow.md`, EXPERIENCE.md lines about the jacket choice and positioning.

## Boundaries

**Always:** posture is staging only (no scoring change); IDs never hard-coded in Kotlin; strings ar/en; tests (pytest for builder, unit tests for VM/plan order, framing hint mapping).
**Never:** no new assessment steps (take-off for T-shirt, shirt, trousers, socks, shoes waits for the owners' step list). Coordinator runs the Gradle gate.

## Review Triage Log

Scope grew with owner approval: 5 take-off tasks (31 steps) from the team draft pending owner review; 20 tasks / 178 steps. Pairing is generic by ID (.on/.off). Known limit: content-only tasks skip the framing check, so their posture hint is not shown there. Coordinator gate + pytest + ruff passed.

## Verification
- `tools/.venv/Scripts/python.exe -m pytest tools/ -q`; `python tools/catalog-builder/build_catalog.py`; coordinator gradle gate.
