# Daily Living Skills Assessment and Training App for Children with Autism

* **Author:** Ahmed Samir
* **Date:** 2026-11-11

## Overview

An Android tablet app, built as a university graduation project, for children with autism spectrum disorder. The interface and audio are in Arabic. One tablet runs two modes:

- **Therapist mode:** add children and their data, and open a dashboard with each child's assessment results, functional report, treatment plan and progress.
- **Child mode:** the tablet shows one step of a daily living skill at a time (picture, text, Arabic audio). The child performs it with real objects in front of the camera. After the assessment, the child does training activities taken from a treatment plan.

The app scores each step itself from the camera, with no therapist input during the session. MediaPipe pose and hand landmarks run on the tablet for body movement and for counting repetitions. An online vision model receives captured frames for steps that involve objects (zipper, soap, spoon). The therapist can correct any score later in the dashboard, and agreement between the app and the therapist is the project's accuracy measure.

Each step gets one of the source's three levels: independent (3), partial assistance (2), full assistance (1). For each functional domain, the average step score selects the mild, moderate or severe treatment plan. Both rules are in `docs/reference/scoring-rules.md`.

The project's own documents in `docs/reference/` define the eating, drinking and hygiene scales (107 steps over 8 tasks) and 148 training activities. The dressing scale (`dressing-assessment-scale.md`, 40 steps over 7 tasks) is assembled only from sentences already in `correct-performance-guide.md` and `project-details.md`. In total: 147 steps over 15 tasks.

> Note: the Goldfish review below was run against an earlier draft that also contained a dressing scale written by Claude (128 steps). That draft was removed from the project at the author's request because its steps did not come from the project's documents. Figures in the Analysis section that mention 235 steps, dressing or midline-crossing counts refer to that draft.

## Analysis

Seven independent reviewers read the concept and all reference files without the earlier conversation. They agree the concept is coherent but not ready for a design document. Five problems block it.

### Blocking problems

**1. The scope does not fit a graduation team or a live demonstration.**
Each of the 235 steps needs a picture, Arabic text, Arabic audio, a prompt variant, a rule for "done", and test clips. The 148 activities come on top. A worst-case full assessment is 705 attempts; one reviewer estimates about 3.9 hours at 20 seconds per attempt. All reviewers recommend committing to a small set of tasks that are fully automated, built on one data-driven step runner, with the remaining tasks loaded as content marked "camera scoring not yet available". The most common proposal is the jacket (putting on and taking off, 23 steps), drinking from a cup (13 steps), and 10 to 15 training activities that are pure repetition counts.

**2. Most steps have no rule a machine can check, and many cannot be seen by a camera.**
No scale says how a step is detected as done. At least 39 steps cannot be judged by a tablet camera: 19 "look at" steps (gaze), 13 mouth steps (chew, swallow, suck), 4 "go to the sink or table" steps (the child leaves the frame), and 3 open-ended "repeat until finished" steps. Only about 19 of the 235 steps are pure body movement, so the online model would carry about 90% of the assessment, not a minority as the concept implies. Reviewers recommend adding per-step fields to the catalogue: the detection method, the measurable condition, the camera framing, and a "not AI-scorable" marker. They prefer checking an end state (zipper open, cup on the mat) over recognising an action.

**3. A detection miss is recorded as the child's weakness.**
The score depends on the attempt at which the step is detected. If the detector misses an independent child's first attempt, the score drops from 3 to 2; three misses give a 1. The error only runs towards "more severe". Ten independent steps with three first-attempt misses give 85%, which is "mild weakness" with a treatment plan. Reviewers recommend three verdicts (done, not done with positive evidence, cannot tell), where only the second uses up an attempt. They also note a change of meaning: in the source, score 1 means "completed with full help"; in this concept it means "not completed", and partial physical help has no score.

