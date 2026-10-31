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
results = []

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
    tokens = [item["word"] for item in words]
    observed = {}
    for phrase in set(expected):
        target = phrase.split()
        count = sum(tokens[i:i + len(target)] == target for i in range(len(tokens) - len(target) + 1))
        observed[phrase] = count
    exact_found = sum(min(observed[phrase], expected.count(phrase)) for phrase in observed)
    results.append({"example": example["id"], "transcript": transcript,
                    "expected": expected, "observed_counts": observed,
                    "exact_found": exact_found, "expected_count": len(expected),
                    "recognized_word_count": len(words)})
    print(f"{example['id']}: {transcript}")
    print(f"  exact expected occurrences: {exact_found}/{len(expected)}; "
          f"details: {observed}; recognized words: {len(words)}")
    print(f"  first/last timestamps: {words[0]['start']:.2f} - {words[-1]['end']:.2f} s")
    # Exact profanity recall varies by TTS voice and model vocabulary; this
    # is deliberately reported as an observed metric rather than a pass.
assert any_words
# Deterministic regression coverage for the words that this model actually hears.
moderate = next(row for row in results if row["example"] == "moderate")
assert moderate["exact_found"] == 2, "Previously passing moderate profanity was lost"
clean = next(row for row in results if row["example"] == "clean")
common = {"fuck", "shit", "bullshit", "crap", "bitch", "damn", "hell"}
assert not any(word in clean["transcript"].split() for word in common), "False positive in clean clip"
Path("asr-regression-report.json").write_text(json.dumps(results, indent=2) + "\n")
print("PASS: model loads, produces valid word timings, detects moderate examples, and clean clip has no common profanity")
print("CAUTION: mild example is a known false negative; custom phrase is not consistently recognized.")
