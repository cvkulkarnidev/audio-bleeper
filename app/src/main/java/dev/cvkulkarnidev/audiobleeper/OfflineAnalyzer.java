package dev.cvkulkarnidev.audiobleeper;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline media analysis: decode compressed audio from a content Uri, convert
 * interleaved PCM to 16 kHz mono and collect Vosk word-level timestamps.
 * No microphone or network permission is used.
 *
 * Recognition runs BEFORE playback, not in real-time. Audio remains on device.
 */
public final class OfflineAnalyzer {
    private static final int RATE = 16000;
    private static final String ASSET_MODEL = "vosk-model-small-en-us-0.15";
    private static final long MAX_US = 30L * 60 * 1_000_000;

    public interface Progress { void onProgress(String message); }
    public static final class Word {
        public final long startMs, endMs;
        public final String text;
        Word(long startMs, long endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }
    }
    private OfflineAnalyzer() {}

    public static List<Word> analyze(Context context, Uri uri, Progress progress) throws Exception {
        progress.onProgress("Preparing offline recognition model (first run may take longer)...");
        File modelDirectory = prepareModel(context);
        List<Word> words = new ArrayList<>();
        try (Model model = new Model(modelDirectory.getAbsolutePath());
             Recognizer recognizer = new Recognizer(model, RATE)) {
            recognizer.setWords(true);
            progress.onProgress("Decoding and recognizing audio...");
            decode(context, uri, recognizer, words, progress);
            collect(recognizer.getFinalResult(), words, 0);
        }
        return words;
    }

