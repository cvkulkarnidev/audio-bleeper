package dev.cvkulkarnidev.audiobleeper;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import static org.junit.Assert.*;

/**
 * Runs a real bundled English speech model and Android MediaCodec decode
 * against our own sample WAV. Unlike the CI desktop test, this exercises
 * OfflineAnalyzer on an Android emulator.
 */
@RunWith(AndroidJUnit4.class)
public class OfflineAnalyzerInstrumentedTest {
    @Test public void cleanExampleProducesTimedWordsOffline() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File example = new File(context.getCacheDir(), "instrumentation-clean.wav");
        try (InputStream in = context.getAssets().open("examples/clean.wav");
             FileOutputStream out = new FileOutputStream(example)) {
            byte[] buffer = new byte[32768];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
        }
        assertTrue(example.isFile());
        assertTrue(example.length() > 10000);
        List<OfflineAnalyzer.Word> words = OfflineAnalyzer.analyze(
                context, Uri.fromFile(example), message -> {});
        assertTrue("Expected recognized speech from bundled clean WAV", words.size() >= 3);
        long lastStart = -1;
        for (OfflineAnalyzer.Word word : words) {
            assertFalse(word.text.isEmpty());
            assertTrue("start timestamps monotonic", word.startMs >= lastStart);
            assertTrue("word end after start", word.endMs > word.startMs);
            lastStart = word.startMs;
        }
    }

    @Test public void phraseMatchingUsesExactWordsAndTimestamps() {
        java.util.ArrayList<OfflineAnalyzer.Word> transcript = new java.util.ArrayList<>();
        transcript.add(new OfflineAnalyzer.Word(100, 250, "class"));
        transcript.add(new OfflineAnalyzer.Word(300, 480, "purple"));
        transcript.add(new OfflineAnalyzer.Word(480, 720, "robot"));
        transcript.add(new OfflineAnalyzer.Word(800, 1000, "purple"));
        transcript.add(new OfflineAnalyzer.Word(1020, 1290, "robot"));
        assertEquals("No substring false positives", 0,
                WordMatcher.find(transcript, "ass", 100, 100).size());
        java.util.List<WordMatcher.Interval> two =
                WordMatcher.find(transcript, "purple robot", 100, 130);
        assertEquals("Must find both occurrences", 2, two.size());
        assertEquals(200L, two.get(0).startMs);
        assertEquals(850L, two.get(0).endMs);
        assertEquals(700L, two.get(1).startMs);
        assertEquals(1420L, two.get(1).endMs);
        assertEquals(0, WordMatcher.find(transcript, "robot purple new", 0, 0).size());
    }
}
