---
title: "PRD: Daily Living Skills Assessment and Training App for Children with Autism"
status: final
created: 2026-11-11
updated: 2026-11-11
author: Ahmed Samir
sources:
  - docs/designs/daily-living-skills-app.md (final reviewed design; wins on conflict)
  - _bmad-output/planning-artifacts/briefs/brief-ai-therapist-2026-11-11/brief.md
  - docs/brainstorm/concepts_brief.md
  - docs/reference/ (project-details.md, three assessment scales, correct-performance-guide.md, treatment-plans.md, scoring-rules.md)
---

# PRD: Daily Living Skills Assessment and Training App for Children with Autism

## 0. Document Purpose

This PRD is for the author (Ahmed Samir), the graduation committee, and the downstream BMAD workflows (UX, architecture, epics and stories). It turns the final reviewed design (`docs/designs/daily-living-skills-app.md`) and the product brief into numbered, testable functional requirements. The design wins on any conflict. All clinical content (steps, domain tags, scoring rules, activities) comes only from `docs/reference/`; this PRD adds none. Terms are defined once in §3 and used verbatim. Technology choices that are fixed decisions appear in §5 as constraints; implementation detail lives in `addendum.md` and in the design. Inferred items are tagged `[ASSUMPTION]` and indexed in §11.

## 1. Vision

An Android tablet app that assesses and trains daily living skills (dressing, eating and drinking, personal hygiene) in children with autism spectrum disorder, in Egyptian Arabic. The tablet shows one step of a skill at a time; the child performs it with real objects in front of the camera; the app scores the step itself during the session. Fixed rules turn step levels into a result per functional domain, a plan level and a list of training activities from the project's treatment plans. The therapist reviews and corrects scores afterwards and follows progress over time.

The project's research question is whether a camera plus AI can replace step-by-step therapist observation. The answer is measured, not asserted: the app's verdicts are compared with blind therapist ratings on a labelled volunteer clip set.

The core value, from the source: not only *that* a child cannot put on a jacket, but *at which step* the difficulty appears, *which functional domains* it may be related to, and *which activities* can help.

## 2. Target User

### 2.1 Jobs To Be Done

- **Occupational therapist:** assess a child's daily living skills without watching every step; get a correct domain analysis and a ready activity list; compare results between assessments; keep the final say on every score.
- **Child with autism spectrum disorder:** follow calm, one-step-at-a-time instructions in their own dialect, with pictures and audio, feel encouraged rather than judged, and want to continue.
- **Graduation committee and guardians:** see that child data stays private, that only approved cropped frames leave the tablet, and that every clinical result is traceable to the project's documents.

### 2.2 Non-Users (v1)

- Parents using the app at home (no parent app).
- Clinics needing several tablets or shared records (no sync).
- Anyone seeking a diagnosis of autism or of a difficulty's cause.
- iOS users.

### 2.3 Key User Journeys

UJ-1 to UJ-6 mirror the design's CUJ 1 to CUJ 6.

- **UJ-1. Therapist sets up the tablet (CUJ 1).** The app is newly installed and the proxy is deployed. The therapist opens the app, sets a PIN, enters the proxy address and shared secret once, and runs the framing check. Settings are stored securely, diagnostics show the proxy reachable, and therapist mode opens.
- **UJ-2. Therapist adds a child (CUJ 2).** After entering the PIN, the therapist creates a profile with name, child code and age, and sets the child's effect level. The child appears in the child list, stored encrypted on the tablet.
- **UJ-3. Child is assessed on drinking from a cup (CUJ 3).** The tablet stands on a table facing the child; a cup stands on the printed placement mat. The therapist starts the assessment and hands over to child mode. For each of the 13 steps the tablet shows the illustration and text and plays the audio; the child performs the step and presses Done. Each step gets 3, 2 or 1 by Rule 1, or "not scored" (for example swallowing). The child collects stars after each step and sees a reward screen at the end. The character walks through the whole task once. **Edge case:** Wi-Fi drops mid-task; the affected attempt becomes "cannot tell", never "not done".
- **UJ-4. Therapist reads the report and corrects a level (CUJ 4).** The therapist opens a finished session, looks at the evidence frames of a step scored 2, and changes it to 3. Both levels are kept, averages, plan levels and the activity list are recalculated, and the step is marked "reviewed".
- **UJ-5. Child does a training session (CUJ 5).** The therapist has picked activities from the treatment plan, including a camera-counted one. The child hears the instruction and performs the movement; the app counts repetitions, shows the count, marks the activity complete when its criterion is met, and saves the result. Guided activities run with instructions, a timer and Done.
- **UJ-6. Team measures accuracy (CUJ 6).** Volunteers record the four camera-scored tasks in scripted ways (done on attempt 1, done later, not done); clips are listed in `eval/clips.csv` and imported into replay mode. The app scores every clip; the therapist rates the same clips in blind review with the app's verdict hidden. The dashboard shows per-attempt agreement, weighted kappa, the "cannot tell" rate and a confusion matrix per step.

