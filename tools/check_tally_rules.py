#!/usr/bin/env python3
"""Check contracts/content/tally-rules.json: the default Tally categories and the rules that sort time.

Both apps ship the file (docs/tally.md), so a rule pointing at a category that isn't there, or a
title rule on Android, would quietly sort time into Other. Run it in CI beside the other contract
checks.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULTS = ROOT / "contracts" / "content" / "tally-rules.json"
THEMES = ROOT / "contracts" / "design" / "themes.json"

MATCHES = {"app", "title", "folder"}
PLATFORMS = {"android", "windows", "any"}
ID_PATTERN = re.compile(r"^[a-z]+$")
# The same bound as tally_rules.pattern and tally_categories.name on the server (migration 0017).
MAX_PATTERN = 200
MAX_NAME = 40


def palette() -> set[str]:
    themes = json.loads(THEMES.read_text(encoding="utf-8"))
    return {entry["id"] for entry in themes["areaPalette"]["colors"]}


def main() -> int:
    document = json.loads(DEFAULTS.read_text(encoding="utf-8"))
    colors = palette()
    problems: list[str] = []

    ids: list[str] = []
    for category in document["categories"]:
        where = category.get("id", "<no id>")
        ids.append(where)
        if not ID_PATTERN.match(where):
            problems.append(f"category {where}: an id is one lowercase word")
        if not 0 < len(category.get("name", "")) <= MAX_NAME:
            problems.append(f"category {where}: a name has 1 to {MAX_NAME} characters")
        if category.get("name") != where.capitalize():
            problems.append(f"category {where}: the name is the id capitalized, as the connector names a default")
        if category.get("color") not in colors:
            problems.append(f"category {where}: {category.get('color')} is not in the area palette")
        if not category.get("emoji"):
            problems.append(f"category {where}: every category has an emoji")
    if len(ids) != len(set(ids)):
        problems.append("two categories share an id")
    shades = [category.get("color") for category in document["categories"]]
    if len(shades) != len(set(shades)):
        problems.append("two categories share a color, so a chart couldn't tell them apart")
    if "other" not in ids:
        problems.append("'other' has to be there: it is where unmatched time goes")

    seen: set[tuple[str, str, str]] = set()
    for index, rule in enumerate(document["rules"]):
        where = f"rule {index} ({rule.get('pattern')})"
        if rule.get("match") not in MATCHES:
            problems.append(f"{where}: match is one of {sorted(MATCHES)}")
        if rule.get("platform") not in PLATFORMS:
            problems.append(f"{where}: platform is one of {sorted(PLATFORMS)}")
        if rule.get("category") not in ids:
            problems.append(f"{where}: {rule.get('category')} is not a category")
        if rule.get("category") == "other":
            problems.append(f"{where}: a rule into Other does nothing a missing rule wouldn't")
        pattern = rule.get("pattern", "")
        if not 0 < len(pattern.strip()) <= MAX_PATTERN or pattern != pattern.strip():
            problems.append(f"{where}: a pattern has 1 to {MAX_PATTERN} characters and no outer spaces")
        if rule.get("match") != "app" and rule.get("platform") == "android":
            problems.append(f"{where}: Android has no window titles or editor folders, only apps")
        key = (rule.get("match"), pattern.lower(), rule.get("platform"))
        if key in seen:
            problems.append(f"{where}: the same rule twice")
        seen.add(key)

    for problem in problems:
        print(problem)
    if problems:
        return 1
    print(f"Tally defaults are sound: {len(ids)} categories, {len(document['rules'])} rules")
    return 0


if __name__ == "__main__":
    sys.exit(main())
