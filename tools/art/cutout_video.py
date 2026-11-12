"""Remove the background from a character animation clip and export a transparent animated WebP.

Usage: python tools/art/cutout_video.py <in.mp4> <out.webp> [--size 540] [--fps 24]

Each frame is matted with rembg (BiRefNet), lightly cleaned (alpha floor/ceiling,
largest component kept), scaled so the longest side is --size, and written as a
looping animated WebP with alpha, which Android plays natively (API 28+).
"""

import argparse
import subprocess
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image
from rembg import new_session, remove


def clean(rgba: Image.Image) -> Image.Image:
    arr = np.array(rgba)
    a = arr[:, :, 3].astype(np.float32)
    a = np.clip((a - 12) * 255 / (243 - 12), 0, 255)  # remove faint haze, solidify the body
    arr[:, :, 3] = a.astype(np.uint8)
    return Image.fromarray(arr)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("dst")
    ap.add_argument("--size", type=int, default=540)
    ap.add_argument("--fps", type=int, default=24)
    ap.add_argument("--model", default="birefnet-general")
    args = ap.parse_args()

    session = new_session(args.model)
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", args.src, f"{tmp}/f_%04d.png"], check=True)
        frames = []
        for f in sorted(Path(tmp).glob("f_*.png")):
            cut = clean(remove(Image.open(f).convert("RGB"), session=session))
            scale = args.size / max(cut.size)
            frames.append(cut.resize((round(cut.width * scale), round(cut.height * scale)), Image.LANCZOS))
            print(f.name, flush=True)
    frames[0].save(
        args.dst, save_all=True, append_images=frames[1:], duration=round(1000 / args.fps),
        loop=0, lossless=False, quality=88, method=6, exact=False,
    )
    print(args.dst, Path(args.dst).stat().st_size // 1024, "KB,", len(frames), "frames")


if __name__ == "__main__":
    main()
