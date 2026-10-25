package dev.cloud.ui;

import java.util.ArrayList;
import java.util.List;

/** Parsed LRC lyrics with per-word timing derived from the line duration. */
public final class Lyrics {

    public static final class Line {
        public final long time;
        public final String text;
        public final String[] words;
        public final long[] wordTimes;

        Line(long time, String text, String[] words, long[] wordTimes) {
            this.time = time;
            this.text = text;
            this.words = words;
            this.wordTimes = wordTimes;
        }
    }

    public final List<Line> lines = new ArrayList<>();

    public boolean isEmpty() { return lines.isEmpty(); }

    public static Lyrics parse(String lrc) {
        Lyrics out = new Lyrics();
        if (lrc == null || lrc.isBlank()) return out;
        List<long[]> times = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (String raw : lrc.split("\n")) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            long t = -1;
            int i = 0;
            while (i < s.length() && s.charAt(i) == '[') {
                int close = s.indexOf(']', i);
                if (close < 0) break;
                long parsed = stamp(s.substring(i + 1, close));
                if (parsed >= 0) t = parsed;
                i = close + 1;
            }
            if (t < 0) continue;
            String text = s.substring(i).trim();
            if (text.isEmpty()) continue;
            times.add(new long[]{t});
            texts.add(text);
        }
        for (int i = 0; i < times.size(); i++) {
            long start = times.get(i)[0];
            long end = i + 1 < times.size() ? times.get(i + 1)[0] : start + 4000L;
            out.lines.add(build(start, end, texts.get(i)));
        }
        return out;
    }

    private static long stamp(String s) {
        try {
            int colon = s.indexOf(':');
            if (colon < 0) return -1;
            int min = Integer.parseInt(s.substring(0, colon).trim());
            String rest = s.substring(colon + 1).trim();
            double sec = Double.parseDouble(rest);
            return (long) (min * 60000L + sec * 1000.0);
        } catch (Exception e) {
            return -1;
        }
    }

    private static Line build(long start, long end, String text) {
        String[] words = text.split(" ");
        long dur = Math.max(400L, end - start);
        int total = 0;
        for (String w : words) total += w.length() + 1;
        if (total <= 0) total = 1;
        long[] wt = new long[words.length];
        long acc = 0;
        for (int i = 0; i < words.length; i++) {
            wt[i] = start + dur * acc / total;
            acc += words[i].length() + 1;
        }
        return new Line(start, text, words, wt);
    }

    /** Index of the line playing at pos, or -1. */
    public int indexAt(long pos) {
        int lo = 0, hi = lines.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).time <= pos) { found = mid; lo = mid + 1; }
            else hi = mid - 1;
        }
        return found;
    }

    public Line line(int i) { return i < 0 || i >= lines.size() ? null : lines.get(i); }
}