## 3. Glossary

- **Area** — one of three self-care sections: Dressing and undressing, Eating and drinking, Personal hygiene. An area has one or more tasks.
- **Task** — one skill assessed as a sequence of steps (for example "Drinking from a cup"). 15 tasks in total.
- **Step** — one action within a task, from the assessment scales. 147 steps in total. Each step has a permanent step ID and zero or more domain tags.
- **Domain tag** — a value from a scale's "Functional domain" column. Six tags exist; "Visual-motor coordination" counts as Motor coordination.
- **Functional domain** — one of five: Motor planning, Hand skills, Motor coordination, Range of motion, Midline crossing. Results are reported per functional domain.
- **Camera-scored step** — one of the 41 steps of the four camera-scored tasks with a detection method of body (local), object (cloud) or none.
- **Camera-scored task** — jacket on (5 steps), jacket off (10), drinking from a cup (13), handwashing (13).
- **Content-only task** — any of the other 11 tasks; runs with illustration, text and audio, all steps "not scored".
- **Body step** — a camera-scored step judged on the tablet from pose or hand landmarks.
- **Object step** — a camera-scored step judged by the online model from cropped frames.
- **Attempt** — one try at a step inside the attempt window. Up to three per step.
- **Attempt window** — the period in which the child performs a step; it opens when the instruction audio ends.
- **Prompt level** — 1, 2 or 3; the amount of help the app gives on an attempt (prompt ladder).
- **Verdict** — the result of judging an attempt: done, not done, or cannot tell, with a reason.
- **Level** — the step result: independent (3), partial assistance (2), full assistance (1). Defined by Rule 1.
- **Not scored** — a step without a level; excluded from every average.
- **Automatic level** — the level computed from attempts.
- **Correction** — a level set by the therapist after the session; it wins over the automatic level; both are kept.
- **Evidence frames** — frames saved for each attempt and shown to the therapist.
- **Assessment** — a session in which a child performs one task's steps.
- **Session** — one run of a task: kind assessment or replay; state running, paused, done or abandoned.
- **Domain average** — mean level of scored steps tagged with a functional domain (Rule 2).
- **Plan level** — per functional domain from the domain average: strength, mild, moderate, severe weakness.
- **Functional report** — strengths, needs, domain averages, plan levels and step counts for an assessment.
- **Treatment plan** — for a child, the combined set of activities for all weak functional domains, weakest first.
- **Activity** — one of the 148 training activities in `treatment-plans.md`, with tools, how to perform, goal and measurement criterion.
- **Camera-counted activity** — an activity whose criterion is a count or hold of one body movement, counted by the camera.
- **Guided activity** — any other activity; runs with instructions, a timer and Done.
- **Training session** — a run of therapist-picked activities by the child.
- **Character** — the original boy character who demonstrates steps and reacts on screen.
- **Effect level** — per-child setting: full, calm or off.
- **Stars, sticker, sticker book** — reward items shown to the child; never affect levels.
- **Replay mode** — scoring recorded clips instead of the live camera.
- **Blind review** — therapist rating of attempts with the app's verdict hidden; stored separately from corrections.
- **Proxy** — the server component that holds the model API key and forwards object-step requests; stores nothing.
- **Framing check** — a pre-task screen confirming the needed landmarks are visible.

