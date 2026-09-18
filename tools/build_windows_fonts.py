#!/usr/bin/env python3
"""Cut the static font faces the Windows app needs from the variable fonts in fonts/ (ADR 0008).

WPF can't choose a variable font's weight or width, so each face a theme uses (its heading, body,
strong body and number styles in contracts/design/themes.json) becomes one static .ttf with its own
family name, which the app asks for by name:

    "GoalMaker <Family> <weight>[ Wide][ Italic]"   for example "GoalMaker Archivo 900 Wide Italic"

The app computes the same name (GoalMaker.App Theming), so no list is kept in two places. Output
goes to windows/src/GoalMaker.App/Assets/Fonts and is committed; rerun after changing fonts or
themes. Needs fontTools (python -m pip install fonttools). Timestamps are kept, so reruns are
byte-identical.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parents[1]
THEMES = ROOT / "contracts" / "design" / "themes.json"
FONTS = ROOT / "fonts"
OUTPUT = ROOT / "windows" / "src" / "GoalMaker.App" / "Assets" / "Fonts"

# Family name in themes.json -> (upright source, italic source or None), relative to fonts/.
SOURCES = {
    "Archivo": ("archivo/Archivo[wdth,wght].ttf", "archivo/Archivo-Italic[wdth,wght].ttf"),
    "Plus Jakarta Sans": ("plusjakartasans/PlusJakartaSans[wght].ttf", None),
    "Space Grotesk": ("spacegrotesk/SpaceGrotesk[wght].ttf", None),
    "Outfit": ("outfit/Outfit[wght].ttf", None),
}
NORMAL_WIDTH = 100.0
WIDE_WIDTH = 112.5


def face_name(family: str, weight: int, width: float, italic: bool) -> str:
    if width not in (NORMAL_WIDTH, WIDE_WIDTH):
        raise ValueError(f"{family}: width {width} isn't normal (100) or wide (112.5)")
    return f"GoalMaker {family} {weight}" + (" Wide" if width == WIDE_WIDTH else "") + (" Italic" if italic else "")


def faces_needed() -> set[tuple[str, int, float, bool]]:
    data = json.loads(THEMES.read_text(encoding="utf-8"))
    faces = set()
    for theme in data["themes"].values():
        typography = theme["typography"]
        for role in ("heading", "number"):
            style = typography[role]
            faces.add((style["family"], style["weight"], float(style["width"]), bool(style["italic"])))
        body = typography["body"]
        for weight in (body["weight"], body["strongWeight"]):
            faces.add((body["family"], weight, float(body["width"]), False))
    return faces


def rename(font: TTFont, name: str) -> None:
    table = font["name"]
    for name_id in (16, 17, 21, 22, 25):
        table.removeNames(nameID=name_id)
    postscript = name.replace(" ", "") + "-Regular"
    for name_id, value in ((1, name), (2, "Regular"), (3, postscript), (4, name), (6, postscript)):
        table.setName(value, name_id, 3, 1, 0x409)
        table.setName(value, name_id, 1, 0, 0)


def build(family: str, weight: int, width: float, italic: bool) -> Path:
    upright, italic_source = SOURCES[family]
    source = italic_source if italic else upright
    if source is None:
        raise ValueError(f"{family} has no italic source")
    font = TTFont(FONTS / source, recalcTimestamp=False)
    axes = {axis.axisTag for axis in font["fvar"].axes}
    location = {"wght": weight}
    if "wdth" in axes:
        location["wdth"] = width
    elif width != NORMAL_WIDTH:
        raise ValueError(f"{family} has no width axis for width {width}")
    static = instancer.instantiateVariableFont(font, location, updateFontNames=False)
    name = face_name(family, weight, width, italic)
    rename(static, name)
    path = OUTPUT / (name.replace(" ", "-") + ".ttf")
    static.save(path)
    return path


def main() -> int:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    wanted = {OUTPUT / (face_name(*face).replace(" ", "-") + ".ttf") for face in faces_needed()}
    for stale in set(OUTPUT.glob("*.ttf")) - wanted:
        stale.unlink()
        print(f"Removed {stale.relative_to(ROOT)}")
    for face in sorted(faces_needed()):
        path = build(*face)
        print(f"Wrote {path.relative_to(ROOT)} ({path.stat().st_size // 1024} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
