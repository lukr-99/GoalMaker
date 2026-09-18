#!/usr/bin/env python3
"""Validate, create, apply, and test immutable SQLite migration chains."""

from __future__ import annotations

import argparse
import hashlib
import re
import sqlite3
import sys
import tempfile
from contextlib import closing
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence


MIGRATION_PATTERN = re.compile(
    r"^(?P<number>\d{4})_(?P<description>[a-z][a-z0-9_]*)\.sql$"
)


class MigrationError(RuntimeError):
    """Raised when a migration chain is unsafe or inconsistent."""


@dataclass(frozen=True)
class Migration:
    """One immutable migration file and its content checksum."""

    number: int
    name: str
    path: Path
    sql: str
    checksum: str


def discover_migrations(directory: Path) -> list[Migration]:
    """Return a validated, contiguous migration chain beginning at 0001."""

    directory = directory.resolve()
    if not directory.is_dir():
        raise MigrationError(f"Migration directory does not exist: {directory}")

    migrations: list[Migration] = []
    invalid_sql_files: list[str] = []
    for path in sorted(directory.iterdir(), key=lambda candidate: candidate.name):
        if not path.is_file() or path.suffix.lower() != ".sql":
            continue
        match = MIGRATION_PATTERN.fullmatch(path.name)
        if match is None:
            invalid_sql_files.append(path.name)
            continue
        raw = path.read_bytes()
        sql = raw.decode("utf-8-sig")
        if not sql.strip():
            raise MigrationError(f"Migration is empty: {path.name}")
        migrations.append(
            Migration(
                number=int(match.group("number")),
                name=path.name,
                path=path,
                sql=sql,
                checksum=hashlib.sha256(raw).hexdigest(),
            )
        )

    if invalid_sql_files:
        joined = ", ".join(invalid_sql_files)
        raise MigrationError(
            "Migration filenames must match 0001_description.sql: " + joined
        )
    if not migrations:
        raise MigrationError(f"No migration files found in {directory}")

    for expected, migration in enumerate(migrations, start=1):
        if migration.number != expected:
            raise MigrationError(
                f"Expected migration {expected:04d}, found {migration.name}. "
                "Migration numbers must be contiguous and begin at 0001."
            )
    return migrations


