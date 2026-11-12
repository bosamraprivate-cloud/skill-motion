# Daily Living Skills Assessment and Training App for Children with Autism

*   **Author:** Ahmed Samir
*   **Status:** Proposed
*   **Date:** 2026-11-11

## Overview

An Android tablet app, built as a university graduation project, that assesses and trains daily living skills in children with autism spectrum disorder. The interface and audio are in Egyptian Arabic. One tablet runs two modes:

*   **Therapist mode:** create a child's profile, start sessions, and open a dashboard with each child's results, functional report, treatment plan and progress. The therapist can correct any automatic score afterwards.
*   **Child mode:** the tablet shows one step of a skill at a time (illustration, text, audio). The child performs it with real objects in front of the tablet camera, and the app scores the step itself. After an assessment, the child does training activities from the treatment plan.

All clinical content comes from the project's documents in [`docs/reference/`](../reference/): 147 assessment steps over 15 tasks, 148 training activities in three plans (mild, moderate and severe weakness), and the scoring rules in [`scoring-rules.md`](../reference/scoring-rules.md). The app reuses open-source code wherever it fits, including code ported from other languages.

### Terms

*   **Functional domains:** the six tags used in the scales' "Functional domain" column: Motor planning, Hand skills, Motor coordination, Range of motion, Midline crossing, and Visual-motor coordination. [`project-details.md`](../reference/project-details.md) (section "Fourth") lists visual-motor coordination as a sub-skill of Motor coordination, so the app counts a "Visual-motor coordination" tag as Motor coordination. The result is reported for the five domains of `project-details.md`, which are also the five sections of each plan in [`treatment-plans.md`](../reference/treatment-plans.md).
*   **Levels:** each step gets one of the source's three levels: independent (3), partial assistance (2), full assistance (1). Rule 1 of `scoring-rules.md` defines them for the app: done on attempt 1 = 3; done on attempt 2 or 3 after the app helped = 2; not done after attempt 3 = 1. "Assistance" therefore means help given by the app (prompts), not physical help by an adult.
*   **Not scored:** a step the camera cannot judge, or could not judge in this session. It is left out of every average.
*   **Plan level:** per domain, from the average level (Rule 2): 3 = strength; 2.5 to under 3 = mild; 1.5 to under 2.5 = moderate; under 1.5 = severe.
*   **Treatment plan:** for a child, the combined set of activities selected across all weak domains. One child can have activities from more than one plan level at once.
*   **Character:** the boy who demonstrates the steps (source Stage 4). He is an original character designed for this app in a modern, flat illustration style with calm colours. He appears in every step illustration and, as a Rive animation, on the step screen: he talks while the instruction plays, points at the illustration on a prompt, and celebrates when a step is done.
*   **Source stages:** `project-details.md` describes the child's journey in eight stages; this design cites Stage 1 (home screen with Start, Next, Help and Repeat buttons), Stage 2 (child file: name, code, age), Stage 4 (steps shown with picture and audio, then the character performs the task), Stage 5 (three attempts per step, three levels) and Stage 7 (strengths and needs).

## The Problem

An occupational therapist who assesses a child's daily living skills today watches the child perform each step, judges how much help the child needed, works out which functional domains the difficult steps depend on, and chooses activities from a plan. The project's source material describes this chain: break a skill into steps, score each step as independent, partial assistance or full assistance, link the weak steps to functional domains, and pick activities by level of weakness.

Doing this by hand has three costs:

1.  The therapist must observe every step of every task, which takes time and limits how often a child can be re-assessed.
2.  Linking each step to its domains and averaging the results is clerical work that is easy to get wrong across 147 steps.
3.  Progress between assessments is hard to compare when results live on paper.

The proposed solution: the tablet presents each step, the camera and an AI model judge whether the child did it and on which attempt, fixed rules turn the levels into a result per domain and a plan level, and the dashboard keeps every result so progress can be compared.

The source describes a standalone device with a large touch screen and therapist scoring. This project changes both: it uses an ordinary Android tablet, and the AI scores during the session with no therapist input.

## Goals

1.  **Child profiles:** the therapist can create, edit and delete a child's profile with name, child code and age (Stage 2). Data is stored encrypted on the tablet only.
2.  **Full content:** every one of the 15 tasks and 147 steps can be run in child mode, each step with an illustration of the character, Egyptian Arabic text and Egyptian Arabic audio.
3.  **Automatic scoring for four tasks:** putting on the jacket (5 steps), taking off the jacket (10 steps), drinking from a cup (13 steps) and handwashing (13 steps) are scored by the app with no therapist input during the session.
4.  **Functional report:** after an assessment, the app shows strengths, needs, the average level and plan level per domain, and how many steps each average is based on.
5.  **Treatment plan:** for each weak domain, the app lists every activity of the matching plan and domain section in `treatment-plans.md`, weakest domain first. The therapist picks which ones the child does in a training session.
6.  **Training:** activities whose measurement criterion is a count or a hold of a body movement are counted by the camera (repetitions or holds). All others run as guided activities with instructions, a timer and a Done button.
7.  **Dashboard:** for each child, the therapist sees every session, every step level with its evidence frames, the report, the plan, and a comparison between two assessments of the same task.
8.  **Measured accuracy:** on a labelled clip set recorded by volunteers, the app's verdicts agree with a therapist rating blind in at least 80% of attempts; the quadratic-weighted kappa on levels is at least 0.6; and the app answers "cannot tell" on at most 15% of attempts.
9.  **Motivating, game-like feedback:** after every step the child gets a reward (stars, a short effect and sound, the character celebrating); after every task a reward screen; across sessions a sticker collection. The therapist sets the effect level per child (full, calm, off).
10. **Poor internet does not change levels:** everything except object-step judging runs on the tablet. A network failure makes an attempt "cannot tell"; it is never counted as "not done".

## Non-Goals

*   A standalone hardware device, as described in the source.
*   Therapist scoring during the session.
*   Scoring physical help by an adult. If an adult helps, the therapist corrects the level afterwards.
*   Diagnosing autism, or the cause of a difficulty. The report says a weak step "may be related to" its domains, as the source does.
*   Any assessment step, domain tag, rule or activity that is not in the project's documents.
*   Taking off garments other than the jacket (the documents contain no steps for it).
*   Camera scoring for the other 11 tasks in the first version; they run as content only.
*   Moving data between tablets, cloud sync, or a parent app.
*   iOS.

## Critical User Journeys (CUJs)

### CUJ 1: First setup

*   **Given**: the app is newly installed and the proxy is deployed.
*   **When**: the therapist opens the app, sets a PIN, enters the proxy address and the shared secret once, and runs the framing check.
*   **Then**: the settings are stored in the Android Keystore, the diagnostics screen shows the proxy reachable, and therapist mode opens.

### CUJ 2: Therapist adds a child

*   **Given**: setup is done.
*   **When**: the therapist enters the PIN and creates a profile with name, child code and age.
*   **Then**: the child appears in the child list, stored encrypted on the tablet.

### CUJ 3: Child is assessed on drinking from a cup