    private static void collect(String result, List<Word> output, long offsetMs) {
        if (result == null) return;
        try {
            JSONArray entries = new JSONObject(result).optJSONArray("result");
            if (entries == null) return;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject word = entries.getJSONObject(i);
                String text = word.optString("word", "").trim().toLowerCase(Locale.ROOT);
                if (text.isEmpty()) continue;
                long start = Math.max(0, Math.round(word.optDouble("start", 0) * 1000.0) + offsetMs);
                long end = Math.max(start + 1, Math.round(word.optDouble("end", 0) * 1000.0) + offsetMs);
                output.add(new Word(start, end, text));
            }
        } catch (Exception ignored) {
            // Vosk may return a text-only result when a fragment has no recognized words.
        }
    }

    private static File prepareModel(Context context) throws Exception {
        File root = new File(context.getNoBackupFilesDir(), ASSET_MODEL);
        File marker = new File(root, ".extracted");
        if (marker.isFile()) return root;
        File temporary = new File(context.getNoBackupFilesDir(), ASSET_MODEL + "-unpacking");
        deleteTree(temporary);
        if (!temporary.mkdirs()) throw new IllegalStateException("Can't create model directory");
        copyAssets(context.getAssets(), ASSET_MODEL, temporary);
        if (!new File(temporary, "am").isDirectory()
                || !new File(temporary, "conf").isDirectory()
                || !new File(temporary, "graph").isDirectory()) {
            throw new IllegalStateException("Bundled speech model is incomplete");
        }
        if (!markerExists(temporary)) throw new IllegalStateException("Can't create model marker");
        deleteTree(root);
        if (!temporary.renameTo(root)) throw new IllegalStateException("Can't install model");
        return root;
    }

    private static boolean markerExists(File root) throws Exception {
        return new File(root, ".extracted").createNewFile();
    }

    private static void copyAssets(AssetManager assets, String assetPath, File destination) throws Exception {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            try (InputStream in = assets.open(assetPath);
                 FileOutputStream out = new FileOutputStream(destination)) {
                byte[] bytes = new byte[32768];
                int n;
                while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            }
            return;
        }
        if (!destination.exists() && !destination.mkdirs())
            throw new IllegalStateException("Can't create " + destination);
        for (String child : children) {
            copyAssets(assets, assetPath + "/" + child, new File(destination, child));
        }
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] files = file.listFiles();
        if (files != null) for (File f : files) deleteTree(f);
        file.delete();
    }

    private static void decode(Context context, Uri uri, Recognizer recognizer,
                               List<Word> result, Progress progress) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(context, uri, null);
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i; format = candidate; break;
                }
            }
            if (track < 0 || format == null) throw new IllegalArgumentException("No readable audio track");
            if (format.containsKey(MediaFormat.KEY_DURATION)
                    && format.getLong(MediaFormat.KEY_DURATION) > MAX_US)
                throw new IllegalArgumentException("v0.2 supports media up to 30 minutes per analysis");
            extractor.selectTrack(track);
            String mime = format.getString(MediaFormat.KEY_MIME);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding = AudioFormat.ENCODING_PCM_16BIT;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            Converter converter = new Converter(recognizer, result);
            boolean inputDone = false, outputDone = false;
            int loopsWithoutOutput = 0;
            int lastProgress = -1;
            long offsetMs = -1L;
            long expectedUs = format.containsKey(MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION) : MAX_US;
            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer input = codec.getInputBuffer(inputIndex);
                        if (input == null) throw new IllegalStateException("No decoder input buffer");
                        input.clear();
                        int size = extractor.readSampleData(input, 0);
                        long pts = extractor.getSampleTime();
                        if (size < 0 || pts > MAX_US) {
                            codec.queueInputBuffer(inputIndex, 0, 0, Math.max(0, pts),
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            if (offsetMs == -1L) offsetMs = Math.max(0L, pts / 1000);
                            codec.queueInputBuffer(inputIndex, 0, size, pts, 0);
                            extractor.advance();
                        }
                    }
                }
                int outputIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat actual = codec.getOutputFormat();
                    rate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    encoding = actual.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            ? actual.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            : AudioFormat.ENCODING_PCM_16BIT;
                    if (encoding != AudioFormat.ENCODING_PCM_16BIT
                            && encoding != AudioFormat.ENCODING_PCM_FLOAT)
                        throw new IllegalArgumentException("Unsupported decoded PCM format: " + encoding);
                } else if (outputIndex >= 0) {
                    loopsWithoutOutput = 0;
                    ByteBuffer buffer = codec.getOutputBuffer(outputIndex);
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);
                        buffer.order(ByteOrder.LITTLE_ENDIAN);
                        converter.consume(buffer, rate, channels, encoding,
                                Math.max(0, offsetMs));
                    }
                    int progressStep = (int)(Math.max(0, info.presentationTimeUs) * 10L
                            / Math.max(1L, expectedUs));
                    if (progressStep > lastProgress) {
                        lastProgress = progressStep;
                        int percent = Math.min(100, progressStep * 10);
                        progress.onProgress("Recognizing audio: " + percent + "% decoded...");
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outputIndex, false);
                } else if (++loopsWithoutOutput > 1000) {
                    throw new IllegalStateException("Audio decoder stalled");
                }
            }
            converter.finish(Math.max(0, offsetMs));
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) {}
                codec.release();
            }
            extractor.release();
        }
    }

    private static final class Converter {
        private final Recognizer recognizer;
        private final List<Word> output;
        private final short[] samples = new short[3200];
        private int sampleCount = 0;
        private long sourceFrame = 0;
        private double nextSample = 0;
        private short previous = 0;
        private int inputRate = -1;

        Converter(Recognizer recognizer, List<Word> output) {
            this.recognizer = recognizer; this.output = output;
        }
        void consume(ByteBuffer input, int rate, int channels, int encoding, long offsetMs) {
            if (rate <= 0 || channels <= 0 || channels > 8)
                throw new IllegalArgumentException("Invalid channel configuration");
            if (inputRate != -1 && inputRate != rate)
                throw new IllegalArgumentException("Mid-stream sample rate change not supported");
            inputRate = rate;
            int bytes = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
            while (input.remaining() >= channels * bytes) {
                float average = 0f;
                for (int channel = 0; channel < channels; channel++) {
                    if (bytes == 4) average += input.getFloat() * 32767f;
                    else average += input.getShort();
                }
                short current = (short)Math.max(-32768, Math.min(32767,
                        Math.round(average / channels)));
                if (sourceFrame > 0) {
                    while (nextSample <= sourceFrame) {
                        double fraction = nextSample - (sourceFrame - 1);
                        double interpolated = previous + (current - previous) * fraction;
                        samples[sampleCount++] = (short)Math.max(-32768, Math.min(32767,
                                Math.round(interpolated)));
                        if (sampleCount == samples.length) flush(offsetMs);
                        nextSample += (double)rate / RATE;
                    }
                }
                previous = current;
                sourceFrame++;
            }
        }
        void finish(long offsetMs) {
            if (sampleCount > 0) flush(offsetMs);
        }
        private void flush(long offsetMs) {
            if (recognizer.acceptWaveForm(samples, sampleCount))
                collect(recognizer.getResult(), output, offsetMs);
            sampleCount = 0;
        }
    }
}
