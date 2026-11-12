"""Decode a base64 image dump (from the browser) into a PNG file.

Usage: python tools/art/save_b64.py <dump.json> <out.png>
Prints the size and whether the PNG has an alpha channel.
"""

import base64
import re
import struct
import sys
from pathlib import Path


def main(dump: str, out: str) -> int:
    text = Path(dump).read_text(encoding="utf-8")
    match = re.search(r"[A-Za-z0-9+/=]{1000,}", text)
    if not match:
        print("no image data:", text[:200])
        return 1
    data = base64.b64decode(match.group(0))
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_bytes(data)
    Path(dump).unlink()
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        width, height, _, color_type = struct.unpack(">IIBB", data[16:26])
        alpha = color_type in (4, 6)
        print(f"{out}: {width}x{height} alpha={alpha}")
    else:
        print(f"{out}: not a PNG ({len(data)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