**4. "Child data stays on the tablet" conflicts with sending frames online.**
The dressing scale includes taking off the T-shirt, shirt and trousers, so frames of a partly undressed child would go to a third party. An examining committee or ethics reviewer is expected to raise this. A hosted model may also refuse such images, which would look like unexplained scoring failures. Options: send only a cropped hand-and-object region with the face masked and a random request ID; never upload undressing frames; perform dressing over the child's own clothes; use an API tier with no retention and no training; obtain guardian consent and university ethics approval; disable Android cloud backup. The alternative that removes uploads is a small object detector trained to run on the tablet for a reduced task set.

**5. "Attempt" and "prompt" are not defined.**
Nothing says what starts or ends an attempt, how long it lasts, which prompt is added on attempt 2 and on attempt 3, or whether the Help and Repeat buttons count as prompts. The source shows the cartoon demonstration as part of the standard instruction, while the scoring rules treat it as the added prompt. Consecutive steps often take under a second in natural movement, while an online verdict takes seconds. Reviewers recommend a per-step state machine, and letting the child end an attempt (a large "done" button, or returning the object to a marked spot).

### Major problems

- **Domain scoring gaps.** A sixth tag, "Visual-motor coordination", appears on 60 steps with no rule mapping it to the five domains. Midline crossing is tagged on 21 steps, all in dressing, so it cannot be banded from eating or hygiene. Cup, bottle and sandwich have only 2 hand-skills steps each, below the 3-step minimum. The band edges leave gaps for values such as 74.5%.
- **Treatment plans do not fit unattended counting.** The severe plan is almost entirely "the therapist helps the child", which collides with "another person's hands means score 1". Many criteria are not repetition counts (bead threading, 5 metres, "without pain"). About a third of activities are estimated to be countable from landmarks. The mild and moderate Motor coordination sections contain the same 10 activities. No rule says which activities are selected.
- **Camera placement.** One tablet must be the child's screen and the camera. Dressing needs the full body from about 2 metres, where text is unreadable and a zipper is a few pixels. Hygiene needs a real sink with water near the tablet.
- **"Another person's hands".** Hand landmarks do not say whose hand it is, and an adult will be nearby for safety. Reviewers recommend treating it as a flag for review, not an automatic 1.
- **The accuracy measure.** An uncorrected score cannot be told apart from an unreviewed one, so agreement would measure how little the therapist reviewed. The therapist also sees the app's score first. Reviewers recommend a blind review mode on a short clip per attempt, a "reviewed" flag, and weighted kappa with a confusion matrix.
- **Online model details.** Provider, model version, frame count, the question asked, API key storage, and behaviour on timeout are undecided. Exam-room Wi-Fi is a single point of failure. Reviewers recommend one call per attempt, a pinned model version, and a replay mode on recorded clips as a demonstration fallback.
- **Data model.** Step numbers restart in each table and activity names repeat, so permanent IDs are needed (for example `dress.jacket.off.04`). Store raw attempts and corrections; compute scores and bands from them. Stamp each result with the catalogue, rules and model versions.
- **Arabic content.** All reference files are now English only. Arabic step text and audio must be produced again as catalogue fields. Pre-recorded audio is preferred over live text-to-speech.

### Inventive contradictions and the Ideal Final Result

| Contradiction | Suggested transform |
|---|---|
| The tablet must be close to be read and far to see the body. | Fixed stations (floor stand, table stand with a placement mat), a framing check before the session, audio and a full-screen picture for distance. |
| Frames must leave the tablet to judge objects, and must not leave it. | Upload only a face-masked hand-and-object crop; score gross dressing movement from the on-device skeleton. |
| An attempt must end early for pace and late for a slow child. | The child ends the attempt; a Help press is itself the prompt event. |
| The detector must be strict and lenient. | Three verdicts, end-state checks, a visibility gate before each attempt. |
| The assessment must be fine-grained and short. | Run the whole task once without interruption, then prompt step by step only where it broke. |
| The therapist must be absent and an independent rater. | Blind review of clips in a separate validation phase. |

