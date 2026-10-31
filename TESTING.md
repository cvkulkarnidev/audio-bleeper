# Audio Bleeper: reproducible verification plan

This project is a prototype, **not a dependable child-safe censorship system**. The goal of this phase is to measure recognition and bleeping separately.

## In-app examples (Test Lab)

All sample clips are offline, generated with eSpeak on GitHub Actions and bundled in the debug APK. No internet is required to run them.

| Test | Spoken sentence | Setup | Expected behavior |
|---|---|---|---|
| A — clean | "The morning sun is shining. Please enjoy this quiet clean example." | Balanced preset | Zero censor intervals; no beeps |
| B — mild | "What the hell is happening? Damn, this is really strange." | Strict preset | **Desired:** 2 bleeps. **Known problem in v0.2:** Vosk missed both in the initial synthetic speech test. |
| C — moderate | "This is bullshit. Stop that crap right now." | Balanced preset | **Desired:** 2 bleeps; both were recognized in the first desktop test |
| D — custom | "The purple robot is speaking. The purple robot is moving." | Add & enable "purple robot" | **Desired:** 2 bleeps. **Initial desktop result:** only the second phrase was recognized exactly. |

### Steps

1. Open the app and select **A**, **B**, **C**, or **D** in **Test Lab**.
2. It will load the bundled WAV and start an offline AI scan automatically.
3. Wait for the recognized transcript and recognized-word count.
4. Review **recognized text**, **expected occurrence counts**, and **generated censor intervals**. Check whether the waveform is actually censored by pressing Play and listening.
5. Repeat with an alternative preset and customized word list. After adding a word, intervals are recalculated without rerunning speech recognition.
6. Tap **Compare: load matched SRT subtitles** to test the bleep/mute behavior independently of AI. SRT fallback uses a full-cue timestamp and intentionally bleeps an **entire sentence**, not the precise word.

### How to interpret failures

- **Recognized text doesn't contain word:** ASR failure. Subtitles can still drive bleeping; customizing the dictionary cannot recover unheard words.
- **Recognized text contains word but bleep interval is zero:** rule matching or preset issue.
- **Interval count looks right but the word leaks:** audio output/timing issue. The current 25-ms poll is not sample-accurate.
- **No audio plays:** media decoding or Android MediaPlayer support issue.
- **Bleep overlaps surrounding words:** timing offset or whole-SRT-cue behavior.

## Automated tests

GitHub Actions:
- Generates the same WAV clips via eSpeak + FFmpeg.
- Runs actual Vosk English recognition against WAVs and logs recognized text and timestamps.
- Compiles Android APK and publishes an artifact.
- Runs an Android emulator instrumentation test for real AudioCodec-to-Vosk operation and exact-word matching (separate job).

The first Linux ASR smoke run (2026-10-31) produced:

| Fixture | Recognized transcript (approx.) | Exact expected |
|---|---|---|
| clean | "the morning sun is shining please enjoy disquiet clean and ma'am for" | Not applicable |
| mild | "that's a fairly happening them this is really strange" | 0/2 |
| moderate | "this is bullshit top that crap right now" | 2/2 |
| custom | "the purple robe out in the king the purple robot is moving" | 1/2 phrase occurrences |

**This demonstrates that the small English model is inadequate for reliable censorship.** False negatives are unacceptable for any strict filtering claim. The app is currently an experimental prototype.

## Current unanswered questions

- Does the Android emulator test pass with the current model and audio decoder?
- Does the sample audio play correctly on a real phone with the intended loudspeaker/headphone output?
- Do mute and tone commands cover the precise word boundaries? They are not guaranteed in this architecture.
- Can a streaming keyword model offer materially better recall at a tolerable false-positive rate?

Do not describe this prototype as "real-time AI profanity filtering" without qualifying that word recognition happens in a pre-scan and playback muting is best-effort.
