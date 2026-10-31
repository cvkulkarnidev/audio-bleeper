package dev.cvkulkarnidev.audiobleeper;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.VideoView;
import android.text.InputType;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int PICK_MEDIA = 101, PICK_SRT = 102;
    private static final int BG = 0xFF0B1220, CARD = 0xFF172338, TEXT = 0xFFF0F5FC;
    private static final int MUTED = 0xFFA7B8CE, ACCENT = 0xFF44D6AA;
    private final ArrayList<WordEntry> words = new ArrayList<>();
    private final ArrayList<Caption> captions = new ArrayList<>();
    private final ArrayList<OfflineAnalyzer.Word> detectedWords = new ArrayList<>();
    private final ArrayList<Segment> manual = new ArrayList<>();
    private final ArrayList<Segment> segments = new ArrayList<>();
    private final Handler timer = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private VideoView video;
    private MediaPlayer player;
    private ToneGenerator tone;
    private TextView status, summary, position, wordCount, diagnostics;
    private LinearLayout wordContainer;
    private SeekBar seek;
    private Button playButton, wordToggle, modeButton, analyzeButton;
    private Uri selectedMedia;
    private volatile boolean analyzing;
    private boolean autoAnalyze;
    private String activeExample;
    private String exampleSentence = "";
    private String[] expectedExample = new String[0];
    private boolean ready, beeping, showWords, entireCue;

    private static class WordEntry {
        String text, group;
        boolean enabled;
        WordEntry(String text, String group, boolean enabled) {
            this.text = text; this.group = group; this.enabled = enabled;
        }
    }
    private static class Caption {
        final long start, end;
        final String text;
        Caption(long start, long end, String text) {
            this.start = start; this.end = end; this.text = text;
        }
    }
    private static class Segment {
        long start, end;
        Segment(long start, long end) {
            this.start = Math.max(0, start);
            this.end = Math.max(this.start, end);
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("audio_bleeper", MODE_PRIVATE);
        entireCue = prefs.getBoolean("entire_cue", true);
        loadWords();
        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80); }
        catch (RuntimeException ex) { tone = null; }
        drawUi();
        timer.post(ticker);
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(text);
        v.setTextColor(color); v.setTextSize(size);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private void space(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }
    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }
    private Button button(String title, int color, Runnable click) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false);
        b.setTextColor(color == ACCENT ? BG : TEXT); b.setTextSize(13);
        b.setBackground(bg(color, 12)); b.setMinHeight(dp(46));
        b.setOnClickListener(v -> click.run()); return b;
    }
    private LinearLayout card(LinearLayout root, String title, String desc) {
        LinearLayout v = column(); v.setBackground(bg(CARD, 18));
        v.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(14); root.addView(v, p);
        v.addView(label(title, 18, TEXT, true));
        if (desc != null) { space(v, 6); v.addView(label(desc, 12, MUTED, false)); }
        space(v, 12); return v;
    }

    private void drawUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(18), dp(20), dp(18), dp(30));
        scroll.addView(root);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(0, bars.top, 0, bars.bottom); return insets;
            });
        } else scroll.setFitsSystemWindows(true);
        setContentView(scroll);
        root.addView(label("AUDIO BLEEPER", 26, ACCENT, true));
        space(root, 5);
        root.addView(label("Your words. Your rules. Your listening.", 14, MUTED, false));
        space(root, 20);

        LinearLayout demo = card(root, "00  Test lab — built-in examples",
                "These four spoken samples are generated reproducibly in GitHub Actions and bundled in the APK. Pick one to load and analyze automatically.");
        demo.addView(button("A  Clean speech · expect zero bleeps", 0xFF30435B, () -> loadExample("clean")),
                new LinearLayout.LayoutParams(-1, dp(47)));
        space(demo, 7);
        demo.addView(button("B  Mild words · select Strict preset", 0xFF30435B, () -> loadExample("mild")),
                new LinearLayout.LayoutParams(-1, dp(47)));
        space(demo, 7);
        demo.addView(button("C  Moderate profanity · Balanced preset", 0xFF30435B, () -> loadExample("moderate")),
                new LinearLayout.LayoutParams(-1, dp(47)));
        space(demo, 7);
        demo.addView(button("D  Custom phrase · add purple robot", 0xFF30435B, () -> loadExample("custom")),
                new LinearLayout.LayoutParams(-1, dp(47)));
        space(demo, 10);
        diagnostics = label("Select an example to view its expected result and recognized transcript.", 12, MUTED, false);
        demo.addView(diagnostics);

        LinearLayout media = card(root, "01  Listen & censor",
                "Choose local audio/video. The app runs without internet permission.");
        media.addView(button("Choose audio / video", ACCENT, this::chooseMedia),
                new LinearLayout.LayoutParams(-1, dp(48)));
        space(media, 12);
        video = new VideoView(this);
        video.setBackgroundColor(Color.BLACK);
        media.addView(video, new LinearLayout.LayoutParams(-1, dp(190)));
        video.setOnPreparedListener(mp -> {
            player = mp; ready = true;
            playButton.setText("Play");
            position.setText("00:00 / " + clock(video.getDuration()));
            resetOutput(); setStatus("Media ready. Press Play.");
            if (autoAnalyze) { autoAnalyze = false; analyzeMedia(); }
        });
        video.setOnCompletionListener(mp -> {
            playButton.setText("Play"); resetOutput(); setStatus("Playback finished.");
        });
        video.setOnErrorListener((mp, what, extra) -> {
            ready = false; resetOutput();
            setStatus("Unable to play this file. Try MP3, MP4 or M4A.");
            return true;
        });
        space(media, 12);
        seek = new SeekBar(this); seek.setMax(1000);
        media.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && ready)
                    position.setText(clock((long) video.getDuration() * p / 1000L)
                            + " / " + clock(video.getDuration()));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                if (ready) {
                    video.seekTo((int)((long)video.getDuration() * s.getProgress() / 1000L));
                    resetOutput();
                }
            }
        });
        position = label("00:00 / 00:00", 13, MUTED, false);
        position.setGravity(Gravity.CENTER);
        media.addView(position); space(media, 10);
        LinearLayout controls = row(); media.addView(controls);
        controls.addView(button("-10s", 0xFF30435B, () -> jump(-10000)), weight());
        playButton = button("Play", ACCENT, this::togglePlay);
        controls.addView(playButton, weight());
        controls.addView(button("+10s", 0xFF30435B, () -> jump(10000)), weight());
        space(media, 10);
        media.addView(button("Bleep here (+0.7s)", 0xFF30435B, this::addManual),
                new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout captionsCard = card(root, "02  Caption matching",
                "Import matching SRT subtitles to automatically censor their blocked words.");
        captionsCard.addView(button("Load SRT subtitles", ACCENT, this::chooseSrt),
                new LinearLayout.LayoutParams(-1, dp(46)));
        space(captionsCard, 10);
        analyzeButton = button("Analyze spoken words offline (AI)", ACCENT, this::analyzeMedia);
        captionsCard.addView(analyzeButton, new LinearLayout.LayoutParams(-1, dp(46)));
        space(captionsCard, 9);
        captionsCard.addView(label("Requires a selected file. First analysis unpacks the bundled ~40 MB English model. Detection is not guaranteed for every accent or swear word.", 12, MUTED, false));
        space(captionsCard, 11);
        modeButton = button(entireCue ? "Mode: Full caption (safer)" : "Mode: Estimated word timing",
                0xFF30435B, () -> {
                    entireCue = !entireCue;
                    prefs.edit().putBoolean("entire_cue", entireCue).apply();
                    modeButton.setText(entireCue ? "Mode: Full caption (safer)"
                            : "Mode: Estimated word timing");
                    rebuild();
                });
        captionsCard.addView(modeButton, new LinearLayout.LayoutParams(-1, dp(46)));
        space(captionsCard, 10);
        captionsCard.addView(label("Full caption covers the entire line. Estimated timing is narrower but may miss speech.", 12, MUTED, false));
        space(captionsCard, 12);
        status = label("Choose media and optionally matching captions.", 13, TEXT, false);
        captionsCard.addView(status); space(captionsCard, 5);
        summary = label("", 12, ACCENT, false); captionsCard.addView(summary);

        LinearLayout dictionary = card(root, "03  Blocked words",
                "Default profanity plus your own custom words and phrases.");
        LinearLayout presets = row(); dictionary.addView(presets);
        presets.addView(button("Balanced", ACCENT, () -> preset("balanced")), weight());
        presets.addView(button("Strict", 0xFF30435B, () -> preset("strict")), weight());
        presets.addView(button("Off", 0xFF30435B, () -> preset("off")), weight());
        space(dictionary, 12);
        wordCount = label("", 13, MUTED, false); dictionary.addView(wordCount);
        space(dictionary, 9);
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setTextSize(15); input.setTextColor(TEXT);
        input.setHintTextColor(MUTED); input.setHint("Add a word or phrase");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        dictionary.addView(input, new LinearLayout.LayoutParams(-1, dp(52)));
        space(dictionary, 6);
        dictionary.addView(button("+ Add custom word", ACCENT, () -> {
            String value = input.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) return;
            for (WordEntry w : words) {
                if (w.text.equalsIgnoreCase(value)) {
                    w.enabled = true; input.setText("");
                    saveWords(); refreshWords(); return;
                }
            }
            words.add(new WordEntry(value, "Custom", true));
            input.setText(""); saveWords(); refreshWords();
        }), new LinearLayout.LayoutParams(-1, dp(46)));
        space(dictionary, 12);
        wordToggle = button("Manage individual words", 0xFF30435B, () -> {
            showWords = !showWords;
            wordContainer.setVisibility(showWords ? View.VISIBLE : View.GONE);
            wordToggle.setText(showWords ? "Hide individual words" : "Manage individual words");
        });
        dictionary.addView(wordToggle, new LinearLayout.LayoutParams(-1, dp(46)));
        space(dictionary, 8);
        wordContainer = column(); wordContainer.setVisibility(View.GONE);
        dictionary.addView(wordContainer);
        refreshWords();

        LinearLayout limits = card(root, "Prototype limitations",
                "This version modifies locally played media, not sound from other apps.");
        limits.addView(label("v0.1 uses subtitles/manual intervals, not live AI speech recognition. Accurate word timing needs an audio model and buffering.", 13, MUTED, false));
        setStatus("Select a media file to begin.");
    }


    /**
     * v0.2: one-time, on-device media scan, BEFORE playback.
     * Vosk returns timestamps for recognized words; subsequent playback uses
     * these timestamps to mute/bleep. This is not live streaming ASR.
     */
    private void analyzeMedia() {
        if (selectedMedia == null || !ready) {
            setStatus("Select a playable media file before analyzing.");
            return;
        }
        if (analyzing) return;
        if (video.isPlaying()) {
            video.pause();
            playButton.setText("Play");
            resetOutput();
        }
        final Uri source = selectedMedia;
        analyzing = true;
        analyzeButton.setEnabled(false);
        setStatus("Preparing offline speech analysis...");
        new Thread(() -> {
            try {
                List<OfflineAnalyzer.Word> result = OfflineAnalyzer.analyze(
                        getApplicationContext(), source,
                        msg -> runOnUiThread(() -> {
                            if (source.equals(selectedMedia)) setStatus(msg);
                        }));
                runOnUiThread(() -> {
                    analyzing = false;
                    analyzeButton.setEnabled(true);
                    if (!source.equals(selectedMedia)) return;
                    detectedWords.clear();
                    detectedWords.addAll(result);
                    rebuild();
                    updateDiagnostics();
                    setStatus("AI analyzed " + result.size() + " recognized words. "
                            + "Check playback: missed speech and inaccurate timings are possible.");
                });
            } catch (Throwable ex) {
                runOnUiThread(() -> {
                    analyzing = false;
                    analyzeButton.setEnabled(true);
                    if (source.equals(selectedMedia))
                        setStatus("AI analysis failed: " + ex.getClass().getSimpleName()
                                + " - " + String.valueOf(ex.getMessage()));
                });
            }
        }, "offline-vosk-analysis").start();
    }


    /** Load a reproducible demo audio file from bundled assets and scan it. */
    private void loadExample(String id) {
        String title, sentence;
        String[] expected;
        switch (id) {
            case "clean":
                title = "Clean control"; sentence = "The morning sun is shining. Please enjoy this quiet clean example.";
                expected = new String[0]; break;
            case "mild":
                title = "Mild profanity"; sentence = "What the hell is happening? Damn, this is really strange.";
                expected = new String[]{"hell", "damn"}; break;
            case "moderate":
                title = "Moderate profanity"; sentence = "This is bullshit. Stop that crap right now.";
                expected = new String[]{"bullshit", "crap"}; break;
            case "custom":
                title = "Custom phrase"; sentence = "The purple robot is speaking. The purple robot is moving.";
                expected = new String[]{"purple robot", "purple robot"}; break;
            default: return;
        }
        try {
            File audio = new File(getCacheDir(), "example-" + id + ".wav");
            try (InputStream in = getAssets().open("examples/" + id + ".wav");
                 FileOutputStream out = new FileOutputStream(audio)) {
                byte[] buffer = new byte[8192];
                int n; while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            }
            activeExample = id;
            exampleSentence = sentence;
            expectedExample = expected;
            autoAnalyze = true;
            selectedMedia = Uri.fromFile(audio);
            captions.clear(); detectedWords.clear(); manual.clear();
            player = null; ready = false;
            resetOutput();
            playButton.setText("Play");
            video.setVideoURI(selectedMedia);
            video.requestFocus();
            updateDiagnostics();
            setStatus(title + " loaded — preparing to run offline AI recognition.");
        } catch (Exception e) {
            autoAnalyze = false;
            setStatus("Example unavailable: " + e.getMessage());
        }
    }

    private void updateDiagnostics() {
        if (diagnostics == null) return;
        if (activeExample == null) {
            diagnostics.setText("Built-in tests include a clean control, two profanity examples and a custom phrase.");
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("Source: ").append(exampleSentence).append("\n");
        if ("mild".equals(activeExample)) message.append("Use Strict preset for 'hell' and 'damn'.\n");
        if ("custom".equals(activeExample)) message.append("Add 'purple robot' to your word list and enable it.\n");
        if (detectedWords.isEmpty()) {
            message.append("Expected: ");
            message.append(expectedExample.length == 0 ? "zero bleeps" : String.join(", ", expectedExample));
            message.append("\nAI transcript: not analyzed yet.");
        } else {
            StringBuilder recognized = new StringBuilder();
            for (OfflineAnalyzer.Word word : detectedWords) {
                if (recognized.length() > 0) recognized.append(' ');
                recognized.append(word.text);
            }
            int found = 0;
            String transcript = recognized.toString();
            for (String target : expectedExample) if (transcript.contains(target)) found++;
            message.append("Recognized: ").append(transcript).append("\n")
                    .append("Exact expected phrase matches: ").append(found)
                    .append(" / ").append(expectedExample.length).append("\n")
                    .append("Generated censor intervals: ").append(segments.size())
                    .append(" (also depends on your selected word rules)");
        }
        diagnostics.setText(message);
    }

    private void chooseMedia() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*"});
        startActivityForResult(intent, PICK_MEDIA);
    }
    private void chooseSrt() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_SRT);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) {}
        if (request == PICK_MEDIA) {
            selectedMedia = uri;
            activeExample = null;
            exampleSentence = "";
            expectedExample = new String[0];
            autoAnalyze = false;
            updateDiagnostics();
            captions.clear(); detectedWords.clear(); manual.clear();
            rebuild();
            resetOutput(); player = null; ready = false;
            playButton.setText("Play");
            video.setVideoURI(uri); video.requestFocus(); setStatus("Loading media...");
        } else if (request == PICK_SRT) {
            try {
                ArrayList<Caption> resultCaptions = parseSrt(readText(uri));
                if (resultCaptions.isEmpty()) {
                    setStatus("No valid SRT entries found. Please choose an .srt file.");
                    return;
                }
                captions.clear(); captions.addAll(resultCaptions);
                rebuild(); setStatus("Loaded " + captions.size() + " caption cues.");
            } catch (Exception ex) {
                setStatus("Could not open subtitles: " + ex.getClass().getSimpleName());
            }
        }
    }
    private String readText(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalArgumentException("Empty stream");
            byte[] bytes = new byte[8192];
            int n;
            while ((n = in.read(bytes)) != -1) {
                if (out.size() + n > 2_000_000) throw new IllegalArgumentException("File too large");
                out.write(bytes, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private ArrayList<Caption> parseSrt(String raw) {
        ArrayList<Caption> parsed = new ArrayList<>();
        raw = raw.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "");
        for (String block : raw.split("\\n\\s*\\n")) {
            String[] lines = block.split("\\n");
            int timeLine = -1;
            for (int i = 0; i < lines.length; i++)
                if (lines[i].contains("-->")) { timeLine = i; break; }
            if (timeLine < 0 || timeLine >= lines.length - 1) continue;
            try {
                String[] bounds = lines[timeLine].trim().split("\\s+-->\\s+");
                if (bounds.length != 2) continue;
                long start = parseTime(bounds[0]);
                long end = parseTime(bounds[1].split("\\s+")[0]);
                if (end <= start) continue;
                StringBuilder t = new StringBuilder();
                for (int i = timeLine + 1; i < lines.length; i++) {
                    if (t.length() > 0) t.append(' ');
                    t.append(lines[i].trim());
                }
                String clean = t.toString().replaceAll("<[^>]*>", "").trim();
                if (!clean.isEmpty()) parsed.add(new Caption(start, end, clean));
            } catch (RuntimeException ignored) {}
        }
        return parsed;
    }
    private long parseTime(String stamp) {
        String[] p = stamp.replace(',', '.').split("[:.]");
        if (p.length != 4) throw new IllegalArgumentException("Bad timestamp");
        return Long.parseLong(p[0]) * 3600000L + Long.parseLong(p[1]) * 60000L
                + Long.parseLong(p[2]) * 1000L + Long.parseLong(p[3]);
    }

    private Matcher matches(String line, String word) {
        String regex = "(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(line);
    }
    private void rebuild() {
        ArrayList<Segment> all = new ArrayList<>(manual);
        for (Caption c : captions) {
            if (entireCue) {
                for (WordEntry w : words) {
                    if (w.enabled && matches(c.text, w.text).find()) {
                        all.add(new Segment(c.start, c.end)); break;
                    }
                }
            } else {
                for (WordEntry w : words) {
                    if (!w.enabled) continue;
                    Matcher m = matches(c.text, w.text);
                    while (m.find()) {
                        long len = c.end - c.start;
                        long a = c.start + len * m.start() / Math.max(1, c.text.length());
                        long b = c.start + len * m.end() / Math.max(1, c.text.length());
                        all.add(new Segment(Math.max(c.start, a - 120), Math.min(c.end, b + 120)));
                    }
                }
            }
        }

        // Match both single words and multi-word custom phrases in the
        // recognized word sequence, preserving the first/last word timestamps.
        for (WordEntry blocked : words) {
            if (!blocked.enabled) continue;
            String[] phrase = blocked.text.trim().toLowerCase(Locale.ROOT).split("\\s+");
            for (int i = 0; i + phrase.length <= detectedWords.size(); i++) {
                boolean equal = true;
                for (int j = 0; j < phrase.length; j++) {
                    if (!phrase[j].equals(detectedWords.get(i + j).text)) {
                        equal = false; break;
                    }
                }
                if (equal) {
                    long start = detectedWords.get(i).startMs;
                    long end = detectedWords.get(i + phrase.length - 1).endMs;
                    all.add(new Segment(Math.max(0, start - 100), end + 130));
                }
            }
        }
        all.sort(Comparator.comparingLong(s -> s.start));
        segments.clear();
        for (Segment s : all) {
            if (segments.isEmpty() || s.start > segments.get(segments.size() - 1).end) {
                segments.add(new Segment(s.start, s.end));
            } else {
                Segment prev = segments.get(segments.size() - 1);
                prev.end = Math.max(prev.end, s.end);
            }
        }
        if (summary != null)
            summary.setText(captions.size() + " captions · " + detectedWords.size() + " AI words · " + segments.size() + " bleep intervals");
        if (diagnostics != null && activeExample != null && !detectedWords.isEmpty()) updateDiagnostics();
        if (wordCount != null) {
            int count = 0; for (WordEntry w : words) if (w.enabled) count++;
            wordCount.setText(count + " enabled / " + words.size() + " words");
        }
    }
    private void addManual() {
        if (!ready) { setStatus("Choose media before adding a manual bleep."); return; }
        long now = video.getCurrentPosition();
        manual.add(new Segment(now, now + 700));
        rebuild(); setStatus("Manual bleep inserted at " + clock(now));
    }
    private void togglePlay() {
        if (!ready) { setStatus("Choose media first."); return; }
        if (video.isPlaying()) {
            video.pause(); playButton.setText("Play"); resetOutput();
        } else {
            video.start(); playButton.setText("Pause");
        }
    }
    private void jump(int delta) {
        if (!ready) return;
        long next = Math.max(0, Math.min(video.getDuration(), (long)video.getCurrentPosition() + delta));
        video.seekTo((int)next); resetOutput();
    }
    private boolean needsBleep(long now) {
        int lo = 0, hi = segments.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Segment s = segments.get(mid);
            if (now < s.start) hi = mid - 1;
            else if (now >= s.end) lo = mid + 1;
            else return true;
        }
        return false;
    }
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (ready && video != null) {
                int current = video.getCurrentPosition();
                int duration = Math.max(1, video.getDuration());
                if (seek != null && !seek.isPressed())
                    seek.setProgress((int)((long)current * 1000L / duration));
                if (position != null) position.setText(clock(current) + " / " + clock(duration));
                boolean censor = video.isPlaying() && needsBleep(current);
                if (censor && !beeping) {
                    if (player != null) player.setVolume(0f, 0f);
                    if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP);
                    beeping = true;
                } else if (!censor && beeping) resetOutput();
            }
            timer.postDelayed(this, 25);
        }
    };
    private void resetOutput() {
        if (tone != null) tone.stopTone();
        if (player != null) {
            try { player.setVolume(1f, 1f); } catch (IllegalStateException ignored) {}
        }
        beeping = false;
    }
    private String clock(long ms) {
        long sec = Math.max(0, ms) / 1000L;
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }
    private void setStatus(String message) {
        if (status != null) status.setText(message);
        rebuild();
    }

    private void refreshWords() {
        if (wordContainer == null) return;
        wordContainer.removeAllViews();
        for (WordEntry w : new ArrayList<>(words)) {
            LinearLayout r = row();
            wordContainer.addView(r, new LinearLayout.LayoutParams(-1, dp(48)));
            r.addView(label(w.text + "  ·  " + w.group, 13, TEXT, false),
                    new LinearLayout.LayoutParams(0, -2, 1));
            Switch toggle = new Switch(this); toggle.setChecked(w.enabled);
            toggle.setOnCheckedChangeListener((btn, checked) -> {
                w.enabled = checked; saveWords(); rebuild();
            });
            r.addView(toggle);
            Button remove = button("×", 0xFF30435B, () -> {
                words.remove(w); saveWords(); refreshWords();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(42), dp(38));
            p.leftMargin = dp(5); r.addView(remove, p);
        }
        rebuild();
    }
    private void preset(String name) {
        for (WordEntry w : words) {
            w.enabled = name.equals("strict") ||
                    (name.equals("balanced") && !w.group.equals("Mild"));
        }
        saveWords(); refreshWords(); setStatus("Preset applied: " + name);
    }
    private void defaults() {
        words.clear();
        for (String w : new String[]{"fuck", "fucking", "motherfucker", "shit", "bitch",
                "cunt", "madarchod", "behenchod", "bhenchod", "chutiya"})
            words.add(new WordEntry(w, "Strong", true));
        for (String w : new String[]{"asshole", "bastard", "crap", "bullshit", "harami"})
            words.add(new WordEntry(w, "Moderate", true));
        for (String w : new String[]{"damn", "hell", "bloody"})
            words.add(new WordEntry(w, "Mild", false));
    }
    private void loadWords() {
        String saved = prefs.getString("word_rules", null);
        if (saved == null) { defaults(); return; }
        try {
            JSONArray list = new JSONArray(saved);
            for (int i = 0; i < list.length(); i++) {
                JSONObject obj = list.getJSONObject(i);
                String w = obj.getString("text").trim();
                if (!w.isEmpty()) words.add(new WordEntry(w,
                        obj.getString("group"), obj.getBoolean("enabled")));
            }
        } catch (Exception ex) { defaults(); }
    }
    private void saveWords() {
        JSONArray list = new JSONArray();
        try {
            for (WordEntry w : words) {
                JSONObject obj = new JSONObject();
                obj.put("text", w.text); obj.put("group", w.group);
                obj.put("enabled", w.enabled); list.put(obj);
            }
            prefs.edit().putString("word_rules", list.toString()).apply();
        } catch (Exception ignored) {}
    }
    @Override protected void onPause() {
        super.onPause();
        if (video != null && video.isPlaying()) video.pause();
        if (playButton != null) playButton.setText("Play");
        resetOutput();
    }
    @Override protected void onDestroy() {
        timer.removeCallbacks(ticker);
        resetOutput();
        if (video != null) video.stopPlayback();
        if (tone != null) tone.release();
        super.onDestroy();
    }
}
