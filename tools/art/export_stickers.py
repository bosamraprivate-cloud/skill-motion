"""Export art/stickers/sticker.NN.png to app assets as 384 px WebP with the white outside made transparent."""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "art" / "stickers"
OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "stickers"
SIZE = 384


def export(src: Path, out: Path) -> None:
    im = Image.open(src).convert("RGBA")
    # The white page around the round sticker becomes transparent (flood fill from the corners).
    mask = Image.new("L", im.size, 0)
    rgb = im.convert("RGB")
    for corner in [(0, 0), (im.width - 1, 0), (0, im.height - 1), (im.width - 1, im.height - 1)]:
        ImageDraw.floodfill(rgb, corner, (255, 0, 255), thresh=18)
    px = rgb.load()
    mpx = mask.load()
    for y in range(im.height):
        for x in range(im.width):
            mpx[x, y] = 0 if px[x, y] == (255, 0, 255) else 255
    im.putalpha(mask)
    box = im.getbbox() or (0, 0, im.width, im.height)
    im = im.crop(box)
    side = max(im.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(im, ((side - im.width) // 2, (side - im.height) // 2))
    square.resize((SIZE, SIZE), Image.LANCZOS).save(out, "WEBP", quality=88, method=6)


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    files = sorted(SRC.glob("sticker.*.png"))
    for f in files:
        export(f, OUT / (f.stem + ".webp"))
    print(f"exported {len(files)} stickers to {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