*   **Given**: a child profile exists; the tablet is on a table stand facing the child; a cup stands on the printed placement mat; the tablet has internet.
*   **When**: the therapist starts an assessment for "Drinking from a cup" and hands over to child mode. For each of the 13 steps, the tablet shows the illustration and text and plays the audio; the child performs the step and presses Done.
*   **Then**: each step gets 3, 2 or 1 by Rule 1, or "not scored" for steps the camera cannot judge (for example swallowing). Evidence frames are saved for each attempt. At the end the character walks through the whole task, one illustration per step with its audio,, and the session is saved.

### CUJ 4: Therapist reads the report and corrects a level

*   **Given**: a finished assessment exists for the child.
*   **When**: the therapist opens the session, looks at the frames of a step scored 2, and changes it to 3.
*   **Then**: the correction is saved next to the automatic level (both kept), averages, plan levels and the activity list are recalculated, and the step is marked "reviewed".

### CUJ 5: Child does a training session

*   **Given**: the child has a treatment plan, and the therapist has picked a camera-counted activity (for example raising the arms 10 times).
*   **When**: the child starts training, hears the instruction, and performs the movement.
*   **Then**: the app counts repetitions, shows the count, marks the activity complete when its criterion from `treatment-plans.md` is met, and saves the result.

### CUJ 6: Measuring accuracy

*   **Given**: volunteers have recorded the four camera-scored tasks in scripted ways (step done on attempt 1, done on a later attempt, not done), each clip is listed in `eval/clips.csv`, and the clips are imported into replay mode with the file picker.
*   **When**: the app scores every clip, and the therapist rates the same clips in blind review mode with the app's verdict hidden.
*   **Then**: the dashboard shows per-attempt agreement, weighted kappa, the "cannot tell" rate and a confusion matrix per step.

## Open Questions

*   **Tablet model:** which tablet the team will buy or borrow. MediaPipe Pose plus Hand must reach 15 frames per second on it (measured in milestone M1).
*   **Which activities are camera-counted:** decided in M4 by checking each activity's criterion. If very few qualify, training is mostly guided and Goal 6 is still met.
*   **Arabic text review:** the author reviews the Egyptian Arabic text for every step; a therapist should review it too if one is available.

## Technical Plan (Architecture)

### Principles

1.  **Content is data.** Every task, step, domain tag and activity lives in JSON files generated from `docs/reference/`. The app code never hard-codes a step. Making another task camera-scored means adding detector settings for its steps, plus a new check type only when a step needs one.
2.  **Store facts, compute results.** The database stores what happened (attempts, verdicts, frames, corrections). Levels, averages, plan levels and activity lists are computed by one pure function from those facts, so a correction recalculates everything.
3.  **Doubt is not failure.** Out of frame, network errors and model refusals give "cannot tell", which never counts as "not done". Detection errors that look like a confident "not done" can still lower a level; Goal 8 measures how often that happens.
4.  **Reuse before writing.** Each component names the open-source project it starts from.

### Components and data flow

```text
                         docs/reference/*.md  (project documents)
                                   |
                     tools/catalog-builder (Python, build time)
                                   |
                                   v
 +-----------------------------------------------------------------------+
 | Android app (Kotlin, Jetpack Compose, right-to-left Arabic)           |
 |                                                                       |
 |  assets/catalog/*.json  assets/audio/ar-eg/*.mp3  assets/illustr/      |
 |            |                                                          |
 |            v                                                          |
 |   CatalogRepository ---------------------------------+                |
 |            |                                         |                |
 |  Child mode                                Therapist mode (PIN)       |
 |   AssessmentRunner (state machine)          Children, Dashboard,      |
 |     |  PromptLadder, AudioPlayer             Session detail, Review,  |
 |     v                                        Blind review, Export     |
 |   CameraX --> FrameBuffer --+--> VisibilityGate (MediaPipe Pose+Hand) |
 |                             |--> PoseVerifier / HandVerifier (local)  |
 |                             +--> CloudVerifier --HTTPS--+             |
 |     |                                                    |            |
 |     v                                                    |            |
 |   Room database (encrypted with SQLCipher)               |            |
 |     children, sessions, attempts, frames, corrections    |            |
 |     |                                                    |            |
 |     v                                                    |            |
 |   RulesEngine (pure): levels -> domain averages -> plan  |            |
 |   TrainingRunner + RepCounter (MediaPipe Pose, local)    |            |
 +----------------------------------------------------------|------------+
                                                            v
                      proxy/ Cloudflare Worker (holds the API key)
                                                            |
                                                            v
                                 DeepSeek API (deepseek-flash, multimodal)
```

### Content catalogue

| File | Role | Path |
|---|---|---|
| Assessment scales, `treatment-plans.md` | Input (project documents) | `docs/reference/` |
| `detectors.json` | Input, hand-written: how each camera-scored step is judged | `tools/catalog-builder/` |
| `strings.ar-eg.json` | Input, translated and reviewed: Arabic text per step and activity | `tools/catalog-builder/` |
| `assessment.json` | Output: areas, tasks, steps with tags, detector settings and Arabic text merged in | `android/app/src/main/assets/catalog/` |
| `activities.json` | Output: activities with plan level, domain, fields and Arabic text | `android/app/src/main/assets/catalog/` |

*   Each step gets a permanent ID (for example `drink.cup.07`, `dress.jacket.off.04`). Its domain tags are copied exactly as written; "Not tagged in source" becomes an empty tag list.
*   `detectors.json` gives each camera-scored step a `method` (`pose`, `hand`, `cloud`, or `none`), a `framing` (`table`, `standing`, `sink`), and a `check` object whose shape depends on the method: for local methods a typed rule such as `{"type": "angleAbove", "joint": "rightShoulder", "deg": 150, "holdSec": 0.5}`; for `cloud`, `{"type": "question", "text": "..."}`.
*   Each activity keeps its four source fields (tools, how to perform, goal, measurement criterion), plus `mode` (`camera-counted` or `guided`) and, for camera-counted ones, a typed criterion such as `{"kind": "reps", "target": 10, "perSide": true}`.
*   The builder matches plan level and domain section by heading text at any heading depth, because heading levels in `treatment-plans.md` are not uniform. It fails if a tag is not one of the six domain names, if an ID repeats, or if a step has no Arabic text or audio file.

### Session runner (state machine per step)

```text
 SHOW(prompt level) -> LISTEN (instruction audio plays)
   -> ATTEMPT: the window opens when the audio ends. It closes when the child presses Done,
               or when at least 20 s have passed and the child has not moved for the last 2 s,
               or at 45 s, whichever comes first.
   -> JUDGE:   VisibilityGate, then the step's local verifier or CloudVerifier
        done        -> save attempt; level 3 on attempt 1, else 2; next step
        not done    -> attempt < 3 ? SHOW(next prompt level) : level 1; next step
        cannot tell -> repeat the same attempt once; a second time -> "not scored"; next step
```

