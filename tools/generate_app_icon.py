#!/usr/bin/env python3
"""Draw the GoalMaker mark (docs/design/questionnaire.md, D14) and write every icon made from it.

The mark is a G whose middle turns into a small trend line that zigzags up and leaves through the
G's opening as an arrow ("Chart", chosen on the 2026-09-18 design board). It is defined once below,
on a 100-unit square, and this script writes:

- windows/src/GoalMaker.App/Assets/GoalMaker.ico (16 to 256 px, rendered here)
- android/app/src/main/res/drawable/ic_launcher_foreground.xml (the adaptive icon's vector layer)
- docs/design/brand/goalmaker-icon.svg (the app tile, for docs and store listings)
- contracts/design/logo.json (the mark as path data, which both apps draw in each theme's logo
  colors from themes.json and recolor when the theme changes)

The icon files use the Track theme's colors (the default): a black tile, a white G, a volt arrow.
Pure Python (math, zlib, struct, json), so it runs anywhere: python tools/generate_app_icon.py
"""

from __future__ import annotations

import json
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ICO = ROOT / "windows" / "src" / "GoalMaker.App" / "Assets" / "GoalMaker.ico"
ANDROID_FOREGROUND = ROOT / "android" / "app" / "src" / "main" / "res" / "drawable" / "ic_launcher_foreground.xml"
SVG = ROOT / "docs" / "design" / "brand" / "goalmaker-icon.svg"
LOGO = ROOT / "contracts" / "design" / "logo.json"

TILE = "#0A0A0A"
LETTER = "#FFFFFF"
ARROW = "#D6FF3A"

# The mark on a 100-unit square, before centering. Angles are SVG angles (y points down); the G's
# arc runs the long way from ARC_START through the top, left and bottom to ARC_END, leaving the
# opening on the right for the arrow.
CENTER = (50.0, 50.0)
RADIUS = 30.0
STROKE = 11.0
ARC_START = -58.0
ARC_END = 15.0
TREND = ((38.0, 58.0), (50.0, 47.0), (58.0, 55.0), (92.0, 22.0))
HEAD = 11.0

ICO_SIZES = (16, 24, 32, 48, 64, 128, 256)
TILE_CORNER = 0.22
SUPERSAMPLE = 4


def point_on_circle(degrees: float) -> tuple[float, float]:
    radians = math.radians(degrees)
    return (CENTER[0] + RADIUS * math.cos(radians), CENTER[1] + RADIUS * math.sin(radians))


def arrow_head() -> tuple[tuple[float, float], list[tuple[float, float]]]:
    """Where the trend line stops, and the head's triangle (tip first)."""
    (x1, y1), (x2, y2) = TREND[-2], TREND[-1]
    length = math.hypot(x2 - x1, y2 - y1)
    ux, uy = (x2 - x1) / length, (y2 - y1) / length
    px, py = -uy, ux
    bx, by = x2 - ux * HEAD, y2 - uy * HEAD
    half = HEAD * 0.8
    triangle = [(x2 + ux * 2, y2 + uy * 2), (bx + px * half, by + py * half), (bx - px * half, by - py * half)]
    return (bx, by), triangle


def in_arc(degrees: float) -> bool:
    return not ARC_START < degrees < ARC_END


def centering_offset() -> tuple[float, float]:
    """Moves the mark so its drawn extent is centered on the 100-unit square."""
    half = STROKE / 2
    xs: list[float] = []
    ys: list[float] = []
    for step in range(0, 360):
        degrees = step - 180
        if in_arc(degrees):
            x, y = point_on_circle(degrees)
            xs += [x - half, x + half]
            ys += [y - half, y + half]
    base, triangle = arrow_head()
    for x, y in [*TREND[:-1], base]:
        xs += [x - half, x + half]
        ys += [y - half, y + half]
    for x, y in triangle:
        xs.append(x)
        ys.append(y)
    return (50 - (min(xs) + max(xs)) / 2, 50 - (min(ys) + max(ys)) / 2)


OFFSET = centering_offset()


def shifted(point: tuple[float, float]) -> tuple[float, float]:
    return (point[0] + OFFSET[0], point[1] + OFFSET[1])