## 4. Features

### 4.1 Setup and Access Control

**Description:** First-run setup and the boundary between therapist mode and child mode. Realizes UJ-1.

#### FR-1: First-run setup
The therapist can, on first launch, set a PIN, enter the proxy address and shared secret, and reach therapist mode. Realizes UJ-1.
- Therapist mode cannot be opened before a PIN is set.
- Proxy address and shared secret are stored encrypted on the tablet and never shown in clear after entry.
- After setup, diagnostics show whether the proxy is reachable.

#### FR-2: PIN-guarded therapist mode
Therapist mode requires the PIN on every entry from child mode or app start.
- A wrong PIN does not open therapist mode.
- The PIN is stored only as a salted hash.
- The therapist can change the PIN in settings after entering the current one.

#### FR-3: Locked child mode
While child mode runs, the child cannot leave the app.
- Child mode uses Android screen pinning; leaving requires the therapist.
- An adult pause control freezes the attempt window (see FR-15).

#### FR-4: Framing check
Before each camera-scored task, the app shows a camera preview with a green outline when the landmarks the task needs are visible. Realizes UJ-1, UJ-3.
- The outline turns green only when the needed landmarks are visible.
- The therapist can draw the named zones (mat, sink, place) once per setup on the preview; they are stored in settings.
- The front-camera image is un-mirrored before any left/right rule is applied.

### 4.2 Child Profiles

**Description:** Profiles hold the source's Stage 2 data. Realizes UJ-2.

#### FR-5: Create, edit, delete a child profile
The therapist can create, edit and delete a profile with name, child code and age (years). Realizes UJ-2.
- Child code is unique; a duplicate code is rejected with a message.
- Name, code and age are required.
- Deleting a profile deletes all its sessions, attempts, evidence frames, corrections, blind ratings, training results and rewards.

#### FR-6: Effect level per child
The therapist can set each child's effect level to full, calm or off; default is calm.
- The chosen level applies to every reward effect in child mode for that child (see FR-39).

### 4.3 Content Catalogue

**Description:** All clinical content is data built from `docs/reference/`. Realizes UJ-3, UJ-5.

#### FR-7: Catalogue from project documents only
The app's steps, domain tags and activities are exactly those in `docs/reference/`.
- The catalogue contains 147 steps over 15 tasks (eating 42, drinking 38, hygiene 27, dressing 40) and 148 activities (48 mild, 50 moderate, 50 severe).
- Each step has a permanent ID; domain tags are copied as written; "Not tagged in source" gives an empty tag list.
- The catalogue build fails if a tag is not one of the six domain tags, an ID repeats, or a step or activity lacks Arabic text, audio (steps) or illustration. [ASSUMPTION: the design requires audio files for steps; activity audio is not required by the build check.]

#### FR-8: Catalogue versioning
Every catalogue build has a catalogue version derived from its content.
- Changing any step, tag, detector setting or activity changes the catalogue version.

#### FR-9: Full step content
Every step of all 15 tasks can be run in child mode with an illustration of the character, one sentence of Egyptian Arabic text and Egyptian Arabic audio. Realizes UJ-3.
- Each of the 147 steps plays its illustration, text and audio.
- Each step has a second, slower audio recording for prompt level 3.

### 4.4 Child-Mode Assessment

**Description:** The child picks or is handed a task and performs it step by step. The app gives three attempts per step with an increasing prompt ladder, judges each attempt, and never blocks the child. Realizes UJ-3.

#### FR-10: Start an assessment
The therapist can start an assessment for a child on any of the 15 tasks and hand the tablet to child mode. Realizes UJ-3.
- The session records child, task, catalogue version and rules version.
- For the jacket, the therapist can choose putting on, taking off, or both (source Stage 3).

#### FR-11: Step screen
Each step screen shows a large illustration, the animated character, one sentence of text, a large Done area, and Help and Repeat buttons (source Stage 1). The interface is right-to-left Egyptian Arabic in calm colours.
- Done can also be triggered by a paired Bluetooth button.

