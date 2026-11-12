# PRD Quality Review — Daily Living Skills Assessment and Training App

Reviewed against `.claude/skills/bmad-prd/assets/prd-validation-checklist.md`, cross-checked with `docs/designs/daily-living-skills-app.md` (wins on conflict) and `docs/reference/scoring-rules.md`.

## Overall verdict
A faithful, well-structured PRD: every design Goal (1-10) and CUJ (1-6) is covered, all numbers checked (147 steps / 15 tasks, 42/38/27/40, 148 activities 48/50/50, 41 camera steps 5/6/30, Rule 1 and Rule 2 thresholds, 20 s / 2 s / 45 s window, 8 s, 300 ms, 15 fps, 80% / 0.6 / 15%, 0.4 s) match the sources, and no invented clinical content was found. The main risks are an honesty gap (the design's admission that confident wrong "not done" verdicts can still lower levels is dropped, and there is no risks/trade-offs section), a few FRs whose done-ness depends on design details the PRD omits (movement definition, un-mirroring, evidence frame count), and an inherited ambiguity in the star/sticker reward rules. Ready for downstream use after medium-sized fixes.

## Decision-readiness — adequate
Fixed decisions are stated as decisions (§5), and Open Questions (§10) are genuinely open. But the PRD carries no Risks section and no trade-offs: the design's Alternatives A-F (e.g. therapist scoring rejected because it would not test the research question; on-device object detector rejected for the 12-week schedule) and its Risks list are not summarised or referenced. A committee member objecting "why not keep the therapist scoring?" finds no answer in the PRD.

### Findings
- **high** Missing acknowledgement that detection errors can lower levels (§4.5 FR-20, §6 NFR-5) — The design (Principle 3) says explicitly: "Detection errors that look like a confident 'not done' can still lower a level; Goal 8 measures how often that happens." The PRD only says network loss never lowers a level, which reads as a stronger guarantee than the design gives. *Fix:* add this sentence to FR-20 or §9 SM-1 and cite it in SM-C1.
- **medium** No risks or rejected alternatives (§5) — Tablet speed, model withdrawal (replay re-scoring, open weights), shared-secret leak, volunteer-vs-child accuracy, illustration production risk are absent. *Fix:* add a short "Risks and Trade-offs" section pointing to the design's Alternatives and Risks.

## Substance over theater — strong
Three user types, each driving FRs (therapist → FR-1..33, child → FR-11..17, 37-40, committee/guardians → FR-41..45). NFRs carry concrete thresholds (300 ms, 8 s, 15 fps, 90% coverage, 3 flashes/s). Vision is specific to this product ("at which step the difficulty appears").

### Findings
- **low** "calm colours" (FR-11) and "calm, game-like motivation" (§4.11) are adjectives without bounds; acceptable given FR-39's concrete limits. *Fix:* reference DESIGN.md/UX output once it exists.

## Strategic coherence — strong
Clear thesis: can camera + AI replace step-by-step observation, measured against blind ratings (§1). SM-1..3 validate the thesis directly; counter-metrics SM-C1..C3 are well chosen. MVP scope (problem-solving / research) matches the milestones.

### Findings
- None beyond the Decision-readiness gap.

## Done-ness clarity — adequate
Most FRs have testable consequences (FR-13, FR-14, FR-19, FR-20, FR-24, FR-27). Gaps:

### Findings
- **medium** "has not moved for 2 s" undefined (§4.4 FR-13) — The design defines movement as largest wrist/elbow displacement over 0.5 s > 0.03 x shoulder width. Without it FR-13's "each closing condition is testable" is not testable. *Fix:* add the definition to FR-13.
- **medium** Un-mirroring and physical setup missing (§4.1 FR-4) — Design requires the front-camera image to be un-mirrored before any left/right rule, and gives distances (60 cm table, 1.8 m jacket, ~1 m sink). These affect scoring correctness. *Fix:* add an FR-4 consequence for un-mirroring; reference setup distances.
- **medium** Reward rules ambiguous (§4.11 FR-37, FR-38; inherited from design) — "1 star for done on any attempt, 1 star for finished with Done even if not done" leaves unclear whether a done step earns 1 or 2 stars; FR-38 says "a new sticker" after each task and also "a new sticker unlocks every 3 tasks". *Fix:* state the exact star count per outcome (e.g. done attempt 1 = 2, done later = 1, not done = 1) and choose one sticker rule; raise as a NOTE FOR PM since the design is also ambiguous.
- **low** FR-16 evidence frames — no count/rate (design: ring buffer at 5 fps, 3 cropped frames for cloud). *Fix:* state what is saved per attempt.
- **low** FR-3 "leaving requires the therapist" — mechanism (PIN to unpin) unstated.

## Scope honesty — strong
Non-Goals (§7) and Out of Scope (§8.2) mirror the design; three inline `[ASSUMPTION]` tags are honest and indexed. Open-item density (4 OQs + 5 assumptions) is fine for a graduation project.

### Findings
- **low** FR-7 adds a check not in the design — "build fails if a step or **activity** lacks Arabic text, **audio** or illustration". The design's builder fails on missing step text/audio and missing step illustration; activity audio is never mentioned (activities get text and illustration). Not clinical content, but a small scope addition. *Fix:* limit to what the design states or tag `[ASSUMPTION]`.

## Downstream usability — strong
Glossary is thorough and used consistently; FR-1..48, UJ-1..6, SM-1..6, NFR-1..8 contiguous; UJ→CUJ mapping explicit.

### Findings
- **low** NFR-4 says every **attempt** stores catalogue and rules versions, while FR-21 (and the design data model) store them per **session**. *Fix:* align NFR-4 with FR-21.

## Shape fit — strong
Chain-top PRD for a small multi-role product; UJs are load-bearing and named. Implementation detail is correctly deferred to the design/addendum.

## Mechanical notes
- Assumptions Index lists §2.1 (stakeholders) and §0 (no market section) but these have no inline `[ASSUMPTION]` tag — roundtrip incomplete.
- UJ-2 adds "sets the child's effect level" which CUJ 2 does not include; harmless (FR-6 supports it) but UJ-1..6 are said to "mirror" the CUJs.
- UJ-3 adds a Wi-Fi-drop edge case while CUJ 3 assumes the tablet has internet; consistent with Goal 10, fine.
- Scoring-rules cross-check: Rule 1, Rule 2 thresholds, multi-domain counting, Visual-motor → Motor coordination with no double count — all exact. `scoring-rules.md` calls the mapping "rounds to the nearest assistance level"; PRD correctly uses the explicit table instead.
- FR-43 consent location is a reasonable `[ASSUMPTION]`; design mentions consent only in Risks.
- `addendum.md` is referenced in §0 and §5; confirm it exists.