def trend_points() -> list[tuple[float, float]]:
    base, _ = arrow_head()
    return [shifted(p) for p in (*TREND[:-1], base)]


def head_points() -> list[tuple[float, float]]:
    _, triangle = arrow_head()
    return [shifted(p) for p in triangle]


# Raster rendering ----------------------------------------------------------------------------------


def segment_distance(px: float, py: float, a: tuple[float, float], b: tuple[float, float]) -> float:
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def inside_triangle(x: float, y: float, triangle: list[tuple[float, float]]) -> bool:
    (x1, y1), (x2, y2), (x3, y3) = triangle
    d1 = (x - x2) * (y1 - y2) - (x1 - x2) * (y - y2)
    d2 = (x - x3) * (y2 - y3) - (x2 - x3) * (y - y3)
    d3 = (x - x1) * (y3 - y1) - (x3 - x1) * (y - y1)
    return not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0))


TREND_SHIFTED = trend_points()
HEAD_SHIFTED = head_points()
ARC_ENDS = (shifted(point_on_circle(ARC_START)), shifted(point_on_circle(ARC_END)))
CENTER_SHIFTED = shifted(CENTER)


def mark_color(x: float, y: float) -> str | None:
    """The mark's color at (x, y) on the 100-unit square, or None where it's empty."""
    half = STROKE / 2
    if inside_triangle(x, y, HEAD_SHIFTED):
        return ARROW
    if any(segment_distance(x, y, a, b) <= half for a, b in zip(TREND_SHIFTED, TREND_SHIFTED[1:])):
        return ARROW
    cx, cy = CENTER_SHIFTED
    degrees = math.degrees(math.atan2(y - cy, x - cx))
    if in_arc(degrees) and abs(math.hypot(x - cx, y - cy) - RADIUS) <= half:
        return LETTER
    if any(math.hypot(x - ex, y - ey) <= half for ex, ey in ARC_ENDS):
        return LETTER
    return None


def rgb(color: str) -> tuple[int, int, int]:
    return (int(color[1:3], 16), int(color[3:5], 16), int(color[5:7], 16))


def mark_scale(size: int) -> float:
    """How much of the tile the 100-unit square fills; tiny icons use more of it to stay legible."""
    if size <= 24:
        return 0.94
    if size <= 32:
        return 0.88
    return 0.84


def sample(x: float, y: float, scale: float) -> tuple[int, int, int, int]:
    cx = min(max(x, TILE_CORNER), 1 - TILE_CORNER)
    cy = min(max(y, TILE_CORNER), 1 - TILE_CORNER)
    if math.hypot(x - cx, y - cy) > TILE_CORNER:
        return (0, 0, 0, 0)
    color = mark_color(50 + (x - 0.5) * 100 / scale, 50 + (y - 0.5) * 100 / scale)
    return (*rgb(color or TILE), 255)


def render(size: int) -> bytes:
    rows = bytearray()
    grid = size * SUPERSAMPLE
    scale = mark_scale(size)
    for row in range(size):
        rows.append(0)  # PNG filter: none
        for column in range(size):
            total = [0, 0, 0, 0]
            for sy in range(SUPERSAMPLE):
                for sx in range(SUPERSAMPLE):
                    r, g, b, a = sample(
                        (column * SUPERSAMPLE + sx + 0.5) / grid,
                        (row * SUPERSAMPLE + sy + 0.5) / grid,
                        scale,
                    )
                    total[0] += r * a
                    total[1] += g * a
                    total[2] += b * a
                    total[3] += a
            alpha = total[3] // (SUPERSAMPLE * SUPERSAMPLE)
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


def write_ico() -> None:
    images = [render(size) for size in ICO_SIZES]
    directory = struct.pack("<HHH", 0, 1, len(images))
    offset = 6 + 16 * len(images)
    entries = b""
    for size, image in zip(ICO_SIZES, images):
        dimension = 0 if size == 256 else size
        entries += struct.pack("<BBBBHHII", dimension, dimension, 0, 0, 1, 32, len(image), offset)
        offset += len(image)
    ICO.parent.mkdir(parents=True, exist_ok=True)
    ICO.write_bytes(directory + entries + b"".join(images))


