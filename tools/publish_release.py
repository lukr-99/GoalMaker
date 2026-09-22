#!/usr/bin/env python3
"""Sign a GoalMaker release manifest and upload the release to the old bucket (ADR 0004, ADR 0010).

Writes the release manifest (contracts/schemas/release-manifest.schema.json), signs its exact bytes
with the ECDSA P-256 manifest key through OpenSSL, uploads the artifacts to
releases/<version>/<file>, then the manifest and signature to releases/<version>/ and releases/latest/.
The release workflow attaches the same manifest to the GitHub Release, which is what the apps read.

Used by .github/workflows/release.yml; also runs locally against the local stack for testing:

  python tools/publish_release.py --version 0.1.1 --android-version-code 2 \\
      --apk path/to/app.apk --windows-installer path/to/setup.exe \\
      --signing-key key.pem --supabase-url http://127.0.0.1:55321 --secret-key sb_secret_...

--dry-run writes manifest.json and manifest.sig next to --output without uploading.
--channel-folder lays the release out the way GitHub Releases serves it, for a local test server
(docs/setup/local-development.md).
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import mimetypes
import shutil
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from datetime import UTC, datetime
from pathlib import Path
from typing import Sequence

BUCKET = "releases"
VERSION = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z")
MAX_BYTES = 50 * 1024 * 1024


class PublishError(RuntimeError):
    """A release can't be published as requested."""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def build_manifest(version: str, version_code: int | None, notes: str, artifacts: list[tuple[str, Path]]) -> tuple[bytes, list[tuple[str, Path]]]:
    entries = []
    uploads = []
    for platform, path in artifacts:
        size = path.stat().st_size
        if size > MAX_BYTES:
            raise PublishError(f"{path.name} is {size} bytes; the free plan stores at most 50 MB per file")
        object_path = f"{version}/{path.name}"
        entries.append({"platform": platform, "path": object_path, "size": size, "sha256": sha256(path)})
        uploads.append((object_path, path))
    manifest: dict[str, object] = {
        "schema": 1,
        "version": version,
        "publishedAt": datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "artifacts": entries,
    }
    if version_code is not None:
        manifest["androidVersionCode"] = version_code
    if notes:
        manifest["notes"] = notes
    return (json.dumps(manifest, indent=2) + "\n").encode("utf-8"), uploads


def sign(data: bytes, key: Path) -> str:
    with tempfile.TemporaryDirectory() as temp:
        source = Path(temp) / "manifest.json"
        source.write_bytes(data)
        result = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", str(key), str(source)],
            capture_output=True,
            check=False,
        )
    if result.returncode != 0:
        raise PublishError("openssl could not sign the manifest: " + result.stderr.decode(errors="replace"))
    return base64.b64encode(result.stdout).decode("ascii")


def upload(url: str, secret_key: str, object_path: str, data: bytes, content_type: str) -> None:
    request = urllib.request.Request(
        f"{url.rstrip('/')}/storage/v1/object/{BUCKET}/{object_path}",
        data=data,
        method="POST",
        headers={
            "apikey": secret_key,
            "Authorization": f"Bearer {secret_key}",
            "Content-Type": content_type,
            "x-upsert": "true",
            "Cache-Control": "no-cache",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            response.read()
    except urllib.error.HTTPError as error:
        body = error.read().decode(errors="replace")
        raise PublishError(f"upload of {object_path} failed: HTTP {error.code} {body}") from error
    print(f"uploaded {BUCKET}/{object_path} ({len(data)} bytes)")


def lay_out_channel(folder: Path, version: str, manifest: bytes, signature: str, uploads: list[tuple[str, Path]]) -> None:
    """The GitHub Releases layout (contracts/vectors/release-channel.json) under a plain folder."""
    latest = folder / "releases" / "latest" / "download"
    tagged = folder / "releases" / "download" / f"v{version}"
    for directory in (latest, tagged):
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "manifest.json").write_bytes(manifest)
        (directory / "manifest.sig").write_text(signature + "\n", encoding="ascii", newline="\n")
    for _, path in uploads:
        shutil.copyfile(path, tagged / path.name)
    print(f"laid out the release under {folder}")


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--version", required=True)
    parser.add_argument("--android-version-code", type=int)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--windows-installer", type=Path)
    parser.add_argument("--notes", default="")
    parser.add_argument("--signing-key", type=Path, required=True, help="PEM ECDSA P-256 private key")
    parser.add_argument("--supabase-url")
    parser.add_argument("--secret-key", help="Supabase secret (service) key; never the publishable key")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--output", type=Path, default=Path("release-out"))
    parser.add_argument("--channel-folder", type=Path, help="also lay the release out like GitHub Releases here")
    args = parser.parse_args(argv)

    try:
        if not VERSION.match(args.version):
            raise PublishError(f"release versions are plain X.Y.Z, got {args.version!r}")
        artifacts: list[tuple[str, Path]] = []
        if args.apk:
            if args.android_version_code is None or args.android_version_code < 1:
                raise PublishError("--android-version-code is required with --apk")
            artifacts.append(("android", args.apk))
        if args.windows_installer:
            artifacts.append(("windows", args.windows_installer))
        if not artifacts:
            raise PublishError("nothing to publish: pass --apk and/or --windows-installer")
        for _, path in artifacts:
            if not path.is_file():
                raise PublishError(f"artifact not found: {path}")

        manifest, uploads = build_manifest(args.version, args.android_version_code, args.notes, artifacts)
        signature = sign(manifest, args.signing_key)

        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / "manifest.json").write_bytes(manifest)
        (args.output / "manifest.sig").write_text(signature + "\n", encoding="ascii", newline="\n")
        print(f"wrote {args.output / 'manifest.json'} and manifest.sig")
        if args.channel_folder:
            lay_out_channel(args.channel_folder, args.version, manifest, signature, uploads)
        if args.dry_run:
            return 0

        if not args.supabase_url or not args.secret_key:
            raise PublishError("--supabase-url and --secret-key are required unless --dry-run")
        for object_path, path in uploads:
            content_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
            upload(args.supabase_url, args.secret_key, object_path, path.read_bytes(), content_type)
        for folder in (args.version, "latest"):
            # A reader between these two uploads sees a mismatched pair, reports "untrusted" and
            # installs nothing; the next check succeeds.
            upload(args.supabase_url, args.secret_key, f"{folder}/manifest.sig", (signature + "\n").encode("ascii"), "text/plain")
            upload(args.supabase_url, args.secret_key, f"{folder}/manifest.json", manifest, "application/json")
    except PublishError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
