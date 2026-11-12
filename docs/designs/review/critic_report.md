# Goldfish Review Report

**Target:** `c:\Ahmed\Projects\Ai therapist\docs\designs\daily-living-skills-app.md` (lines 1-299, FeatureRange up to `## Detailed Implementation Plan`)

## Reviewer Status

| Reviewer | Status | Persona / standard read |
| --- | --- | --- |
| `egm-design` | completed | design_critic.md and feature-spec-standard.md: both read |
| `egm-structural` | completed | structural.md and feature-spec-standard.md: both read |
| `egm-style` | completed | style.md read; persona default standard used |

Reviewers were told not to re-litigate the author's fixed constraints. Coverage note: none of the reviewers checked the counts (147 steps, 15 tasks, 148 activities, 5/10/13/13 steps per task) against the three scales, correct-performance-guide.md or treatment-plans.md.

**Totals:** 3 Blocker, 13 Major, 9 Nit (25 findings). When reviewers gave the same issue different severities, the highest one is used.

## Prioritized Findings (Blockers -> Major -> Nits)

### B1. The activity-selection rule is not in the project documents - Severity: Blocker
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** Lines 38, 202
* **Problem:** The rule "first 3 activities of the matching plan and domain section, weakest domain first" does not appear in scoring-rules.md, which stops at choosing the plan level. This breaks the constraint that only rules from the project documents may be used.
* **Fix:** Cite the clause in treatment-plans.md or project-details.md that sets this rule. If no clause exists, either select every activity in the matched plan and domain section, or add the rule to scoring-rules.md with the same author sign-off note that the existing rules have.

### B2. Rejected alternatives rest on "Rejected by author" - Severity: Blocker
* **Reviewer Lens:** `egm-design` (Blocker), `egm-structural` (Major), `egm-style` (Nit)
* **Affected Scope:** Lines 248, 254, 260, 266, 272, 278
* **Problem:** The standard requires each rejection to state a concrete technical or scope trade-off, not a person's decision. A, B, E and F give only the decision. C and D give a technical reason but still open with "Rejected by author".
* **Fix:** Remove "Rejected by author" from all six and state the trade-off itself. Examples:
  * A: removes the hands-off scoring the project is meant to show (Problem cost 1).
  * B: a single frame cannot show movement or the 3/2/1 levels of Rule 1.
  * E: there is no labelled data and not enough time; sending frames is already approved.
  * F: content from outside the documents cannot be defended as the clinical basis.

### B3. No Risks & Mitigations section - Severity: Blocker
* **Reviewer Lens:** `egm-structural` (Blocker), `egm-design` (Major)
* **Affected Scope:** Whole document. The risks that are covered are scattered across lines 42, 58, 101, 181, 187, 283 and 284.
* **Problem:** The standard requires this section. Several risks are not mentioned anywhere:
  * the proxy token is stolen and the paid model is abused;
  * the model drifts or is withdrawn;
  * the ARASAAC NC licence;
  * volunteer clips do not match how autistic children move;
  * the model's accuracy on Egyptian household objects;
  * the quality of Egyptian Arabic text-to-speech;
  * the tablet misses the 15 fps target;
  * the 80% / kappa goal is not met.
* **Fix:** Add `## Risks & Mitigations` between Alternatives and Non-Functional Requirements. Use a table with columns Risk | Likelihood/Impact | Mitigation | Fallback/Milestone. Examples: if the tablet misses 15 fps, lower the resolution or run Pose only; if the model is unavailable, use replay mode.

### M1. The domain count does not match - Severity: Major
* **Reviewer Lens:** `egm-design`, `egm-structural`, `egm-style`
* **Affected Scope:** Lines 20 and 155
* **Problem:** Line 20 lists five domains, which matches the domain table in project-details.md. Line 155 says the builder rejects names "outside the six names used in the scales".
* **Fix:** Check the "Functional domain" columns in the three scales. List the exact names once, for example in the Content catalogue section, and make lines 20 and 155 use that list.

