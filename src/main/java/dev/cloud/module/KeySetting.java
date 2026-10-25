package dev.cloud.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;

/** An extra per-module bind (keyboard key or mouse button), polled every tick. */
public class KeySetting extends Setting {
    public int key = -1;
    public boolean mouse;
    private boolean was;

    public KeySetting(String name) { super(name); }

    public KeySetting showIf(BooleanSupplier s) { setVisible(s); return this; }

    public boolean down(MinecraftClient mc) {
        if (key < 0 || mc.currentScreen != null) return false;
        long h = mc.getWindow().getHandle();
        return mouse ? GLFW.glfwGetMouseButton(h, key) == GLFW.GLFW_PRESS : GLFW.glfwGetKey(h, key) == GLFW.GLFW_PRESS;
    }

    /** True only on the tick the bind goes down. Call once per tick. */
    public boolean pressed(MinecraftClient mc) {
        boolean d = down(mc);
        boolean r = d && !was;
        was = d;
        return r;
    }

    @Override public JsonElement toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("key", key);
        o.addProperty("mouse", mouse);
        return o;
    }

    @Override public void fromJson(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        key = o.get("key").getAsInt();
        mouse = o.get("mouse").getAsBoolean();
    }
}
