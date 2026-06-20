#!/usr/bin/env python3
"""Assemble a Windows .ico from PNG files (PNG-compressed entries, Vista+).

Usage: make_ico.py out.ico 16.png 24.png 32.png 48.png 64.png 128.png 256.png
Stdlib only — no Pillow needed.
"""
import struct
import sys


def main() -> None:
    out, pngs = sys.argv[1], sys.argv[2:]
    images = []
    for path in pngs:
        with open(path, "rb") as fh:
            data = fh.read()
        # PNG IHDR width/height live at byte offset 16..24.
        width, height = struct.unpack(">II", data[16:24])
        images.append((width, height, data))

    header = struct.pack("<HHH", 0, 1, len(images))  # reserved, type=icon, count
    offset = 6 + 16 * len(images)
    entries, blobs = b"", b""
    for width, height, data in images:
        entries += struct.pack(
            "<BBBBHHII",
            width & 0xFF,   # 0 means 256
            height & 0xFF,
            0,              # palette colors
            0,              # reserved
            1,              # color planes
            32,             # bits per pixel
            len(data),
            offset,
        )
        blobs += data
        offset += len(data)

    with open(out, "wb") as fh:
        fh.write(header + entries + blobs)
    print(f"wrote {out} ({len(images)} sizes)")


if __name__ == "__main__":
    main()
