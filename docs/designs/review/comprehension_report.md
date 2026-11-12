# Comprehension Report (Stage 6)

Target: `docs/designs/daily-living-skills-app.md` (FeatureRange 1-299, DetailedRange 300-482). Reviewer: egm-comprehension, document only.

## What this proposes

The document proposes an Android tablet app in Egyptian Arabic, built as a university graduation project, for children with autism. In child mode the tablet shows one step of a daily living task at a time and the camera judges whether the child did it. The child gets up to three attempts, and the prompt gets stronger on each one. A step done on attempt 1 scores 3, a later success scores 2, and failing all three scores 1. When the app is unsure, the step is "not scored". Only four tasks are judged by camera (jacket on, jacket off, drinking from a cup, handwashing: 41 steps). The other 106 steps are shown as content only.

Body-movement steps are judged on the tablet using MediaPipe body and hand tracking. Steps involving objects (zipper, cup, soap, tap) send three cropped frames through a Cloudflare Worker proxy to Claude, which answers done, not done or cannot tell. A pure rules engine turns step scores into an average per functional domain, a weakness level per domain (strength, mild, moderate, severe) and three activities per weak domain from the matching treatment plan. A camera rep counter tracks 12 training activities; the rest run as guided activities. A therapist mode behind a PIN shows sessions, evidence frames, corrections, comparisons, a blind review mode and diagnostics. Success means at least 80% agreement with a therapist and a weighted kappa of at least 0.6 on recorded volunteer clips.

## How the existing system works, per this document

There is no existing software. Today an occupational therapist watches each step by hand and scores it as independent, partial assistance or full assistance. The therapist links weak steps to functional domains, averages the scores and picks a plan by weakness level. The source documents in `docs/reference/` (147 steps over 15 tasks, 148 activities, `scoring-rules.md`) describe a standalone touch-screen device scored by the therapist. This project replaces that with an ordinary tablet and AI scoring. The detailed range (lines 300-482) is a file-by-file build plan: a Python catalogue builder, an audio generator and a pictogram fetcher, then a Kotlin app (camera, vision, session, scoring, training, UI, replay), the Worker proxy, evaluation scripts, five milestones over 12 weeks, and a verification plan.

## Findings

1. **Major, lines 20, 155, 330 (domain names).** Line 20 names five domains; line 155 refers to "the six names used in the scales" without listing them. **Fix:** list all six names once, exactly as spelled in the scales, and refer to that list from lines 155 and 330.
2. **Major, lines 14, 20, 164, 169 (assistance vs. scores).** The document does not say it reinterprets "assistance" as prompt level, and physical help (an extra hand) gives "cannot tell", so "done with help" clips in CUJ 5 have no defined score. **Fix:** quote Rule 1, state how each prompt level and physical help map to 3/2/1, and say what score "done with help" clips should get.
3. **Major, lines 149, 163 (`pose+cloud`).** No rule for combining the two verdicts. **Fix:** add the combination rule and a test, or drop the method.
4. **Major, lines 193, 405, 455 (device token).** Who creates the token, how it reaches the tablet and the Worker, and where it is stored are not described; no file implements it. **Fix:** describe the setup flow and add the file.
5. **Major, line 238 (Bluetooth Done button).** No implementation item covers pairing, key mapping or disconnection. **Fix:** add key mapping to items 17 and 21 and pairing to the README and setup sections.
6. **Major, lines 148-153, 330-337 (catalogue file locations).** `detectors.json` and `strings.ar-eg.json` are placed inconsistently as inputs or outputs. **Fix:** one table of catalogue files with path and input/output role; say how Arabic strings reach the app.
7. **Nit, lines 75, 169, 173, 430.** "The character" is not defined, nor how per-step animations are made. **Fix:** define it and its production.
8. **Nit, lines 34, 173, 430.** "Source Stage 1/2/4" not summarised. **Fix:** one-line summary per cited stage.
9. **Nit, line 330.** "The four bullet labels" not named. **Fix:** name them.
10. **Nit, lines 14, 38, 202.** "The selected treatment plan" is singular, but levels are per domain. **Fix:** define the plan as the combined activity set.
11. **Nit, line 162.** The attempt end condition is ambiguous. **Fix:** a precise rule.
12. **Nit, lines 41, 93, 460.** Kappa weighting and handling of "not scored" not stated. **Fix:** state both.
13. **Nit, lines 91, 440.** How replay clips reach the tablet and map to steps is not described. **Fix:** describe import and linking to `clips.csv`.
14. **Nit, lines 290, 470, 482.** "Goldfish review", "EGM" and `concepts_brief.md` unexplained. **Fix:** explain or drop.
15. **Nit, lines 51-52, 59.** No fallback if fewer than 12 activities qualify for camera counting. **Fix:** state the fallback.