*   Prompt ladder: attempt 1 = illustration, text and audio; attempt 2 = audio replayed while the character points at the illustration, which is enlarged; attempt 3 = the illustration full screen, the character acting the movement where an animation exists, and a slower recording of the instruction. Help moves to the next prompt level and counts as a prompt; Repeat replays the current level and does not.
*   Steps with `method: none` are shown and played, the child presses Done, and the step is saved as "not scored".
*   A failed step does not stop the task; the next step is shown, as in the source example where later steps are still scored.
*   Every state change is saved at once. The adult's pause button freezes the window; on resume the current attempt starts again from its beginning.
*   At distance (jacket, sink) the child can press Done with a Bluetooth button paired in Android settings; the app treats its key press as the on-screen Done.
*   At the end of the task the character walks through the whole task, one illustration per step with its audio, once (Stage 4).

### Verifiers

All verifiers implement one interface: `verify(step, frames, landmarks) -> Verdict(done | notDone | cannotTell, reason)`.

*   **VisibilityGate (local):** checks that the landmarks the step needs are visible (MediaPipe visibility above 0.5) in most frames. If not, the verdict is "cannot tell" with reason `out_of_frame`.
*   **PoseVerifier and HandVerifier (local):** evaluate the step's typed `check` on MediaPipe Pose (33 landmarks) and Hand (21 landmarks per hand) output, using joint angles, distances and hold time. They also run the training counts.
*   **CloudVerifier:** for object steps (zipper, cup, soap, tap). It takes 3 frames from the attempt (start, middle, end) from the analysis stream, crops them around the child's hands and the object using the landmarks, and sends them with the step's question to the proxy. It asks for a fixed JSON answer `{verdict: "done" | "not_done" | "cannot_tell", reason}`. No child name or code is sent; each request has a random request ID. On a timeout (8 s), network error, refusal or malformed answer, the verdict is "cannot tell" and the reason is stored.

### Online model and proxy

*   `proxy/` is a Cloudflare Worker in TypeScript. DeepSeek's API follows the OpenAI chat-completions format, so the Worker calls it with the `openai` npm SDK pointed at DeepSeek's base URL. It keeps the DeepSeek API key as a Worker secret, accepts `POST /verify` only with the shared secret entered at setup (CUJ 1), rejects payloads with more than 3 images, builds one chat request from a fixed prompt template with the three images and JSON output mode for the verdict, validates the returned JSON against the verdict schema, and returns the verdict and the model ID. It stores nothing. A monthly spending limit is set on the DeepSeek account.
*   Model: DeepSeek V4.1 Flash through the model ID `deepseek-flash`, chosen by the author. It is DeepSeek's multimodal model; DeepSeek's text-only models reject images. The exact snapshot served under `deepseek-flash` is recorded from each response and stored with every attempt; if DeepSeek changes the snapshot during the evaluation, the clip set is re-scored. Endpoint, model ID and image limits are checked against DeepSeek's official API documentation in M1.

### Rules engine

`RulesEngine` is pure Kotlin with no Android dependency, so it runs in JVM unit tests:

1.  `stepLevel(attempts, correction)`: 3, 2, 1 or not scored (Rule 1). A therapist correction, if present, wins.
2.  `domainAverages(stepLevels, tags)`: average of scored steps per domain, after mapping "Visual-motor coordination" to Motor coordination; each domain counts a step at most once. A domain with no scored steps has no average and shows "no scored steps".
3.  `planLevel(average)`: Rule 2.
4.  `activitiesFor(planLevels)`: every activity of the matching plan and domain section, weakest domain first.
5.  `strengthsAndNeeds`: steps at level 3 are strengths; steps at 2 or 1 are needs (Stage 7).

### Training runner

*   **Camera-counted activities:** `RepCounter` follows one joint angle from MediaPipe Pose, smooths it, and counts a repetition when the value passes the activity's high threshold and then its low threshold (hysteresis), ignoring repetitions shorter than 0.4 s. Holds use a timer that runs while the condition is true.
*   **Guided activities:** instructions, the activity's illustration, a timer and a Done button.
*   Each result (count, duration, criterion met or not) is saved for the dashboard.

### Rewards and effects

The child mode looks and feels like a calm game, so children want to continue:

*   **After each step:** when the step is done, a star flies into the star bar at the top, a short particle burst plays around the illustration, a chime sounds, and the character celebrates. When the step is not done after attempt 3, there is no negative sound or image: the character says an encouraging line ("we'll try it together next time") and the task moves on. Stars reward taking part and finishing, not the level, so a child who needs help still collects them: 1 star for a step done on any attempt, 1 star for a step finished with Done even if not done, and a bonus star for a step done on attempt 1.
*   **After each task:** a reward screen with the stars earned, the character's big celebration animation, and progress toward the next sticker (a new sticker unlocks every 3 finished tasks).
*   **Across sessions:** the sticker book and a simple progress path show how many tasks the child has finished; a new sticker unlocks every 3 tasks.
*   **Effect levels:** many autistic children are sensitive to sound and motion, so each child profile has an effect level set by the therapist: **full** (particles, sounds, celebration), **calm** (slow fade and soft sound, no particles), **off** (star counter only, no sound). Effects never flash more than 3 times per second and never play while the instruction audio is speaking.
*   **Scoring is not affected:** rewards are shown after the verdict is saved and do not change levels. During training, stars follow the same rules per repetition set.

Effects are built with Rive (character celebrations) and Konfetti (particle bursts in Compose); sound effects are generated once with ElevenLabs sound effects and reviewed.

### Therapist mode and dashboard

*   A PIN guards therapist mode; child mode uses Android screen pinning so the child cannot leave the app.
*   Screens: child list; child profile; session list; session detail (each step with its level, attempt frames, verdict reason, and a Correct button); functional report; plan (with activity picker); training history; comparison of two assessments of the same task (only steps scored in both); blind review; diagnostics (camera frame rate, landmark visibility, proxy reachability and latency, "cannot tell" rate); settings (PIN, proxy, CSV export of results without names).
*   Charts use Vico.

### Data and privacy

*   Room database encrypted with SQLCipher; the key is held in the Android Keystore.
*   Frames are stored as encrypted JPEG files in app-private storage and referenced from attempts. They are kept until the child's profile is deleted.
*   `android:allowBackup="false"`.
*   Only cropped frames and the step question leave the tablet, as approved by the committee and the guardians.

### Reused open-source projects

| Need | Project (stack) | License | What is reused |
|---|---|---|---|
| Camera and MediaPipe on Android | `google-ai-edge/mediapipe-samples` (Kotlin) | Apache-2.0 | CameraX setup, landmarker helper classes, model files, overlay drawing |
| Encrypted database | `sqlcipher/sqlcipher-android` | BSD-style | Encrypted SQLite driver for Room |
| Dashboard charts | `patrykandpatrick/vico` | Apache-2.0 | Charts in Compose |
| Animated character | Rive (`rive-app/rive-android` runtime) | MIT | Plays the character's animations and state machine in Compose |
| Reward particle effects | `DanielMartinus/Konfetti` (Compose) | ISC | Star and confetti bursts after steps and tasks |
| Egyptian Arabic audio | ElevenLabs text-to-speech (newest model available at generation time, an Egyptian-accented voice from its voice library) | Commercial service; a paid plan is used so the generated audio may be shipped in the app | Generates audio files once at build time; each file is reviewed. Not open source: chosen by the author for audio quality |
| Proxy | `openai` npm SDK (OpenAI-compatible client) on Cloudflare Workers | Apache-2.0 | Chat-completions client pointed at the DeepSeek API |

