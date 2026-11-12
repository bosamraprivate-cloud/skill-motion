# Goldfish Review Report: Stage 8 Intermediate Zoom Out

**Target:** `c:\Ahmed\Projects\Ai therapist\docs\designs\daily-living-skills-app.md` (lines 1-482)

**Earlier reports considered:** zoom_out_report.md, comprehension_report.md, critic_report.md. The document has not been edited since those reports.

## Reviewer Status

| Reviewer | Status |
| --- | --- |
| `egm-zoom-out` | completed |

The reviewer read its persona (`agents/zoom_out.md`; the file `egm-zoom-out.md` does not exist) and the standard `resources/document_standards/zoom-out-standard.md` in full. It read the target, the three earlier reports and scoring-rules.md in full. It read project-details.md and treatment-plans.md only in part, and searched the three scale files rather than reading them row by row. That partial reading limits only the count checks, not the scope findings.

**Totals:** 3 Blocker, 13 Major, 6 Nit (22 findings).

**Verdict:** The main structure sits at the right altitude. The real problems are two mismatches with the source documents and one invented rule. About half of the earlier fixes would add scope this design does not need, and several of them contradict each other.

## Blockers

### 1. The "first 3 activities" rule has no source - Blocker
* **Lines:** 38, 59, 202
* **Problem:** Critic B1 found that the "first 3 activities" rule is invented. Zoom-out M9 and comprehension finding 15 propose fixes that depend on that rule. Neither treatment-plans.md nor project-details.md contains a "first 3" rule.
* **Fix:** Select every activity in the matched plan and domain section. This removes the invented rule and also resolves zoom-out M9 and comprehension finding 15. Change line 59 to "the 12 are chosen from those sections". If there are fewer than 12, count the ones that exist.

### 2. The design's domains do not match the source domains - Blocker
* **Lines:** 20, 155, 330
* **Problem:** The scales tag steps "Visual-motor coordination". project-details.md (line 69, and the zipper example at lines 79-80) treats it as a sub-skill of Motor coordination.
* **Fix:** List the five source domains once. Map the scale tag "Visual-motor coordination" to Motor coordination and cite project-details.md line 69. If the author rejects this mapping, use zoom-out B1's fallback instead: report visual-motor with no plan section. Change the word "six" on line 155 to match the option chosen.

### 3. The catalogue builder breaks on real headings and missing tags - Blocker
* **Lines:** 155, 330, 345
* **Problem:** This is zoom-out B2. treatment-plans.md line 9 has a `####` heading with no `###` above it, and heading numbering differs between plans.
* **Fix:** Match plan sections by heading text at any heading depth. Treat "Not tagged in source" as an empty tag list.

## Major

### 4. Reject the pendingCloud queue and provisional reports (zoom-out M1) - Major
* **Lines:** 42, 166, 182
* **Problem:** The fix adds a durable queue, background retry, a third verdict state and provisional reports. It also conflicts with the fixed constraint that the AI scores during the session. A verdict that arrives after the session cannot rebuild the prompt ladder, because attempts 2 and 3 never ran.
* **Fix:** Reject it. Keep "cannot tell" leading to "not scored", and report the "cannot tell" rate (finding 14). Replay mode (line 440) is already the offline fallback for evaluation.

### 5. Reject the second high-resolution camera stream (zoom-out M2) - Major
* **Lines:** 182, 188, 390, 405
* **Problem:** Not every device supports two camera streams at once, and a second stream needs coordinate remapping. Nobody has shown that 640x480 crops fail.
* **Fix:** Add one sentence: crops come from the analysis stream (about 200-300 px) and are never upscaled; milestone M1 checks one cup step at this resolution; if that fails, raise the analysis resolution before adding a second stream. Delete "1024 px" from lines 182, 188 and 405.

### 6. Keep the risks section short (critic B3) - Major
* **Problem:** A table of 8 or more risks with likelihood, impact and milestone columns would repeat what lines 42, 58, 189 and 283 already handle, and would lower the document's altitude.
* **Fix:** Write a list of 5-6 lines, each giving one risk, one fallback and where the design handles it. Cover: 15 fps not reached, accuracy target missed, the gap between volunteers and children, a leaked secret or runaway spend, text-to-speech quality, and the non-commercial ARASAAC licence.

### 7. Reject the device-token system (critic M5, critic N5, comprehension finding 4) - Major
* **Lines:** 193, 455
* **Problem:** Token issuance through Worker KV, setup codes, per-token rate limits, revocation and a new CUJ 0 amount to an auth system for one tablet.
* **Fix:** Use one shared secret, stored as a Worker secret and in the app's build config (the same way as `PROXY_URL` on line 365). Set a monthly spend limit in the Anthropic console. Revoke by rotating the secret. Add one README line and no new CUJ. List the remaining risk (the secret can be pulled from the APK; the spend cap limits the damage) in the short risks list.

### 8. Cut the cost arithmetic (critic M9, M10) - Major
* **Lines:** 186-189
* **Problem:** Derived costs, monthly totals and dated API sources are too much detail for the Technical Plan.
* **Fix:** Cut to: "The model ID is proxy configuration, pinned per evaluation run and stored on every attempt (line 285). Spend is bounded by the console cap." Keep the API parameters only in item 26 (line 455). Remove the "40 times" multiplier on line 189.

### 9. Reject the minimum of 2 scored steps per domain (critic M2, zoom-out M10) - Major
* **Problem:** A minimum of 2 is a new clinical cut-off, which breaks the "only rules from the documents" constraint. Rule 2 already says to average the scored steps.
* **Fix:** Handle only the case of zero scored steps: report the domain as "insufficient data" with no plan, and add one test. Show the number of scored steps per domain in the report.

