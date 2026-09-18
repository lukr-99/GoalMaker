#!/usr/bin/env python3
"""Check contracts/schemas/synced-tables.json against the replica and (optionally) the server.

  python tools/check_synced_tables.py            # the replica schema (replica/migrations)
  python tools/check_synced_tables.py --server   # also the local Supabase stack (must be running)

Every described table must exist with exactly the described columns, in both places, and the
column kinds must fit the SQL types. The apps build their SQL and JSON mapping from the description,
so a mismatch here is a sync bug waiting to happen.
"""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
import subprocess
import sys
import tempfile
from contextlib import closing
from pathlib import Path
from typing import Sequence

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

from migrations import apply_migrations, discover_migrations  # noqa: E402

DESCRIPTION = ROOT / "contracts" / "schemas" / "synced-tables.json"
REPLICA = ROOT / "replica" / "migrations"

REPLICA_TYPES = {
    "text": {"TEXT"},
    "integer": {"INTEGER"},
    "real": {"REAL"},
    "boolean": {"INTEGER"},
    "timestamp": {"TEXT"},
    "date": {"TEXT"},
    "time": {"TEXT"},
}
SERVER_TYPES = {
    "text": {"text", "uuid"},
    "integer": {"integer", "bigint", "smallint"},
    "real": {"double precision", "real", "numeric"},
    "boolean": {"boolean"},
    "timestamp": {"timestamp with time zone"},
    "date": {"date"},
    "time": {"time without time zone"},
}


def described() -> dict[str, list[tuple[str, str]]]:
    document = json.loads(DESCRIPTION.read_text(encoding="utf-8"))
    return {table["name"]: [(c["name"], c["kind"]) for c in table["columns"]] for table in document["tables"]}


def compare(where: str, expected: dict[str, list[tuple[str, str]]], actual: dict[str, dict[str, str]], types: dict[str, set[str]]) -> list[str]:
    problems = []
    for table, columns in expected.items():
        found = actual.get(table)
        if found is None:
            problems.append(f"{where}: table {table} is missing")
            continue
        names = [name for name, _ in columns]
        if set(names) != set(found):
            extra = sorted(set(found) - set(names))
            missing = sorted(set(names) - set(found))
            problems.append(f"{where}: {table} columns differ (missing {missing}, undescribed {extra})")
            continue
        for name, kind in columns:
            if found[name] not in types[kind]:
                problems.append(f"{where}: {table}.{name} is {found[name]}, which doesn't fit kind {kind}")
    return problems


def replica_columns() -> dict[str, dict[str, str]]:
    with tempfile.TemporaryDirectory() as temp, closing(sqlite3.connect(Path(temp) / "replica.sqlite3")) as db:
        apply_migrations(db, discover_migrations(REPLICA))
        tables = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type = 'table'")]
        return {
            table: {row[1]: str(row[2]).upper() for row in db.execute(f"PRAGMA table_info({table})")}
            for table in tables
        }


def server_columns(container: str) -> dict[str, dict[str, str]]:
    query = (
        "select table_name || '|' || column_name || '|' || data_type from information_schema.columns "
        "where table_schema = 'public' order by table_name, ordinal_position"
    )
    result = subprocess.run(
        ["docker", "exec", container, "psql", "-U", "postgres", "-d", "postgres", "-At", "-c", query],
        capture_output=True, text=True, check=False,
    )
    if result.returncode != 0:
        raise SystemExit(f"could not read the server schema: {result.stderr.strip()}")
    columns: dict[str, dict[str, str]] = {}
    for line in result.stdout.splitlines():
        table, column, data_type = line.split("|")
        columns.setdefault(table, {})[column] = data_type
    return columns


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server", action="store_true", help="also check the running local Supabase stack")
    args = parser.parse_args(argv)

    expected = described()
    problems = compare("replica", expected, replica_columns(), REPLICA_TYPES)
    if args.server:
        config = (ROOT / "supabase" / "config.toml").read_text(encoding="utf-8")
        project = re.search(r'^project_id\s*=\s*"([^"]+)"', config, re.M)
        container = f"supabase_db_{project.group(1) if project else 'goalmaker'}"
        problems += compare("server", expected, server_columns(container), SERVER_TYPES)
    for problem in problems:
        print(f"error: {problem}", file=sys.stderr)
    if problems:
        return 1
    print(f"synced-tables.json matches {'the replica and the server' if args.server else 'the replica'} ({len(expected)} tables)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