def _sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def _ensure_history_table(connection: sqlite3.Connection) -> None:
    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS schema_migrations (
            number INTEGER PRIMARY KEY,
            filename TEXT NOT NULL UNIQUE,
            checksum_sha256 TEXT NOT NULL,
            applied_at_utc TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """
    )
    connection.commit()


def applied_migrations(connection: sqlite3.Connection) -> dict[int, tuple[str, str]]:
    """Return applied migration filename/checksum values keyed by number."""

    _ensure_history_table(connection)
    rows = connection.execute(
        "SELECT number, filename, checksum_sha256 FROM schema_migrations ORDER BY number"
    ).fetchall()
    return {int(row[0]): (str(row[1]), str(row[2])) for row in rows}


def apply_migrations(
    connection: sqlite3.Connection,
    migrations: Sequence[Migration],
    *,
    through: int | None = None,
    only: int | None = None,
) -> list[Migration]:
    """Apply pending migrations transactionally and verify applied checksums."""

    if through is not None and only is not None:
        raise ValueError("Use either through or only, not both.")

    connection.execute("PRAGMA foreign_keys = ON")
    applied = applied_migrations(connection)
    known = {migration.number: migration for migration in migrations}

    for number, (filename, checksum) in applied.items():
        migration = known.get(number)
        if migration is None:
            raise MigrationError(
                f"Database contains migration {number:04d} ({filename}) missing from the chain."
            )
        if filename != migration.name or checksum != migration.checksum:
            raise MigrationError(
                f"Applied migration {number:04d} differs from {migration.name}; "
                "restore the original file and add a new migration."
            )

    selected: Iterable[Migration] = migrations
    if through is not None:
        selected = (migration for migration in migrations if migration.number <= through)
    if only is not None:
        selected = (migration for migration in migrations if migration.number == only)

    completed: list[Migration] = []
    for migration in selected:
        if migration.number in applied:
            continue
        if only is not None and migration.number > 1 and migration.number - 1 not in applied:
            raise MigrationError(
                f"Cannot apply {migration.name} independently: migration "
                f"{migration.number - 1:04d} is not applied."
            )

        record = (
            "INSERT INTO schema_migrations(number, filename, checksum_sha256) VALUES ("
            f"{migration.number}, {_sql_literal(migration.name)}, "
            f"{_sql_literal(migration.checksum)});"
        )
        script = "BEGIN IMMEDIATE;\n" + migration.sql + "\n" + record + "\nCOMMIT;\n"
        try:
            connection.executescript(script)
        except sqlite3.Error as error:
            if connection.in_transaction:
                connection.rollback()
            raise MigrationError(f"Failed to apply {migration.name}: {error}") from error
        completed.append(migration)
        applied[migration.number] = (migration.name, migration.checksum)
    return completed


def assert_database_integrity(connection: sqlite3.Connection) -> None:
    """Fail when SQLite integrity or foreign-key checks report a problem."""

    integrity = connection.execute("PRAGMA integrity_check").fetchone()
    if integrity is None or integrity[0] != "ok":
        raise MigrationError(f"SQLite integrity check failed: {integrity}")
    foreign_key_errors = connection.execute("PRAGMA foreign_key_check").fetchall()
    if foreign_key_errors:
        raise MigrationError(f"SQLite foreign-key check failed: {foreign_key_errors}")


def test_migration_chain(
    directory: Path,
    *,
    target: int | None = None,
    fixture: Path | None = None,
) -> None:
    """Test the full chain and optionally one migration from its predecessor."""

    migrations = discover_migrations(directory)
    target = target or migrations[-1].number
    if target not in {migration.number for migration in migrations}:
        raise MigrationError(f"Migration {target:04d} does not exist.")

    with tempfile.TemporaryDirectory(prefix="codeprint-migrations-") as temp_dir:
        full_path = Path(temp_dir) / "full-chain.sqlite3"
        with closing(sqlite3.connect(full_path)) as connection:
            apply_migrations(connection, migrations)
            assert_database_integrity(connection)

        isolated_path = Path(temp_dir) / f"isolated-{target:04d}.sqlite3"
        with closing(sqlite3.connect(isolated_path)) as connection:
            if target > 1:
                apply_migrations(connection, migrations, through=target - 1)
            if fixture is not None:
                connection.executescript(fixture.read_text(encoding="utf-8-sig"))
                connection.commit()
            apply_migrations(connection, migrations, only=target)
            assert_database_integrity(connection)


def create_migration(directory: Path, description: str) -> Path:
    """Create the next numbered migration without modifying existing files."""

    if not re.fullmatch(r"[a-z][a-z0-9_]*", description):
        raise MigrationError("Description must be lowercase snake_case.")
    directory.mkdir(parents=True, exist_ok=True)
    existing = discover_migrations(directory) if any(directory.glob("*.sql")) else []
    number = len(existing) + 1
    if number > 9999:
        raise MigrationError("Migration number exceeds the four-digit policy.")
    path = directory / f"{number:04d}_{description}.sql"
    path.write_text(
        f"-- {path.name}\n"
        "-- One coherent, forward-only change. Do not add BEGIN/COMMIT; the runner owns it.\n\n",
        encoding="utf-8",
        newline="\n",
    )
    return path


def _parse_number(value: str) -> int:
    try:
        number = int(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("Migration must be a number such as 0007.") from error
    if number < 1 or number > 9999:
        raise argparse.ArgumentTypeError("Migration must be between 0001 and 9999.")
    return number


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate_parser = subparsers.add_parser("validate", help="Validate filenames and sequence.")
    validate_parser.add_argument("directory", type=Path)

    new_parser = subparsers.add_parser("new", help="Create the next immutable migration file.")
    new_parser.add_argument("directory", type=Path)
    new_parser.add_argument("description")

    apply_parser = subparsers.add_parser("apply", help="Apply pending SQLite migrations.")
    apply_parser.add_argument("directory", type=Path)
    apply_parser.add_argument("database", type=Path)

    test_parser = subparsers.add_parser("test", help="Test full and isolated migration paths.")
    test_parser.add_argument("directory", type=Path)
    test_parser.add_argument("--migration", type=_parse_number)
    test_parser.add_argument("--fixture", type=Path)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "validate":
            migrations = discover_migrations(args.directory)
            print(f"Validated {len(migrations)} migrations through {migrations[-1].number:04d}.")
        elif args.command == "new":
            print(create_migration(args.directory, args.description))
        elif args.command == "apply":
            migrations = discover_migrations(args.directory)
            args.database.parent.mkdir(parents=True, exist_ok=True)
            with closing(sqlite3.connect(args.database)) as connection:
                completed = apply_migrations(connection, migrations)
                assert_database_integrity(connection)
            print(f"Applied {len(completed)} migration(s) to {args.database}.")
        elif args.command == "test":
            test_migration_chain(
                args.directory,
                target=args.migration,
                fixture=args.fixture,
            )
            suffix = f" {args.migration:04d}" if args.migration else " latest"
            print(f"Full-chain and isolated{suffix} migration tests passed.")
    except (MigrationError, OSError, UnicodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