### M2. "Not scored" steps can leave a domain result undefined or biased - Severity: Major
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** Lines 166, 199-202, 286
* **Problem:** If every step in a domain is "not scored", the domain average is undefined and `planLevel` has no case for it. Separately, a later "cannot tell" can wipe out an earlier real "not done".
* **Fix:**
  * Define an "insufficient data" result for a domain with a minimum number of scored steps (for example at least 2), and show it in the report.
  * Define the precedence when "cannot tell" follows an earlier "not done".
  * Show the "not scored" rate per domain in the report.

### M3. Adult help conflicts with the extra-hand rule and skews validation - Severity: Major
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** Lines 91, 101, 181
* **Problem:** Any physical help from an adult makes the attempt "cannot tell". The clips recorded "done with help" in CUJ 5 will therefore always come out "cannot tell", which distorts the Goal 8 agreement figures.
* **Fix:** State that adult physical help is outside the assessment protocol. Either drop "done with help" from the clip script, or say how those clips count in the kappa calculation.

### M4. The Goal 8 accuracy target hides abstention - Severity: Major
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** Line 41
* **Problem:** If "cannot tell" is left out of the agreement figure, the app can reach 80% just by abstaining. Results measured only on volunteers may also not carry over to children.
* **Fix:**
  * Set a ceiling on the "cannot tell" rate (for example 15% or less) and report it next to the agreement figure.
  * Say whether "cannot tell" attempts are included in or excluded from the agreement and kappa figures.
  * Give the clip-set size per step and per verdict.
  * Record the volunteer-to-child gap as a risk.

### M5. The proxy device token and free-tier claim are not specified - Severity: Major
* **Reviewer Lens:** `egm-design`, `egm-style`
* **Affected Scope:** Line 193
* **Problem:** The design does not say how the token is issued, checked or revoked. A leaked token gives open access to a paid endpoint. "The free Worker tier covers the volume" is asserted without numbers.
* **Fix:**
  * Issue the token from a one-time setup code, store it in Worker KV and check it on every request.
  * Add a per-token rate limit and a daily spending cap.
  * Describe how a token is revoked.
  * State the expected requests per day against the free-tier limits, with the date those limits were checked.

### M6. Additional Resources and Future Improvements are at the wrong heading level - Severity: Major
* **Reviewer Lens:** `egm-structural` (Major), `egm-design` (Nit)
* **Affected Scope:** Lines 287, 292
* **Problem:** Both are H3 headings, so they sit under Non-Functional Requirements.
* **Fix:** Change both to `##`.

### M7. The architecture section mixes unrelated content - Severity: Major
* **Reviewer Lens:** `egm-structural`
* **Affected Scope:** Lines 95-240
* **Problem:** One H2 holds principles, components, vendor choice, cost (line 188), licensing (line 231) and physical setup. Model pinning (lines 186-189) has no link to the Versioning requirement at line 285.
* **Fix:** Keep components and data flow under the Technical Plan. Move cost and licence notes to Risks (B3) or to a `### Constraints` subsection. Cross-reference line 285 from `### Online model`.

### M8. The rounding in Rule 2 is not stated - Severity: Major
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 37, 201
* **Problem:** scoring-rules.md Rule 2 says the app "rounds it to the nearest assistance level". The design gives the cut-off bands but leaves out the rounding wording, so a reader could apply a second rounding step.
* **Fix:** Add one sentence saying that the Rule 2 bands are the rounding.

### M9. The cost figures are not derived - Severity: Major
* **Reviewer Lens:** `egm-style` (Major), `egm-design` (Nit)
* **Affected Scope:** Lines 188-189
* **Problem:** "About USD 0.01 per attempt", "under USD 0.30" and "about 40 times cheaper" have no prices, no date and no output tokens, and they ignore repeats after "cannot tell".
* **Fix:** Show the arithmetic: input and output price per million tokens as of a date, tokens per image, and the worst case of 13 steps x 3 attempts plus the repeats. Give a monthly total. Source the multiplier or delete it.

