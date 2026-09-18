#!/usr/bin/env python3
"""Validate the mechanically checkable CodePrint repository baseline."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable, Sequence

from migrations import MigrationError, discover_migrations


TEXT_EXTENSIONS = {
    ".cs",
    ".csproj",
    ".json",
    ".iss",
    ".kt",
    ".kts",
    ".md",
    ".props",
    ".ps1",
    ".py",
    ".sql",
    ".targets",
    ".xaml",
    ".xml",
    ".yaml",
    ".yml",
}
IGNORED_DIRECTORY_NAMES = {
    ".git",
    ".gradle",
    ".idea",
    ".pytest_cache",
    ".vscode",
    "__pycache__",
    "artifacts",
    "bin",
    "build",
    "dist",
    "node_modules",
    "obj",
}
CS_TYPE_PATTERN = re.compile(
    r"^(?:(?:public|internal|file|abstract|sealed|static|partial|readonly)\s+)*"
    r"(?:class|interface|record(?:\s+(?:class|struct))?|enum|struct)\s+"
    r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)
KOTLIN_TYPE_PATTERN = re.compile(
    r"^(?:(?:public|internal|private|protected|open|abstract|sealed|data|value|annotation|expect|actual)\s+)*"
    r"(?:enum\s+class|annotation\s+class|data\s+class|sealed\s+class|value\s+class|class|interface|object)\s+"
    r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)
MARKDOWN_LINK_PATTERN = re.compile(r"(?<!!)\[[^\]]+\]\((?P<target>[^)]+)\)")


def load_config(root: Path) -> dict[str, Any]:
    config_path = root / ".codeprint.json"
    if not config_path.is_file():
        return {
            "required_documents": [
                "README.md",
                "ARCHITECTURE.md",
                "AGENTS.md",
                "CONTRIBUTING.md",
            ],
            "migration_directories": [],
            "type_scan_roots": ["src", "app/src/main", "agent"],
            "type_policy_excludes": [
                "**/generated/**",
                "**/obj/**",
                "**/bin/**",
                "**/build/**",
            ],
            "link_check_excludes": ["templates/**"],
        }
    try:
        value = json.loads(config_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeError) as error:
        raise ValueError(f"Invalid .codeprint.json: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(".codeprint.json must contain a JSON object.")
    return value


def _matches_any(path: Path, patterns: Iterable[str]) -> bool:
    normalized = Path(path.as_posix())
    return any(normalized.match(pattern) for pattern in patterns)


def _iter_files(root: Path) -> Iterable[Path]:
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(root)
        if any(part in IGNORED_DIRECTORY_NAMES for part in relative.parts):
            continue
        yield path


def check_required_documents(root: Path, config: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    for relative in config.get("required_documents", []):
        if not (root / str(relative)).is_file():
            errors.append(f"missing required document: {relative}")
    return errors


def check_text_hygiene(root: Path) -> list[str]:
    errors: list[str] = []
    for path in _iter_files(root):
        if path.suffix.lower() not in TEXT_EXTENSIONS and path.name not in {
            ".editorconfig",
            ".gitattributes",
            ".gitignore",
        }:
            continue
        relative = path.relative_to(root).as_posix()
        try:
            text = path.read_text(encoding="utf-8-sig")
        except (UnicodeError, OSError) as error:
            errors.append(f"{relative}: not readable UTF-8 text ({error})")
            continue
        if text and not text.endswith("\n"):
            errors.append(f"{relative}: missing final newline")
        if path.suffix.lower() != ".md":
            for index, line in enumerate(text.splitlines(), start=1):
                if line.endswith((" ", "\t")):
                    errors.append(f"{relative}:{index}: trailing whitespace")
    return errors


def _expected_type_name(path: Path) -> str:
    if path.name.endswith(".xaml.cs"):
        return path.name[: -len(".xaml.cs")]
    return path.stem


def check_one_type_per_file(root: Path, config: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    excludes = [str(value) for value in config.get("type_policy_excludes", [])]
    for scan_root_value in config.get("type_scan_roots", []):
        scan_root = root / str(scan_root_value)
        if not scan_root.is_dir():
            continue
        for path in scan_root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in {".cs", ".kt"}:
                continue
            relative = path.relative_to(root)
            if _matches_any(relative, excludes):
                continue
            text = path.read_text(encoding="utf-8-sig")
            pattern = CS_TYPE_PATTERN if path.suffix.lower() == ".cs" else KOTLIN_TYPE_PATTERN
            names = pattern.findall(text)
            if len(names) > 1:
                errors.append(
                    f"{relative.as_posix()}: multiple top-level types ({', '.join(names)})"
                )
            elif len(names) == 1 and names[0] != _expected_type_name(path):
                errors.append(
                    f"{relative.as_posix()}: type {names[0]} must be in {names[0]}{path.suffix}"
                )
    return errors


def check_migrations(root: Path, config: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    for relative in config.get("migration_directories", []):
        directory = root / str(relative)
        try:
            discover_migrations(directory)
        except MigrationError as error:
            errors.append(f"{relative}: {error}")
    return errors


def _markdown_files(root: Path) -> Iterable[Path]:
    yield from (path for path in _iter_files(root) if path.suffix.lower() == ".md")


def check_markdown_links(root: Path, config: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    excludes = [str(value) for value in config.get("link_check_excludes", [])]
    for path in _markdown_files(root):
        relative = path.relative_to(root)
        if _matches_any(relative, excludes):
            continue
        text = path.read_text(encoding="utf-8-sig")
        for match in MARKDOWN_LINK_PATTERN.finditer(text):
            raw_target = match.group("target").strip()
            target = raw_target.split(maxsplit=1)[0].strip("<>")
            if not target or target.startswith(("#", "http://", "https://", "mailto:")):
                continue
            target_path = target.split("#", maxsplit=1)[0]
            if not target_path:
                continue
            resolved = (path.parent / target_path).resolve()
            if not resolved.exists():
                line = text.count("\n", 0, match.start()) + 1
                errors.append(
                    f"{relative.as_posix()}:{line}: missing link target {target_path}"
                )
    return errors


def validate(root: Path) -> list[str]:
    root = root.resolve()
    try:
        config = load_config(root)
    except ValueError as error:
        return [str(error)]
    errors: list[str] = []
    errors.extend(check_required_documents(root, config))
    errors.extend(check_text_hygiene(root))
    errors.extend(check_one_type_per_file(root, config))
    errors.extend(check_migrations(root, config))
    errors.extend(check_markdown_links(root, config))
    return sorted(errors)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    errors = validate(args.root)
    if errors:
        print("Repository validation failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1
    print(f"Repository validation passed: {args.root.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
