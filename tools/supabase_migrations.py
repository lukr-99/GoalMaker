#!/usr/bin/env python3
"""Verify and test the Supabase migration chain (CodePrint data-lifecycle rules).

Commands:
  lock    Record the SHA-256 of every migration in supabase/migrations.lock.json. Existing entries
          are never changed: an applied migration is immutable.
  verify  Check names (0001_description.sql, no gaps), the lock, and that every migration after
          0001 has an isolated-test fixture pair in supabase/migration-tests/.
  test    verify, then on the running local stack: full chain from 0001 plus pgTAP, and for every
          migration N >= 2 an isolated N-1 -> N run with its fixtures. Ends on a fresh full chain.

The local stack must be running (npx supabase start). The Supabase CLI applies every migration in
one transaction; the isolated step does the same with psql --single-transaction.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Sequence

ROOT = Path(__file__).resolve().parents[1]
SUPABASE = ROOT / "supabase"
MIGRATIONS = SUPABASE / "migrations"
FIXTURES = SUPABASE / "migration-tests"
LOCK = SUPABASE / "migrations.lock.json"
NAME = re.compile(r"^(\d{4})_[a-z0-9]+(?:_[a-z0-9]+)*\.sql$")


class HarnessError(RuntimeError):
    """A rule violation or a failed step; the message says which."""


def migration_files() -> list[Path]:
    files = sorted(MIGRATIONS.glob("*.sql"))
    numbers: list[int] = []
    for path in files:
        match = NAME.match(path.name)
        if not match:
            raise HarnessError(f"{path.name}: expected 0001_description.sql (lowercase, underscores)")
        numbers.append(int(match.group(1)))
    expected = list(range(1, len(numbers) + 1))
    if numbers != expected:
        raise HarnessError(f"migration numbers must run 0001..{len(numbers):04d} without gaps, got {numbers}")
    return files


def sha256(path: Path) -> str:
    # Normalize line endings so a Windows checkout and CI hash the same content.
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def read_lock() -> dict[str, str]:
    if not LOCK.is_file():
        return {}
    return dict(json.loads(LOCK.read_text(encoding="utf-8"))["migrations"])


def lock() -> None:
    entries = read_lock()
    for path in migration_files():
        digest = sha256(path)
        recorded = entries.get(path.name)
        if recorded is None:
            entries[path.name] = digest
            print(f"locked {path.name}")
        elif recorded != digest:
            raise HarnessError(f"{path.name} changed after it was locked; add a new migration instead")
    document = {
        "description": "SHA-256 of each applied migration (LF line endings). Written by tools/supabase_migrations.py lock.",
        "migrations": dict(sorted(entries.items())),
    }
    LOCK.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8", newline="\n")


def verify() -> list[Path]:
    files = migration_files()
    entries = read_lock()
    names = {path.name for path in files}
    for name in entries:
        if name not in names:
            raise HarnessError(f"{name} is locked but missing; applied migrations must never be deleted")
    for path in files:
        recorded = entries.get(path.name)
        if recorded is None:
            raise HarnessError(f"{path.name} is not locked; run: python tools/supabase_migrations.py lock")
        if recorded != sha256(path):
            raise HarnessError(f"{path.name} changed after it was locked; add a new migration instead")
    for path in files[1:]:
        number = path.name[:4]
        for suffix in ("before", "after"):
            fixture = FIXTURES / f"{number}_{suffix}.sql"
            if not fixture.is_file():
                raise HarnessError(f"{path.name} needs {fixture.relative_to(ROOT)} for its isolated test")
    print(f"verified {len(files)} migrations")
    return files


def project_id() -> str:
    match = re.search(r'^project_id\s*=\s*"([^"]+)"', (SUPABASE / "config.toml").read_text(encoding="utf-8"), re.M)
    if not match:
        raise HarnessError("project_id missing from supabase/config.toml")
    return match.group(1)


def run(args: list[str], *, stdin: bytes | None = None, label: str) -> None:
    print(f"==> {label}", flush=True)
    result = subprocess.run(args, cwd=ROOT, input=stdin, capture_output=True)
    output = (result.stdout + result.stderr).decode("utf-8", errors="replace")
    if result.returncode != 0:
        print(output)
        raise HarnessError(f"{label} failed (exit {result.returncode})")


def supabase(*args: str, label: str) -> None:
    npx = shutil.which("npx") or shutil.which("npx.cmd")
    if npx is None:
        raise HarnessError("npx not found; install Node.js and run npm ci")
    run([npx, "supabase", *args], label=label)


def psql_file(container: str, path: Path, label: str) -> None:
    run(
        ["docker", "exec", "-i", container, "psql", "-U", "postgres", "-d", "postgres",
         "-v", "ON_ERROR_STOP=1", "--single-transaction", "-q"],
        stdin=path.read_bytes(),
        label=label,
    )


def test() -> None:
    files = verify()
    container = f"supabase_db_{project_id()}"
    supabase("db", "reset", "--local", label="full chain from 0001")
    supabase("test", "db", label="pgTAP tests")
    for previous, current in zip(files, files[1:]):
        number = current.name[:4]
        supabase("db", "reset", "--local", "--no-seed", "--version", previous.name[:4], label=f"reset to {previous.name}")
        psql_file(container, FIXTURES / f"{number}_before.sql", f"{number} fixture before")
        psql_file(container, current, f"isolated {previous.name[:4]} -> {current.name}")
        psql_file(container, FIXTURES / f"{number}_after.sql", f"{number} checks after")
    supabase("db", "reset", "--local", label="restore the full chain")
    print("migration harness passed")


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("lock", "verify", "test"))
    args = parser.parse_args(argv)
    try:
        {"lock": lock, "verify": verify, "test": test}[args.command]()
    except HarnessError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