#### FR-12: Prompt ladder
Attempt 1 uses prompt level 1 (illustration, text, audio); attempt 2 uses level 2 (audio replayed, character points, illustration enlarged); attempt 3 uses level 3 (illustration full screen, character acts the movement where an animation exists, slow audio).
- Help moves to the next prompt level and counts as a prompt.
- Repeat replays the current prompt level and does not count as a prompt.

#### FR-13: Attempt window
The attempt window opens when the instruction audio ends and closes on Done, or when at least 20 s have passed and the child has not moved for 2 s, or at 45 s, whichever comes first.
- Movement means the largest displacement of any tracked wrist or elbow over the last 0.5 s exceeds 0.03 x shoulder width.
- Each closing condition is testable on its own.

#### FR-14: Judging an attempt
At window close, the app judges the attempt: a visibility check first, then the step's body or object judging. Realizes UJ-3.
- Needed landmarks not visible gives "cannot tell" with reason out of frame.
- Done: the step's level is set by Rule 1 and the task moves on.
- Not done: next attempt at the next prompt level, or level 1 after attempt 3.
- Cannot tell: the same attempt repeats once; a second "cannot tell" makes the step "not scored".
- Steps with detection method none are shown and played, the child presses Done, and the step is saved as "not scored".
- A failed step never stops the task.

#### FR-15: Pause, resume and recovery
Every state change is saved immediately. The adult can pause; on resume the current attempt restarts from its beginning.
- If the app is closed mid-task, therapist mode shows a "Continue" card; continuing restarts the saved attempt and discards frames of the interrupted attempt.

#### FR-16: Evidence frames
The app saves evidence frames for every attempt and links them to the attempt.
- Frames are viewable in session detail (FR-26).

#### FR-17: Character walkthrough
At the end of each task the character walks through the whole task once, one illustration per step with its audio (source Stage 4).

### 4.5 Automatic Scoring

**Description:** Scoring of the 41 camera-scored steps of the four camera-scored tasks, with no therapist input during the session. The per-step method and question are fixed in the design's camera-scored steps table (5 none, 6 local, 30 cloud). Realizes UJ-3.

#### FR-18: Body-step judging on the tablet
Body steps are judged on the tablet from pose and hand landmarks using the step's typed check.
- Verdict available within 300 ms of window close.
- No frames leave the tablet for body steps.

#### FR-19: Object-step judging through the proxy
For object steps, the app sends three cropped frames (start, middle, end, cropped around hands and object) and the step's question to the proxy and receives done, not done or cannot tell with a reason.
- No child name or code is sent; each request has a random request ID.
- At most 3 images per request; the proxy rejects more.
- The proxy accepts requests only with the shared secret.
- The proxy stores nothing.

#### FR-20: Doubt is not failure
Timeout (8 s), network error, model refusal, malformed answer or any non-200 proxy response gives "cannot tell" with the reason stored; never "not done". Realizes UJ-3 edge case.
- With Wi-Fi off mid-task, the affected attempt is saved as "cannot tell".
- Known limit (design Principle 3): a detection error that looks like a confident "not done" can still lower a level; SM-1 to SM-3 measure how often.

#### FR-21: Attempt provenance
Every attempt stores attempt number, prompt level, whether Help was pressed, verdict, reason, judging method, model ID and prompt version (object steps), latency, and evidence frame references; every session stores catalogue and rules versions.

#### FR-22: Content-only tasks
The 11 content-only tasks run fully in child mode; all their steps are "not scored". [ASSUMPTION: their sessions still appear in session lists; the report shows "no scored steps" for every domain unless the therapist enters corrections.]

### 4.6 Scoring Rules

**Description:** One pure calculation from stored facts to results; any correction recalculates everything. Implements `scoring-rules.md` exactly.

#### FR-23: Rule 1 (step level)
Step level = 3 if done on attempt 1; 2 if done on attempt 2 or 3; 1 if not done after attempt 3; "not scored" as in FR-14. A correction, if present, wins.