Open-source code is used only where it is current and better than writing the part ourselves. The repetition counter, the child-mode interface, the step-by-step flow, the therapist dashboard and the handwashing check are written for this project: the projects found for them were small, old or a poor fit. If code is later copied from a repository whose owner gave permission, the written permission goes in `docs/permissions/` and the copied files keep the original license notice and credit.

### Physical setup

*   Table task (cup): tablet on a table stand about 60 cm from the child at face height, front camera; the cup on a printed placement mat.
*   Jacket: tablet on a floor stand about 1.8 m away at chest height. Handwashing: tablet on a shelf beside the sink, about 1 m away.
*   A framing check runs before each task and shows a green outline when the needed landmarks are visible.
*   The front-camera image is un-mirrored before any left or right rule.

## Alternatives Considered

### Alternative A: Therapist scores each step with buttons, the AI only suggests

*   **Pros**: no detection errors reach the report.
*   **Cons**: needs a therapist watching every step, which is the cost the project exists to remove.
*   **Reason for Rejection**: Rejected by author: it does not test whether the camera can replace observation, which is the project's research question.

### Alternative B: One photo per step on a button press, right or wrong, unlocking the next step

*   **Pros**: simplest to build.
*   **Cons**: one frame shows a result, not a movement; it gives two outcomes where the source scale has three levels; a wrong "wrong" blocks the child.
*   **Reason for Rejection**: Rejected by author: the three-attempt rule gives the source's three levels and never blocks the child.

### Alternative C: Adapt one existing open-source app end to end

*   **Pros**: less code to write.
*   **Cons**: searches across all languages found no project that covers assessment, domain analysis and plans for daily living skills.
*   **Reason for Rejection**: Rejected by author: no such project exists; parts of several projects are reused instead.

### Alternative D: Flutter or React Native instead of native Kotlin

*   **Pros**: one code base for Android and iOS.
*   **Cons**: MediaPipe on those frameworks depends on small community wrappers, while Google's own samples are Kotlin; iOS is a non-goal.
*   **Reason for Rejection**: Rejected by author (confirmed 2026-11-11): the camera pipeline is the riskiest part, Google's official MediaPipe samples are Kotlin, and passing every camera frame through a Flutter bridge lowers the frame rate on a mid-range tablet.

### Alternative E: Score object steps on the tablet with a custom object detector

*   **Pros**: no frames leave the tablet; works offline.
*   **Cons**: needs a labelled image set and training for zippers, cups, soap and taps, which does not fit the project's 12 weeks.
*   **Reason for Rejection**: Rejected by author: an online vision model needs no training data, and sending cropped frames was approved.

### Alternative F: Add clinical content not in the project's documents

*   **Pros**: fuller dressing coverage and more precise rules.
*   **Cons**: content without a source in the project's documents cannot be defended as the project's clinical basis.
*   **Reason for Rejection**: Rejected by author: only steps, tags, rules and activities from the project's documents are used.

## Risks and Mitigations

*   **The tablet is too slow for Pose plus Hand:** measured in M1; fall back to running Hand only on hand steps and Pose only on body steps.
*   **The online model changes or is withdrawn:** the served model version is stored with every attempt; replay mode re-scores saved clips with a new model. DeepSeek published open weights for its flash vision model, so the proxy could later point to a self-hosted copy.
*   **Frames are processed on DeepSeek's servers:** the committee and guardians approved sending cropped frames; the consent text names DeepSeek as the receiving service.
*   **The shared secret leaks:** the Console spend limit caps the cost; the secret is rotated in the Worker and re-entered in settings.
*   **Volunteer clips are easier than children's movements:** the accuracy figure is reported as volunteer accuracy; results with children are reported separately once available.
*   **Audio service changes voices or pricing:** audio is generated once and shipped as files, so the app never depends on the service at run time; the voice and model IDs are recorded in `config.json`.
*   **Illustrations are inconsistent or slow to produce:** one character reference sheet fixes the style; only the 41 camera-scored steps need finished art in M2, the rest can use a simpler draft until M5.

## Non-Functional Requirements

*   **Security and privacy:** child data encrypted on the tablet; no cloud backup; only cropped frames without identifiers leave the tablet; the API key stays in the proxy.
*   **Latency:** local verdicts within 300 ms of the end of an attempt; cloud verdicts within 8 s or "cannot tell".
*   **Versioning:** every attempt stores the catalogue version, rules version, model ID and prompt version.

## Additional Resources

*   [`docs/reference/`](../reference/): project details, the three assessment scales, the correct-performance guide, the treatment plans and the scoring rules.

## Future Improvements

*   Camera scoring for the 11 content-only tasks.
*   An on-device object detector, so object steps can be judged without uploading frames.
*   Accuracy measured with children after the volunteer evaluation.

--------------------------------------------------------------------------------

## Detailed Implementation Plan

### Repository layout

```text
android/                      Android app (Gradle, Kotlin)
proxy/                        Cloudflare Worker (TypeScript)
tools/catalog-builder/        Python: docs/reference -> catalogue JSON
tools/audio-gen/              Python: Arabic text -> audio files (ElevenLabs API)
art/                          character sheet, Rive file, step illustrations, stickers, sound effects (source files)
docs/reference/               project documents (unchanged)
docs/designs/                 this design
eval/                         clip set manifest and evaluation script
```

Package name: `com.aitherapist.dls`. Minimum Android API 30 (Android 11).

### Milestones

1.  **M1 Camera spike (weeks 1–2):** copy the MediaPipe pose and hand samples into `android/`, measure frame rate on the chosen tablet, build `VisibilityGate` and one `PoseVerifier` check, deploy the proxy, and get a verdict for one cup step.
2.  **M2 Catalogue and runner (weeks 3–4):** catalogue builder, all 147 steps runnable as content with illustrations and audio, state machine, database.
3.  **M3 Camera scoring for four tasks (weeks 5–7):** detector settings for the 41 steps, cloud questions, replay mode, record the volunteer clips, measure deepseek-flash on them.
4.  **M4 Rules, report, plan, training (weeks 8–9):** rules engine, report, plan, camera-counted and guided activities.
5.  **M5 Dashboard and evaluation (weeks 10–12):** therapist screens, corrections, blind review, metrics.

### Conventions

