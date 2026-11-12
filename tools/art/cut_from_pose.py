"""Cut a full-body pose image into rig layers with polygon masks.

Usage: python tools/art/cut_from_pose.py <pose.png> <out_dir>
Layers are full-canvas PNGs (same size as the pose), so they line up exactly
when stacked at the same position. Pixels removed from the body for the arms
and head are filled by inpainting, so no hole shows when a limb moves.
Polygons are in pose pixel coordinates (1024x1536 boy idle pose).
"""

import sys
from pathlib import Path

import cv2
import numpy as np

# Character's right arm = viewer's left.
UPPERARM_R = [(372, 445), (330, 515), (300, 640), (290, 705), (380, 705), (398, 610), (408, 530), (400, 470)]
FOREARM_R = [(292, 660), (280, 760), (282, 860), (270, 900), (262, 1010), (372, 1010), (372, 860), (380, 760), (388, 660)]
UPPERARM_L = [(648, 445), (692, 515), (724, 640), (734, 705), (642, 705), (624, 610), (614, 530), (620, 470)]
FOREARM_L = [(732, 660), (745, 760), (760, 860), (765, 900), (772, 1010), (668, 1010), (656, 860), (642, 760), (634, 660)]
OVERLAP = 7  # px of original pixels the body keeps under each cut edge
HEAD = [(0, 0), (1024, 0), (1024, 395), (600, 395), (565, 412), (560, 470), (462, 470), (455, 412), (420, 395), (0, 395)]


def poly_mask(shape, pts, feather=0):
    m = np.zeros(shape[:2], np.uint8)
    cv2.fillPoly(m, [np.array(pts, np.int32)], 255)
    if feather:
        m = cv2.GaussianBlur(m, (feather * 2 + 1, feather * 2 + 1), 0)
    return m


def layer(img, mask):
    out = img.copy()
    out[:, :, 3] = (img[:, :, 3].astype(np.float32) * (mask.astype(np.float32) / 255)).astype(np.uint8)
    return out


def main(pose_path, out_dir):
    img = cv2.imread(pose_path, cv2.IMREAD_UNCHANGED)
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    masks = {
        "upperarm_r": poly_mask(img.shape, UPPERARM_R),
        "forearm_r": poly_mask(img.shape, FOREARM_R),
        "upperarm_l": poly_mask(img.shape, UPPERARM_L),
        "forearm_l": poly_mask(img.shape, FOREARM_L),
        "head": poly_mask(img.shape, HEAD),
    }
    for name, m in masks.items():
        cv2.imwrite(str(out / f"{name}.png"), layer(img, m))
    # Body = everything else; fill the regions under arms with inpainted cloth so
    # raising an arm shows hoodie, not a hole.
    taken = np.zeros(img.shape[:2], np.uint8)
    for name in ("upperarm_r", "forearm_r", "upperarm_l", "forearm_l"):
        taken = np.maximum(taken, masks[name])
    # Remove limbs/head from the body only inside an eroded mask, so the body keeps a
    # band of the original pixels under every cut edge and no seam shows at rest.
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * OVERLAP + 1, 2 * OVERLAP + 1))
    taken = cv2.erode(taken, kernel)
    head_cut = cv2.erode(masks["head"], kernel)
    body_alpha = img[:, :, 3].copy()
    body_alpha[head_cut > 128] = 0
    # Inside the torso silhouette (x between the arm polygons' inner edges) keep the cloth filled.
    torso_fill = poly_mask(img.shape, [(350, 455), (670, 455), (684, 520), (628, 620), (640, 860), (380, 860), (392, 620), (336, 520)])
    hole = ((taken > 128) & (torso_fill > 0)).astype(np.uint8) * 255
    rgb = img[:, :, :3].copy()
    rgb = cv2.inpaint(rgb, hole, 9, cv2.INPAINT_TELEA)
    body_alpha[(taken > 128) & (torso_fill == 0)] = 0
    body_alpha[hole > 0] = 255
    body = np.dstack([rgb, body_alpha])
    cv2.imwrite(str(out / "body.png"), body)
    print("layers:", sorted(p.name for p in out.glob("*.png")))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