The Ideal Final Result: the child performs a task naturally, once; the app records which steps broke without the child stopping at each one; no identifiable image leaves the tablet; and every score carries the evidence a therapist needs to confirm it.

## Goals vs. Non-Goals

* **In-Scope Goals:**
  * Android tablet app with Arabic interface and audio, therapist mode and child mode.
  * Local storage of child records on the tablet.
  * Camera-based automatic scoring of each step during the session, with later therapist correction.
  * Domain results per step tags, functional report (strengths and needs), and plan activities from `treatment-plans.md`.
  * Training activities with automatic repetition counting where the activity allows it.
  * A measured agreement figure between the app and a therapist.
* **Out-of-Scope Non-Goals:**
  * The standalone hardware device described in the source document.
  * Therapist scoring during the session.
  * Diagnosis of autism or of the cause of a difficulty; the report says "may be related to".
  * Camera scoring for tasks other than the cup, taking off the jacket and handwashing in the first version.
  * Any assessment step not written in the project's documents.
  * Camera counting for guided training activities.

## Alternatives Weighed

* **Therapist records each step score with buttons, AI only suggests.** Rejected by author: all scoring must be done by the AI.
* **A single photo per step, taken on a button press, deciding right or wrong and unlocking the next step.** Replaced by author with the attempt-based 3/2/1 rule: a single frame shows a result and not a movement, and the source scale has three levels, not two.
* **Adapting an existing open-source app.** Rejected by author: GitHub and web searches found no project covering this workflow. Small MediaPipe exercise-tracking projects (FlexiPose, Gati-rehab, mediapipe-exercise-rep-counter) and one autism care app (ACE) remain as code references only.
* **Copying the Modified Barthel Index cut-offs for weakness bands.** Rejected by author: those cut-offs were validated for a different instrument and score range.
* **Phone as a second camera.** Proposed by a reviewer and not recommended by the same reviewer: it relaxes the single-tablet constraint.

Open alternatives raised by reviewers, not yet decided: one online vision model judging every assessment step; an object detector running on the tablet so no frame is uploaded; merging small steps into observable checkpoints; scoring finalised from clips a few minutes after the live session.

## Key Decisions & Staging for Next Steps

Decided:

1. Platform: Android tablet. Arabic interface and audio. Graduation project.
2. One app, two modes: therapist and child.
3. The AI scores every step during the session. The therapist may correct scores afterwards in the dashboard.
4. MediaPipe on the tablet for movement and repetition counting; an online vision model for object steps. The author accepted sending camera frames online.
5. Child records are stored on the tablet.
6. **Scoring rules** (`scoring-rules.md`), agreed with the author: done on attempt 1 = independent (3); done on attempt 2 or 3 after the app helped = partial assistance (2); not done after attempt 3 = full assistance (1). Per domain, the average step score gives: 3 = strength; 2.5 to under 3 = mild plan; 1.5 to under 2.5 = moderate plan; under 1.5 = severe plan. These link parts that exist in the documents and add no new clinical content.
7. **Only steps from the project's documents are used.** No step is written by Claude. Dressing steps are the "Motor Planning" sentences of `correct-performance-guide.md` for each garment (5 steps each, putting on only), tagged with every guide section that describes the same action. Taking off the jacket uses the 10 steps of `project-details.md` Stage 4; only steps 3 and 4 have domain tags in the source. The guide has no text for taking off other garments, so they are not assessed.
8. Project documents are kept in English only.
9. Privacy: the examining committee and the guardians approved sending camera frames to the online model, including dressing and undressing. Frames are sent without the child's name or code, through an API tier that does not keep or train on the data.

Resolved after the Goldfish review (the author delegated these choices):