*   Kotlin files below are under `android/app/src/main/java/com/aitherapist/dls/`; unit tests under `android/app/src/test/java/com/aitherapist/dls/`; instrumented tests under `android/app/src/androidTest/java/com/aitherapist/dls/`. Every other path is relative to the repository root.
*   Step IDs: `<area>.<task>.<NN>` with two-digit step numbers. Areas: `dress`, `eat`, `drink`, `hygiene`. Tasks: `jacket.on`, `jacket.off`, `tshirt.on`, `shirt.on`, `trousers.on`, `socks.on`, `shoes.on`, `sandwich`, `spoon`, `fork`, `cup`, `bottle`, `straw`, `handwash`, `teeth`. Example: `drink.cup.07`.
*   Activity IDs: `<plan>.<domain>.<NN>` with plan `mild|moderate|severe` and domain `rom|coord|hand|midline|planning`. Example: `mild.rom.03`.
*   `catalogVersion` is the first 12 hex characters of the SHA-256 of the two output JSON files concatenated.
*   Landmark names are MediaPipe's (`LEFT_WRIST`, `RIGHT_SHOULDER`, `MOUTH_LEFT`, ...), after un-mirroring. `either` means the check passes if it passes for the left or the right side. Distances are expressed as a ratio of the shoulder width measured in the same frame.

### Activity parsing rule

In `treatment-plans.md`: a level-2 heading starting with `1.`, `2.` or `3.` sets the plan (mild, moderate, severe). Every level-5 heading (`##### N. Title`) is one activity; there are exactly 148. An activity's domain is taken from the nearest preceding level-3 or level-4 heading whose text contains one of the five domain names (case-insensitive). This also covers the mild plan's range-of-motion activities, whose nearest such heading is the level-4 "Therapeutic activities for improving range of motion".

### Check types

`Check` is a sealed class; JSON `type` selects the subclass. All "for s" conditions must hold continuously for that many seconds within the attempt window.

| `type` | Fields | Passes when |
|---|---|---|
| `angleAbove` / `angleBelow` | `a`, `vertex`, `b`, `deg`, `holdSec`, `side` | the angle a-vertex-b is above / below `deg` for `holdSec` |
| `pointAbove` | `point`, `ref`, `margin`, `holdSec`, `side` | `point` is higher than `ref` by `margin` (ratio) for `holdSec` |
| `distanceBelow` / `distanceAbove` | `a`, `b`, `ratio`, `holdSec`, `side` | distance a-b is below / above `ratio` x shoulder width for `holdSec` |
| `inZone` | `landmark`, `zone`, `holdSec`, `side` | the landmark lies inside the named zone for `holdSec` |
| `handsRubbing` | `maxWristRatio`, `minMotionRatio`, `holdSec` | both hands tracked, wrists closer than `maxWristRatio`, and their relative movement over each 0.5 s exceeds `minMotionRatio`, for `holdSec` |
| `question` | `text` | (cloud) the model answers `done` |

Zones (`mat`, `sink`, `place`) are rectangles the therapist draws once per setup on the camera preview in the framing check and are stored in settings.

### Camera-scored steps

| Step ID | Step | Method | Check |
|---|---|---|---|
| `dress.jacket.on.01` | Identifies the front and the back of the jacket | cloud | "Is the child holding the jacket up with the collar at the top and the inside facing the child?" |
| `dress.jacket.on.02` | Opens the jacket and prepares it | cloud | "Is the child holding the jacket open by both front edges?" |
| `dress.jacket.on.03` | Inserts the first arm, then the second | cloud | "Are both of the child's arms inside the jacket sleeves?" |
| `dress.jacket.on.04` | Pulls the jacket onto the shoulders and adjusts it | cloud | "Is the jacket resting on both of the child's shoulders?" |
| `dress.jacket.on.05` | Fastens the jacket from bottom to top | cloud | "Is the jacket zipper closed at least up to the chest?" |
| `dress.jacket.off.01` | Locate the zipper or the buttons | none | gaze cannot be judged |
| `dress.jacket.off.02` | Reach the zipper | hand | `distanceBelow` a=`either WRIST`, b=midpoint of shoulders, ratio 0.5, holdSec 0.3 |
| `dress.jacket.off.03` | Grasp the zipper | cloud | "Is the child pinching the zipper tab between the fingers?" |
| `dress.jacket.off.04` | Open the zipper | cloud | "Is the jacket zipper fully open?" |
| `dress.jacket.off.05` | Grasp both sides of the jacket | cloud | "Is the child holding both front edges of the open jacket?" |
| `dress.jacket.off.06` | Move the jacket away from the body | cloud | "Has the jacket been pushed off at least one shoulder?" |
| `dress.jacket.off.07` | Take the first arm out of the sleeve | cloud | "Is at least one of the child's arms fully out of its sleeve?" |
| `dress.jacket.off.08` | Take the second arm out of the sleeve | cloud | "Are both of the child's arms out of the sleeves?" |
| `dress.jacket.off.09` | Take the jacket off completely | cloud | "Is the child no longer wearing the jacket?" |
| `dress.jacket.off.10` | Put the jacket in the designated place | cloud | "Is the jacket lying inside the marked area, with no hand on it?" |
| `drink.cup.01` | Look at the cup | none | gaze cannot be judged |
| `drink.cup.02` | Reach your hand to the cup | pose | `inZone` landmark=`either WRIST`, zone `mat`, holdSec 0.3 |
| `drink.cup.03` | Hold the cup | cloud | "Is the child's hand wrapped around the cup?" |
| `drink.cup.04` | Lift the cup | cloud | "Is the cup held in the air above the mat?" |
| `drink.cup.05` | Bring the cup close to your mouth | pose | `distanceBelow` a=`either WRIST`, b=`MOUTH` midpoint, ratio 0.6, holdSec 0.3 |
| `drink.cup.06` | Place the cup on your lips | cloud | "Is the rim of the cup touching the child's lips?" |
| `drink.cup.07` | Tilt the cup slowly | cloud | "Is the cup tilted toward the child's mouth?" |
| `drink.cup.08` | Drink a little water | none | mouth action cannot be judged |
| `drink.cup.09` | Swallow the water | none | cannot be judged |
| `drink.cup.10` | Return the cup to its normal position | cloud | "Is the cup upright again while still held near the mouth?" |
| `drink.cup.11` | Move the cup away from your mouth | pose | `distanceAbove` a=`either WRIST`, b=`MOUTH` midpoint, ratio 1.0, holdSec 0.3 |
| `drink.cup.12` | Lower the cup onto the table | cloud | "Is the cup standing on the mat while the child still holds it?" |
| `drink.cup.13` | Let go of the cup | cloud | "Is the cup standing on the mat with no hand touching it?" |
| `hygiene.handwash.01` | Look at the sink | none | gaze cannot be judged |
| `hygiene.handwash.02` | Go to the sink | pose | `inZone` landmark=midpoint of hips, zone `sink`, holdSec 1.0 |
| `hygiene.handwash.03` | Turn on the tap | cloud | "Is water running from the tap?" |
| `hygiene.handwash.04` | Wet your hands with water | cloud | "Are both hands under the running water?" |
| `hygiene.handwash.05` | Get the soap | cloud | "Is the child holding the soap or pressing the soap pump?" |
| `hygiene.handwash.06` | Put soap on your hands | cloud | "Is there soap or lather on the child's hands?" |
| `hygiene.handwash.07` | Rub your palms together | hand | `handsRubbing` maxWristRatio 0.6, minMotionRatio 0.05, holdSec 2.0 |
| `hygiene.handwash.08` | Rub between your fingers | cloud | "Are the child's fingers interlaced while rubbing?" |
| `hygiene.handwash.09` | Rub the backs of your hands and around your nails | cloud | "Is the child rubbing the back of one hand with the palm of the other?" |
| `hygiene.handwash.10` | Rinse your hands with water | cloud | "Are the hands under running water with little or no lather left?" |
| `hygiene.handwash.11` | Turn off the tap | cloud | "Is the tap closed with no water running?" |
| `hygiene.handwash.12` | Get the towel | cloud | "Is the child holding a towel?" |
| `hygiene.handwash.13` | Dry your hands well | cloud | "Are the child's hands inside or rubbing the towel?" |

