---
title: "Product Brief: Daily Living Skills Assessment and Training App for Children with Autism"
status: final
created: 2026-11-11
updated: 2026-11-11
author: Ahmed Samir
sources:
  - docs/designs/daily-living-skills-app.md (final reviewed design; wins on conflict)
  - docs/brainstorm/concepts_brief.md
  - docs/reference/ (project-details.md, three assessment scales, correct-performance-guide.md, treatment-plans.md, scoring-rules.md)
---

# Product Brief: Daily Living Skills Assessment and Training App for Children with Autism

## Executive Summary

A university graduation project: an Android tablet app that assesses and trains daily living skills (dressing, eating and drinking, personal hygiene) in children with autism spectrum disorder. The interface and audio are in Egyptian Arabic. The tablet shows one step of a skill at a time, the child performs it with real objects in front of the camera, and the app scores the step itself — no therapist input during the session. Fixed rules turn step levels into a result per functional domain, a plan level, and a list of training activities. The therapist reviews and corrects scores afterwards.

The source concept describes a standalone device scored by a watching therapist. This project changes two things: it runs on an ordinary Android tablet, and the AI scores during the session. Whether a camera plus AI can replace step-by-step observation is the project's research question, and it is measured against blind therapist ratings.

All clinical content — 147 assessment steps over 15 tasks, 148 training activities in three plans (mild, moderate, severe weakness), and the scoring rules — comes only from the project's documents. Nothing clinical is invented.

## The Problem

An occupational therapist assessing daily living skills today watches every step, judges how much help the child needed, links weak steps to functional domains, averages results, and picks activities from a plan. This has three costs:

1. Observing every step of every task takes time and limits how often a child can be re-assessed.
2. Linking 147 steps to their domains and averaging them is clerical work that is easy to get wrong.
3. Progress between assessments is hard to compare when results live on paper.

The core question from the source: not only *that* a child cannot put on a jacket, but *at which step* the difficulty appears, *which functional domains* it may relate to, and *which activities* can help.

## The Solution

One tablet, two modes:

- **Child mode.** Each step appears with an illustration of an original animated character (a boy, modern flat style, calm colours, animated with Rive), one sentence of Egyptian Arabic text, and Egyptian Arabic audio. The child performs the step and presses Done. The child gets three attempts with an increasing prompt ladder; the level follows Rule 1 (done on attempt 1 = 3, on attempt 2 or 3 = 2, not done = 1). A step the camera cannot judge is "not scored" and left out of averages. After an assessment, the child does training activities from the plan.
- **Therapist mode (PIN).** Child profiles (name, code, age), session detail with evidence frames per attempt, a Correct button (automatic and corrected levels both kept; everything recalculates), functional report (strengths, needs, average and plan level per domain, step counts), treatment plan with activity picker, comparison of two assessments, blind review, diagnostics, and anonymised CSV export.

How steps are judged:

- **Body steps:** MediaPipe Pose and Hand on the tablet.
- **Object steps** (zipper, cup, soap, tap): three cropped frames go through a Cloudflare Worker proxy to DeepSeek V4.1 Flash (`deepseek-flash`), which answers done / not done / cannot tell. No child name or code is sent.
- **Doubt is not failure:** out of frame, network errors and model refusals give "cannot tell", never "not done". Poor internet never lowers a level.

**Game-like rewards** keep the child engaged: stars and a short effect and chime after each step, the character celebrating, a reward screen after each task, and a sticker book across sessions. Stars reward taking part, not the level; a failed step gets an encouraging line, never a negative sound. The therapist sets the effect level per child (full, calm, off) because many autistic children are sensitive to sound and motion. Rewards never change scores.

## What Makes This Different

- **Step-level, domain-linked analysis on a tablet**, not a video player: searches across languages found no open-source project covering assessment, domain analysis and plans for daily living skills.
- **Automatic scoring with a measured accuracy claim**, checked against blind therapist ratings rather than asserted.
- **Clinically traceable:** every step, tag, rule and activity comes from the project's documents; facts are stored and results computed by one pure function, so every report is reproducible and versioned (catalogue, rules, model and prompt version stored with each attempt).
- Honest note: the advantage is careful scoping and execution, not proprietary technology. The parts are reused open source (MediaPipe samples, SQLCipher, Vico, Rive runtime, Konfetti, `openai` SDK on Workers) wherever current and better than building.

