#!/usr/bin/env python3
"""Check contracts/schemas/synced-tables.json against the replica and (optionally) the server.

  python tools/check_synced_tables.py            # the replica schema (replica/migrations)
  python tools/check_synced_tables.py --server   # also the local Supabase stack (must be running)

Every described table must exist with exactly the described columns, in both places, the column
kinds must fit the SQL types, and a column the description calls required must be NOT NULL on the
server. The apps build their SQL and JSON mapping from the description, so a mismatch here is a sync
bug waiting to happen: a row carries every column, so a null in a column the server has NOT NULL is
refused on the push whatever default that column has. The replica may be laxer than the description,
never stricter.
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
    "json": {"TEXT"},
}
SERVER_TYPES = {
    "text": {"text", "uuid"},
    "integer": {"integer", "bigint", "smallint"},
    "real": {"double precision", "real", "numeric"},
    "boolean": {"boolean"},
    "timestamp": {"timestamp with time zone"},
    "date": {"date"},
    "time": {"time without time zone"},
    "json": {"jsonb", "json"},
}


def described() -> dict[str, list[tuple[str, str, bool]]]:
    document = json.loads(DESCRIPTION.read_text(encoding="utf-8"))
    return {
        table["name"]: [(c["name"], c["kind"], bool(c.get("required", False))) for c in table["columns"]]
        for table in document["tables"]
    }


def compare(
    where: str,
    expected: dict[str, list[tuple[str, str, bool]]],
    actual: dict[str, dict[str, str]],
    types: dict[str, set[str]],
    not_null: dict[str, set[str]],
    exact: bool,
) -> list[str]:
    """Columns, kinds and nullability.

    `exact`: every required column must be NOT NULL there. Otherwise that schema may be laxer (a
    required column may be nullable there) but never stricter: a column the description does not
    require may not be NOT NULL, or a local write would fail where a push would not.
    """
    problems = []
    for table, columns in expected.items():
        found = actual.get(table)
        if found is None:
            problems.append(f"{where}: table {table} is missing")
            continue
        names = [name for name, _, _ in columns]
        if set(names) != set(found):
            extra = sorted(set(found) - set(names))
            missing = sorted(set(names) - set(found))
            problems.append(f"{where}: {table} columns differ (missing {missing}, undescribed {extra})")
            continue
        demanded = not_null.get(table, set())
        for name, kind, required in columns:
            if found[name] not in types[kind]:
                problems.append(f"{where}: {table}.{name} is {found[name]}, which doesn't fit kind {kind}")
            if required and exact and name not in demanded:
                problems.append(f"{where}: {table}.{name} is described as required but is nullable there")
            if not required and name in demanded:
                problems.append(f"{where}: {table}.{name} is NOT NULL there but the description does not require it")
    return problems


def replica_columns() -> tuple[dict[str, dict[str, str]], dict[str, set[str]]]:
    with tempfile.TemporaryDirectory() as temp, closing(sqlite3.connect(Path(temp) / "replica.sqlite3")) as db:
        apply_migrations(db, discover_migrations(REPLICA))
        tables = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type = 'table'")]
        columns: dict[str, dict[str, str]] = {}
        not_null: dict[str, set[str]] = {}
        for table in tables:
            info = list(db.execute(f"PRAGMA table_info({table})"))
            columns[table] = {row[1]: str(row[2]).upper() for row in info}
            not_null[table] = {row[1] for row in info if row[3] == 1}
        return columns, not_null


def server_columns(container: str) -> tuple[dict[str, dict[str, str]], dict[str, set[str]]]:
    query = (
        "select table_name || '|' || column_name || '|' || data_type || '|' || is_nullable "
        "from information_schema.columns "
        "where table_schema = 'public' order by table_name, ordinal_position"
    )
    result = subprocess.run(
        ["docker", "exec", container, "psql", "-U", "postgres", "-d", "postgres", "-At", "-c", query],
        capture_output=True, text=True, check=False,
    )
    if result.returncode != 0:
        raise SystemExit(f"could not read the server schema: {result.stderr.strip()}")
    columns: dict[str, dict[str, str]] = {}
    not_null: dict[str, set[str]] = {}
    for line in result.stdout.splitlines():
        table, column, data_type, nullable = line.split("|")
        columns.setdefault(table, {})[column] = data_type
        not_null.setdefault(table, set())
        if nullable == "NO":
            not_null[table].add(column)
    return columns, not_null


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server", action="store_true", help="also check the running local Supabase stack")
    args = parser.parse_args(argv)

    expected = described()
    columns, not_null = replica_columns()
    problems = compare("replica", expected, columns, REPLICA_TYPES, not_null, exact=False)
    if args.server:
        config = (ROOT / "supabase" / "config.toml").read_text(encoding="utf-8")
        project = re.search(r'^project_id\s*=\s*"([^"]+)"', config, re.M)
        container = f"supabase_db_{project.group(1) if project else 'goalmaker'}"
        columns, not_null = server_columns(container)
        problems += compare("server", expected, columns, SERVER_TYPES, not_null, exact=True)
    for problem in problems:
        print(f"error: {problem}", file=sys.stderr)
    if problems:
        return 1
    print(f"synced-tables.json matches {'the replica and the server' if args.server else 'the replica'} ({len(expected)} tables)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