Totals: 41 steps; 5 not camera-scored (`none`), 6 local, 30 cloud. The thresholds above are starting values, tuned in M3 on the volunteer clips and then frozen with `catalogVersion`.

### Camera-counted activity selection

An activity is camera-counted only when its measurement criterion is a count of repetitions or a hold time of one body movement that a single `angleAbove`/`angleBelow`/`pointAbove`/`distanceBelow` check describes (for example "Raising the arms overhead, 10 repetitions"). The list and each criterion's check and thresholds are written in `tools/catalog-builder/activity_counters.json` (same `Check` format, plus `kind: reps|hold`, `target`, `perSide`, `low`, `high`). Every activity not in that file is guided.

### Attempt window and movement

*   Movement means the largest displacement of any tracked wrist or elbow over the last 0.5 s exceeds 0.03 x shoulder width.
*   The window closes on Done, or when at least 20 s have passed and there has been no movement for the last 2 s, or at 45 s.
*   Resume: the runner saves `(sessionId, stepIndex, attemptNumber, promptLevel)` on every transition. Reopening the app with a session in state `running` shows a "Continue" card in therapist mode; continuing restarts the saved attempt from its beginning (frames of the interrupted attempt are discarded).

### Data model

| Table | Columns (type) | Keys |
|---|---|---|
| `child` | `id` (UUID text), `name` (text), `code` (text, unique), `ageYears` (int), `effectLevel` (`full`/`calm`/`off`, default `calm`), `createdAt` (epoch ms) | PK `id` |
| `reward` | `id`, `childId`, `sessionId` (nullable), `stars` (int), `stickerId` (nullable), `createdAt` | PK `id`; FK `childId` → `child.id` CASCADE |
| `session` | `id` (UUID), `childId`, `taskId` (text), `kind` (`assessment`/`replay`), `state` (`running`/`paused`/`done`/`abandoned`), `startedAt`, `endedAt` (nullable), `catalogVersion`, `rulesVersion`, `stepIndex`, `attemptNumber`, `promptLevel` | PK `id`; FK `childId` → `child.id` ON DELETE CASCADE |
| `attempt` | `id` (UUID), `sessionId`, `stepId`, `number` (1-3), `promptLevel` (1-3), `helpPressed` (bool), `verdict` (`done`/`not_done`/`cannot_tell`), `reason` (text), `verifier` (`pose`/`hand`/`cloud`/`none`), `modelId` (nullable), `promptVersion` (nullable), `latencyMs` (int), `framePaths` (JSON array), `createdAt` | PK `id`; FK `sessionId` → `session.id` CASCADE; index (`sessionId`, `stepId`) |
| `correction` | `id`, `sessionId`, `stepId`, `level` (1-3 or null for "not scored"), `createdAt` | PK `id`; FK `sessionId` CASCADE; unique (`sessionId`, `stepId`) - latest wins |
| `blind_rating` | `id`, `attemptId`, `verdict` (`done`/`not_done`/`cannot_tell`), `createdAt` | PK `id`; FK `attemptId` → `attempt.id` CASCADE; unique `attemptId` |
| `training_result` | `id`, `childId`, `activityId`, `count` (nullable), `durationSec`, `criterionMet` (bool), `createdAt` | PK `id`; FK `childId` CASCADE |

Blind ratings are stored separately and never change levels; only `correction` rows do. `compare(a, b)` uses sessions of the same `taskId`, includes steps with a level in both, and reports per-domain averages of those steps for each session and the difference.

### Proxy API

Request `POST /verify`, header `Authorization: Bearer <shared secret>`, JSON body:

```json
{"requestId": "uuid", "stepId": "drink.cup.06", "question": "Is the rim of the cup touching the child's lips?",
 "images": ["<base64 JPEG>", "<base64 JPEG>", "<base64 JPEG>"]}
```

Response 200:

```json
{"verdict": "done", "reason": "rim at lower lip", "modelId": "deepseek-flash", "promptVersion": "v1"}
```

Errors: 401 wrong secret; 413 more than 3 images or body over 3 MB; 502 model error. The app treats every non-200 response as `cannot_tell` with `reason` = `http_<status>`. Invalid or unparseable model output is returned by the proxy as 200 with `cannot_tell` and reason `invalid_model_output`.

### Encryption

*   One AES-256-GCM key in Android Keystore (`dls_master`), not exportable.
*   The SQLCipher passphrase is 32 random bytes generated on first run, encrypted with `dls_master`, and stored in app-private DataStore.
*   PIN (PBKDF2 hash with salt), proxy URL and shared secret are stored in the same DataStore, the secret encrypted with `dls_master`.
*   Frames are JPEG bytes encrypted with `dls_master` (random 12-byte IV prefixed) in `files/frames/<sessionId>/<attemptId>-<n>.bin`.

### Replay

Replay uses MediaPipe in VIDEO running mode. One clip is one attempt: the window is the whole clip and Done is implied at the end. The clip's file name is `<stepId>__<attemptNumber>__<outcome>__<volunteer>.mp4`, which links it to its row in `eval/clips.csv`.

### Results export

Settings → Export writes `results.csv` to the Downloads folder with columns: `sessionId, childCode, taskId, stepId, attemptId, attemptNumber, promptLevel, verdict, reason, verifier, modelId, latencyMs, level, correctedLevel, blindVerdict, clipFile`. For replay sessions `clipFile` is the clip name; this is the join key with `eval/clips.csv`. Names are never exported.

### Tooling

*   Gradle plugins: `org.jlleitschuh.gradle.ktlint`, `org.jetbrains.kotlinx.kover` (coverage), KSP for Room.
*   Python: `pytest`, `ruff`, listed in `tools/requirements.txt`; the `elevenlabs` SDK in `tools/audio-gen/requirements.txt`.
*   Proxy: `wrangler`, `vitest`, `typescript`; `npm run build` = `wrangler deploy --dry-run`.

### Milestone exit criteria

| Milestone | Items | Exit criterion |
|---|---|---|
| M1 | 7-9, 13-16 (one check), 26 | Pose and Hand at 15 fps or more on the tablet; one cloud verdict returned in under 8 s |
| M2 | 1-6, 10-12, 17, 20, 21, 24 (runner) | All 147 steps play with illustration, Arabic text and audio; builder tests pass |
| M3 | 2 (41 steps), 15, 16, 23, 27 (clips) | All 41 steps scored on volunteer clips; deepseek-flash accuracy measured |
| M4 | 18, 19, `activity_counters.json`, 24 (rules, counter) | Rules and counter tests pass; report and plan shown |
| M5 | 22, 25, 27, 28 | Goal 8 measured and reported |