# Vector outputs -------------------------------------------------------------------------------------


def number(value: float) -> str:
    return f"{value:.2f}".rstrip("0").rstrip(".")


def paths(transform) -> tuple[str, str, str]:
    """The G, the trend line and the head as path data, through transform((x, y)) -> (x, y)."""
    start, end = (transform(p) for p in ARC_ENDS)
    radius = RADIUS * transform.scale
    letter = f"M{number(start[0])},{number(start[1])} A{number(radius)},{number(radius)} 0 1,0 {number(end[0])},{number(end[1])}"
    trend = " ".join(
        f"{'M' if index == 0 else 'L'}{number(x)},{number(y)}"
        for index, (x, y) in enumerate(transform(p) for p in TREND_SHIFTED)
    )
    head = "M" + " L".join(f"{number(x)},{number(y)}" for x, y in (transform(p) for p in HEAD_SHIFTED)) + " Z"
    return letter, trend, head


class Transform:
    """Maps the 100-unit square onto a target square: scale per unit, around a center point."""

    def __init__(self, scale: float, center: float):
        self.scale = scale
        self.center = center

    def __call__(self, point: tuple[float, float]) -> tuple[float, float]:
        return (self.center + (point[0] - 50) * self.scale, self.center + (point[1] - 50) * self.scale)


def write_android_foreground() -> None:
    # 108 dp adaptive layer; the mark's 100 units become 62 dp, inside the 66 dp safe zone.
    transform = Transform(0.62, 54)
    letter, trend, head = paths(transform)
    width = number(STROKE * transform.scale)
    ANDROID_FOREGROUND.write_text(
        f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/generate_app_icon.py: the GoalMaker mark (a G with a trend arrow). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="{letter}"
        android:strokeColor="@color/launcher_foreground"
        android:strokeWidth="{width}"
        android:strokeLineCap="round" />
    <path
        android:pathData="{trend}"
        android:strokeColor="@color/launcher_accent"
        android:strokeWidth="{width}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
    <path
        android:fillColor="@color/launcher_accent"
        android:pathData="{head}" />
</vector>
""",
        encoding="utf-8",
        newline="\n",
    )


def write_svg() -> None:
    transform = Transform(512 * 0.84 / 100, 256)
    letter, trend, head = paths(transform)
    width = number(STROKE * transform.scale)
    SVG.parent.mkdir(parents=True, exist_ok=True)
    SVG.write_text(
        f"""<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <title>GoalMaker</title>
  <!-- Generated by tools/generate_app_icon.py -->
  <rect width="512" height="512" rx="{number(512 * TILE_CORNER)}" fill="{TILE}"/>
  <path d="{letter}" fill="none" stroke="{LETTER}" stroke-width="{width}" stroke-linecap="round"/>
  <path d="{trend}" fill="none" stroke="{ARROW}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="{head}" fill="{ARROW}"/>
</svg>
""",
        encoding="utf-8",
        newline="\n",
    )


def write_logo() -> None:
    # The same tile as the SVG, on a 100-unit square: what the apps draw at any size.
    transform = Transform(0.84, 50)
    letter, trend, head = paths(transform)
    logo = {
        "schema": 1,
        "description": (
            "The GoalMaker mark on a 100-unit tile, generated by tools/generate_app_icon.py (do not edit). "
            "Draw a rounded square 'size' wide with corner 'tileCorner' in the theme's logo.tile, the G "
            "('letter', a stroked arc with round caps) in logo.letter, and the trend line ('trend', stroked "
            "with round caps and joins) and its head ('head', filled) in logo.arrow. Strokes are 'stroke' wide."
        ),
        "size": 100,
        "tileCorner": float(number(100 * TILE_CORNER)),
        "stroke": float(number(STROKE * transform.scale)),
        "letter": letter,
        "trend": trend,
        "head": head,
    }
    LOGO.write_text(json.dumps(logo, indent=2) + "\n", encoding="utf-8", newline="\n")


def main() -> int:
    write_ico()
    write_android_foreground()
    write_svg()
    write_logo()
    for path in (ICO, ANDROID_FOREGROUND, SVG, LOGO):
        print(f"Wrote {path.relative_to(ROOT)} ({path.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
