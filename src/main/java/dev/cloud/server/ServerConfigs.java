package dev.cloud.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cloud.module.Module;
import dev.cloud.module.Modules;
import dev.cloud.storage.CloudPaths;
import dev.cloud.storage.SafeJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Per-server presets. Identity is the normalized address (host:port), so renaming or duplicate display
 * names never collide. A preset is applied on join and the global settings are restored on disconnect.
 * Global settings are never overwritten by a server preset.
 */
public final class ServerConfigs {
    private ServerConfigs() {}

    public static final class Preset {
        public String name;
        public JsonObject modules = new JsonObject();
    }

    public static final class Record {
        public int version = 1;
        public String address;
        public String displayName;
        public String activeConfig;
        public List<Preset> configs = new ArrayList<>();
    }

    private static final Pattern HOST = Pattern.compile("[a-z0-9._\\-\\[\\]:]+");
    private static final int DEFAULT_PORT = 25565;

    /** Global module state captured before a preset was applied. Non-null only while a preset is active. */
    private static JsonObject globalSnapshot;
    private static String activeKey;

    /** Normalizes "Play.Example.com." -> "play.example.com:25565". Throws IllegalArgumentException when invalid. */
    public static String normalize(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) throw new IllegalArgumentException("Server address is empty.");
        String host;
        String port = null;
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end < 0) throw new IllegalArgumentException("Malformed IPv6 address.");
            host = s.substring(0, end + 1);
            String rest = s.substring(end + 1);
            if (rest.startsWith(":")) port = rest.substring(1);
            else if (!rest.isEmpty()) throw new IllegalArgumentException("Malformed server address.");
        } else {
            if (s.indexOf(':') != s.lastIndexOf(':'))
                throw new IllegalArgumentException("IPv6 addresses must be wrapped in [brackets].");
            int c = s.indexOf(':');
            if (c >= 0) {
                host = s.substring(0, c);
                port = s.substring(c + 1);
            } else {
                host = s;
            }
        }
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty() || !HOST.matcher(host).matches()) throw new IllegalArgumentException("Invalid server host.");
        int p = DEFAULT_PORT;
        if (port != null && !port.isEmpty()) {
            try {
                p = Integer.parseInt(port);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Port must be a number.");
            }
            if (p < 1 || p > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }
        return host + ":" + p;
    }

    public static Record load(String key) {
        Record r = SafeJson.load(fileFor(key), Record.class, () -> null);
        if (r == null || !key.equals(r.address)) return null;
        if (r.configs == null) r.configs = new ArrayList<>();
        r.configs.removeIf(p -> p == null || p.name == null);
        for (Preset p : r.configs) if (p.modules == null) p.modules = new JsonObject();
        return r;
    }

    public static List<Record> listAll() {
        List<Record> out = new ArrayList<>();
        try (var s = Files.list(CloudPaths.SERVERS)) {
            s.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                Record r = SafeJson.load(p, Record.class, () -> null);
                if (r != null && r.address != null) out.add(r);
            });
        } catch (IOException ignored) {
            // no servers saved yet
        }
        return out;
    }

    /** Creates the server record if needed. Duplicate display names are allowed. */
    public static Record open(String rawAddress, String displayName) throws IOException {
        String key = normalize(rawAddress);
        Record r = load(key);
        if (r == null) {
            r = new Record();
            r.address = key;
            r.displayName = cleanName(displayName, key);
            SafeJson.save(fileFor(key), r);
        }
        return r;
    }

    public static void rename(String key, String displayName) throws IOException {
        Record r = require(key);
        r.displayName = cleanName(displayName, key);
        SafeJson.save(fileFor(key), r);
    }

    public static void createPreset(String key, String name) throws IOException {
        Record r = require(key);
        Preset p = new Preset();
        p.name = checkPresetName(r, name, null);
        p.modules = captureModules();
        r.configs.add(p);
        SafeJson.save(fileFor(key), r);
    }

    public static void duplicatePreset(String key, String from, String newName) throws IOException {
        Record r = require(key);
        Preset src = findPreset(r, from);
        if (src == null) throw new IllegalArgumentException("Preset not found.");
        Preset p = new Preset();
        p.name = checkPresetName(r, newName, null);
        p.modules = JsonParser.parseString(src.modules.toString()).getAsJsonObject();
        r.configs.add(p);
        SafeJson.save(fileFor(key), r);
    }

    public static void renamePreset(String key, String from, String newName) throws IOException {
        Record r = require(key);
        Preset p = findPreset(r, from);
        if (p == null) throw new IllegalArgumentException("Preset not found.");
        String n = checkPresetName(r, newName, from);
        if (r.activeConfig != null && r.activeConfig.equals(from)) r.activeConfig = n;
        p.name = n;
        SafeJson.save(fileFor(key), r);
    }

    /** Overwrites a preset with the current module state. */
    public static void updatePreset(String key, String name) throws IOException {
        Record r = require(key);
        Preset p = findPreset(r, name);
        if (p == null) throw new IllegalArgumentException("Preset not found.");
        p.modules = captureModules();
        SafeJson.save(fileFor(key), r);
    }

    public static void deletePreset(String key, String name) throws IOException {
        Record r = require(key);
        Preset p = findPreset(r, name);
        if (p == null) throw new IllegalArgumentException("Preset not found.");
        r.configs.remove(p);
        if (name.equals(r.activeConfig)) r.activeConfig = null;
        SafeJson.save(fileFor(key), r);
    }

    /** null = use global settings. Applies immediately when already connected to this server. */
    public static void setActive(String key, String name) throws IOException {
        Record r = require(key);
        if (name != null && findPreset(r, name) == null) throw new IllegalArgumentException("Preset not found.");
        r.activeConfig = name;
        SafeJson.save(fileFor(key), r);
        if (key.equals(activeKey)) applyFor(key);
    }

    /** Moves the record to backups/ instead of hard-deleting it. */
    public static void deleteServer(String key) throws IOException {
        Path f = fileFor(key);
        if (!Files.exists(f)) return;
        if (key.equals(activeKey)) restoreGlobal();
        Files.createDirectories(CloudPaths.BACKUPS);
        Files.move(f, CloudPaths.BACKUPS.resolve(f.getFileName() + "." + System.currentTimeMillis() + ".deleted"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    /** Called on join. Null means singleplayer or LAN, so nothing is applied. */
    public static void onJoin(String rawAddress) {
        if (rawAddress == null) return;
        String key;
        try {
            key = normalize(rawAddress);
        } catch (IllegalArgumentException e) {
            return;
        }
        applyFor(key);
    }

    /** Called on disconnect. Restores the global settings. */
    public static void onDisconnect() {
        restoreGlobal();
    }

    /** Global module state while a preset is applied, or null. Config.save uses this to keep server values out of the global file. */
    public static JsonObject activeGlobal() {
        return globalSnapshot;
    }

    private static void applyFor(String key) {
        restoreGlobal();
        Record r = load(key);
        if (r == null || r.activeConfig == null) return;
        Preset p = findPreset(r, r.activeConfig);
        if (p == null) return;
        globalSnapshot = captureModules();
        activeKey = key;
        for (Module m : Modules.ALL) {
            JsonElement e = p.modules.get(m.name);
            if (e != null && e.isJsonObject()) m.load(e.getAsJsonObject());
        }
    }

    private static void restoreGlobal() {
        if (globalSnapshot == null) return;
        for (Module m : Modules.ALL) {
            JsonElement e = globalSnapshot.get(m.name);
            if (e != null && e.isJsonObject()) m.load(e.getAsJsonObject());
        }
        globalSnapshot = null;
        activeKey = null;
    }

    private static JsonObject captureModules() {
        JsonObject ms = new JsonObject();
        for (Module m : Modules.ALL) ms.add(m.name, m.save());
        return ms;
    }

    private static Record require(String key) {
        Record r = load(key);
        if (r == null) throw new IllegalArgumentException("Server not found.");
        return r;
    }

    private static Preset findPreset(Record r, String name) {
        if (name == null) return null;
        for (Preset p : r.configs) if (p.name.equals(name)) return p;
        return null;
    }

    private static String checkPresetName(Record r, String raw, String exceptName) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty() || n.length() > 32) throw new IllegalArgumentException("Preset name must be 1-32 characters.");
        for (Preset p : r.configs)
            if (p.name.equalsIgnoreCase(n) && !p.name.equals(exceptName))
                throw new IllegalArgumentException("A preset named \"" + n + "\" already exists.");
        return n;
    }

    private static String cleanName(String raw, String fallback) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) return fallback;
        if (n.length() > 48) throw new IllegalArgumentException("Server name must be 48 characters or fewer.");
        return n;
    }

    /** servers/<first 12 bytes of SHA-256 of the normalized address>.json */
    private static Path fileFor(String key) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(String.format("%02x", d[i]));
            sb.append(".json");
            return CloudPaths.SERVERS.resolve(sb.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
