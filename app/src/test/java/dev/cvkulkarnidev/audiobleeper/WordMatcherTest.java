package dev.cvkulkarnidev.audiobleeper;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class WordMatcherTest {
    @Test public void matchedPhraseOccursTwiceWithTiming() {
        List<OfflineAnalyzer.Word> words = new ArrayList<>();
        words.add(new OfflineAnalyzer.Word(100, 250, "purple"));
        words.add(new OfflineAnalyzer.Word(260, 490, "robot"));
        words.add(new OfflineAnalyzer.Word(510, 680, "and"));
        words.add(new OfflineAnalyzer.Word(800, 1000, "purple"));
        words.add(new OfflineAnalyzer.Word(1050, 1250, "robot"));
        List<WordMatcher.Interval> hits = WordMatcher.find(words, "purple robot", 75, 90);
        assertEquals(2, hits.size());
        assertEquals(25, hits.get(0).startMs);
        assertEquals(580, hits.get(0).endMs);
        assertEquals(725, hits.get(1).startMs);
        assertEquals(1340, hits.get(1).endMs);
    }
    @Test public void doesNotMatchSubstringOrMissingWords() {
        List<OfflineAnalyzer.Word> words = new ArrayList<>();
        words.add(new OfflineAnalyzer.Word(0, 100, "class"));
        words.add(new OfflineAnalyzer.Word(110, 260, "please"));
        assertTrue(WordMatcher.find(words, "ass", 20, 20).isEmpty());
        assertTrue(WordMatcher.find(words, "please now", 20, 20).isEmpty());
        assertEquals(1, WordMatcher.find(words, "please", 20, 20).size());
        assertTrue(WordMatcher.find(words, "", 20, 20).isEmpty());
    }
}
