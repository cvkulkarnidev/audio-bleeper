#!/usr/bin/env python3
"""Generate reproducible spoken WAV examples for Android and CI.
Speech is synthetic on purpose: same examples for every APK build.
"""
import json
import subprocess
import wave
from pathlib import Path

EXAMPLES = [
    {"id": "clean", "name": "Clean control (no bleep)", "text": "The morning sun is shining. Please enjoy this quiet clean example.", "expected": [], "purpose": "No word should be censored."},
    {"id": "mild", "name": "Mild words (Strict preset)", "text": "What the hell is happening? Damn, this is really strange.", "expected": ["hell", "damn"], "purpose": "Try Strict and Balanced presets to compare."},
    {"id": "moderate", "name": "Moderate profanity", "text": "This is bullshit. Stop that crap right now.", "expected": ["bullshit", "crap"], "purpose": "Tests the default moderate word list."},
    {"id": "custom", "name": "Custom phrase (purple robot)", "text": "The purple robot is speaking. The purple robot is moving.", "expected": ["purple robot"], "purpose": "Add 'purple robot' to custom words; expect two bleeps."},
]

def main():
    target = Path("app/src/main/assets/examples")
    target.mkdir(parents=True, exist_ok=True)
    (target / "manifest.json").write_text(json.dumps(EXAMPLES, indent=2) + "\n")
    for item in EXAMPLES:
        source = target / (item["id"] + ".source.wav")
        output = target / (item["id"] + ".wav")
        subprocess.run(["espeak", "-v", "en-us", "-s", "135", "-w", str(source), item["text"]], check=True)
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
                        "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le", str(output)], check=True)
        source.unlink()
        with wave.open(str(output), "rb") as audio:
            duration_ms = round(audio.getnframes() * 1000 / audio.getframerate())
        hours, remain = divmod(duration_ms, 3600000)
        minutes, remain = divmod(remain, 60000)
        seconds, milliseconds = divmod(remain, 1000)
        end = f"{hours:02}:{minutes:02}:{seconds:02},{milliseconds:03}"
        # Whole-clip SRT fallback deliberately tests the subtitle engine rather
        # than pretending to provide speech-aligned timestamps.
        (target / (item["id"] + ".srt")).write_text(
            "1\n00:00:00,000 --> " + end + "\n" + item["text"] + "\n",
            encoding="utf-8",
        )
        print(f"{item['id']}: {output.stat().st_size} bytes - {item['text']}")
    print(f"Generated {len(EXAMPLES)} reproducible examples")

if __name__ == "__main__":
    main()
