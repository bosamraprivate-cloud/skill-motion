---
name: Skillmotion (daily living skills)
status: final
created: 2026-11-11
updated: 2026-11-11
author: Ahmed Samir
sources:
  - _bmad-output/planning-artifacts/prds/prd-Ai therapist-2026-11-11/prd.md
  - _bmad-output/planning-artifacts/prds/prd-Ai therapist-2026-11-11/addendum.md
  - _bmad-output/planning-artifacts/briefs/brief-ai-therapist-2026-11-11/brief.md
  - docs/designs/daily-living-skills-app.md (wins on conflict)
  - docs/reference/project-details.md
---

# Experience Spine

Terms (task, step, attempt, prompt level, verdict, level, not scored, cannot tell, correction, effect level, framing check, blind review) are used exactly as defined in PRD §3. All clinical content (steps, tags, rules, activities) comes from the catalogue built from `docs/reference/`; this document adds none. `DESIGN.md` owns visuals; tokens are referenced as `{colors.x}`, `{typography.x}`.

## Foundation

- **Form factor:** one Android tablet (minSdk 30), landscape only, Kotlin + Jetpack Compose with Material 3 as the base system for therapist mode; child mode uses custom components on top.
- **Language:** Egyptian Arabic UI and audio, full right-to-left layout and mirrored navigation. Directional icons (back, progress) mirror; the check, replay and hand icons do not.
- **Two modes, one device:** child mode (screen-pinned, no system navigation) and therapist mode (PIN). No accounts, no network needed except for object-step judging.
- **Primary users:** the occupational therapist (operates everything, sets up, reviews) and the child with autism (only ever sees child mode).

## Information Architecture

### Child mode (reached only by therapist hand-off)

| Surface | Reached from | Purpose |
|---|---|---|
| Task start ("Start") | Therapist starts assessment/training | Character greets, task picture, big Start (source Stage 1) |
| Framing check | Task start, camera-scored tasks | Preview with outline; adult confirms position |
| Step screen | Sequence | One step: character still frame, sentence, audio, Done / Help / Repeat; Help plays the character's demonstration clip |
| Step reward moment | After verdict saved | Stars and effect per effect level (overlay, 1.5–3 s) |
| Character walkthrough | After last step | Character performs the whole task once: the step demonstration clips back to back with audio (Stage 4) |
| Task reward screen | After walkthrough | Stars earned, big celebration, sticker progress |
| Sticker book | Task reward screen ("My stickers") | Earned stickers and progress path |
| Training activity screen | Training hand-off | Camera-counted (counter) or guided (character demonstration clip, timer, Done) |
| Adult pause sheet | Long-press adult corner 2 s | Pause / resume / exit to therapist (PIN) |

### Therapist mode (PIN)

| Surface | Reached from | Purpose |
|---|---|---|
| First-run setup | First launch | PIN, proxy address + secret, zones, framing check |
| PIN screen | App start, leaving child mode | Gate |
| Child list | Rail "Children" (home) | Children, search by name/code, Add, "Continue" card for interrupted sessions |
| Child profile | Child list row | Details, effect level, sessions tab, report, plan, training history, compare |
| New session dialog | Profile | Pick task (jacket: on/off/both), or Training → hand-off |
| Session detail | Session row | Steps with levels, attempts, evidence frames, reasons, Correct |
| Functional report | Profile / session | Strengths, needs, domain averages, plan levels, step counts |
| Plan + activity picker | Profile | Activities per weak domain (weakest first), select for training |
| Compare | Profile | Two assessments of the same task, per-domain averages and difference |
| Training history | Profile | Activity, count, duration, criterion met |
| Replay & evaluation | Rail | Import clips, replay sessions, blind review, accuracy metrics |
| Blind review | Replay & evaluation | Rate attempts with verdict hidden |
| Diagnostics | Rail | Frame rate, landmark visibility, proxy reachability/latency, cannot-tell rate |
| Settings | Rail | PIN change, proxy, zones, CSV export |

Navigation rail on the start (right) edge; list–detail panes; modal depth one.

## Voice and Tone

**Child:** Egyptian Arabic, warm, present tense, one short sentence, second person, the child's action first. Step sentences come from the catalogue (`strings.ar-eg.json`) and are reviewed by the author; UI labels below are [ASSUMPTION] pending author review.