#### FR-24: Rule 2 (domain average and plan level)
For each functional domain, the domain average is the mean level of scored steps tagged with that domain. Plan level: 3 = strength; 2.5 to under 3 = mild; 1.5 to under 2.5 = moderate; under 1.5 = severe.
- "Visual-motor coordination" counts as Motor coordination; a step tagged with both counts once.
- A step with several functional domains counts once in each.
- A domain with no scored steps has no average and shows "no scored steps".
- Boundary values 3, 2.5 and 1.5 map as above (unit-tested).

#### FR-25: Recalculation
Levels, domain averages, plan levels, strengths, needs and the treatment plan are always computed from stored attempts and corrections, never stored as results.
- Changing a correction changes every dependent screen with no other action.

### 4.7 Therapist Review and Dashboard

**Description:** Therapist screens to review sessions, correct levels and follow progress. Realizes UJ-4.

#### FR-26: Session list and detail
For each child, the therapist sees every session; session detail lists each step with its level, attempts, evidence frames and verdict reasons.

#### FR-27: Correct a level
The therapist can set any step's level to 3, 2, 1 or "not scored" after the session. Realizes UJ-4.
- Automatic and corrected levels are both kept and shown.
- The step is marked "reviewed".
- The latest correction for a step wins.

#### FR-28: Comparison of two assessments
The therapist can compare two assessments of the same task for a child, using only steps with a level in both, showing per-domain averages for each and their difference.

#### FR-29: Diagnostics
A diagnostics screen shows camera frame rate, landmark visibility, proxy reachability and latency, and the "cannot tell" rate.

#### FR-30: Training history
The therapist sees each child's training results (activity, count, duration, criterion met).

### 4.8 Functional Report

**Description:** The source's Stage 6–7 output. Realizes UJ-4.

#### FR-31: Report content
After an assessment, the report shows strengths (steps at level 3), needs (steps at 2 or 1), and per functional domain the domain average, plan level and the number of scored steps behind it.
- Wording states a weak step "may be related to" its domains; no diagnosis is made.

### 4.9 Treatment Plan

**Description:** Activities chosen from `treatment-plans.md` by plan level and functional domain. Realizes UJ-4, UJ-5.

#### FR-32: Plan generation
For each weak functional domain, the plan lists every activity of the matching plan level and domain section, weakest domain first.
- A child can hold activities from more than one plan level at once.
- Strength domains contribute no activities.
- Each activity shows its tools, how to perform, goal and measurement criterion as in the source.

#### FR-33: Activity picker
The therapist picks which plan activities the child does in a training session.

### 4.10 Training

**Description:** The child does the picked activities. Realizes UJ-5.

#### FR-34: Camera-counted activities
For camera-counted activities, the app counts repetitions or hold time of one body movement, shows the count, and marks the activity complete when its criterion is met.
- Repetitions shorter than 0.4 s are ignored.
- Only activities whose source criterion is a count or hold of one body movement describable by a single body check are camera-counted; the list is fixed in data. [ASSUMPTION: the list is decided in M4; if few qualify, training is mostly guided.]

#### FR-35: Guided activities
All other activities run with instructions, the activity illustration, a timer and Done.

#### FR-36: Training results saved
Each result (count, duration, criterion met) is saved per child and activity.

### 4.11 Rewards and Effects

**Description:** Calm, game-like motivation that never affects scoring. Realizes UJ-3, UJ-5.

#### FR-37: Step rewards
After each step: 1 star for a step done on any attempt, 1 star for a step finished with Done even if not done, a bonus star for done on attempt 1 (so a step done on attempt 1 earns 2 stars); at full effect level a star flies to the star bar with a particle burst, chime and the character celebrating.
- A step not done after attempt 3 gets an encouraging line and no negative sound or image.

#### FR-38: Task reward and sticker book
After each task, a reward screen shows stars earned, the character's big celebration and progress toward the next sticker; across sessions a sticker book and progress path show finished tasks; a new sticker unlocks every 3 tasks. [ASSUMPTION: the design says both "a new sticker" after each task and "a new sticker every 3 tasks"; this PRD reads the task reward screen as showing sticker-book progress, with a sticker unlocked every 3 tasks. See Open Question 5.]

#### FR-39: Effect levels
Full = particles, sounds, celebration; calm = slow fade and soft sound, no particles; off = star counter only, no sound.
- Effects never flash more than 3 times per second.
- Effects never play while instruction audio is speaking.

