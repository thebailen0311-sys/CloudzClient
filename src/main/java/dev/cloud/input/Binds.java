package dev.cloud.input;

import dev.cloud.Config;
import dev.cloud.module.Module;
import dev.cloud.module.Modules;
import dev.cloud.ui.ClickGui;
import dev.cloud.ui.Hud;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/** Client-side bind handling (keys + mouse buttons, toggle or hold). Nothing is registered in vanilla Controls. */
public final class Binds {
    private Binds() {}

    public static boolean valid(int key) {
        return key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_LAST;
    }

    private static boolean down(long h, Module m) {
        if (m.key < 0) return false;
        return m.mouse ? GLFW.glfwGetMouseButton(h, m.key) == GLFW.GLFW_PRESS
                : GLFW.glfwGetKey(h, m.key) == GLFW.GLFW_PRESS;
    }

    public static void tick(MinecraftClient mc) {
        long h = mc.getWindow().getHandle();
        boolean ingame = mc.currentScreen == null && mc.player != null;
        for (Module m : Modules.ALL) {
            boolean d = down(h, m);
            if (ingame) {
                if (m == Modules.CLICKGUI) {
                    if (d && !m.bindDown) mc.setScreen(new ClickGui());
                } else if (m.hold) {
                    if (m.key >= 0 && d != m.isEnabled()) m.setEnabled(d);
                } else if (d && !m.bindDown) {
                    m.toggle();
                    Hud.notify(m);
                    Config.save();
                }
            }
            m.bindDown = d;
        }
    }
}
