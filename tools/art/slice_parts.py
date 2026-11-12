"""Cut a transparent parts sheet into one PNG per separate part.

Usage: python tools/art/slice_parts.py <sheet.png> <out_dir>
Parts are found as connected opaque regions (alpha > 16) on a 1/4-scale mask,
then cropped from the full-size sheet with a small padding. Tiny specks are ignored.
Output files are numbered left-to-right, top-to-bottom: part_01.png, part_02.png, ...
A contact sheet `_index.png` shows each number on its part.
"""

import sys
from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw

SCALE = 4
MIN_AREA = 40  # in mask pixels
PAD = 6


def components(mask, w, h):
    seen = bytearray(w * h)
    boxes = []
    for start in range(w * h):
        if not mask[start] or seen[start]:
            continue
        queue = deque([start])
        seen[start] = 1
        x0 = y0 = 10**9
        x1 = y1 = -1
        area = 0
        while queue:
            p = queue.popleft()
            x, y = p % w, p // w
            area += 1
            x0, y0, x1, y1 = min(x0, x), min(y0, y), max(x1, x), max(y1, y)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h:
                    q = ny * w + nx
                    if mask[q] and not seen[q]:
                        seen[q] = 1
                        queue.append(q)
        if area >= MIN_AREA:
            boxes.append((x0, y0, x1, y1))
    return boxes


def main(sheet_path: str, out_dir: str) -> int:
    sheet = Image.open(sheet_path).convert("RGBA")
    small = sheet.getchannel("A").resize((sheet.width // SCALE, sheet.height // SCALE))
    w, h = small.size
    mask = bytearray(1 if v > 16 else 0 for v in small.getdata())
    boxes = components(mask, w, h)
    # Reading order: rows by top edge (bucketed), then left to right.
    boxes.sort(key=lambda b: (b[1] // 40, b[0]))
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    index = Image.new("RGBA", sheet.size, (246, 244, 238, 255))
    index.alpha_composite(sheet)
    draw = ImageDraw.Draw(index)
    for n, (x0, y0, x1, y1) in enumerate(boxes, start=1):
        box = (
            max(0, x0 * SCALE - PAD),
            max(0, y0 * SCALE - PAD),
            min(sheet.width, (x1 + 1) * SCALE + PAD),
            min(sheet.height, (y1 + 1) * SCALE + PAD),
        )
        sheet.crop(box).save(out / f"part_{n:02d}.png")
        draw.rectangle(box, outline=(46, 58, 64, 255), width=2)
        draw.text((box[0] + 4, box[1] + 2), str(n), fill=(46, 58, 64, 255))
    index.convert("RGB").save(out / "_index.png")
    print(f"{len(boxes)} parts -> {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