### Target Files to Create/Modify

#### 1. `tools/catalog-builder/build_catalog.py` (Create)

*   **Type / Scope**: Python 3.11 build script.
*   **Proposed Changes**: Parses the tables in `docs/reference/*-assessment-scale.md` (columns `Step No.`, `Step`, `Functional domain` split on ` + `, `Functional sub-skills`; "Not tagged in source" gives an empty list). Parses `treatment-plans.md` by the Activity parsing rule above; fields from the labels Tools, How to perform, Goal, Measurement criterion. Assigns IDs, merges `detectors.json`, `activity_counters.json` and `strings.ar-eg.json`, validates, and writes `assessment.json` and `activities.json` with a `catalogVersion` hash.

#### 2. `tools/catalog-builder/detectors.json` (Create)

*   **Type / Scope**: hand-written data.
*   **Proposed Changes**: For each of the 41 camera-scored steps: `method`, `framing`, typed `check`. All other steps default to `method: "none"`.

#### 2a. `tools/catalog-builder/activity_counters.json` (Create)

*   **Type / Scope**: hand-written data.
*   **Proposed Changes**: The camera-counted activities chosen by the rule in "Camera-counted activity selection", each with its `Check`, `kind`, `target`, `perSide`, `low` and `high`. Filled in M4.

#### 2b. `tools/requirements.txt`, `tools/audio-gen/requirements.txt` (Create)

*   **Type / Scope**: Python dependencies.
*   **Proposed Changes**: `pytest`, `ruff` for the tools; the `elevenlabs` SDK for audio generation.

#### 3. `tools/catalog-builder/strings.ar-eg.json` (Create)

*   **Type / Scope**: data.
*   **Proposed Changes**: Egyptian Arabic text for each step and activity ID, translated from the English reference files and reviewed line by line.

#### 4. `tools/catalog-builder/test_build_catalog.py` (Create)

*   **Type / Scope**: pytest.
*   **Proposed Changes**: Asserts counts (eating 42, drinking 38, hygiene 27, dressing 40 steps; 148 activities; 48 mild, 50 moderate, 50 severe), unique IDs, allowed domain names, empty tags for untagged jacket-off steps, and that the mild plan's range-of-motion activities get the Range of motion section.

#### 5. `tools/audio-gen/generate_audio.py` (Create)

*   **Type / Scope**: Python script.
*   **Proposed Changes**: Generates one MP3 per ID from `strings.ar-eg.json` through the ElevenLabs text-to-speech API with the official `elevenlabs` Python SDK (one fixed voice ID and model ID set in `tools/audio-gen/config.json`; the API key read from the `ELEVENLABS_API_KEY` environment variable, never committed) into `android/app/src/main/assets/audio/ar-eg/`. It skips IDs whose text has not changed since the last run (hash stored in `tools/audio-gen/manifest.json`), and writes `review.csv` to mark each file approved or regenerated. Slow-speed audio for prompt level 3 is a second file per step generated with a lower speaking rate, not slowed playback.

#### 6. `art/character-sheet.png`, `art/character.riv`, `art/steps/<id>.png`, `tools/art/export_illustrations.py` (Create)

*   **Type / Scope**: art assets and a small export script.
*   **Proposed Changes**: The character sheet defines the boy (front, side, back, hands close-up) and the colour palette. Each step and activity gets one illustration of the character doing the action, drawn or generated with an image model from the character sheet and then corrected by the team so the action matches the step text exactly. `character.riv` is built in the Rive editor with a state machine: `idle`, `talk`, `point`, `celebrate`, plus `celebrateSmall`, `celebrateBig` and `encourage`, and movement animations for the 41 camera-scored steps where useful. `art/stickers/` holds at least 30 sticker images in the same style, and `art/sfx/` the star chime, the task fanfare and the soft calm-mode sound, generated once with ElevenLabs sound effects. The script resizes the PNGs to 1280 px WebP into `android/app/src/main/assets/illustr/` and copies the Rive file to `android/app/src/main/res/raw/`. The catalogue builder fails if a step has no illustration.

#### 7. `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle/libs.versions.toml` (Create)

*   **Type / Scope**: build configuration.
*   **Proposed Changes**: One `app` module; versions for Kotlin, Compose, CameraX, MediaPipe Tasks Vision, Room, SQLCipher, Vico, kotlinx-serialization, OkHttp.

#### 8. `android/app/build.gradle.kts` (Create)

*   **Type / Scope**: module build.
*   **Proposed Changes**: Dependencies; `minSdk 30`; task that downloads the MediaPipe model files into assets, as the samples do.

#### 9. `android/app/src/main/AndroidManifest.xml` (Create)

*   **Type / Scope**: manifest.
*   **Proposed Changes**: Camera and internet permissions; `allowBackup="false"`; `supportsRtl="true"`; landscape; one activity.

#### 10. `MainActivity.kt`, `ui/AppNavigation.kt` (Create)

*   **Type / Scope**: Compose entry and navigation.
*   **Proposed Changes**: Locale `ar-EG`, right-to-left; first-run setup route (CUJ 1); therapist and child routes; screen pinning in child mode.

#### 11. `catalog/CatalogModels.kt`, `catalog/CatalogRepository.kt` (Create)

*   **Type / Scope**: Kotlin.
*   **Proposed Changes**: `Step(id, text, textAr, domains, subSkills, detector)`, `Detector(method, framing, check)` with `Check` as a sealed class matching the JSON `type`; `Activity(id, plan, domain, tools, howTo, goal, criterionText, criterion?, mode)`; loads JSON from assets.

#### 12. `data/AppDatabase.kt`, `data/Entities.kt`, `data/Daos.kt`, `data/SecureSettings.kt`, `data/FrameStore.kt` (Create)

*   **Type / Scope**: Room with SQLCipher.
*   **Proposed Changes**: Entities exactly as in the Data model table (`child`, `session`, `attempt`, `correction`, `blind_rating`, `training_result`). `SecureSettings` implements the Encryption section. `FrameStore` writes encrypted JPEGs and deletes a child's frames with the profile. Migrations declared from version 1.

#### 13. `camera/CameraController.kt`, `camera/FrameBuffer.kt` (Create, adapted from mediapipe-samples)

*   **Type / Scope**: CameraX.
*   **Proposed Changes**: Front camera analysis stream at 640x480; ring buffer of the last 45 s of frames at 5 per second (at most 225 frames) for evidence and cloud crops; un-mirroring.

#### 14. `vision/Landmarkers.kt` (Create, adapted from the samples' `PoseLandmarkerHelper` and `HandLandmarkerHelper`)

*   **Type / Scope**: MediaPipe Tasks Vision, live-stream mode.
*   **Proposed Changes**: Runs only the landmarker(s) the current step needs (GPU delegate, CPU fallback) and publishes timestamped results.

