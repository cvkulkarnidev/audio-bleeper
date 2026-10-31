# Audio Bleeper (Android)

An experimental offline Android media player that bleeps selected words using imported SRT subtitle timing, plus manual censor intervals.

## v0.1
- Play locally selected audio/video files.
- Import matching .srt subtitles and censor profanity based on subtitle cues.
- Customize a built-in profanity dictionary with enable/disable, add and remove.
- Balanced, Strict, Off presets.
- Full-caption (safer but broader) and estimated word-position modes (approximate, may miss speech).
- Manually add a 0.7-second bleep at the playhead.

**Important:** This first prototype is NOT automatic speech recognition and does NOT intercept audio from external apps. Caption-based timing is not equivalent to word-level speech alignment. v0.1 audio muting uses a position poll, not sample-accurate PCM processing.

## APK build
Go to the Actions tab, open the Android Debug APK workflow's latest successful run, and download the audio-bleeper-v0.1-debug-apk artifact. Unzip and install app-debug.apk (Android 8+). This is a debug APK.

To build locally, use JDK 17, Android SDK 35, Gradle 8.9 and run gradle :app:assembleDebug.

## Test
Pick an audio/video file, load a matching SRT caption file, customize the blocked words, then play. You will hear beeped caption intervals whenever captions contain blocked words. A manual Bleep here control tests real-time replacement with no subtitles.

## Next
Streaming on-device keyword spotting/ASR, accurate word timestamps, buffered PCM playback, testing cross-language recognition and latency. System-wide intercept/replacement requires Android privileges and is not promised.
