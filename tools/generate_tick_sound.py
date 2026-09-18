#!/usr/bin/env python3
"""Synthesize the optional completion tick (docs/design/spec.md, "Motion and feedback").

A soft, short click: two sine partials with a fast exponential decay, 70 ms, 16-bit mono at
44.1 kHz. Written for both apps, so they sound the same:

- android/app/src/main/res/raw/tick.wav
- windows/src/GoalMaker.App/Assets/tick.wav

Pure Python (math, struct, wave); the output is byte-identical on every run.
"""

from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUTS = (
    ROOT / "android" / "app" / "src" / "main" / "res" / "raw" / "tick.wav",
    ROOT / "windows" / "src" / "GoalMaker.App" / "Assets" / "tick.wav",
)
RATE = 44_100
LENGTH = 0.07
PARTIALS = ((1_900.0, 0.7), (2_850.0, 0.3))
DECAY = 0.012
ATTACK = 0.0015
VOLUME = 0.45


def samples() -> bytes:
    frames = bytearray()
    for index in range(int(RATE * LENGTH)):
        t = index / RATE
        envelope = min(1.0, t / ATTACK) * math.exp(-t / DECAY)
        value = sum(weight * math.sin(2 * math.pi * frequency * t) for frequency, weight in PARTIALS)
        frames += struct.pack("<h", round(value * envelope * VOLUME * 32_767))
    return bytes(frames)


def main() -> int:
    data = samples()
    for output in OUTPUTS:
        output.parent.mkdir(parents=True, exist_ok=True)
        with wave.open(str(output), "wb") as sound:
            sound.setnchannels(1)
            sound.setsampwidth(2)
            sound.setframerate(RATE)
            sound.writeframes(data)
        print(f"Wrote {output.relative_to(ROOT)} ({output.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