## Who This Serves

- **Children with autism spectrum disorder** (primary users of child mode): need clear, calm, one-step-at-a-time instruction in their own dialect, with motivation and no punishment for failure.
- **Occupational therapists** (therapist mode): need less observation time, correct domain analysis, a ready activity list, and comparable results over time — with the final say on every score.
- **Graduation committee and guardians** (stakeholders): approved sending cropped frames; need privacy, consent and a defensible clinical basis.

## Success Criteria

- **Accuracy (Goal 8):** on a labelled volunteer clip set, the app's verdicts agree with a blind therapist rating in at least 80% of attempts; quadratic-weighted kappa on levels at least 0.6; "cannot tell" on at most 15% of attempts. Reported as volunteer accuracy; results with children reported separately once available.
- **Content:** all 15 tasks / 147 steps run with illustration, Arabic text and audio.
- **Automatic scoring** for four tasks (41 steps): jacket on (5), jacket off (10), drinking from a cup (13), handwashing (13).
- **Latency:** local verdicts within 300 ms; cloud verdicts within 8 s or "cannot tell".
- **Performance:** MediaPipe Pose plus Hand at 15 fps or more on the chosen tablet.

## Scope

**In (first version):** child profiles; all 147 steps as content; camera scoring for the four tasks above; Rule 1 and Rule 2 scoring; functional report; treatment plan; training (camera-counted repetitions/holds where the criterion allows, otherwise guided with timer and Done); therapist dashboard with corrections, comparison and blind review; rewards with per-child effect levels; replay mode for evaluation.

**Fixed decisions:**
- Android tablet only; Kotlin + Jetpack Compose (Flutter/React Native rejected: MediaPipe official samples are Kotlin, frame-rate risk).
- All data stays on the tablet, encrypted (Room + SQLCipher, Keystore keys, no backup). No external dashboard or backend for data; the proxy stores nothing.
- Egyptian Arabic UI and audio; audio generated once with ElevenLabs (Egyptian-accented voice) and shipped as files.
- AI scores during the session with no therapist input; therapist corrects afterwards.
- Only cropped frames and the step question leave the tablet, as approved by the committee and guardians.
- Only steps, tags, rules and activities from the project's documents.

**Out:** standalone hardware; therapist scoring during the session; scoring physical help by an adult (therapist corrects afterwards); diagnosis of autism or of a difficulty's cause (the report says "may be related to"); any clinical content not in the documents; undressing garments other than the jacket; camera scoring for the other 11 tasks; cloud sync, multi-tablet, parent app; iOS.

## Constraints and Risks

- **Timeline:** 12 weeks in five milestones (M1 camera spike, M2 catalogue and runner, M3 camera scoring, M4 rules/report/plan/training, M5 dashboard and evaluation).
- **Tablet too slow:** measured in M1; fallback runs only the landmarker each step needs.
- **Online model changes:** model version stored per attempt; replay re-scores clips.
- **Frames processed by DeepSeek:** covered by committee and guardian approval; consent text names DeepSeek.
- **Volunteer clips easier than children's movements:** accuracy reported as volunteer accuracy.

## Open Questions

- Which tablet model (must reach 15 fps for Pose plus Hand).
- Which activities qualify as camera-counted (decided in M4).
- Egyptian Arabic text review by a therapist, if one is available.

## Vision

Camera scoring for the remaining 11 tasks; an on-device object detector so object steps need no uploads; accuracy measured with children after the volunteer evaluation; possibly a self-hosted copy of the open-weight vision model.

## Assumptions

- Stakeholders listed as committee and guardians are inferred from the approval statement; no other external stakeholders are named in the sources.
- The brief is right-sized for a graduation-project committee, not an investor audience; no market sizing is included.
