# Rive guide: building `character_boy.riv`

Step-by-step for building the boy in the Rive editor (rive.app, in the browser). The girl is made afterwards by duplicating this file and swapping the images (section 8).
Parts are in `art/parts/boy/`. Side names are the **character's** sides: `_r` = his right = **your left on screen**.

## 1. New file

1. Rive → **New file** → name it `character_boy`.
2. Select the artboard → set **Width 1024, Height 1536**, background transparent.
3. Drag the whole `art/parts/boy/` folder (all PNGs except `parts-sheet.png`) onto the canvas. Rive imports each as an image.

## 2. Assemble the boy

Use `art/poses/boy/idle.png` as a guide: drag it in, set its opacity to 30%, lock it, and place the parts on top of it. Delete it when you are done.

Layer order in the Hierarchy, **top = in front**:

```
hair
brow_r, brow_l
eye_r_open, eye_l_open, eye_r_closed, eye_l_closed   (closed eyes: opacity 0)
mouth_smile, mouth_talk, mouth_happy, mouth_o        (only mouth_smile visible)
head
hand_r_open, hand_r_point, hand_r_grasp              (only hand_r_open visible)
forearm_r
upperarm_r
hand_l_open
forearm_l
upperarm_l
torso
thigh_r, shin_r, thigh_l, shin_l
```

Group the face pieces with the head: select head + hair + brows + eyes + mouths → right click → **Group** → name it `face`.

## 3. Bones (skeleton)

Bone tool (B):

1. **Spine:** click the hips centre → click the chest → click the neck base → click the top of the head. Name them `hips`, `chest`, `neck`, `head`.
2. **Right arm:** start at the right shoulder (your left on screen) → elbow → wrist. Name them `upperarm_r_bone`, `forearm_r_bone`. Do the same on the other side for `_l`.
3. **Legs:** hip → knee → ankle, for both legs.
4. **Bind:** select each image, open **Bind bones** in the Inspector, and pick its bone:

| Part | Bone |
|---|---|
| `face` group | `head` |
| torso | `chest` |
| upperarm | upperarm bone |
| forearm + its hands | forearm bone |
| thigh / shin | leg bones |

Tip: put the joint ends of each part (the rounded caps) exactly on the bone joints, so the arms bend without gaps.

## 4. Hair mesh (soft bounce)

Select `hair` → **Edit mesh** → add about 12 points around the outline plus a few inside → bind them to `head` with a little weight toward the outer curls. This gives the hair a soft follow-through when the head moves.

## 5. Animations (timeline)

Create these animations. All are **calm**: small movements and slow ease-in-out, never fast or flashing.

| Animation | Loop | What moves |
|---|---|---|
| `idle` | Loop, 3 s | `chest` scales 100→101.5→100% (breathing). Head tilts ±1°. One blink at 2.4 s: open eyes opacity 1→0, closed eyes 0→1, back after 0.12 s |
| `talk` | Loop, 0.5 s | mouth_smile → mouth_talk → mouth_o → mouth_talk → mouth_smile (opacity keys). Head nods ±1.5° |
| `point` | Loop, 2 s | Right arm rises: upperarm to about 70° forward/sideways, forearm straight; swap `hand_r_open` → `hand_r_point`. Head turns slightly toward the hand. Hold, small hand bob ±2 px |
| `encourage` | One shot, 1.2 s | Gentle nod twice, mouth_smile, brows up 4 px, then back to neutral |
| `celebrateSmall` | One shot, 1 s | Small hop of 12 px, mouth_happy, eyes stay open |
| `celebrate` | One shot, 1.5 s | Both arms up about 45°, hop of 20 px, mouth_happy |
| `celebrateBig` | One shot, 2 s | Both arms fully up, two hops of 28 px, mouth_happy, a happy squint (closed eyes for 0.4 s) |

## 6. State machine (the names must match exactly)

**Animate** → **State Machine** → name it `Character`.

**Inputs** (the app sets these by name):

| Input | Type |
|---|---|
| `talk` | Boolean |
| `point` | Boolean |
| `encourage` | Trigger |
| `celebrateSmall` | Trigger |
| `celebrate` | Trigger |
| `celebrateBig` | Trigger |
| `idle` | Trigger (forces a return to idle) |

**States:**

- **Entry →** `idle` (loop).
- **Talk:** `idle` → `talk` when `talk` is true; `talk` → `idle` when `talk` is false.
- **Point:** `idle` → `point` when `point` is true; `point` → `idle` when `point` is false.
- **One-shots:** `idle` → `encourage` / `celebrateSmall` / `celebrate` / `celebrateBig` on their trigger. Each goes back to `idle` with **Exit time 100%**.
- **Any State → `idle`** on trigger `idle`.
- **Transition duration:** 250 ms on every transition, so nothing snaps.

## 7. Check and export

1. Press **Play** on the state machine and toggle each input in the panel. Look for gaps at the joints and for anything that moves too much.
2. **Export → Download → Runtime (.riv)** → save as `art/character_boy.riv`.
3. Tell Claude. Claude copies the file to `android/app/src/main/res/raw/character_boy.riv`, wires it up and runs it on your phone.

## 8. The girl

1. In Rive, right click the file → **Duplicate** → rename it `character_girl`.
2. Claude cuts the girl's parts into `art/parts/girl/` with the **same file names**.
3. Select each image → Inspector → **Replace image** with the girl's file of the same name. Bones, meshes, animations and the state machine all stay.
4. Fix the hair: the girl has a ponytail. Add `ponytail` as an extra image bound to `head`, with a small swing in `idle`.
5. Export as `art/character_girl.riv`.
