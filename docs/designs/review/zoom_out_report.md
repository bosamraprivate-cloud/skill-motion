# Goldfish Review Report

**Target:** `docs/designs/daily-living-skills-app.md` (lines 1-482)
**Stage:** 5, Initial Zoom Out Review (Mode: REVIEW)

## Reviewer Status

| Reviewer | Status | Notes |
| --- | --- | --- |
| `egm-zoom-out` | completed | Persona read from `agents/zoom_out.md` (the hyphenated name does not exist). Standard `zoom-out-standard.md` read. Read project-details, scoring-rules and the dressing scale in full; read the eating scale and treatment-plans in part. Did not read the hygiene scale or the correct-performance guide. |
| `egm-inventive-catalyst` | completed | Persona read from `agents/inventive_catalyst.md`. The persona references no standard. Read only scoring-rules and the dressing scale from the sources. |

Severity breakdown: 2 Blocker, 10 Major, 6 Nit.

## Blockers

### B1. The domain set and the plan-section mapping do not match the sources - Blocker
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** lines 20, 155, 202, 330
* **Discrepancy Description:** The scales use six domains, including "Visual-motor coordination" (eating-drinking scale). Line 20 lists five domains and line 155 says six. treatment-plans.md has no visual-motor section, so `selectActivities` has nothing to return for a weak visual-motor domain.
* **Rationale & Recommendation:** Name all six domains. For visual-motor, show the domain result in the report with "no section in treatment-plans.md" and select no activities, or have the author add a mapping rule to scoring-rules.md. Do not invent a mapping. Add a RulesEngine test for this case.

### B2. The builder's parse and validation rules fail on the real documents - Blocker
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** lines 155, 330, 345
* **Discrepancy Description:** In the mild plan, range-of-motion activities sit under a `####` heading with no `###` heading, and the heading formats vary between plans ("### 3. Hand skills" versus "### First: Motor planning"). In the dressing scale, 8 jacket-off steps are "Not tagged in source", so strict domain validation makes every build fail.
* **Rationale & Recommendation:** Map sections by matching heading text to the six domain names, whatever the heading depth or numbering. Treat "Not tagged in source" as an empty tag list. Add tests for the mild range-of-motion activities, the 148-activity total, and the untagged jacket-off steps (1, 2, 5-10). State that untagged steps count toward strengths and needs but toward no domain average.

## Majors

### M1. Cloud failure permanently loses steps - Major
* **Reviewer Lens:** `egm-inventive-catalyst`
* **Affected Scope:** lines 42, 101, 166, 182, 283, 385
* **Discrepancy Description:** A network failure becomes "cannot tell", is repeated once, and the step ends as "not scored". The frames are already saved and replay mode already exists, so this loss is not necessary.
* **Rationale & Recommendation:** Add a `pendingCloud` verdict. Keep the encrypted frames in a durable Room queue and retry with WorkManager. Recompute the report when the verdict arrives and mark it "provisional" until then. A deferred "not done" still cannot recover prompted attempts that never ran.

### M2. Evidence crops are low-resolution - Major
* **Reviewer Lens:** `egm-inventive-catalyst`, `egm-zoom-out`
* **Affected Scope:** lines 182, 390, 405
* **Discrepancy Description:** The camera analyses frames at 640x480, so a hand crop is about 200-300 px. The "at most 1024 px" limit never applies, and the model has to judge small objects such as zippers and soap from low-detail images.
* **Rationale & Recommendation:** Add a second, high-resolution CameraX stream for evidence frames and scale landmark coordinates to it. Store frames as JPEG and state "native resolution, never upscaled". Check in M1 that the tablet supports two streams. If you keep one stream, say the crops come from the analysis stream.

### M3. The custom rule grammar is over-engineered - Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** lines 150, 400
* **Discrepancy Description:** A string mini-language with its own parser is built for a small set of local steps.
* **Rationale & Recommendation:** Make `check` a typed JSON object and deserialise it into a sealed class with kotlinx-serialization.

### M4. The `pose+cloud` method is speculative - Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** line 149
* **Discrepancy Description:** No step is shown to need both verifiers.
* **Rationale & Recommendation:** Drop it and keep `pose | hand | cloud | none` until M3 shows a step that needs it.

### M5. Per-step character animations are an unscoped content burden - Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** lines 169, 173, 430
* **Discrepancy Description:** Prompt level 3 implies up to 147 animations, plus 15 whole-task animations. ARASAAC provides only static pictograms.
* **Rationale & Recommendation:** Define attempt 3 as the step pictogram, enlarged and highlighted, with audio. Define the whole-task character as a slideshow of the task's pictograms.