### 10. ExtraHandDetector conflicts with scoring "done with help" clips (zoom-out M6, critic M3, comprehension finding 2) - Major
* **Lines:** 91, 101, 181
* **Problem:** The earlier fixes cannot all hold. If the detector stays, help clips always come out "cannot tell". If it goes and help clips stay, helped attempts are scored "done".
* **Fix:** Cut ExtraHandDetector from v1. State that physical help from an adult is outside the session protocol, because Rule 1 defines help only as the app's prompts. Remove "done with help" from the CUJ 5 clip script, and remove "extra hand" from line 101 and from the diagram.

### 11. Drop the pose+cloud method (zoom-out M4 conflicts with comprehension finding 3) - Major
* **Line:** 149
* **Problem:** Comprehension finding 3 asks for a rule and a test for combining two methods, but no step has been shown to need that combination.
* **Fix:** Drop pose+cloud and do not write a combination rule.

### 12. The `check` field means two things (zoom-out M3 versus line 150) - Major
* **Lines:** 150, 400
* **Problem:** Zoom-out M3 proposes a typed JSON check, while line 150 lets `check` hold either a rule or a cloud question.
* **Fix:** Make `check` a tagged object, `{kind: "angle" | "distance" | "above" | "hold" | "question", ...}`, read into a sealed class. This one change removes both the string parser in line 400 and the double meaning of `check`.

### 13. Choose the model by evaluation, not arithmetic - Major
* **Lines:** 186-189
* **Problem:** The model choice rests on cost arithmetic (finding 8) rather than on measured results.
* **Fix:** In milestone M3, evaluate Haiku and Opus on the clip set. Use the cheaper model if it meets Goal 8.

### 14. Goal 8 says nothing about "cannot tell" verdicts - Major
* **Line:** 41
* **Problem:** Goal 8 does not say how "cannot tell" verdicts affect agreement and kappa, or how they are reported.
* **Fix:** Add: "Agreement and kappa are computed on attempts with a done/not-done verdict; the 'cannot tell' rate is reported alongside, with a target set by the author." Do not invent a 15% cap.

### 15. Catalogue input and output paths are unclear (comprehension finding 6) - Major
* **Lines:** 148, 330-337
* **Problem:** The document does not say whether detectors.json and strings.ar-eg.json are build inputs or files the app reads at runtime.
* **Fix:** Add one sentence: they are build inputs in `tools/catalog-builder/`; the builder merges them into assessment.json and activities.json and writes a strings file to assets.

### 16. Some guarantees are overstated (critic M11, M12) - Major
* **Lines:** 42, 99, 101
* **Problem:** The wording claims more than the design guarantees.
* **Fix:** Apply critic M11 and M12's wording ("detected doubt", "no new screens") and nothing more.

## Nits

### 17. Reject uploading crops during the attempt (zoom-out N2) - Nit
* **Problem:** Uploading during the attempt, with a waiting animation, optimises latency before anyone has measured it.
* **Fix:** Reject it and measure latency in M1.

### 18. The Bluetooth button needs one clause, not a design (comprehension finding 5) - Nit
* **Line:** 238
* **Problem:** A pairing and disconnection design over-specifies a simple input.
* **Fix:** Use one clause: "a Bluetooth HID button that sends a key mapped to Done in `StepScreen`; pairing is done in Android settings." The timeout rule on line 162 already covers a failed button.

### 19. Link and Stage-summary polish is optional (critic N4, comprehension finding 8) - Nit
* **Problem:** Relative links everywhere and a summary of every cited Stage would help, but the design holds together without them.
* **Fix:** Optional.

### 20. Frame retention, import and diagnostics (zoom-out M7 versus critic N7) - Nit
* **Problem:** Critic N7 found that frame retention conflicts with comparison. Zoom-out M7 proposes cutting the retention setting and the import.
* **Fix:** Apply M7. Cut the import (it contradicts line 53) and the user-facing retention setting, which also removes critic N7. Replace the encrypted backup with the anonymised CSV export that `eval/compute_metrics.py` needs (line 460). Fold diagnostics into the framing check plus one indicator showing whether the proxy is reachable.

### 21. Prompt level 3 and the "character" are undefined (zoom-out M5, comprehension findings 7 and 13, critic N9) - Nit
* **Problem:** Prompt level 3 calls for an "animation", and the whole-task "character" is never defined.
* **Fix:** Define attempt 3 as the enlarged, highlighted pictogram with audio, which Rule 1 allows ("the step picture or demonstration shown again"). Define the character as a slideshow of the task's pictograms.

### 22. Wording and structure fixes - Nit
* **Problem:** The earlier reports raise several small wording and structure issues that are still open.
* **Fix:** Apply critic M6 (heading levels) and critic M8 (one sentence saying the Rule 2 bands are the rounding). Apply zoom-out N4 (Done wins, and the attempt window restarts on resume). Apply zoom-out N5 and N6 (remove `concepts_brief.md` and the `elephant-goldfish-model` .gitignore line). For each of the six rejected alternatives, replace "Rejected by author" with a one-clause trade-off (critic B2). This is a Nit, not a Blocker, because the reasons come from fixed constraints.

## Smallest set of changes

Apply findings 1, 2 and 3 (Blockers), then 10, 11, 12, 13, 14, 15, 16, 21 and 22. Use the reduced versions of findings 5, 6, 7, 8, 9, 18 and 20. Reject findings 4 and 17. Everything else proposed in the earlier reports should stay out of this design.
