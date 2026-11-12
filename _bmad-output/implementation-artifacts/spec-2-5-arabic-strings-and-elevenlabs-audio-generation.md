---
title: 'Story 2.5: Arabic strings and ElevenLabs audio generation'
type: 'feature'
created: '2026-11-13'
status: 'draft'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-Ai therapist-2026-11-11/EXPERIENCE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Steps and activities exist only as English text parsed from `docs/reference/`; the child needs one Egyptian Arabic sentence per step and activity, spoken in a normal and a slower version.

**Approach:** Write `strings.ar-eg.json` (step and activity sentences translated from the source text with no added clinical content, plus child-facing UI labels from EXPERIENCE.md), and a resumable `generate_audio.py` that calls ElevenLabs text-to-speech for each ID (normal + slow by lower speaking rate), skips unchanged text by hash, and stops cleanly when the account's character quota runs out so a later run (possibly with another account's key) continues.

## Boundaries & Constraints

**Always:** Every step ID (147) and activity ID (148) from `build_catalog.parse_steps()/parse_activities()` gets exactly one sentence: short, simple, second person to the child, Egyptian Arabic, same action as the English source, no new action, object or clinical detail. Voice ID `I3u6waC588j43py1kDN2` (Fatima, Egyptian) and model ID fixed in `config.json`; key only from `ELEVENLABS_API_KEY` env var, never written, printed or logged. Output `android/app/src/main/assets/audio/ar-eg/<id>.mp3` and `<id>.slow.mp3`; `manifest.json` stores per-file text hash, voice, model, settings. Unchanged hash → no API call. Quota/limit error (HTTP 401 quota_exceeded / 429) → save manifest, print how many remain, exit code 3, no partial file left. `--dry-run` prints character totals without calling the API. `--only <id>` for a single test file. `review.csv` lists every file with status `pending` until the author marks `approved` or `regenerate` (regenerate forces a new call).

**Never:** No Arabic copies of documents (strings live only in this JSON). No translation by changing meaning; when the source is ambiguous, keep it literal and flag the ID in a `notes` field. No commit of audio until the author approves a sample.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| First run | no manifest | mp3 + slow mp3 per ID, manifest written | N/A |
| Rerun unchanged | same text | zero API calls | N/A |
| Text changed | one sentence edited | only that ID regenerated | N/A |
| Regenerate flag | review.csv says regenerate | that ID regenerated, status back to pending | N/A |
| Quota hit | API quota error mid-run | stop, manifest saved, exit 3, remaining count printed | no half-written mp3 |
| Missing key | env var unset | exit 2 with a clear message | key never echoed |
| Missing string | an ID without a sentence | exit 1 listing the IDs | N/A |
| Dry run | `--dry-run` | totals of characters for normal + slow | no network |

</frozen-after-approval>

## Code Map

- `tools/catalog-builder/build_catalog.py` -- `parse_steps()`, `parse_activities()` give IDs and English text (`textEn`); reuse, do not change parsing.
- `tools/catalog-builder/step_ids.snapshot.txt`, `activity_ids.snapshot.txt` -- ID lists to check completeness.
- `tools/.venv` -- Python 3.14 venv; add the official `elevenlabs` SDK (latest) to `tools/audio-gen/requirements.txt`.
- `_bmad-output/planning-artifacts/ux-designs/.../EXPERIENCE.md` -- child-facing labels (Done, Help, Repeat, praise lines) to include under a `ui` section.

## Tasks & Acceptance

**Execution:**
- [ ] `tools/catalog-builder/strings.ar-eg.json` -- `{ "steps": {id: text}, "activities": {id: text}, "ui": {...}, "notes": {id: note} }`, complete for all IDs.
- [ ] `tools/audio-gen/config.json` -- voice ID, model ID (`eleven_multilingual_v2`), output format mp3 44.1 kHz 128 kbps, normal and slow `speed` settings (slow ≈ 0.8).
- [ ] `tools/audio-gen/generate_audio.py` -- per the constraints; `--dry-run`, `--only`, exit codes 1/2/3.
- [ ] `tools/audio-gen/requirements.txt`, `review.csv` (generated), `manifest.json` (generated).
- [ ] `tools/audio-gen/test_generate_audio.py` -- mocked SDK: every matrix row.
- [ ] `tools/catalog-builder/test_strings.py` -- every step/activity ID has a non-empty Arabic sentence containing Arabic letters; no extra IDs.

**Acceptance Criteria:**
- Given `--dry-run`, when run, then it prints the character totals (so the author can plan accounts).
- Given `--only drink.cup.01` with the key set, when run, then one normal and one slow file exist for the author to listen to.
- Given `pytest tools/` and `ruff check tools`, when run, then they pass.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

Quota handling exists because the author will use several free ElevenLabs accounts in turn; the manifest makes each run continue where the last stopped. Slow audio uses the voice `speed` setting so the voice speaks slower rather than being time-stretched.

## Verification

**Commands:**
- `python -m pytest tools/ -q` -- expected: pass
- `python -m ruff check tools` -- expected: clean
- `tools/.venv/Scripts/python.exe tools/audio-gen/generate_audio.py --dry-run` -- expected: totals printed
