package dev.cloud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cloud.module.Module;
import dev.cloud.server.ServerConfigs;
import dev.cloud.storage.CloudPaths;
import dev.cloud.storage.SafeJson;
import dev.cloud.module.Modules;
import dev.cloud.module.impl.TriggerbotModule;
import dev.cloud.module.impl.WTapModule;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private Config() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return CloudPaths.ROOT.resolve("config.json");
    }

    public static void load() {
        Modules.init();
        try {
            Path p = path();
            if (!Files.exists(p)) for (String legacy : new String[]{"cloudclient.json", "wtapclient.json"}) {
                Path lp = FabricLoader.getInstance().getConfigDir().resolve(legacy); // old location, read once
                if (Files.exists(lp)) { p = lp; break; }
            }
            if (Files.exists(p)) {
                JsonObject root = SafeJson.load(p, JsonObject.class, JsonObject::new);
                if (root.has("modules") && root.get("modules").isJsonObject()) {
                    JsonObject ms = root.getAsJsonObject("modules");
                    for (Module m : Modules.ALL) {
                        JsonElement e = ms.get(m.name);
                        if (e != null && e.isJsonObject()) m.load(e.getAsJsonObject());
                    }
                } else {
                    migrate(root);
                }
            }
        } catch (Exception e) {
            System.err.println("[CloudClient] Failed to load config: " + e);
        }
        save();
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 2);
            JsonObject ms = ServerConfigs.activeGlobal(); // never write server-preset values as global
            if (ms == null) {
                ms = new JsonObject();
                for (Module m : Modules.ALL) ms.add(m.name, m.save());
            }
            root.add("modules", ms);
            SafeJson.save(path(), root);
        } catch (Exception e) {
            System.err.println("[CloudClient] Failed to save config: " + e);
        }
    }

    /** Reads the old flat v1 config so existing settings carry over. */
    private static void migrate(JsonObject o) {
        WTapModule w = Modules.WTAP;
        TriggerbotModule t = Modules.TRIGGER;
        if (o.has("wtapEnabled")) w.setEnabled(o.get("wtapEnabled").getAsBoolean());
        if (o.has("wtapWeaponOnly")) w.weaponOnly.set(o.get("wtapWeaponOnly").getAsBoolean());
        if (o.has("wtapOnlySprinting")) w.onlySprinting.set(o.get("wtapOnlySprinting").getAsBoolean());
        if (o.has("wtapResprint")) w.resprint.set(o.get("wtapResprint").getAsBoolean());
        if (o.has("wtapReactMin")) w.reactMin.set(o.get("wtapReactMin").getAsDouble());
        if (o.has("wtapReactMax")) w.reactMax.set(o.get("wtapReactMax").getAsDouble());
        if (o.has("wtapHoldMin")) w.holdMin.set(o.get("wtapHoldMin").getAsDouble());
        if (o.has("wtapHoldMax")) w.holdMax.set(o.get("wtapHoldMax").getAsDouble());
        if (o.has("triggerEnabled")) t.setEnabled(o.get("triggerEnabled").getAsBoolean());
        if (o.has("triggerWeaponOnly")) t.weaponOnly.set(o.get("triggerWeaponOnly").getAsBoolean());
        if (o.has("triggerPlayersOnly")) t.playersOnly.set(o.get("triggerPlayersOnly").getAsBoolean());
        if (o.has("triggerReactMin")) t.reactMin.set(o.get("triggerReactMin").getAsDouble());
        if (o.has("triggerReactMax")) t.reactMax.set(o.get("triggerReactMax").getAsDouble());
        if (o.has("minCooldownPct")) t.minCharge.set(o.get("minCooldownPct").getAsDouble());
        if (o.has("critMode")) t.critMode.set(o.get("critMode").getAsBoolean());
        if (o.has("critReleaseSprint")) t.critDropSprint.set(o.get("critReleaseSprint").getAsBoolean());
        if (o.has("critTimeout")) t.critFallback.set(o.get("critTimeout").getAsDouble());
        w.tog.x = w.isEnabled() ? 1f : 0f;
        t.tog.x = t.isEnabled() ? 1f : 0f;
    }
}
