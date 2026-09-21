#!/usr/bin/env python3
"""A fork runs its own project, its own sender and its own signing key (M6-10).

Four things in the repository belong to whoever runs it. This says when a clone that is not the
original still carries the original's: a release built that way is signed by a key its own apps do
not trust, and a push would reach a project the owner does not own.

Run from anywhere: python tools/check_own_copy.py
"""

from __future__ import annotations

import io
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# The original, which a fork must replace. Both are public, neither is a secret.
UPSTREAM_REMOTE = "lukr-99/goalmaker"
UPSTREAM_PROJECT = "wkjnauxqwlqhsqrqnkhg"
UPSTREAM_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEy3KCbpOC2e4uMEtCJ3oUFODtdowqWkFtQqgwi7EqPLkmF7rA1WW3DzH36C51ML3rO6oWRDmU0kmVFxBwMTZ0Lg=="


def remote() -> str:
    try:
        url = subprocess.run(
            ["git", "remote", "get-url", "origin"],
            cwd=ROOT, capture_output=True, text=True, check=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return ""
    return re.sub(r"\.git$", "", url).lower()


def main() -> int:
    origin = remote()
    if not origin or UPSTREAM_REMOTE in origin:
        print("this is the original copy, or has no origin yet: nothing to check")
        return 0

    problems = []
    config = io.open(ROOT / "supabase/config.toml", encoding="utf-8").read()
    if UPSTREAM_PROJECT in config:
        problems.append(
            "supabase/config.toml still names the original project. Put your own project ref in "
            "project_id and in [remotes.production].auth.site_url."
        )

    key = ROOT / "contracts/keys/release-manifest-public.b64"
    if key.is_file() and io.open(key, encoding="utf-8").read().strip() == UPSTREAM_KEY:
        problems.append(
            "contracts/keys/release-manifest-public.b64 is still the original's update signing key, "
            "so your apps would trust the original's releases and refuse yours. Make your own with "
            "tools/setup-update-signing.ps1."
        )

    for problem in problems:
        print(problem)
    if problems:
        print("See docs/setup/your-own-copy.md.")
        return 1
    print("this copy runs its own project and its own signing key")
    return 0


if __name__ == "__main__":
    sys.exit(main())
