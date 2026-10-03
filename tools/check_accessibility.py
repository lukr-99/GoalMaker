#!/usr/bin/env python3
"""Every control a screen reader meets has something to read (M6-05).

Windows: an interactive element in XAML is named by AutomationProperties.Name, by its Content or
Header, by a PlaceholderText, or by the text inside it. Android: an IconButton says what it does
through its icon's contentDescription, its own semantics, or the tooltip it sits in.

And every Android control can be reached: a Scaffold's bottomBar slot adds no window insets, so a
bar built from a Row or a Column has to keep clear of the system navigation bar itself. Material's
NavigationBar and BottomAppBar already do.

And Tab on Windows reads the way the screen does: WPF's Tab follows the order controls are written
in, and a DockPanel draws a child docked Right or Bottom away from where it is written, so such a
child that does something, written before another one that does, makes Tab jump. The panel then
sets KeyboardNavigation.TabNavigation="Local" and gives the controls TabIndex in reading order.

Run from anywhere: python tools/check_accessibility.py
"""

from __future__ import annotations

import glob
import io
import re
import sys
import xml.etree.ElementTree as ElementTree
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


# What keeps a bottom bar above the system navigation bar.
CLEAR = ("navigationBarsPadding", "systemBarsPadding", "safeDrawing", "windowInsetsPadding", "NavigationBar(", "BottomAppBar(")


def lambda_body(text: str, brace: int) -> str:
    """The lambda that opens at [brace], braces and all."""
    depth, end = 0, brace
    while end < len(text):
        if text[end] == "{":
            depth += 1
        elif text[end] == "}":
            depth -= 1
            if depth == 0:
                break
        end += 1
    return text[brace : end + 1]


def bottom_bars() -> list[str]:
    found = []
    for path in sorted(glob.glob(str(ROOT / "android/app/src/main/kotlin/**/*.kt"), recursive=True)):
        text = io.open(path, encoding="utf-8").read()
        for match in re.finditer(r"\bbottomBar\s*=\s*\{", text):
            body = lambda_body(text, match.end() - 1)
            if any(clear in body for clear in CLEAR):
                continue
            line = text.count("\n", 0, match.start()) + 1
            found.append(f"{Path(path).relative_to(ROOT).as_posix()}:{line} bottomBar can sit under the navigation bar")
    return found


# What Tab stops on, by element name (ui:Button and Button alike).
TAB_STOP = re.compile(
    r"(Button|ToggleButton|ToggleSwitch|TextBox|ComboBox|CheckBox|RadioButton|HyperlinkButton|Slider"
    r"|DatePicker|ListBox|NumberBox|AutoSuggestBox)$"
)


def name(element: ElementTree.Element) -> str:
    """An element's own name, without its namespace."""
    return element.tag.rsplit("}", 1)[-1]


def attribute(element: ElementTree.Element, suffix: str) -> str | None:
    """The value of the attribute whose name ends with [suffix] (attached properties come namespaced)."""
    return next((value for key, value in element.attrib.items() if key.rsplit("}", 1)[-1] == suffix), None)


def stops_tab(element: ElementTree.Element) -> bool:
    """Whether Tab stops on [element] or on anything inside it."""
    for inner in element.iter():
        if "." not in name(inner) and TAB_STOP.search(name(inner)) and attribute(inner, "IsTabStop") != "False":
            return True
    return False


def tab_order() -> list[str]:
    found = []
    for path in sorted(glob.glob(str(ROOT / "windows/src/**/*.xaml"), recursive=True)):
        if "obj" in Path(path).relative_to(ROOT).parts:
            continue
        for panel in ElementTree.parse(path).iter():
            if name(panel) != "DockPanel" or attribute(panel, "KeyboardNavigation.TabNavigation"):
                continue
            children = [child for child in panel if "." not in name(child)]
            for index, child in enumerate(children):
                if attribute(child, "DockPanel.Dock") not in ("Right", "Bottom") or not stops_tab(child):
                    continue
                if any(stops_tab(later) for later in children[index + 1 :]):
                    found.append(f"{Path(path).relative_to(ROOT).as_posix()} {name(child)} docked "
                                 f"{attribute(child, 'DockPanel.Dock')} comes before a control it is drawn after")
    return found


def main() -> int:
    unnamed = windows() + android()
    hidden = bottom_bars()
    jumps = tab_order()
    for problem in unnamed + hidden + jumps:
        print(problem)
    if unnamed:
        print(f"{len(unnamed)} control(s) a screen reader cannot name")
    if hidden:
        print(f"{len(hidden)} bottom bar(s) the system navigation bar can cover")
    if jumps:
        print(f"{len(jumps)} DockPanel(s) where Tab does not read the way the screen does")
    if unnamed or hidden or jumps:
        return 1
    print("every control has something to read, keeps clear of the system bars and is reached by Tab in reading order")
    return 0


if __name__ == "__main__":
    sys.exit(main())
