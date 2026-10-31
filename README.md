# Audio Bleeper — Android v0.2

An experimental **offline profanity censor** for locally selected audio/video.

## What's new in v0.2

- **Automatic speech recognition**: pick a local audio/video file, then tap **Analyze spoken words offline (AI)**. The bundled Vosk English model decodes speech and estimates word start/end times **before** playback.
- Censor matching words while playing the media. No manual captions are required for the AI mode.
- Default profanity list and custom words/phrases: add, remove, enable, disable and use Balanced, Strict or Off presets.
- Existing SRT subtitle import still supported (whole caption or rough word timing modes).
- Manual bleep test, local playback and seek.
- Offline recognition: **no internet or microphone permission** and no uploads to a server.

## Important limits

**v0.2 does not recognize words while audio is streaming.** It analyzes the selected file first, then mutes and adds bleeps during subsequent playback. It cannot intercept system-wide audio from YouTube/Netflix/Instagram or protected apps. Accuracy is imperfect, especially for music, profanity not in the English model's vocabulary, Hindi/Marathi, accents or overlapping speakers.

**Playback censorship is best-effort.** v0.2 uses a UI-thread 25 ms playback position polling loop and Android ToneGenerator. Some portions of a word may leak, and audio may briefly click. Don't use this for a strict child-safety guarantee. The next milestone is sample-accurate PCM audio replacement and buffered streaming recognition.

The media file needs a supported audio track (MediaExtractor/MediaCodec). Analysis is currently limited to files of up to 30 minutes. First use copies a bundled ~40MB compressed model into app-private storage; total installation size and storage needs are substantially larger than v0.1.

## Get an APK

Go to [Actions → Android Debug APK](../../actions/workflows/android.yml). Open a **successful** run and download the `audio-bleeper-v0.2-debug-apk` artifact. Unzip it and install `app-debug.apk` on Android 8+.

## Test

1. Select a local MP3/M4A/MP4 file with spoken English.
2. Tap **Analyze spoken words offline (AI)** and allow it to finish.
3. Confirm the recognized-word and bleep-interval counts, then press Play.
4. Test profanity and change the custom blocked words. Playback interval matching updates without re-running recognition.
5. Optionally load matching SRT subtitles; caption-based intervals are combined with AI intervals and manual intervals.

## Build locally

Requires JDK 17, Android SDK 35 and Gradle 8.9. Download and extract the official model into `app/src/main/assets/vosk-model-small-en-us-0.15/` before running `gradle :app:assembleDebug`. The GitHub Actions workflow performs this automatically.

Offline recognition powered by [Vosk](https://alphacephei.com/vosk/), using their [small English model](https://alphacephei.com/vosk/models) (Apache-2.0 license). Android UI and player use Android framework APIs.

## Proposed v0.3

- Native/Media3 PCM processing for sample-accurate censorship.
- Better alignment, confidence scoring, human review/override, phrase matching across uncertain ASR.
- Streaming audio buffering and ASR while playing in-app content.
- Multilingual speech models and more robust Hindi/Marathi support.
- Rootless system-audio capture experiment only where Android/apps permit it.
