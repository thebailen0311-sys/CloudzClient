package dev.cloud.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Real mod version + async GitHub release check. */
public final class Ver {
    private Ver() {}

    private static String local;
    private static volatile String latest;
    private static volatile boolean checked;
    private static volatile boolean available;

    public static String get() {
        if (local == null) {
            local = FabricLoader.getInstance().getModContainer("cloudclient")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("dev");
        }
        return local;
    }

    public static String tag() { return "v" + get(); }

    public static boolean updateAvailable() { return available; }

    public static String latest() { return latest; }

    public static void checkAsync() {
        if (checked) return;
        checked = true;
        Thread th = new Thread(() -> {
            try {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/thebailen0311-sys/CloudzClient/releases/latest"))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "cloudclient")
                        .timeout(Duration.ofSeconds(6))
                        .GET().build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) return;
                JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                if (!o.has("tag_name")) return;
                String name = o.get("tag_name").getAsString();
                latest = name.startsWith("v") ? name.substring(1) : name;
                available = compare(latest, get()) > 0;
            } catch (Throwable ignored) {}
        }, "cloude-update-check");
        th.setDaemon(true);
        th.start();
    }

    private static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int xi = i < x.length ? num(x[i]) : 0;
            int yi = i < y.length ? num(y[i]) : 0;
            if (xi != yi) return Integer.compare(xi, yi);
        }
        return 0;
    }

    private static int num(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c); else break;
        }
        return sb.length() == 0 ? 0 : Integer.parseInt(sb.toString());
    }
}
