#!/usr/bin/env python3
"""Every control a screen reader meets has something to read (M6-05).

Windows: an interactive element in XAML is named by AutomationProperties.Name, by its Content or
Header, by a PlaceholderText, or by the text inside it. Android: an IconButton says what it does
through its icon's contentDescription, its own semantics, or the tooltip it sits in.

Run from anywhere: python tools/check_accessibility.py
"""

from __future__ import annotations

import glob
import io
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

XAML = re.compile(
    r"<(ui:Button|Button|ToggleButton|ui:ToggleSwitch|ui:TextBox|TextBox|ComboBox|CheckBox"
    r"|ui:HyperlinkButton|Slider)(?=[\s/>])"
)
NAMED = ("AutomationProperties.Name", "Content=", "Header=", "PlaceholderText=")


def element(text: str, start: int, kind: str) -> tuple[str, str]:
    """The opening tag, and the whole element as far as its closing tag."""
    end = text.find(">", start)
    tag = text[start : end + 1]
    if tag.rstrip().endswith("/>"):
        return tag, tag
    close = text.find("</" + kind + ">", end)
    return tag, text[start : close if close > 0 else end + 1]


def windows() -> list[str]:
    found = []
    for path in sorted(glob.glob(str(ROOT / "windows/src/**/*.xaml"), recursive=True)):
        text = io.open(path, encoding="utf-8").read()
        for match in XAML.finditer(text):
            tag, whole = element(text, match.start(), match.group(1))
            if any(name in tag for name in NAMED):
                continue
            body = whole[len(tag) :]
            # A control holding text already reads as that text.
            if "TextBlock" in body or "Text=" in body or "ItemTemplate" in body:
                continue
            line = text.count("\n", 0, match.start()) + 1
            found.append(f"{Path(path).relative_to(ROOT).as_posix()}:{line} {match.group(1)} has nothing to read")
    return found


def block(text: str, start: int) -> str:
    """An IconButton call with the lambda that follows it."""
    depth, index = 0, text.index("(", start)
    while index < len(text):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                break
        index += 1
    brace = text.find("{", index)
    if brace < 0:
        return text[start : index + 1]
    depth, end = 0, brace
    while end < len(text):
        if text[end] == "{":
            depth += 1
        elif text[end] == "}":
            depth -= 1
            if depth == 0:
                break
        end += 1
    return text[start : end + 1]


def android() -> list[str]:
    found = []
    for path in sorted(glob.glob(str(ROOT / "android/app/src/main/kotlin/**/*.kt"), recursive=True)):
        text = io.open(path, encoding="utf-8").read()
        for match in re.finditer(r"\bIconButton\(", text):
            whole = block(text, match.start())
            named = "contentDescription" in whole and not re.search(r"contentDescription\s*=\s*null", whole)
            if named or "semantics" in whole:
                continue
            # A button inside a tooltip is named by the tooltip.
            if "TooltipBox" in text[max(0, match.start() - 400) : match.start()]:
                continue
            line = text.count("\n", 0, match.start()) + 1
            found.append(f"{Path(path).relative_to(ROOT).as_posix()}:{line} IconButton has nothing to read")
    return found


def main() -> int:
    problems = windows() + android()
    for problem in problems:
        print(problem)
    if problems:
        print(f"{len(problems)} control(s) a screen reader cannot name")
        return 1
    print("every control has something to read")
    return 0


if __name__ == "__main__":
    sys.exit(main())
