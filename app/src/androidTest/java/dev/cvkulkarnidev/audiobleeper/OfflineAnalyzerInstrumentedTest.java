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
}
