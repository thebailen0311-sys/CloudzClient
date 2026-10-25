package dev.cloud.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/** JSON files with atomic writes, timestamped backups, and quarantine + recovery for corrupted files. */
public final class SafeJson {
    private SafeJson() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final int KEEP_BACKUPS = 10;

    /** Loads a file. Missing file -> fallback. Corrupt file -> quarantined, then newest valid backup, then fallback. */
    public static <T> T load(Path file, Class<T> type, Supplier<T> fallback) {
        if (!Files.exists(file)) return fallback.get();
        T v = parse(file, type);
        if (v != null) return v;
        quarantine(file);
        for (Path b : backupsOf(file)) {
            v = parse(b, type);
            if (v != null) {
                System.err.println("[CloudClient] Recovered " + file.getFileName() + " from backup " + b.getFileName());
                return v;
            }
        }
        return fallback.get();
    }

    /** Writes via a temp file + atomic move. The previous content is copied to backups/ first when it changed. */
    public static void save(Path file, Object value) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.createDirectories(CloudPaths.BACKUPS);
        String json = GSON.toJson(value);
        if (Files.isRegularFile(file)) {
            try {
                if (!json.equals(Files.readString(file, StandardCharsets.UTF_8))) {
                    Files.copy(file, CloudPaths.BACKUPS.resolve(file.getFileName() + "." + stamp() + ".bak"),
                            StandardCopyOption.REPLACE_EXISTING);
                    prune(file);
                }
            } catch (IOException e) {
                System.err.println("[CloudClient] Backup of " + file.getFileName() + " failed: " + e);
            }
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static <T> T parse(Path p, Class<T> type) {
        try {
            return GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), type); // null for blank input
        } catch (Exception e) {
            return null;
        }
    }

    private static void quarantine(Path file) {
        try {
            Files.createDirectories(CloudPaths.BACKUPS);
            Files.move(file, CloudPaths.BACKUPS.resolve("corrupt-" + file.getFileName() + "." + stamp() + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING);
            System.err.println("[CloudClient] Corrupted file moved to backups: " + file.getFileName());
        } catch (IOException e) {
            System.err.println("[CloudClient] Could not quarantine " + file.getFileName() + ": " + e);
        }
    }

    /** Backups for one file, newest first. */
    private static List<Path> backupsOf(Path file) {
        String prefix = file.getFileName() + ".";
        List<Path> out = new ArrayList<>();
        try (var s = Files.list(CloudPaths.BACKUPS)) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(prefix) && n.endsWith(".bak");
            }).forEach(out::add);
        } catch (IOException ignored) {
            // no backups directory yet
        }
        out.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        return out;
    }

    private static void prune(Path file) {
        List<Path> all = backupsOf(file);
        for (int i = KEEP_BACKUPS; i < all.size(); i++) {
            try {
                Files.deleteIfExists(all.get(i));
            } catch (IOException ignored) {
                // keep it if it cannot be removed
            }
        }
    }

    private static String stamp() {
        return LocalDateTime.now().format(STAMP);
    }
}
