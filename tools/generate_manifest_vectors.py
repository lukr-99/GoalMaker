#!/usr/bin/env python3
"""Regenerate contracts/vectors/release-manifest.json.

Creates two throwaway ECDSA P-256 key pairs in a temporary folder, signs a set of manifests with
OpenSSL, and writes the manifest bytes, signatures and the trusted public key as vectors. No private
key is kept. ECDSA signatures are randomized, so every run produces different signature bytes with
the same expected outcomes.
"""

from __future__ import annotations

import base64
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "contracts" / "vectors" / "release-manifest.json"

HASH_APK = "3f1c0e0b7a7d4e5c9a0b1c2d3e4f50617283940a1b2c3d4e5f60718293a4b5c6"
HASH_EXE = "a1b2c3d4e5f60718293a4b5c6d7e8f90112233445566778899aabbccddeeff00"


def run(*args: str, cwd: Path) -> bytes:
    return subprocess.run(args, cwd=cwd, check=True, capture_output=True).stdout


def make_key(work: Path, name: str) -> tuple[Path, str]:
    private = work / f"{name}.pem"
    run("openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(private), cwd=work)
    public_der = run("openssl", "ec", "-in", str(private), "-pubout", "-outform", "DER", cwd=work)
    return private, base64.b64encode(public_der).decode("ascii")


def sign(work: Path, private: Path, data: bytes) -> str:
    source = work / "manifest.json"
    source.write_bytes(data)
    signature = run("openssl", "dgst", "-sha256", "-sign", str(private), str(source), cwd=work)
    return base64.b64encode(signature).decode("ascii")


def manifest(**overrides: object) -> dict[str, object]:
    value: dict[str, object] = {
        "schema": 1,
        "version": "0.2.0",
        "androidVersionCode": 2,
        "publishedAt": "2026-09-18T12:00:00Z",
        "notes": "Second release.",
        "artifacts": [
            {"platform": "android", "path": "0.2.0/GoalMaker-0.2.0.apk", "size": 18234567, "sha256": HASH_APK},
            {"platform": "windows", "path": "0.2.0/GoalMaker-0.2.0-setup.exe", "size": 52345678, "sha256": HASH_EXE},
        ],
    }
    value.update(overrides)
    return value


def encode(value: dict[str, object]) -> bytes:
    return (json.dumps(value, indent=2) + "\n").encode("utf-8")


def main() -> int:
    with tempfile.TemporaryDirectory() as temp:
        work = Path(temp)
        trusted, trusted_public = make_key(work, "trusted")
        other, _ = make_key(work, "other")

        valid = encode(manifest())
        cases: list[dict[str, object]] = []

        def add(name: str, data: bytes, signature: str, outcome: str, **expected: object) -> None:
            case: dict[str, object] = {
                "name": name,
                "manifestBase64": base64.b64encode(data).decode("ascii"),
                "signatureBase64": signature,
                "outcome": outcome,
            }
            case.update(expected)
            cases.append(case)

        add(
            "valid manifest with both platforms",
            valid,
            sign(work, trusted, valid),
            "valid",
            version="0.2.0",
            androidVersionCode=2,
            artifacts=[
                {"platform": "android", "path": "0.2.0/GoalMaker-0.2.0.apk", "size": 18234567, "sha256": HASH_APK},
                {"platform": "windows", "path": "0.2.0/GoalMaker-0.2.0-setup.exe", "size": 52345678, "sha256": HASH_EXE},
            ],
        )
        tampered = valid.replace(b"0.2.0/GoalMaker-0.2.0.apk", b"0.2.0/GoalMaker-0.2.9.apk")
        add("one byte changed after signing", tampered, sign(work, trusted, valid), "bad-signature")
        add("signed by another key", valid, sign(work, other, valid), "bad-signature")
        add("signature is not base64", valid, "not base64!", "bad-signature")
        add("empty signature", valid, "", "bad-signature")

        unknown = encode(manifest(channel="stable", extra={"a": 1}))
        add(
            "unknown fields are ignored",
            unknown,
            sign(work, trusted, unknown),
            "valid",
            version="0.2.0",
            androidVersionCode=2,
            artifacts=[
                {"platform": "android", "path": "0.2.0/GoalMaker-0.2.0.apk", "size": 18234567, "sha256": HASH_APK},
                {"platform": "windows", "path": "0.2.0/GoalMaker-0.2.0-setup.exe", "size": 52345678, "sha256": HASH_EXE},
            ],
        )

        windows_only = encode(
            {
                "schema": 1,
                "version": "0.2.1",
                "publishedAt": "2026-09-19T08:30:00Z",
                "artifacts": [
                    {"platform": "windows", "path": "0.2.1/GoalMaker-0.2.1-setup.exe", "size": 1, "sha256": HASH_EXE},
                ],
            }
        )
        add(
            "windows-only manifest needs no android version code",
            windows_only,
            sign(work, trusted, windows_only),
            "valid",
            version="0.2.1",
            androidVersionCode=None,
            artifacts=[
                {"platform": "windows", "path": "0.2.1/GoalMaker-0.2.1-setup.exe", "size": 1, "sha256": HASH_EXE},
            ],
        )

        bad_manifests: list[tuple[str, bytes]] = [
            ("unsupported schema", encode(manifest(schema=2))),
            ("version is not semantic", encode(manifest(version="v0.2"))),
            ("no artifacts", encode(manifest(artifacts=[]))),
            (
                "path escapes the folder",
                encode(manifest(artifacts=[{"platform": "android", "path": "../secret.apk", "size": 10, "sha256": HASH_APK}])),
            ),
            (
                "path starts with a slash",
                encode(manifest(artifacts=[{"platform": "android", "path": "/0.2.0/a.apk", "size": 10, "sha256": HASH_APK}])),
            ),
            (
                "sha256 is uppercase",
                encode(manifest(artifacts=[{"platform": "android", "path": "0.2.0/a.apk", "size": 10, "sha256": HASH_APK.upper()}])),
            ),
            (
                "size is zero",
                encode(manifest(artifacts=[{"platform": "android", "path": "0.2.0/a.apk", "size": 0, "sha256": HASH_APK}])),
            ),
            (
                "unknown platform",
                encode(manifest(artifacts=[{"platform": "linux", "path": "0.2.0/a.tar", "size": 10, "sha256": HASH_APK}])),
            ),
            (
                "android artifact without a version code",
                encode({key: value for key, value in manifest().items() if key != "androidVersionCode"}),
            ),
            ("not json", b"<html>not a manifest</html>\n"),
        ]
        for name, data in bad_manifests:
            add(name, data, sign(work, trusted, data), "bad-manifest")

        document = {
            "schema": 1,
            "description": (
                "Release manifest verification. Verify the ECDSA P-256 SHA-256 signature (DER, base64) over "
                "the exact manifest bytes with trustedPublicKey (SubjectPublicKeyInfo DER, base64) first; "
                "only then parse and validate the JSON. Outcomes: valid, bad-signature, bad-manifest. "
                "Regenerate with tools/generate_manifest_vectors.py."
            ),
            "trustedPublicKey": trusted_public,
            "cases": cases,
        }
        OUTPUT.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Wrote {len(cases)} cases to {OUTPUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
