package dev.cloud.module.impl;

import dev.cloud.module.BoolSetting;
import dev.cloud.module.Category;
import dev.cloud.module.ModeSetting;
import dev.cloud.module.Module;
import dev.cloud.module.NumSetting;
import dev.cloud.ui.R;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Screen-space atmosphere: motes, vignette and a slow aurora band. No vanilla assets. */
public class AmbienceModule extends Module {
    public final ModeSetting mode = add(new ModeSetting("Mode", "Ash", "Ash", "Snow", "Stars", "Spores", "Embers", "Rain"));
    public final NumSetting density = add(new NumSetting("Density", 22, 4, 60, 1, ""));
    public final NumSetting speed = add(new NumSetting("Speed", 1.0, 0.2, 3.0, 0.1, "x"));
    public final BoolSetting parallax = add(new BoolSetting("Parallax", true));
    public final BoolSetting vignette = add(new BoolSetting("Vignette", true));
    public final BoolSetting aurora = add(new BoolSetting("Aurora", true));
    public final NumSetting strength = add(new NumSetting("Intensity", 60, 10, 100, 1, "%"));

    private static final class Mote {
        float x, y, depth, phase, size, vx, vy;
    }

    private final List<Mote> motes = new ArrayList<>();
    private final Random rnd = new Random(0xC10D);
    private float lastYaw = Float.NaN, lastPitch;
    private float t;

    public AmbienceModule() { super("Ambience", "Screen-space atmosphere and overlays", Category.RENDER, false); }

    @Override public String suffix() { return mode.get(); }

    private Mote make(float w, float h) {
        Mote m = new Mote();
        m.x = rnd.nextFloat() * w;
        m.y = rnd.nextFloat() * h;
        m.depth = 0.25f + rnd.nextFloat() * 0.75f;
        m.phase = rnd.nextFloat() * (float) (Math.PI * 2);
        switch (mode.get()) {
            case "Snow" -> { m.size = 1.1f + rnd.nextFloat() * 1.5f; m.vy = 16 + rnd.nextFloat() * 22; m.vx = (rnd.nextFloat() - 0.5f) * 10; }
            case "Stars" -> { m.size = 0.7f + rnd.nextFloat() * 1.1f; m.vy = 1 + rnd.nextFloat() * 2; m.vx = 0; }
            case "Spores" -> { m.size = 4 + rnd.nextFloat() * 6; m.vy = -4 + rnd.nextFloat() * 8; m.vx = (rnd.nextFloat() - 0.5f) * 6; }
            case "Embers" -> { m.size = 0.9f + rnd.nextFloat() * 1.6f; m.vy = -8 - rnd.nextFloat() * 14; m.vx = (rnd.nextFloat() - 0.5f) * 8; }
            case "Rain" -> { m.size = 0.6f + rnd.nextFloat() * 0.5f; m.vy = 60 + rnd.nextFloat() * 50; m.vx = -8 - rnd.nextFloat() * 6; }
            default -> { m.size = 0.9f + rnd.nextFloat() * 1.9f; m.vy = 3 + rnd.nextFloat() * 8; m.vx = (rnd.nextFloat() - 0.5f) * 6; }
        }
        return m;
    }

    public void render(DrawContext ctx, float dt) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
        float w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        float inten = (float) (strength.get() / 100.0);
        t += dt;

        if (vignette.get()) {
            int a = (int) (150 * inten);
            int edge = (a << 24);
            R.vgrad(ctx, 0, 0, w, h * 0.30f, edge, 0x00000000);
            R.vgrad(ctx, 0, h * 0.72f, w, h * 0.28f, 0x00000000, edge);
            R.hgrad(ctx, 0, 0, w * 0.22f, h, edge, 0x00000000);
            R.hgrad(ctx, w * 0.78f, 0, w * 0.22f, h, 0x00000000, edge);
        }

        if (aurora.get()) {
            float drift = (float) Math.sin(t * 0.22f) * 0.5f + 0.5f;
            int a1 = (int) (16 * inten);
            R.vgrad(ctx, 0, 0, w, h * 0.42f, (a1 << 24) | 0xFFFFFF, 0x00FFFFFF);
            R.hgrad(ctx, -w * 0.2f + w * 0.4f * drift, 0, w * 0.7f, h * 0.3f,
                    (int) (10 * inten) << 24, 0x00000000);
        }

        float spd = (float) speed.get() * dt;
        while (motes.size() < density.getInt()) motes.add(make(w, h));
        while (motes.size() > density.getInt()) motes.remove(motes.size() - 1);

        float yaw = mc.player.getYaw(), pitch = mc.player.getPitch();
        if (Float.isNaN(lastYaw)) { lastYaw = yaw; lastPitch = pitch; }
        float dy = yaw - lastYaw;
        if (dy > 180) dy -= 360; else if (dy < -180) dy += 360;
        float dp = pitch - lastPitch;
        lastYaw = yaw;
        lastPitch = pitch;
        float px = parallax.get() ? -dy * 8f : 0f, py = parallax.get() ? -dp * 5f : 0f;

        boolean snow = mode.is("Snow"), stars = mode.is("Stars"), spores = mode.is("Spores");
        boolean embers = mode.is("Embers"), rain = mode.is("Rain");
        int rgb = spores ? 0xE8E8EE : snow || stars || rain ? 0xFFFFFF : 0xD9DCE2;

        for (Mote m : motes) {
            m.phase += dt * (snow ? 1.6f : stars ? 2.2f : 0.7f);
            float sway = snow ? 0.6f + 0.4f * (float) Math.sin(m.phase) : 1f;
            m.x += m.vx * spd + px * m.depth;
            m.y += m.vy * sway * spd + py * m.depth;
            if (m.x < -24) m.x += w + 48; else if (m.x > w + 24) m.x -= w + 48;
            if (m.y < -24) m.y += h + 48; else if (m.y > h + 24) m.y -= h + 48;

            float tw = stars ? 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(m.phase * 2.4f)) : 1f;
            float base = spores ? 0.10f : stars ? 0.75f : embers ? 0.55f : 0.28f;
            int col = ((int) (255 * base * tw * m.depth * inten) << 24) | rgb;
            if (rain) {
                R.line(ctx, m.x, m.y, m.x + m.vx * 0.06f, m.y + 7f, m.size, col);
            } else {
                R.circle(ctx, m.x, m.y, m.size, col);
                if (spores || embers) {
                    R.circle(ctx, m.x, m.y, m.size * 2.2f, ((int) (255 * 0.05f * tw * inten) << 24) | rgb);
                    R.circle(ctx, m.x, m.y, m.size * 0.4f, ((int) (255 * 0.14f * tw * inten) << 24) | 0xFFFFFF);
                }
            }
        }
    }
}
