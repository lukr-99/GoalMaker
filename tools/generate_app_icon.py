#!/usr/bin/env python3
"""Render the placeholder GoalMaker icon (a target with a check) to a multi-size Windows .ico.

Pure Python (zlib + struct), so it runs anywhere. The mark matches the Android launcher vector
(android/app/src/main/res/drawable/ic_launcher_foreground.xml) until the design questionnaire
replaces both.
"""

from __future__ import annotations

import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "windows" / "src" / "GoalMaker.App" / "Assets" / "GoalMaker.ico"
SIZES = (16, 24, 32, 48, 64, 128, 256)
BACKGROUND = (0x5B, 0x2E, 0xE6)
FOREGROUND = (0xFF, 0xFF, 0xFF)
SUPERSAMPLE = 4
CHECK = ((0.40, 0.51), (0.47, 0.58), (0.61, 0.43))


def segment_distance(px: float, py: float, a: tuple[float, float], b: tuple[float, float]) -> float:
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def inside_rounded_square(x: float, y: float, radius: float = 0.22) -> bool:
    cx = min(max(x, radius), 1 - radius)
    cy = min(max(y, radius), 1 - radius)
    return math.hypot(x - cx, y - cy) <= radius


def sample(x: float, y: float) -> tuple[int, int, int, int]:
    if not inside_rounded_square(x, y):
        return (0, 0, 0, 0)
    distance = math.hypot(x - 0.5, y - 0.5)
    ring = 0.23 <= distance <= 0.30
    check = min(segment_distance(x, y, CHECK[0], CHECK[1]), segment_distance(x, y, CHECK[1], CHECK[2])) <= 0.035
    color = FOREGROUND if ring or check else BACKGROUND
    return (*color, 255)


def render(size: int) -> bytes:
    rows = bytearray()
    grid = size * SUPERSAMPLE
    for row in range(size):
        rows.append(0)  # PNG filter: none
        for column in range(size):
            total = [0, 0, 0, 0]
            for sy in range(SUPERSAMPLE):
                for sx in range(SUPERSAMPLE):
                    r, g, b, a = sample(
                        (column * SUPERSAMPLE + sx + 0.5) / grid,
                        (row * SUPERSAMPLE + sy + 0.5) / grid,
                    )
                    total[0] += r * a
                    total[1] += g * a
                    total[2] += b * a
                    total[3] += a
            count = SUPERSAMPLE * SUPERSAMPLE
            alpha = total[3] // count
            if total[3]:
                rows.extend((total[0] // total[3], total[1] // total[3], total[2] // total[3], alpha))
            else:
                rows.extend((0, 0, 0, 0))
    return png(size, bytes(rows))


def png(size: int, raw: bytes) -> bytes:
    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def main() -> int:
    images = [render(size) for size in SIZES]
    directory = struct.pack("<HHH", 0, 1, len(images))
    offset = 6 + 16 * len(images)
    entries = b""
    for size, image in zip(SIZES, images):
        dimension = 0 if size == 256 else size
        entries += struct.pack("<BBBBHHII", dimension, dimension, 0, 0, 1, 32, len(image), offset)
        offset += len(image)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(directory + entries + b"".join(images))
    print(f"Wrote {OUTPUT} ({OUTPUT.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