#### 15. `vision/Verifier.kt`, `vision/VisibilityGate.kt`, `vision/PoseVerifier.kt`, `vision/HandVerifier.kt`, `vision/Geometry.kt` (Create)

*   **Type / Scope**: Kotlin.
*   **Proposed Changes**: The interface and `Verdict`; evaluation of each `Check` subtype (angle above/below with hold, distance below, point above point with hold); angle math.

#### 16. `vision/CloudVerifier.kt` (Create)

*   **Type / Scope**: Kotlin with OkHttp.
*   **Proposed Changes**: Picks start, middle and end frames, crops around hands with margin, JPEG quality 80, base64; posts the Proxy API request; maps the response or any failure to a `Verdict`.

#### 17. `session/AssessmentRunner.kt`, `session/PromptLadder.kt`, `session/AttemptWindow.kt` (Create)

*   **Type / Scope**: Kotlin state machine (coroutines, `StateFlow`).
*   **Proposed Changes**: The state machine in the Technical Plan; Done from screen or Bluetooth key event; pause and resume; saves each transition.

#### 18. `scoring/RulesEngine.kt` (Create)

*   **Type / Scope**: pure Kotlin.
*   **Proposed Changes**: `stepLevel`, `domainAverages`, `planLevel`, `activitiesFor`, `strengthsAndNeeds`, `compare`. Constant `RULES_VERSION = "1"`.

#### 19. `training/RepCounter.kt`, `training/TrainingRunner.kt` (Create)

*   **Type / Scope**: Kotlin.
*   **Proposed Changes**: Smoothed hysteresis counter and hold timer driven by the typed criterion; guided flow; saves `TrainingResult`.

#### 20. `audio/AudioPlayer.kt` (Create)

*   **Type / Scope**: MediaPlayer wrapper.
*   **Proposed Changes**: Plays `audio/ar-eg/<id>.mp3` at normal or slow speed; reports end of playback.

#### 21. `ui/child/` (Create): `TaskPickerScreen.kt`, `StepScreen.kt`, `FramingCheckScreen.kt`, `TrainingScreen.kt`, `CharacterView.kt`, `StarBar.kt`, `RewardEffects.kt`, `TaskRewardScreen.kt`, `StickerBookScreen.kt`; `rewards/RewardRules.kt`

*   **Type / Scope**: Compose with the Rive Android runtime.
*   **Proposed Changes**: Calm colours, a large illustration with the animated character beside it, one sentence of text, large Done area, Help and Repeat buttons (Stage 1); framing outline; star bar, particle and sound effects scaled by the child's `effectLevel`, task reward screen and sticker book as in Rewards and effects; `RewardRules` (pure Kotlin, unit-tested) computes stars and sticker unlocks; whole-task walkthrough by the character.

#### 22. `ui/therapist/` (Create): `SetupScreen.kt`, `PinScreen.kt`, `ChildListScreen.kt`, `ChildProfileScreen.kt`, `SessionDetailScreen.kt`, `ReportScreen.kt`, `PlanScreen.kt`, `CompareScreen.kt`, `BlindReviewScreen.kt`, `DiagnosticsScreen.kt`, `SettingsScreen.kt`

*   **Type / Scope**: Compose with Vico.
*   **Proposed Changes**: The screens listed in the Technical Plan. Blind review hides the app's verdict and writes `blind_rating` rows only. Settings export `results.csv` as defined in Results export; About shows open-source credits.

#### 23. `replay/ReplaySource.kt` (Create)

*   **Type / Scope**: Kotlin.
*   **Proposed Changes**: Implements the Replay section; imports clips with the Android file picker into a `replay` session.

#### 24. Unit tests in `android/app/src/test/` (Create): `RulesEngineTest.kt`, `GeometryTest.kt`, `RepCounterTest.kt`, `AssessmentRunnerTest.kt`

*   **Type / Scope**: JVM tests.
*   **Proposed Changes**: Rule 1 and Rule 2 boundaries (exactly 3, 2.5, 1.5); a correction overriding a level; "not scored" excluded; a domain with no scored steps; visual-motor mapped to Motor coordination without double counting; activity order; `RewardRules` star and sticker counts; angle math; counting on synthetic angle series; runner transitions for done, not done, cannot tell, pause and resume; window closing rule; each `Check` subtype on synthetic landmarks; `compare`; `SecureSettings` round trip (instrumented); CloudVerifier mapping of 200, 401, 413, 502 and timeout (MockWebServer).

#### 25. `android/app/src/androidTest/DatabaseTest.kt` (Create)

*   **Type / Scope**: instrumented test.
*   **Proposed Changes**: Encrypted database opens with the Keystore key and rejects a wrong key; migrations run.

#### 26. `proxy/package.json`, `proxy/wrangler.toml`, `proxy/src/index.ts`, `proxy/src/prompt.ts`, `proxy/test/index.test.ts` (Create)

*   **Type / Scope**: Cloudflare Worker, TypeScript, `openai` SDK (DeepSeek base URL), Vitest.
*   **Proposed Changes**: `POST /verify` checks the shared secret and payload size, sends one chat-completions request to DeepSeek with the model from the `MODEL_ID` variable (default `deepseek-flash`), the fixed system prompt, the question and the three images as image content parts, and JSON output mode; validates the JSON against the verdict schema; refusals and errors become `cannot_tell`. Secrets: `DEEPSEEK_API_KEY`, `SHARED_SECRET`. Tests mock the SDK.

#### 27. `eval/clips.csv`, `eval/compute_metrics.py` (Create)

*   **Type / Scope**: evaluation data and script.
*   **Proposed Changes**: Clip manifest (file, task, step, scripted outcome); the script reads the CSV export and prints per-attempt agreement, quadratic-weighted kappa on levels (attempts and steps "not scored" are excluded from kappa and reported as the "cannot tell" rate), and a confusion matrix per step.

#### 28. `README.md` (Create)

*   **Type / Scope**: documentation.
*   **Proposed Changes**: How to build the catalogue, audio and illustrations; build and install the app; deploy the proxy and set its secrets and spend limit; pair the Bluetooth button; the physical setup; credits and licenses.

### Verification Plan

*   **Compilation/Build**: `./gradlew assembleDebug` in `android/`; `npm run build` in `proxy/`; `python tools/catalog-builder/build_catalog.py` exits 0.
*   **Unit Tests**: `./gradlew test`; `pytest tools/`; `npm test` in `proxy/`.
*   **Static Analysis & Linting**: `./gradlew lint ktlintCheck`; `ruff check tools eval`; `npx tsc --noEmit` in `proxy/`.
*   **Test Coverage Requirement**: 90% line coverage for `RulesEngine`, `Geometry` and `RepCounter`.
*   **E2E/Integration**: on the chosen tablet, run each camera-scored task with a volunteer (done on attempt 1, done later, not done) and check the saved levels; switch off Wi-Fi mid-task and check the attempt becomes "cannot tell"; close and reopen the app mid-task and check it resumes; run `eval/compute_metrics.py` and check Goal 8 (agreement of at least 80%, kappa of at least 0.6, "cannot tell" at most 15%).