#### FR-40: Rewards independent of scoring
Rewards are shown only after the verdict is saved and never change levels. During training, stars follow the same rules per repetition set.

### 4.12 Data, Privacy and Export

**Description:** All data on the tablet, encrypted. Supports the committee and guardian approval.

#### FR-41: Encrypted local storage
All child data, sessions, attempts, corrections and settings are stored encrypted on the tablet with keys in the Android Keystore; evidence frames are stored encrypted in app-private storage.
- The database cannot be opened with a wrong key.
- App backup is disabled.
- No external dashboard or backend stores data.

#### FR-42: Data leaving the tablet
Only cropped frames and the step question for object steps leave the tablet, to the proxy.
- Requests contain no name, child code or profile data.

#### FR-43: Consent text
The app shows consent text that names DeepSeek as the receiving service for cropped frames. [ASSUMPTION: shown in the therapist setup flow and recorded per child profile as acknowledged; the design names the consent text but not where it appears.]

#### FR-44: Anonymised CSV export
The therapist can export `results.csv` with per-attempt rows (session, child code, task, step, attempt, prompt level, verdict, reason, method, model ID, latency, level, corrected level, blind verdict, clip file).
- Names are never exported.

#### FR-45: Data retention
Evidence frames are kept until the child's profile is deleted.

### 4.13 Replay and Evaluation

**Description:** Measures the research question. Realizes UJ-6.

#### FR-46: Replay mode
The therapist can import clips with the file picker into a replay session; each clip is one attempt, the window is the whole clip, and Done is implied at the end.
- The clip file name links it to its row in `eval/clips.csv`.
- Replay can re-score saved clips with a new model or catalogue version.

#### FR-47: Blind review
The therapist can rate attempts with the app's verdict hidden; ratings are stored separately and never change levels.

#### FR-48: Accuracy metrics
The dashboard and evaluation script report per-attempt agreement, quadratic-weighted kappa on levels, the "cannot tell" rate and a confusion matrix per step.
- "Not scored" steps and "cannot tell" attempts are excluded from kappa and reported as the "cannot tell" rate.

## 5. Constraints and Guardrails (Fixed Decisions)

- Graduation project; 12 weeks in five milestones (M1 camera spike, M2 catalogue and runner, M3 camera scoring, M4 rules/report/plan/training, M5 dashboard and evaluation).
- Android tablet only, landscape, minimum Android 11; Kotlin + Jetpack Compose.
- All data on the tablet; no external dashboard or backend for data.
- Egyptian Arabic UI and audio; audio generated once with ElevenLabs and shipped as files.
- Body steps on device with MediaPipe; object steps through a Cloudflare Worker proxy to DeepSeek V4.1 Flash (`deepseek-flash`); proxy holds the API key and a monthly spend limit is set.
- AI scores during the session; the therapist corrects afterwards.
- Sending cropped frames approved by the committee and guardians.
- Original Rive character; game-like rewards with per-child effect level.
- Open-source reuse only where current and better (details in `addendum.md`).
- Clinical content only from `docs/reference/`.

## 6. Cross-Cutting NFRs

- **NFR-1 Latency:** local verdicts within 300 ms of window close; cloud verdicts within 8 s or "cannot tell".
- **NFR-2 Performance:** pose plus hand detection at 15 fps or more on the chosen tablet (fallback: run only the detector the step needs).
- **NFR-3 Security and privacy:** as FR-41, FR-42; API key never on the tablet.
- **NFR-4 Versioning and reproducibility:** every attempt stores model ID and prompt version, and every session stores catalogue and rules versions, so each attempt is traceable to all four; results are reproducible from stored facts.
- **NFR-5 Offline tolerance:** everything except object-step judging works without internet; network loss never lowers a level (confident detection errors still can; see FR-20).
- **NFR-6 Sensory safety:** FR-39 limits apply in every mode.
- **NFR-7 Quality:** at least 90% line coverage for the scoring rules, geometry and repetition counter.
- **NFR-8 Localisation:** right-to-left layout; all child-facing text and audio in Egyptian Arabic.

