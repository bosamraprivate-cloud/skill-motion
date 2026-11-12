"""Find where each rig part sits on a full-body reference pose.

Usage: python tools/art/place_parts.py <reference.png> <parts_dir> <out.json>
Coarse multi-scale template matching on a 1/4-size image, then a fine search
(full size, +-6% scale, small window) around the best coarse hit.
Output per part: centre x/y in reference pixels, scale, and match score.
"""

import json
import sys
from pathlib import Path

import cv2
import numpy as np

COARSE = 0.25


def prep(img):
    bgr = img[:, :, :3].copy()
    bgr[img[:, :, 3] < 20] = 0
    return bgr


def match(ref_bgr, part, scale):
    q = cv2.resize(part, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
    if q.shape[0] >= ref_bgr.shape[0] or q.shape[1] >= ref_bgr.shape[1] or min(q.shape[:2]) < 6:
        return None
    mask = (q[:, :, 3] > 40).astype(np.uint8) * 255
    if mask.sum() == 0:
        return None
    res = cv2.matchTemplate(ref_bgr, q[:, :, :3], cv2.TM_CCORR_NORMED, mask=mask)
    res = np.nan_to_num(res, nan=0, posinf=0, neginf=0)
    _, val, _, loc = cv2.minMaxLoc(res)
    return val, loc, q.shape[:2]


def main(ref_path, parts_dir, out_path):
    ref = cv2.imread(ref_path, cv2.IMREAD_UNCHANGED)
    ref_bgr = prep(ref)
    small_ref = cv2.resize(ref_bgr, None, fx=COARSE, fy=COARSE, interpolation=cv2.INTER_AREA)
    result = {}
    for f in sorted(Path(parts_dir).glob("*.png")):
        if f.stem == "parts-sheet":
            continue
        part = cv2.imread(str(f), cv2.IMREAD_UNCHANGED)
        best = None
        for s in np.arange(0.6, 1.45, 0.05):
            m = match(small_ref, part, s * COARSE)
            if m and (best is None or m[0] > best[0]):
                best = (m[0], s, m[1])
        if best is None:
            print(f.stem, "no match", flush=True)
            continue
        _, s0, (cx, cy) = best
        fine = None
        for s in np.arange(s0 - 0.06, s0 + 0.061, 0.015):
            h, w = int(part.shape[0] * s), int(part.shape[1] * s)
            x0 = max(0, int(cx / COARSE) - 24)
            y0 = max(0, int(cy / COARSE) - 24)
            x1 = min(ref_bgr.shape[1], x0 + w + 48)
            y1 = min(ref_bgr.shape[0], y0 + h + 48)
            m = match(ref_bgr[y0:y1, x0:x1], part, s)
            if m and (fine is None or m[0] > fine[0]):
                fine = (m[0], s, (m[1][0] + x0, m[1][1] + y0), m[2])
        val, s, (x, y), (h, w) = fine
        result[f.stem] = {"score": round(float(val), 3), "scale": round(float(s), 3),
                          "cx": round(x + w / 2, 1), "cy": round(y + h / 2, 1)}
        print(f.stem, result[f.stem], flush=True)
    Path(out_path).write_text(json.dumps(result, indent=1))


if __name__ == "__main__":
    main(*sys.argv[1:4])
