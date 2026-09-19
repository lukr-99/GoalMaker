#!/usr/bin/env python3
"""Check contracts/content/prompts.json: ids, categories, review kinds, placeholders and triggers.

The library is content both apps and the connector ship, so a typo here would reach the review
screens (docs/reviews.md). Run it in CI beside the other contract checks.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIBRARY = ROOT / "contracts" / "content" / "prompts.json"

KINDS = {"weekly", "monthly", "yearly"}
TRIGGERS = {
    "habit_missed",
    "habit_streak",
    "task_slipping",
    "goal_behind",
    "goal_ahead",
    "quiet_period",
    "busy_period",
    "no_goals",
}
ID_PATTERN = re.compile(r"^[a-z]+/[a-z_]+$")
PLACEHOLDERS = re.compile(r"\{([a-z]+)\}")


def main() -> int:
    document = json.loads(LIBRARY.read_text(encoding="utf-8"))
    problems: list[str] = []
    categories = [category["id"] for category in document["categories"]]
    if len(categories) != len(set(categories)):
        problems.append("two categories share an id")

    seen: set[str] = set()
    triggered: set[str] = set()
    for prompt in document["prompts"]:
        where = prompt.get("id", "<no id>")
        if not ID_PATTERN.match(where):
            problems.append(f"{where}: an id is '<category>/<name>' in lowercase")
        if where in seen:
            problems.append(f"{where}: two prompts share an id")
        seen.add(where)
        if len(where) > 60:
            problems.append(f"{where}: too long for the server's check on reviews.reflections")
        if prompt["category"] not in categories and "trigger" not in prompt:
            problems.append(f"{where}: {prompt['category']} is not a category")
        if not prompt["reviews"] or set(prompt["reviews"]) - KINDS:
            problems.append(f"{where}: reviews are any of {sorted(KINDS)}")
        text = prompt["text"]
        if not text.strip() or text.strip() != text:
            problems.append(f"{where}: the text is empty or has loose spaces")
        if not text.rstrip().endswith("?"):
            problems.append(f"{where}: a prompt asks a question")
        for placeholder in PLACEHOLDERS.findall(text):
            if placeholder not in {"period", "subject"}:
                problems.append(f"{where}: unknown placeholder {{{placeholder}}}")
        trigger = prompt.get("trigger")
        if trigger is not None:
            if trigger not in TRIGGERS:
                problems.append(f"{where}: unknown trigger {trigger}")
            if trigger in triggered:
                problems.append(f"{where}: {trigger} already has a prompt")
            triggered.add(trigger)
            if trigger in {"habit_missed", "habit_streak", "task_slipping", "goal_behind", "goal_ahead"} and "{subject}" not in text:
                problems.append(f"{where}: a prompt about one thing names it with {{subject}}")
        elif "{subject}" in text:
            problems.append(f"{where}: only a triggered prompt has a subject")

    for category in categories:
        for kind in KINDS:
            count = sum(
                1 for prompt in document["prompts"]
                if prompt["category"] == category and kind in prompt["reviews"] and "trigger" not in prompt
            )
            if count < 3:
                problems.append(f"{category}: only {count} prompts for a {kind} review, which is too few to rotate")

    missing = TRIGGERS - triggered
    if missing:
        problems.append(f"no prompt for {sorted(missing)}")

    library = sum(1 for prompt in document["prompts"] if "trigger" not in prompt)
    if library < 100:
        problems.append(f"the library holds {library} prompts, fewer than the hundred the spec asks for")

    if problems:
        for problem in problems:
            print(f"prompts.json: {problem}", file=sys.stderr)
        return 1

    print(f"prompts.json: {library} prompts in {len(categories)} categories, {len(triggered)} triggers")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