### M10. API behaviour claims have no source - Severity: Major
* **Reviewer Lens:** `egm-style`, `egm-design`
* **Affected Scope:** Lines 186-187
* **Problem:** "Current models reject non-default sampling values", `output_config.effort` and `fallbacks: "default"` are stated without any source. "Because the task is a short classification" is a justification that leads to no decision.
* **Fix:** Link the API documentation with a date, or move these claims to M1 for checking. Simplify to "No `temperature` is sent."

### M11. "Never penalised" overstates the guarantee - Severity: Major
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 42, 101
* **Problem:** A local verifier that wrongly returns "not done" still lowers the child's score, so "never" is not true.
* **Fix:** Limit the claim to what the design covers: "Detected doubt (out of frame, network error, refusal, extra hand) becomes 'cannot tell', never 'not done'." Apply the same wording in Goal 9.

### M12. "Camera scoring later needs only catalogue settings" is overstated - Severity: Major
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 99, 294
* **Problem:** Object steps in other tasks may need new `check` kinds or new verifier code.
* **Fix:** Reword to "...needs no new screens; it needs detector settings and, where a step needs a new kind of check, verifier code."

### M13. Comparative and popularity claims are unsupported - Severity: Major
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 231, 247, 266
* **Problem:** "Highest accuracy; no detection errors reach the report", "faster and safer" and "widely used with autistic children" have no evidence.
* **Fix:**
  * Line 247: "accuracy limited only by the therapist's rating; no camera or model errors reach the report."
  * Line 266: "avoids writing or maintaining a MediaPipe bridge."
  * Line 231: "Pictogram images; credit in the About screen." Put the licence reasoning in its own sentence.

### N1. Resolved decisions sit under Open Questions - Severity: Nit
* **Reviewer Lens:** `egm-structural`, `egm-style`
* **Affected Scope:** Line 61
* **Fix:** Move these items to a `### Decisions` subsection, or into the components they affect (strings, pictograms, proxy).

### N2. The Arabic step text has no clinical reviewer - Severity: Nit
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** Line 61
* **Fix:** Name a native-speaker therapist to review the text, or record this as a risk under B3.

### N3. Open Questions appear before their terms are introduced - Severity: Nit
* **Reviewer Lens:** `egm-structural`
* **Affected Scope:** Lines 56-63
* **Fix:** Move the section to just before Additional Resources.

### N4. References are bare code spans or contain process wording - Severity: Nit
* **Reviewer Lens:** `egm-structural`, `egm-style`
* **Affected Scope:** Lines 289-291
* **Fix:**
  * Use relative Markdown links, for example `[scoring-rules.md](../reference/scoring-rules.md)`.
  * Name the three scale files.
  * Give the full path of concepts_brief.md and describe its content instead of writing "brainstorm and Goldfish review".

### N5. No CUJ covers first-run setup - Severity: Nit
* **Reviewer Lens:** `egm-structural`
* **Affected Scope:** Lines 64-93, 193
* **Fix:** Add "CUJ 0: First-run setup" covering the PIN, device-token creation and the framing check.

### N6. Horizontal rules are used inconsistently - Severity: Nit
* **Reviewer Lens:** `egm-structural`
* **Affected Scope:** Lines 7, 298
* **Fix:** Remove both, or use `---` consistently before each top-level part.

### N7. 90-day frame deletion conflicts with the comparison screen - Severity: Nit
* **Reviewer Lens:** `egm-design`
* **Affected Scope:** Line 219 (Goal 7)
* **Fix:** State that comparisons use only stored levels and that evidence frames may be missing for older sessions.

### N8. Some wording is vague or unmeasured - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 16, 25, 26, 102
* **Fix:**
  * Lines 16 and 102: point to the "Reused open-source projects" table instead of the general statement.
  * Line 25: "manual arithmetic over up to 147 steps and several tags per step."
  * Line 26: "cannot be compared step by step without retyping the paper records."

### N9. "Character" is used before it is defined - Severity: Nit
* **Reviewer Lens:** `egm-style`
* **Affected Scope:** Lines 75, 173
* **Fix:** Define it at first use ("the ARASAAC-based character that demonstrates steps") and use one term after that.
