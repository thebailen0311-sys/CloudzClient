package dev.cloud.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local data root: %AppData%\.cloudeclient on Windows (falls back to the user home elsewhere). */
public final class CloudPaths {
    private CloudPaths() {}

    public static final Path ROOT = resolveRoot();
    public static final Path SERVERS = ROOT.resolve("servers");
    public static final Path UPDATES = ROOT.resolve("updates");
    public static final Path BACKUPS = ROOT.resolve("backups");

    private static Path resolveRoot() {
        String appData = System.getenv("APPDATA");
        Path base = (appData == null || appData.isBlank()) ? Path.of(System.getProperty("user.home")) : Path.of(appData);
        return base.resolve(".cloudeclient");
    }

    public static void ensureDirs() {
        for (Path p : new Path[]{ROOT, SERVERS, UPDATES, BACKUPS}) {
            try {
                Files.createDirectories(p);
            } catch (IOException e) {
                System.err.println("[CloudClient] Cannot create " + p + ": " + e);
            }
        }
    }
}
