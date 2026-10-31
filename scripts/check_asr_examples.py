#!/usr/bin/env python3
"""Real recognizer smoke test against generated spoken clips.

Exit non-zero if the model crashes, returns no words for all fixtures, or
if output timestamps are invalid. Log expected-word recall without pretending
that recognition is reliable enough to certify censorship.
"""
import json
import wave
from pathlib import Path
from vosk import Model, KaldiRecognizer, SetLogLevel

SetLogLevel(-1)
model = Model("app/src/main/assets/vosk-model-small-en-us-0.15")
examples = json.loads(Path("app/src/main/assets/examples/manifest.json").read_text())
assert examples
any_words = False

for example in examples:
    file = Path("app/src/main/assets/examples") / (example["id"] + ".wav")
    with wave.open(str(file), "rb") as wav:
        assert wav.getnchannels() == 1, file
        assert wav.getsampwidth() == 2, file
        assert wav.getframerate() == 16000, file
        rec = KaldiRecognizer(model, 16000)
        rec.SetWords(True)
        words = []
        while chunk := wav.readframes(3200):
            if rec.AcceptWaveform(chunk):
                words.extend(json.loads(rec.Result()).get("result", []))
        words.extend(json.loads(rec.FinalResult()).get("result", []))
        duration = wav.getnframes() / wav.getframerate()
    assert all(0 <= w["start"] < w["end"] <= duration + 0.5 for w in words), example["id"]
    assert words, f"Recognizer produced no words for {example['id']}"
    any_words = True
    transcript = " ".join(w["word"] for w in words)
    expected = example["expected"]
    detected = [word for word in expected if word in transcript]
    print(f"{example['id']}: {transcript}")
    print(f"  expected words/phrases: {expected}; found exact: {detected}; count: {len(words)}")
    print(f"  first/last timestamps: {words[0]['start']:.2f} - {words[-1]['end']:.2f} s")
    # Exact profanity recall varies by TTS voice and model vocabulary; this
    # is deliberately reported as an observed metric rather than a pass.
assert any_words
print("PASS: model loads, decodes spoken clips, produces valid word timestamps")
