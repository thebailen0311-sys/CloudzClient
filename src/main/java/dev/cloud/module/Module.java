package dev.cloud.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.cloud.input.Binds;
import dev.cloud.ui.Anim;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class Module {
    public final String name, description;
    public final Category category;
    private boolean enabled;
    public int key = -1;
    public boolean mouse, hold, hidden;
    public final List<Setting> settings = new ArrayList<>();

    // runtime + animation state
    public boolean bindDown, expanded;
    public float hoverAnim, expandAnim, arrayAnim, arrayWidth = -1f, arrayTarget;
    public float arrayY = Float.NaN;
    public final Anim.Spring tog;

    protected Module(String name, String description, Category category, boolean enabled) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabled;
        this.tog = new Anim.Spring(enabled ? 1f : 0f);
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean v) {
        if (v == enabled) return;
        enabled = v;
        if (v) onEnable(); else onDisable();
        dev.cloud.ui.Hud.notify(this);
    }

    public void toggle() { setEnabled(!enabled); }

    protected void onEnable() {}

    protected void onDisable() {}

    /** Called every rendered frame (and from the tick as a fallback when the HUD is hidden). */
    public void frame(MinecraftClient mc, float dt) {}

    /** Called at the start of every client tick. */
    public void tick(MinecraftClient mc) {}

    public String suffix() { return null; }

    protected <T extends Setting> T add(T s) {
        settings.add(s);
        return s;
    }

    public String bindName() { return keyName(key, mouse); }

    public static String keyName(int key, boolean mouse) {
        if (key < 0) return "none";
        if (mouse) {
            return switch (key) {
                case 0 -> "lmb";
                case 1 -> "rmb";
                case 2 -> "mmb";
                default -> "mb" + (key + 1);
            };
        }
        String s = switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "lshift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "rshift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "lctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "rctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "lalt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "ralt";
            case GLFW.GLFW_KEY_SPACE -> "space";
            case GLFW.GLFW_KEY_TAB -> "tab";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "caps";
            case GLFW.GLFW_KEY_ENTER -> "enter";
            case GLFW.GLFW_KEY_INSERT -> "insert";
            case GLFW.GLFW_KEY_HOME -> "home";
            case GLFW.GLFW_KEY_END -> "end";
            case GLFW.GLFW_KEY_PAGE_UP -> "pgup";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "pgdn";
            case GLFW.GLFW_KEY_UP -> "up";
            case GLFW.GLFW_KEY_DOWN -> "down";
            case GLFW.GLFW_KEY_LEFT -> "left";
            case GLFW.GLFW_KEY_RIGHT -> "right";
            default -> null;
        };
        if (s != null) return s;
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) return "f" + (key - GLFW.GLFW_KEY_F1 + 1);
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) return "num" + (key - GLFW.GLFW_KEY_KP_0);
        String n = GLFW.glfwGetKeyName(key, 0);
        return n != null ? n.toLowerCase(Locale.ROOT) : "key" + key;
    }

    public JsonObject save() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.addProperty("key", key);
        o.addProperty("mouse", mouse);
        o.addProperty("hold", hold);
        o.addProperty("expanded", expanded);
        JsonObject s = new JsonObject();
        for (Setting st : settings) s.add(st.name, st.toJson());
        o.add("settings", s);
        return o;
    }

    public void load(JsonObject o) {
        try {
            if (o.has("key")) key = o.get("key").getAsInt();
            if (o.has("mouse")) mouse = o.get("mouse").getAsBoolean();
            if (o.has("hold")) hold = o.get("hold").getAsBoolean();
            if (o.has("expanded")) expanded = o.get("expanded").getAsBoolean();
            if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean() && !hold;
            if (o.has("settings") && o.get("settings").isJsonObject()) {
                JsonObject s = o.getAsJsonObject("settings");
                for (Setting st : settings) {
                    JsonElement e = s.get(st.name);
                    if (e == null) continue;
                    try { st.fromJson(e); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        if (mouse && (key < 0 || key > GLFW.GLFW_MOUSE_BUTTON_LAST)) key = -1;
        if (!mouse && key >= 0 && !Binds.valid(key)) key = -1;
        tog.x = enabled ? 1f : 0f;
        expandAnim = expanded ? 1f : 0f;
    }
}
