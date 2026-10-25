package dev.cloud.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

public final class UpdateService {
    private static final String MOD_ID = "cloudclient";
    private static final String REPO = "thebailen0311-sys/CloudzClient";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static volatile String status = "Idle";
    private static volatile String notes = "";
    private static volatile boolean ready = false;

    private UpdateService() {}

    public static String status() { return status; }
    public static String notes() { return notes; }
    public static boolean ready() { return ready; }

    /** Call once at client startup. Runs in the background and never blocks the game. */
    public static void start() {
        Thread t = new Thread(UpdateService::checkAndStage, "Cloude-Updater");
        t.setDaemon(true);
        t.start();
    }

    static void checkAndStage() {
        try {
            ModContainer mod = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
            String current = mod.getMetadata().getVersion().getFriendlyString();
            status = "Checking for updates...";

            JsonObject rel = JsonParser.parseString(text("https://api.github.com/repos/" + REPO + "/releases/latest"))
                    .getAsJsonObject();
            String remote = rel.get("tag_name").getAsString().replaceFirst("^v", "");
            if (compare(remote, current) <= 0) {
                status = "Up to date (" + current + ")";
                return;
            }

            String jarUrl = null, jarName = null, shaUrl = null;
            for (JsonElement e : rel.getAsJsonArray("assets")) {
                JsonObject a = e.getAsJsonObject();
                String name = a.get("name").getAsString();
                String url = a.get("browser_download_url").getAsString();
                if (name.endsWith(".jar") && !name.contains("sources")) { jarUrl = url; jarName = name; }
                if (name.equals("jar.sha256")) shaUrl = url;
            }
            if (jarUrl == null || shaUrl == null) {
                status = "Release " + remote + " is missing its jar or hash";
                return;
            }
            notes = rel.has("body") && !rel.get("body").isJsonNull() ? rel.get("body").getAsString() : "";
            String expected = text(shaUrl).trim().split("\\s+")[0].toLowerCase();

            Path dir = updatesDir();
            Files.createDirectories(dir);
            Path jar = dir.resolve(jarName);
            Path part = dir.resolve(jarName + ".part");

            IOException last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    download(jarUrl, part, expected, remote);
                    Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);
                    last = null;
                    break;
                } catch (IOException e) {
                    last = e;
                    Thread.sleep(1000L * attempt);
                }
            }
            if (last != null) {
                status = "Download failed: " + last.getMessage();
                return;
            }

            Path target = mod.getOrigin().getPaths().get(0);
            if (!target.toString().endsWith(".jar") || !Files.isRegularFile(target)
                    || !"mods".equals(target.getParent().getFileName().toString())) {
                status = "Update " + remote + " downloaded. Install it manually: Cloude jar not found in mods.";
                return;
            }
            Path bak = backupsDir().resolve("cloudclient-" + current + ".jar");

            ready = true;
            status = "Update " + remote + " ready. Restart Minecraft to install.";
            Runtime.getRuntime().addShutdownHook(new Thread(() -> scheduleSwap(jar, target, bak)));
        } catch (Exception e) {
            status = "Update check failed: " + e.getMessage();
        }
    }

    /** Runs when Minecraft exits. A small script waits for the old jar to unlock, backs it up, then swaps. */
    private static void scheduleSwap(Path staged, Path target, Path bak) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        try {
            Files.createDirectories(bak.getParent());
            Path script = updatesDir().resolve("apply-update.cmd");
            Files.writeString(script, String.join("\r\n",
                    "@echo off",
                    "for /L %%i in (1,1,60) do (",
                    "  ping -n 2 127.0.0.1 >nul",
                    "  move /Y \"" + target + "\" \"" + bak + "\" >nul 2>&1 && goto swap",
                    ")",
                    "exit /b 1",
                    ":swap",
                    "copy /Y \"" + staged + "\" \"" + target + "\" >nul",
                    "if errorlevel 1 move /Y \"" + bak + "\" \"" + target + "\" >nul",
                    ""));
            new ProcessBuilder("cmd.exe", "/c", script.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Exception ignored) {
            // If this fails, the old jar is untouched and the update is offered again next launch.
        }
    }

    private static void download(String url, Path part, String expected, String version) throws Exception {
        HttpResponse<InputStream> r = HTTP.send(req(url, 5), HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
        long total = r.headers().firstValueAsLong("Content-Length").orElse(-1L);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long done = 0;
        try (InputStream in = r.body(); var out = Files.newOutputStream(part)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
                out.write(buf, 0, n);
                done += n;
                if (total > 0) status = "Downloading " + version + " " + (done * 100 / total) + "%";
            }
        }
        if (!HexFormat.of().formatHex(md.digest()).equals(expected)) {
            Files.deleteIfExists(part);
            throw new IOException("SHA-256 mismatch, file rejected");
        }
    }

    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < 3; i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static String text(String url) throws Exception {
        HttpResponse<String> r = HTTP.send(req(url, 1), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
        return r.body();
    }

    private static HttpRequest req(String url, long minutes) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(minutes))
                .header("User-Agent", "CloudeClient")
                .GET().build();
    }

    private static Path updatesDir() { return Paths.get(System.getenv("APPDATA"), ".cloudeclient", "updates"); }
    private static Path backupsDir() { return Paths.get(System.getenv("APPDATA"), ".cloudeclient", "backups"); }
}
