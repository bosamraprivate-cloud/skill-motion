# Remaining work to 100%

Updated 2026-11-15. Ordered: each group mostly depends on the one before. Tick items as they finish.

## 1. Owner feedback (now)

- [ ] Owners try the latest release (v0.1.1; v0.1.2 adds the time ring after the device check) and send notes.
- [ ] Fix any bug or flow problem they report before adding anything big.
- [ ] Owners review `docs/reference/undressing-steps-team-draft.md` (31 take-off steps, team draft).
- [ ] Owners review `docs/reference/step-timing-draft.md` (step seconds + 3 open questions).
- [ ] Device check of the time ring (Story 6.2): look, RTL, pause, FULL/CALM, time up, switch off.
- [ ] Device check of the sticker cabinet on the child profile and the new-sticker moment (finish 3 tasks; FULL and CALM).
- [ ] Release v0.1.2 for the owners after the device checks (time ring, stickers, sticker moment).

## 2. Assets (largest item)

- [ ] **Voice:** about 590 short Egyptian clips with ElevenLabs (`tools/audio-gen/generate_audio.py`, resumable). Blocked on ElevenLabs balance. No step audio is in the app yet.
- [ ] **Boy step clips** (Flow, needs a credited account):
  - [x] drink.cup, drink.bottle
  - [ ] drink.straw (last clip: away-lower-release)
  - [ ] handwash (6 clips), teeth (6 clips)
  - [ ] dressing, 6 garments, take-off then put-on (needs garment start frames; socks and shoes seated)
- [ ] **Girl step clips and reactions:** same list as the boy.
- [ ] Compress all clips so the APK stays small.
- [x] **Stickers:** 30 sticker images in the character style (`art/stickers/`, prompts `tools/art/sticker_prompts.json`, exported by `tools/art/export_stickers.py`).
- [x] **New-sticker moment** on the task reward (pop, glow, calm variant) — `ui/child/StickerUnlock.kt`.
- [ ] **Sound effects** (`art/sfx/`, ElevenLabs Sound Effects, needs balance), then wire each into the app:
  - [ ] Star chime after each finished step (FULL) and a softer version (CALM).
  - [ ] Task fanfare on the task reward screen (FULL) and a soft calm-mode sound.
  - [ ] New-sticker sound for the sticker moment (sparkle "pop"), FULL and CALM.
  - [ ] Time-ring sounds (Story 6.2): a gentle "done early" sparkle sound; time up stays silent or a soft neutral tone (never negative, never ticking).
  - [ ] Button tap sound (optional, very soft) for Done / Help / Repeat.
  - [ ] Respect the child's effect level and the system volume; no sound when the session is paused.
- [ ] **Character voice lines for rewards** (part of the ElevenLabs voice batch): "Well done! You got a new sticker", task-finished praise, time-up encouragement ("Let's try together"), so the side character speaks during the sticker moment and time-up encourage.
- [ ] **Arabic review** of UI text and activity details by the owner or project owners.

## 3. AI zone suggestions

- [ ] The app suggests the table, sink and jacket zones from the camera image; the therapist confirms or adjusts. Needs real room photos, so after the assets.

## 4. Real accuracy measurement (Story 5.9)

- [ ] Volunteer videos recorded per `eval/recording-protocol.md` (with consent).
- [ ] Run them through the app, therapist blind review, compute agreement, kappa and cannot-tell rate against Goal 8.

## 5. Security and stability before delivery

- [ ] Proxy: rotate the shared secret (it is in the APK and the repo was public) and add a daily usage limit.
- [ ] Pin SHA-256 fingerprints for the downloaded pose and hand models.
- [ ] Switch the GitHub repo back to private.
- [ ] Update the outdated GitHub Actions versions in `.github/workflows/android-release.yml`.

## 6. Final testing

- [ ] `connectedAndroidTest` (includes MigrationTest 7→8; wipes app data on the device, so run last).
- [ ] Full therapist + child run on a real tablet or a tablet emulator, every edge case.
- [ ] `pytest tools/`, `ruff`, proxy `npm test`/`build`/`tsc`, final review.

## 7. Closing

- [ ] Release v1.0.
- [ ] Final docs: README, how-to-use guide, evaluation results.
- [ ] Epic retrospective and final project summary.
- [ ] Reset the test phone's screen size (`adb shell wm size reset`, `wm density reset`).

**Only the owner can unblock:** volunteer videos, ElevenLabs balance (voice + sound effects), a credited Flow account. Item 5 can start any time.
