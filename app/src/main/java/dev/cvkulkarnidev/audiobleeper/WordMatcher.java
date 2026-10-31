package dev.cvkulkarnidev.audiobleeper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure word-sequence matcher; never censor substrings within longer words. */
public final class WordMatcher {
    private WordMatcher() {}

    public static final class Interval {
        public final long startMs, endMs;
        Interval(long startMs, long endMs) {
            this.startMs = startMs; this.endMs = endMs;
        }
    }

    public static List<Interval> find(List<OfflineAnalyzer.Word> recognized,
                                      String blockedPhrase, int beforeMs, int afterMs) {
        List<Interval> result = new ArrayList<>();
        if (blockedPhrase == null || blockedPhrase.trim().isEmpty()) return result;
        String[] tokens = blockedPhrase.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (tokens.length == 0) return result;
        for (int i = 0; i + tokens.length <= recognized.size(); i++) {
            boolean equal = true;
            for (int j = 0; j < tokens.length; j++) {
                if (!tokens[j].equals(recognized.get(i + j).text.toLowerCase(Locale.ROOT))) {
                    equal = false;
                    break;
                }
            }
            if (equal) {
                long start = Math.max(0, recognized.get(i).startMs - Math.max(0, beforeMs));
                long end = recognized.get(i + tokens.length - 1).endMs + Math.max(0, afterMs);
                result.add(new Interval(start, end));
            }
        }
        return result;
    }
}
