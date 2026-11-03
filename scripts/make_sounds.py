#!/usr/bin/env python3
"""Synthesize the answer sounds for Trump or Trap.

Usage: make_sounds.py <raw_dir>

Writes answer_right.wav (a bright rising two-note chime) and answer_wrong.wav
(a low falling two-note buzz) to <raw_dir>, normally app/src/main/res/raw.
Both are generated from plain tones, so there is no third-party audio.
"""
import argparse
import math
import pathlib
import struct
import wave

RATE = 44100


def note(freq, start, length, harmonics, decay, glide=0.0, attack=0.006):
    """One note as (start sample, samples): harmonic partials under an exponential decay.

    harmonics maps partial number to its level; glide bends the pitch by that
    fraction over the note's length.
    """
    count = int(length * RATE)
    samples = []
    phase = 0.0
    for i in range(count):
        t = i / RATE
        f = freq * (1 + glide * t / length)
        phase += 2 * math.pi * f / RATE
        envelope = min(1.0, t / attack) * math.exp(-t / decay)
        # Fade the last few milliseconds so notes never end in a click.
        envelope *= min(1.0, (count - i) / (0.008 * RATE))
        samples.append(envelope * sum(level * math.sin(n * phase) for n, level in harmonics.items()))
    return int(start * RATE), samples


def mix(notes, peak):
    """Lays the notes over each other and scales the loudest sample to peak."""
    end = max(start + len(samples) for start, samples in notes)
    out = [0.0] * end
    for start, samples in notes:
        for i, s in enumerate(samples):
            out[start + i] += s
    loudest = max(abs(s) for s in out)
    return [s * peak / loudest for s in out]


def write_wav(path, samples):
    with wave.open(str(path), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(b"".join(struct.pack("<h", round(s * 32767)) for s in samples))


# Bell-like: strong fundamental, a touch of octave and twelfth.
BELL = {1: 1.0, 2: 0.28, 3: 0.08}
# Buzzy: odd partials, like a soft square wave.
BUZZ = {1: 1.0, 3: 0.33, 5: 0.2, 7: 0.14, 9: 0.1}


def right():
    return mix(
        [
            note(1318.5, 0.0, 0.16, BELL, decay=0.09),   # E6
            note(1760.0, 0.09, 0.55, BELL, decay=0.22),  # A6
        ],
        peak=0.5,
    )


def wrong():
    return mix(
        [
            note(233.1, 0.0, 0.2, BUZZ, decay=0.25, glide=-0.03),   # B-flat 3
            note(174.6, 0.18, 0.42, BUZZ, decay=0.3, glide=-0.06),  # F3, sagging
        ],
        peak=0.45,
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("raw_dir", type=pathlib.Path)
    args = parser.parse_args()
    args.raw_dir.mkdir(parents=True, exist_ok=True)
    write_wav(args.raw_dir / "answer_right.wav", right())
    write_wav(args.raw_dir / "answer_wrong.wav", wrong())


if __name__ == "__main__":
    main()
