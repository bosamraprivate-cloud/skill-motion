"""Remove the background of a generated character clip with Robust Video Matting (GPU).

RVM keeps a temporal state across frames, so the alpha edge does not flicker the way
per-frame matting does. Output: looping transparent animated WebP for the app.

Usage:
    python tools/art/cutout_video.py IN.mp4 OUT.webp [--size 960] [--speed 1.25] [--quality 85]
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import tempfile
from pathlib import Path

import numpy as np
import torch
from PIL import Image
from torchvision.io import read_video


def matte(src: Path, frames_dir: Path, size: int) -> float:
    video, _, info = read_video(str(src), pts_unit="sec", output_format="TCHW")
    fps = float(info["video_fps"])
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = torch.hub.load("PeterL1n/RobustVideoMatting", "mobilenetv3", trust_repo=True)
    model = model.eval().to(device)
    h, w = video.shape[2:]
    scale = size / max(h, w)
    out_hw = (round(h * scale) // 2 * 2, round(w * scale) // 2 * 2)
    rec = [None] * 4
    with torch.no_grad():
        for i, frame in enumerate(video):
            x = frame.unsqueeze(0).float().div(255).to(device)
            x = torch.nn.functional.interpolate(x, size=out_hw, mode="bilinear", antialias=True)
            fgr, pha, *rec = model(x, *rec, downsample_ratio=0.4)
            rgba = torch.cat([fgr, pha], 1)[0].clamp(0, 1).mul(255).byte().permute(1, 2, 0)
            Image.fromarray(np.ascontiguousarray(rgba.cpu().numpy()), "RGBA").save(
                frames_dir / f"f_{i:04d}.png"
            )
    return fps


def encode(frames_dir: Path, out: Path, fps: float, quality: int) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-framerate", f"{fps:.3f}",
         "-i", str(frames_dir / "f_%04d.png"), "-c:v", "libwebp_anim", "-lossless", "0",
         "-q:v", str(quality), "-compression_level", "4", "-loop", "0",
         "-pix_fmt", "yuva420p", str(out)],
        check=True,
    )


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("src", type=Path)
    p.add_argument("out", type=Path)
    p.add_argument("--size", type=int, default=960)
    p.add_argument("--speed", type=float, default=1.25)
    p.add_argument("--quality", type=int, default=85)
    a = p.parse_args()
    tmp = Path(tempfile.mkdtemp(prefix="matte_"))
    try:
        fps = matte(a.src, tmp, a.size)
        encode(tmp, a.out, fps * a.speed, a.quality)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print(f"{a.out} ok")


if __name__ == "__main__":
    main()
