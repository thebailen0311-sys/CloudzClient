package dev.cloud.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cloud.storage.CloudPaths;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Now-playing + synced lyrics.
 *
 * Credentials live in config/cloudclient/spotify.json:
 *   { "clientId": "...", "clientSecret": "...", "refreshToken": "..." }
 *
 * Track data comes from the Spotify Web API, lyrics from lrclib.net (free, no key).
 * Everything runs on daemon threads; nothing here touches the render thread.
 */
public final class Spotify {
    private Spotify() {}

    public static final class Track {
        public String title = "";
        public String artist = "";
        public String album = "";
        public long progress;
        public long duration;
        public boolean playing;
        public long stamp;

        public String key() { return title + "\u0000" + artist; }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static volatile Track track;
    private static volatile Lyrics lyrics;
    private static volatile String lyricsKey = "";
    private static volatile String status = "not configured";
    private static volatile boolean busy;
    private static volatile boolean enabled;

    private static String clientId = "", clientSecret = "", refreshToken = "";
    private static String access = "";
    private static long accessExp;
    private static long lastPoll;
    private static long configStamp;

    public static void setEnabled(boolean v) { enabled = v; }

    public static String status() { return status; }

    public static Track track() { return track; }

    public static Lyrics lyrics() { return lyrics; }

    /** Interpolated playback position in ms. */
    public static long position() {
        Track t = track;
        if (t == null) return 0L;
        long p = t.progress;
        if (t.playing) p += System.currentTimeMillis() - t.stamp;
        return Math.max(0L, Math.min(t.duration, p));
    }

    public static void tick() {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        if (now - lastPoll < 1500L) return;
        lastPoll = now;
        if (busy) return;
        busy = true;
        Thread th = new Thread(() -> {
            try { poll(); } catch (Throwable e) { status = "offline"; } finally { busy = false; }
        }, "cloude-spotify");
        th.setDaemon(true);
        th.start();
    }

    private static Path file() { return CloudPaths.ROOT.resolve("spotify.json"); }

    private static void loadConfig() {
        try {
            Path p = file();
            if (!Files.exists(p)) { status = "no spotify.json"; return; }
            long stamp = Files.getLastModifiedTime(p).toMillis();
            if (stamp == configStamp) return;
            configStamp = stamp;
            JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            clientId = str(o, "clientId");
            clientSecret = str(o, "clientSecret");
            refreshToken = str(o, "refreshToken");
            access = "";
            accessExp = 0L;
            status = refreshToken.isEmpty() ? "no refreshToken" : "ready";
        } catch (Throwable e) {
            status = "bad spotify.json";
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static boolean token() throws Exception {
        if (!access.isEmpty() && System.currentTimeMillis() < accessExp - 60000L) return true;
        if (refreshToken.isEmpty()) return false;
        String body = "grant_type=refresh_token"
                + "&refresh_token=" + enc(refreshToken)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) { status = "auth " + res.statusCode(); return false; }
        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!o.has("access_token")) return false;
        access = o.get("access_token").getAsString();
        long ttl = o.has("expires_in") ? o.get("expires_in").getAsLong() : 3600L;
        accessExp = System.currentTimeMillis() + ttl * 1000L;
        return true;
    }

    private static void poll() throws Exception {
        loadConfig();
        if (refreshToken.isEmpty()) { status = "no refreshToken"; return; }
        if (!token()) return;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
                .header("Authorization", "Bearer " + access)
                .timeout(Duration.ofSeconds(8))
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 204) { track = null; lyrics = null; status = "idle"; return; }
        if (res.statusCode() == 401) { access = ""; status = "reauth"; return; }
        if (res.statusCode() != 200) { status = "api " + res.statusCode(); return; }
        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!o.has("item") || o.get("item").isJsonNull()) { track = null; status = "idle"; return; }
        JsonObject item = o.getAsJsonObject("item");
        Track t = new Track();
        t.title = item.has("name") ? item.get("name").getAsString() : "";
        t.album = item.has("album") && item.getAsJsonObject("album").has("name")
                ? item.getAsJsonObject("album").get("name").getAsString() : "";
        StringBuilder artists = new StringBuilder();
        if (item.has("artists")) {
            JsonArray arr = item.getAsJsonArray("artists");
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) artists.append(", ");
                artists.append(arr.get(i).getAsJsonObject().get("name").getAsString());
            }
        }
        t.artist = artists.toString();
        t.progress = o.has("progress_ms") && !o.get("progress_ms").isJsonNull() ? o.get("progress_ms").getAsLong() : 0L;
        t.duration = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0L;
        t.playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();
        t.stamp = System.currentTimeMillis();
        track = t;
        status = t.playing ? "playing" : "paused";
        if (!t.key().equals(lyricsKey)) {
            lyricsKey = t.key();
            lyrics = null;
            fetchLyrics(t);
        }
    }

    private static void fetchLyrics(Track t) {
        try {
            String url = "https://lrclib.net/api/get?artist_name=" + enc(t.artist)
                    + "&track_name=" + enc(t.title)
                    + "&duration=" + (t.duration / 1000L);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "CloudClient/1.2.0 (https://github.com/thebailen0311-sys/CloudzClient)")
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return;
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            if (!o.has("syncedLyrics") || o.get("syncedLyrics").isJsonNull()) return;
            Lyrics l = Lyrics.parse(o.get("syncedLyrics").getAsString());
            if (!l.isEmpty()) lyrics = l;
        } catch (Throwable ignored) {}
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