| Element | Text | Note |
|---|---|---|
| Done | خلصت | "I'm done" |
| Help | ساعدني | |
| Repeat | تاني | "again" |
| Start | يلا نبدأ | |
| Step done | brava line from character audio, e.g. "شاطر!" | Only after verdict saved |
| Not done after attempt 3 | "هنجربها سوا المرة الجاية" | From design: "we'll try it together next time" |
| Cannot tell | "يلا نجرب تاني" (same as a neutral retry) | Never implies failure |

Never: "wrong", "no", "try harder", scores, levels, timers counting down at the child.

**Therapist:** English-free Egyptian/Modern Standard Arabic professional labels, neutral and precise. Reports state a weak step "may be related to" its domains; never diagnostic language. Errors say what happened and what to do ("Proxy not reachable. Object steps will be saved as cannot tell. Check Wi-Fi.").

## Component Patterns

- **Step screen layout is invariant:** same zones, same button positions, same order of events on every step (predictability). See DESIGN Layout.
- **Done area** — tap anywhere in it, or Bluetooth key press, closes the attempt window. Debounced 1 s. Disabled (dimmed, not hidden) while instruction audio plays, so the window opens only after audio ends.
- **Help** — moves to the next prompt level immediately, counts as a prompt (FR-12). At prompt level 3 Help replays level 3.
- **Repeat** — replays the current prompt level's audio and character talk; does not count as a prompt.
- **Prompt ladder presentation:** level 1 = illustration + sentence + audio, character `talk`; level 2 = audio replayed, character `point`, illustration enlarged; level 3 = the step's demonstration clip full screen (the character performs the step), slow audio. Buttons remain visible at level 3 in the bottom band.
- **Step schedule** — advances one dot per step; never shows outcome.
- **Star bar** — counts stars per FR-37; stars only ever increase.
- **Character state machine:** `idle` default; `talk` during instruction audio; `point` at level 2; `celebrateSmall` on step reward; `encourage` after not done at attempt 3; `celebrateBig` on task reward. Effect level off keeps `idle`/`talk`/`point` only.
- **Correct (therapist)** — opens a sheet with 3 / 2 / 1 / not scored, shows automatic level; saving marks "reviewed" and recalculates all dependent screens without further action.
- **Activity picker** — checklist grouped by domain then plan level, each activity expandable to tools, how to perform, goal, criterion (verbatim from catalogue).

## State Patterns

| State | Child sees | Therapist sees |
|---|---|---|
| Instruction playing | Character `talk`, Done dimmed, no effects | — |
| Attempt window open | Calm idle screen, Done active | — |
| Judging (local ≤300 ms / cloud ≤8 s) | Character `idle` "thinking" pose, soft dots; no spinner text | — |
| Done | Reward moment per effect level | Level 3 or 2 |
| Not done, attempt < 3 | Next prompt level begins, no feedback beyond the new prompt | Attempt logged |
| Not done, attempt 3 | `encourage` line, 1 star, next step | Level 1 |
| Cannot tell | Same attempt repeats once, neutral retry line | Reason stored; second time "not scored" |
| Network lost | Nothing different | Banner in therapist mode; attempts saved as cannot tell |
| Paused | Character sits, screen dims; resume restarts the attempt | — |
| Interrupted (app closed) | — | "Continue" card on child list |
| Loading (therapist) | — | Skeleton of the final layout with one shimmer (after 150 ms); never a full-screen spinner (AD-13) |
| Error (therapist) | — | Inline `ErrorState` with message and Retry; child mode never shows errors |
| Empty: no children / sessions / plan | — | Single message + primary action ("Add child") |
| Domain with no scored steps | — | "No scored steps" instead of average |
| Content-only task | Same step flow, no camera judging | All steps "not scored" |

## Interaction Primitives

- Touch: single tap only in child mode; no swipes, drags, long-presses (except the hidden adult 2 s long-press).
- Bluetooth HID button: any key press = Done; paired in Android settings.
- Audio: every child screen speaks its instruction; text is secondary.
- Camera: front camera, un-mirrored before left/right rules; the camera preview is not shown to the child during steps [ASSUMPTION: avoids self-image distraction], only in framing check.
- Therapist: standard Material touch, text fields, dialogs; tables scroll vertically only.