## 6a. Risks and Mitigations (from the design)

- Tablet too slow for pose plus hand: measured in M1; run only the detector each step needs.
- Online model changes or is withdrawn: model version stored per attempt; replay re-scores clips; open weights allow a self-hosted copy later.
- Frames processed on DeepSeek's servers: committee and guardian approval; consent text names DeepSeek.
- Shared secret leaks: spend limit caps cost; rotate the secret in the proxy and settings.
- Volunteer clips easier than children's movements: report as volunteer accuracy.
- Audio service changes: audio generated once and shipped; voice and model IDs recorded.
- Illustration production: one character sheet; finished art first for the 41 camera-scored steps.

Rejected alternatives (therapist button scoring, single photo per step, adapting one app, cross-platform frameworks, on-device object detector, extra clinical content) are recorded in `addendum.md` and the design.

## 7. Non-Goals

- A standalone hardware device.
- Therapist scoring during the session.
- Scoring physical help by an adult (therapist corrects afterwards).
- Diagnosing autism or the cause of a difficulty.
- Any step, domain tag, rule or activity not in the project's documents.
- Taking off garments other than the jacket.
- Camera scoring for the 11 content-only tasks in v1.
- Moving data between tablets, cloud sync, a parent app.
- iOS.

## 8. MVP Scope

### 8.1 In Scope
FR-1 to FR-48: profiles, all 147 steps as content, camera scoring for 41 steps, Rule 1 and Rule 2, report, plan, training (camera-counted and guided), dashboard with corrections, comparison, diagnostics, blind review, rewards with effect levels, encrypted storage, CSV export, replay.

### 8.2 Out of Scope for MVP (future)
- Camera scoring for the 11 content-only tasks.
- On-device object detector (no uploads).
- Accuracy measured with children (after the volunteer evaluation).
- Self-hosted copy of the open-weight vision model.

## 9. Success Metrics

**Primary**
- **SM-1 Agreement:** app verdicts agree with blind therapist rating in at least 80% of attempts on the volunteer clip set. Validates FR-18, FR-19, FR-48.
- **SM-2 Kappa:** quadratic-weighted kappa on levels at least 0.6. Validates FR-23, FR-48.
- **SM-3 Cannot-tell rate:** at most 15% of attempts. Validates FR-14, FR-20.

**Secondary**
- **SM-4 Content completeness:** all 15 tasks / 147 steps run with illustration, text and audio. Validates FR-9.
- **SM-5 Latency and frame rate:** NFR-1 and NFR-2 met on the chosen tablet.
- **SM-6 Rule correctness:** scoring-rule unit tests pass at all boundaries. Validates FR-23, FR-24.

**Counter-metrics (do not optimize)**
- **SM-C1 Low cannot-tell rate by guessing:** do not lower SM-3 by turning doubt into "not done"; counterbalances SM-3 (FR-20 wins).
- **SM-C2 Stars or engagement as a score:** rewards must not influence levels; counterbalances any engagement measure.
- **SM-C3 Volunteer accuracy as child accuracy:** SM-1 is reported as volunteer accuracy only.

## 10. Open Questions

1. Which tablet model (must reach 15 fps for pose plus hand; measured in M1).
2. Which activities qualify as camera-counted (decided in M4).
3. Egyptian Arabic text review by a therapist, if one is available (author reviews all text).
4. DeepSeek endpoint, model ID and image limits confirmed against official documentation in M1.

## 11. Assumptions Index

- §4.3 FR-7 — activity audio not required by the build check.
- §4.11 FR-38 — sticker unlock every 3 tasks.

- §4.5 FR-22 — content-only task sessions appear in lists; report shows "no scored steps" unless corrected.
- §4.10 FR-34 — camera-counted list fixed in M4; training may be mostly guided.
- §4.12 FR-43 — consent text shown in therapist setup and acknowledged per child profile.
- §2.1 (inferred, not tagged inline) — committee and guardians are the only external stakeholders (inferred from the approval statement).
- §0 (inferred, not tagged inline) — no market sizing or monetization section: graduation project for a committee audience.
