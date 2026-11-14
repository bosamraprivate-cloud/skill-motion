# Step timing draft (Story 6.2, pending owner review)

> Status: **draft, pending review by the project owners.** These seconds are a staging choice made by the development team on 2026-11-14 from the step wording only; they are not taken from a clinical source. They set how long each attempt window stays open (the child's time ring). They add no step, change no step text and never change scoring: a step that runs out of time is never `not_done` (verdict from evidence; no evidence is `cannot_tell`, then the next prompt level with the demonstration).

Machine-readable copy: `tools/catalog-builder/step_timing.json` (per-task defaults plus per-step overrides, whole seconds 5 to 60). The builder writes `timeSec` on every step of `assessment.json`.

## Draft rules

| Steps | Seconds |
|---|---|
| Steps whose English text starts with "Look", "Identifies" or "Locate" | 10 |
| Eating and drinking, default | 15 |
| Eating and drinking: drink, suck (sip), chew, swallow steps | 20 |
| Handwashing, default | 15 |
| Handwashing: rubbing steps | 20 |
| Brushing teeth, default | 15 |
| Brushing teeth: brushing steps ("Brush ...", "Finish brushing ...") | 25 |
| Dressing and undressing, default | 20 |
| Dressing: inserting arms, head or feet ("Inserts ...") and fastening ("Fastens ...", "Begins fastening ...") | 30 |

## Questions for the owners

- Are these durations suitable for the target children, or should some tasks be longer?
- Should "Repeat the steps until you finish ..." (eating) have a longer time than the eating default?
- Should undressing steps that unfasten buttons ("Unfasten the buttons ...") get the fastening time (30 s)? The draft keeps them at 20 s.

The therapist can hide the ring per child ("Show time ring"); the step time still closes the window.
