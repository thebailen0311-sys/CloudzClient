package dev.cloud.module.impl;

import dev.cloud.module.*;
import dev.cloud.module.Module;
import dev.cloud.ui.R;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Fully custom screen-space atmosphere motes. No vanilla particle or sound assets. */
public class AmbienceModule extends Module {
    public final ModeSetting mode = add(new ModeSetting("Mode", "Ash", "Ash", "Snow", "Stars", "Spores"));
    public final NumSetting density = add(new NumSetting("Density", 18, 4, 48, 1, ""));
    public final NumSetting speed = add(new NumSetting("Speed", 1.0, 0.2, 3.0, 0.1, "x"));
    public final BoolSetting parallax = add(new BoolSetting("Parallax", true));

    private static final class Mote {
        float x, y, depth, phase, size, vx, vy;
    }

    private final List<Mote> motes = new ArrayList<>();
    private final Random rnd = new Random(0xC10D);
    private float lastYaw = Float.NaN, lastPitch;

    public AmbienceModule() { super("Ambience", "Custom screen-space atmosphere motes", Category.RENDER, false); }

    @Override public String suffix() { return mode.get(); }

    private Mote make(float w, float h) {
        Mote m = new Mote();
        m.x = rnd.nextFloat() * w;
        m.y = rnd.nextFloat() * h;
        m.depth = 0.25f + rnd.nextFloat() * 0.75f;
        m.phase = rnd.nextFloat() * (float) (Math.PI * 2);
        switch (mode.get()) {
            case "Snow" -> { m.size = 1.2f + rnd.nextFloat() * 1.6f; m.vy = 16 + rnd.nextFloat() * 22; m.vx = (rnd.nextFloat() - 0.5f) * 10; }
            case "Stars" -> { m.size = 0.8f + rnd.nextFloat() * 1.2f; m.vy = 1 + rnd.nextFloat() * 2; m.vx = 0; }
            case "Spores" -> { m.size = 5 + rnd.nextFloat() * 7; m.vy = -4 + rnd.nextFloat() * 8; m.vx = (rnd.nextFloat() - 0.5f) * 6; }
            default -> { m.size = 1 + rnd.nextFloat() * 2.2f; m.vy = 3 + rnd.nextFloat() * 8; m.vx = (rnd.nextFloat() - 0.5f) * 6; }
        }
        return m;
    }

    public void render(DrawContext ctx, float dt) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
        float w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        float spd = (float) speed.get() * dt;

        while (motes.size() < density.getInt()) motes.add(make(w, h));
        while (motes.size() > density.getInt()) motes.remove(motes.size() - 1);

        // fake parallax driven by view rotation
        float yaw = mc.player.getYaw(), pitch = mc.player.getPitch();
        if (Float.isNaN(lastYaw)) { lastYaw = yaw; lastPitch = pitch; }
        float dy = yaw - lastYaw;
        if (dy > 180) dy -= 360; else if (dy < -180) dy += 360;
        float dp = pitch - lastPitch;
        lastYaw = yaw;
        lastPitch = pitch;
        float px = parallax.get() ? -dy * 8f : 0f, py = parallax.get() ? -dp * 5f : 0f;

        boolean snow = mode.is("Snow"), stars = mode.is("Stars"), spores = mode.is("Spores");
        int rgb = spores ? 0xE8E8EE : snow || stars ? 0xFFFFFF : 0xD9DCE2;
        for (Mote m : motes) {
            m.phase += dt * (snow ? 1.6f : stars ? 2.2f : 0.7f);
            float sway = snow ? 0.6f + 0.4f * (float) Math.sin(m.phase) : 1f;
            m.x += m.vx * spd + px * m.depth;
            m.y += m.vy * sway * spd + py * m.depth;
            if (m.x < -24) m.x += w + 48; else if (m.x > w + 24) m.x -= w + 48;
            if (m.y < -24) m.y += h + 48; else if (m.y > h + 24) m.y -= h + 48;

            float tw = stars ? 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(m.phase * 2.4f)) : 1f;
            float base = spores ? 0.10f : stars ? 0.75f : 0.30f;
            int col = ((int) (255 * base * tw * m.depth) << 24) | rgb;
            R.circle(ctx, m.x, m.y, m.size, col);
            if (spores) R.circle(ctx, m.x, m.y, m.size * 0.4f, ((int) (255 * 0.12f * tw) << 24) | 0xFFFFFF);
        }
    }
}