10. **Scope.** Every task and activity in the project's documents is loaded as content. Camera scoring in the first version covers four tasks: putting on the jacket (5 steps), taking off the jacket (10 steps, the source's worked example), drinking from a cup (13 steps), and handwashing (13 steps). Other tasks show "camera scoring not yet available". Taking off the jacket is scored step by step, but only its steps 3 and 4 feed the domain analysis, because only they are tagged in the source.
11. **Attempt rules.**
    * An attempt starts when the instruction audio ends.
    * It ends when the child presses a large "Done" button, or after 20 seconds, extended while the child is still moving.
    * Attempt 1: standard instruction (picture, text, audio). Attempt 2: audio replayed and the key part of the picture highlighted. Attempt 3: a short animation of the cartoon character doing that one step.
    * The Help button jumps to the next prompt level and counts as a prompt. Repeat replays the current level and does not.
    * The full cartoon run of the whole task is shown once at the end of the task, as in the source, and is not a prompt.
12. **Three verdicts per attempt:** done, not done (with visible evidence), cannot tell. "Cannot tell" (child out of frame, model timeout or refusal) does not use up an attempt. Two "cannot tell" results in a row make the step "not scored". The level then follows decision 6.
13. **Steps the camera cannot judge** (gaze, chewing, swallowing, walking to the sink, "repeat until finished") are marked "not camera-scored" in the catalogue. The child still sees and does them; they are recorded as "not scored" and excluded from domain percentages. The source tables are not merged or changed.
14. **Domain mapping.** Each step counts toward the domains written on its row in the scale, exactly as written; "Visual-motor coordination" counts as Motor coordination, as `project-details.md` section "Fourth" lists it under that domain (counted once per step). One assessment is one task. Midline crossing comes from the dressing scale only.
15. **The adult.** One adult stands behind the child, out of the working area, and says nothing during attempts. They have a pause button that never changes a score. A hand not attached to the child's tracked arms makes that attempt "cannot tell" and flags it for review; it is not scored as 1.
16. **Training.** Each activity is tagged "camera-counted" or "guided". The first version counts 12 range-of-motion and midline-crossing activities with MediaPipe joint angles. Guided activities show instructions and a timer, and the child presses Done. For each weak domain, the plan takes the first 3 activities of the matching plan and domain section, weakest domain first. The severe plan is shown as guided activities done with the adult.
17. **Tablet and setup.** A mid-range Android 11+ tablet with a front camera of at least 8 MP, benchmarked before the design is fixed. Dressing: tablet on a floor stand about 1.8 m away at chest height, child standing on a floor mat. Cup: tablet on a table stand, cup on a printed placement mat. A framing check runs before every task. The front camera image is un-mirrored before any left/right rule.
18. **App stack.** Native Android in Kotlin with Jetpack Compose (right-to-left Arabic layout), CameraX, MediaPipe Tasks (Pose and Hand Landmarker), and Room with SQLCipher for encrypted local storage. Android cloud backup is disabled; the therapist can export an encrypted backup file. Reason: MediaPipe's official Android library is native, which avoids depending on small Flutter or React Native wrappers.
19. **Online model.** DeepSeek V4.1 Flash (`deepseek-flash`, multimodal), chosen by the author; the served version is stored with every attempt. One call per attempt, with 3 frames, a per-step question stored in the catalogue, and a fixed JSON answer. The API key stays in a small proxy server, not in the app. If the network fails, the attempt is "cannot tell", and a replay mode can score recorded clips after the session or in the demonstration.
20. **Evaluation.** First, 6 adult volunteers perform each camera-scored step in scripted ways (done, not done, done with help), giving a labelled clip set. Then children, as approved by the committee. The therapist rates clips in a blind review mode with the app's score hidden. Metrics: per-attempt done/not-done agreement and weighted kappa on 3/2/1 scores, with a confusion matrix per step. Targets: at least 80% agreement and kappa of at least 0.6.
21. **Arabic content.** Step text and audio are in Egyptian Arabic, which the children hear at home. Audio is generated once per step with ElevenLabs (Egyptian-accented voice), reviewed, and bundled with the app, not produced live.

Still needs the supervising occupational therapist:

22. The original dressing and undressing scale, if it exists, would replace the assembled one.
23. The duplicated Motor coordination section in the mild and moderate plans is accepted as it is (author, 2026-11-11).

