# Volunteer clip recording protocol

This protocol tells every volunteer session how to record the clips for the camera-scored tasks, so that the
clips can be scored in replay mode and compared with the scripted outcome. The shot list is `eval/clips.csv`.

## What gets recorded

- The four camera-scored tasks: `drink.cup`, `dress.jacket.on`, `dress.jacket.off`, `hygiene.handwash`.
- Only the steps whose `method` is not `none` (36 of the 41). Steps marked `none` cannot be judged by the camera
  and get no clip.
- What each step means, and what counts as done, comes only from the step text and the Check column of the
  design's camera-scored step table (`docs/designs/daily-living-skills-app.md`, "Camera-scored steps"). Look the
  step up by its ID. Do not perform anything that is not in that table.
- Each step is recorded in three scripted ways for each volunteer slot (the `scenario` column):
  - `done_attempt_1`: the step is done on the first try. Attempt `1`, outcome `done`.
  - `done_later`: the step is done on a later try. Attempt `2`, outcome `done`. Perform it plainly done; the
    attempt number only sets the prompt level replay uses.
  - `not_done`: the step is not done. Attempt `1`, outcome `not_done`. Stay in frame and do not complete the
    action named in the Check (for example stop before it, or do nothing). Do not leave the frame or block the
    camera: that tests "cannot tell", not "not done".

## Volunteers and consent

- Volunteers are adults acting the steps. No children appear in eval clips unless separate written consent for
  that has been collected.
- Each volunteer signs a consent form before recording. Signed forms are kept in `eval/consent/`, which is
  git-ignored and never committed. Only the volunteer slot (`v01`, `v02`, ...) appears in file names and in
  `eval/clips.csv`; never a name. The link between slot and person exists only on the signed form.

## Physical setup per framing

The framing of each step is in the `framing` column of `eval/clips.csv`.

| Framing | Tasks | Tablet position | Scene |
|---|---|---|---|
| `table` | `drink.cup` | On a table stand about 60 cm from the volunteer, at face height | Volunteer seated on a chair at a table; the cup stands on the printed placement mat |
| `standing` | `dress.jacket.on`, `dress.jacket.off` | On a floor stand about 1.8 m away, at chest height | Volunteer standing; jacket ready (held or on the designated place for `off.10`) |
| `sink` | `hygiene.handwash` | On a shelf beside the sink, about 1 m away | Volunteer standing at the sink; soap and towel ready |

- The whole body part the step needs (hands, shoulders, face, items) stays inside the frame for the whole clip.
- Zones (`mat`, `sink`, `place`) must be visible and in the same place as when the therapist drew them in the
  framing check, so replay judges against the same rectangles.
- Lighting: even, bright room light from the front or side; no strong window or lamp behind the volunteer; no
  flicker. Keep the same lighting for all clips of one session.

## Camera and file settings

- Use the tablet camera that the app uses (front camera), or the rear camera if the tablet's stand allows.
- Front-camera video must be saved un-mirrored. Many camera apps mirror front video by default; switch off
  "mirror front camera" (or "save selfies as previewed") before recording, and note the setting used in the
  session notes. If a batch was recorded mirrored anyway, import it with the "Import mirrored clips" switch on
  the Replay screen so left and right are not swapped; never mix mirrored and un-mirrored clips in one import.
- One clip is one attempt of one step: start a moment before the action and stop a moment after it. Aim for
  3 to 10 seconds; the whole clip is the judging window.
- Format: `.mp4`.
- File name: exactly the `file` column of the row, `<stepId>__<attemptNumber>__<outcome>__<volunteer>.mp4`.

## Storing and checking the clips

- Save clips in `eval/clips/` (git-ignored). Clip videos are never committed; only `eval/clips.csv` is.
- Check the manifest and the folder:
  `tools/.venv/Scripts/python.exe tools/eval/validate_clips.py eval/clips.csv --clips-dir eval/clips`
- The shot list is generated, not hand-edited:
  `tools/.venv/Scripts/python.exe tools/eval/generate_clips.py` (rebuild after a catalogue change).
- Import the clips into replay mode with the file picker.