### M6. ExtraHandDetector undermines the core metric - Major
* **Reviewer Lens:** `egm-zoom-out`, `egm-inventive-catalyst`
* **Affected Scope:** lines 101, 166, 181
* **Discrepancy Description:** The wrist-proximity heuristic is fragile when hands overlap or hold objects. It will cause false "cannot tell" verdicts, and the repeat after "cannot tell" changes nothing about the setup, so the same cause usually happens again.
* **Rationale & Recommendation:** Cut the detector from v1, or make it a review flag that does not change the verdict. Report the "cannot tell" rate next to agreement and kappa. Choose the repeat action from the reason: rerun the framing check for `out_of_frame`, show a "hands away" cue for an extra hand, and send network failures to the M1 queue.

### M7. Therapist UI scope exceeds the goals - Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** lines 213, 220, 432, 435
* **Discrepancy Description:** Tablet-to-tablet import contradicts the Non-Goals (one tablet only). The diagnostics screen and the frame-retention setting are not required by any goal.
* **Rationale & Recommendation:** Cut import. Keep only an anonymised CSV export for `eval/compute_metrics.py`. Fold diagnostics into the framing check plus one proxy-reachability indicator. Keep blind review.

### M8. Model choice defaults to the costly option - Major
* **Reviewer Lens:** `egm-zoom-out`, `egm-inventive-catalyst`
* **Affected Scope:** lines 186-189
* **Discrepancy Description:** The most expensive model is the default for a yes/no check, and Haiku is only a later candidate.
* **Rationale & Recommendation:** Evaluate both models in M3 and default to the cheaper one if it meets Goal 8. State that the model ID is proxy configuration, so switching needs no app change. Optionally set the model per step in `detectors.json` and record `modelId` for each attempt.

### M9. Camera-counted activities may never be selected - Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** lines 39, 59, 202
* **Discrepancy Description:** `selectActivities` takes the first 3 activities per section, but the 12 camera-counted activities are chosen separately. CUJ 4 may therefore never run.
* **Rationale & Recommendation:** Choose the camera-counted set from activities `selectActivities` can return, or define it as "every first-3 range-of-motion or midline activity with a count or hold criterion".

### M10. "Not scored" is undefined in the clinical rules - Major
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** lines 162-166; scoring-rules.md Rule 1
* **Discrepancy Description:** The rules define only levels 3, 2 and 1. Excluding "not scored" steps changes domain averages; for example, the drink and swallow steps are always excluded.
* **Rationale & Recommendation:** Add a one-line note to scoring-rules.md, agreed with the author. Show in the report how many steps went into each domain average.

## Nits

### N1. Empty-domain average is undefined - Nit
* **Reviewer Lens:** `egm-inventive-catalyst`
* **Affected Scope:** lines 199-202, 445
* **Discrepancy Description:** If every step in a domain is "not scored", the average is 0/0.
* **Rationale & Recommendation:** Return `insufficientData` for that domain, leave it out of the plan, and add a test for it in `RulesEngineTest`.

### N2. Wait after each object step - Nit
* **Reviewer Lens:** `egm-inventive-catalyst`
* **Affected Scope:** lines 162-166, 283
* **Discrepancy Description:** The cloud request starts only after the attempt window closes, so the child can wait up to 8 s.
* **Rationale & Recommendation:** Upload the start and middle crops during the attempt, and show a calm character animation while waiting.

### N3. API detail at the wrong altitude - Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** lines 186, 455
* **Discrepancy Description:** API parameter details (fallbacks, beta flag, temperature, `output_config`) are in the Technical Plan, and line 186 repeats line 455.
* **Rationale & Recommendation:** Move these details to the proxy section or the proxy README.

### N4. Two end-of-attempt signals, and pause behaviour is undefined - Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** lines 162, 172, 238
* **Discrepancy Description:** Both the Done button and the timer can end an attempt, and the design does not say what happens to the attempt window on pause and resume.
* **Rationale & Recommendation:** State which signal wins and whether the window restarts on resume.

### N5. Dangling reference - Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** line 290
* **Discrepancy Description:** `concepts_brief.md` is referenced without a path.
* **Rationale & Recommendation:** Add the path or remove the reference.

### N6. Out-of-place `.gitignore` entry - Nit
* **Reviewer Lens:** `egm-zoom-out`
* **Affected Scope:** line 470
* **Discrepancy Description:** The design mentions `elephant-goldfish-model/`, which is unrelated to the project.
* **Rationale & Recommendation:** Remove it from the design.