## Rewards and Effects

| Effect level | Step done | Task end | Sound |
|---|---|---|---|
| full | Star flies to bar, Konfetti burst around illustration, `celebrateSmall` | `celebrateBig`, Konfetti, sticker progress | Chime |
| calm (default) | Star fades into bar (slow), `celebrateSmall` | `celebrateBig` slowed, no particles | Soft sound |
| off | Star counter increments only | Static stars + sticker progress | None |

Rules: never more than 3 flashes per second; no effect or sound while instruction audio speaks (effects wait for audio end); rewards appear only after the verdict is saved and never change levels; sticker unlocks every 3 finished tasks; no negative sound or image in any mode.

## Accessibility Floor (autistic children)

- **Predictability:** identical layout and event order on every step; Start screen previews the task picture; the visual schedule shows how many steps remain; the character walkthrough closes every task the same way.
- **Low sensory load:** calm palette, one moving element at a time, default effect level calm, no auto-playing music, no time pressure shown.
- **Multimodal instruction:** illustration + one sentence + audio on every step; Repeat always available.
- **Distance use (1–1.8 m, jacket and sink):** audio carries the instruction; level 3 full-screen illustration; Done by Bluetooth button; Done area and stars readable at distance.
- **Motor:** child targets at least 120dp; Done at least 220×160dp; no precision gestures.
- **No failure signal:** errors, network problems and cannot-tell are invisible to the child.
- **Lock-in:** screen pinning; leaving requires the adult corner + PIN.
- **Therapist:** Material 3 contrast AA, colour plus text on all level encodings, TalkBack labels on chips and charts.

## Key Flows

### Flow 1: Youssef drinks from a cup (CUJ 3)

Youssef, 7, sits at a table; the tablet is 60 cm away; the cup is on the mat. Therapist Mona [ASSUMPTION: names are illustrative].
1. Mona opens Youssef's profile, taps New session → Drinking from a cup.
2. Framing check: outline turns sage; Mona taps "Hand to child"; screen pinning starts.
3. Task start: character waves, cup picture, "يلا نبدأ". Youssef taps Start.
4. Step 1: illustration, sentence, audio; Done dims until audio ends; Youssef acts and taps Done.
5. Verdict saved; calm star fades into the bar; character `celebrateSmall`; schedule moves on.
6. A later step not done on attempt 1: level 2 prompt (character points, picture grows). He does it; star.
7. Wi-Fi drops on an object step: Youssef only sees a neutral retry; therapist data shows cannot tell.
8. **Climax:** after step 13 the character performs the whole task; the reward screen shows his stars and the second of three sticker slots filling.
9. Mona long-presses the corner, enters PIN, back in therapist mode.

### Flow 2: Mona reviews and corrects (CUJ 4)
1. Child list → Youssef → Sessions → today's session.
2. Step list with level chips; she taps a step at 2, views the three evidence frames per attempt and the reason.
3. Correct → 3. **Climax:** chip shows 2 struck, 3 in `corrected`, "reviewed"; report and plan update instantly.

### Flow 3: Jacket at distance with Bluetooth button
Tablet on floor stand 1.8 m away. Each step plays audio; at level 3 the illustration fills the screen. Youssef presses the Bluetooth button instead of walking to the tablet. **Climax:** whole task done without touching the screen.

### Flow 4: Training (CUJ 5)
Mona picks activities in the plan picker → Start training → hand-off. Camera-counted activity shows a large counter and ring; at the criterion, a calm reward; guided activities show illustration, timer and Done. Results appear in training history.

### Flow 5: First-run setup (CUJ 1)
Welcome → set PIN (twice) → proxy address + secret → draw zones on preview → framing check → diagnostics shows proxy reachable → child list (empty state, "Add child").

### Flow 6: Accuracy evaluation (CUJ 6)
Replay & evaluation → import clips → app scores → Blind review (verdict hidden, one attempt at a time, done / not done / cannot tell) → metrics: agreement, weighted kappa, cannot-tell rate, confusion matrix per step.

## Responsive & Platform

Landscape tablets 8–11 inch only; portrait is locked. Layout scales by width class (medium/expanded); child zones keep proportions. No phone layout in v1.
