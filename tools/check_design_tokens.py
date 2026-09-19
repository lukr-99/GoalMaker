#!/usr/bin/env python3
"""Check contracts/design/themes.json: every theme complete, every pair readable (WCAG 2.2 AA).

Text needs 4.5:1 against what it sits on; large text (the big numbers) and controls that must be
seen (checkbox borders, filled checks, buttons, the check mark) need 3:1. The pure-black option is
checked as the dark palette with the theme's 'black' surfaces. Run in CI (codeprint.yml):

    python tools/check_design_tokens.py
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOKENS = ROOT / "contracts" / "design" / "themes.json"
HEX = re.compile(r"#[0-9A-F]{6}")
BLACK_ROLES = {"background", "surface", "surfaceVariant"}
TYPE_ROLES = ("heading", "body", "number")
SHAPES = ("card", "row", "checkbox", "button")

# (foreground role, background role, minimum ratio, what it is)
PAIRS = [
    ("text", "background", 4.5, "body text on the page"),
    ("text", "surface", 4.5, "body text on cards and rows"),
    ("text", "surfaceVariant", 4.5, "typed text in the composer"),
    ("textMuted", "background", 4.5, "secondary text on the page"),
    ("textMuted", "surface", 4.5, "secondary text on cards and rows"),
    ("textMuted", "surfaceVariant", 4.5, "the composer's placeholder"),
    ("onPrimary", "primary", 4.5, "button labels"),
    ("onHero", "hero", 4.5, "text on big-number cards"),
    ("heroAccent", "hero", 3.0, "the big number (large text) and its bar"),
    ("danger", "background", 4.5, "errors on the page"),
    ("danger", "surface", 4.5, "errors on cards"),
    ("outline", "surface", 3.0, "unchecked boxes on rows"),
    ("outline", "background", 3.0, "input borders on the page"),
    ("accent", "surface", 3.0, "checked boxes and progress on rows"),
    ("accent", "background", 3.0, "rings and progress on the page"),
    ("onAccent", "accent", 3.0, "the check mark"),
    ("primary", "background", 3.0, "buttons on the page"),
    ("primary", "surfaceVariant", 3.0, "the send button in the composer"),
]


def luminance(color: str) -> float:
    def channel(value: int) -> float:
        c = value / 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b = (int(color[i:i + 2], 16) for i in (1, 3, 5))
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast(first: str, second: str) -> float:
    a, b = sorted((luminance(first), luminance(second)), reverse=True)
    return (a + 0.05) / (b + 0.05)


def check(data: dict) -> list[str]:
    problems: list[str] = []
    roles = set(data["roles"])
    themes = data["themes"]
    if data.get("defaultTheme") not in themes:
        problems.append(f"defaultTheme '{data.get('defaultTheme')}' is not a theme")

    for key, theme in themes.items():
        for field in ("name", "summary"):
            if not theme.get(field):
                problems.append(f"{key}: missing {field}")
        for role in TYPE_ROLES:
            style = theme.get("typography", {}).get(role)
            if not style or not style.get("family") or not isinstance(style.get("weight"), int):
                problems.append(f"{key}: typography.{role} needs a family and an integer weight")
        for shape in SHAPES:
            if not isinstance(theme.get("shape", {}).get(shape), int):
                problems.append(f"{key}: shape.{shape} must be an integer")

        palettes = {}
        for mode in ("light", "dark"):
            palette = theme.get(mode, {})
            missing, extra = roles - set(palette), set(palette) - roles
            if missing:
                problems.append(f"{key}.{mode}: missing {', '.join(sorted(missing))}")
            if extra:
                problems.append(f"{key}.{mode}: unknown roles {', '.join(sorted(extra))}")
            palettes[mode] = palette
        black = theme.get("black", {})
        if set(black) != BLACK_ROLES:
            problems.append(f"{key}.black: must override exactly {', '.join(sorted(BLACK_ROLES))}")
        palettes["black"] = {**palettes["dark"], **black}

        for mode, palette in palettes.items():
            for role, color in palette.items():
                if not HEX.fullmatch(color):
                    problems.append(f"{key}.{mode}.{role}: '{color}' is not #RRGGBB (uppercase)")
            for fore, back, minimum, what in PAIRS:
                if fore in palette and back in palette:
                    ratio = contrast(palette[fore], palette[back])
                    if ratio < minimum:
                        problems.append(
                            f"{key}.{mode}: {fore} on {back} is {ratio:.2f}:1, needs {minimum}:1 ({what})"
                        )

    for color in data["areaPalette"]["colors"]:
        if not HEX.fullmatch(color["swatch"]):
            problems.append(f"area {color['id']}: swatch '{color['swatch']}' is not #RRGGBB")
        for mode in ("light", "dark"):
            pair = color[mode]
            ratio = contrast(pair["content"], pair["container"])
            if ratio < 4.5:
                problems.append(f"area {color['id']}.{mode}: chip text is {ratio:.2f}:1, needs 4.5:1")
    ids = [color["id"] for color in data["areaPalette"]["colors"]]
    if len(ids) < 12 or len(set(ids)) != len(ids):
        problems.append("areaPalette: needs at least 12 colors, each with its own id")
    return problems


def main() -> int:
    data = json.loads(TOKENS.read_text(encoding="utf-8"))
    problems = check(data)
    if problems:
        print(f"{TOKENS.relative_to(ROOT)}: {len(problems)} problem(s)")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print(f"{TOKENS.relative_to(ROOT)}: {len(data['themes'])} themes x light, dark, black pass WCAG AA")
    return 0


if __name__ == "__main__":
    sys.exit(main())
