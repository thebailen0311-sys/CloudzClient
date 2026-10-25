package dev.cloud.module.impl;

import dev.cloud.module.*;
import dev.cloud.module.Module;
import dev.cloud.ui.Anim;
import dev.cloud.ui.R;
import dev.cloud.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Random;

/** Fully custom vector hit markers drawn around the crosshair. No vanilla sounds or particles. */
public class HitEffectsModule extends Module {
    public final ModeSetting style = add(new ModeSetting("Style", "Full", "Full", "Marker", "Ring", "Sparks"));
    public final NumSetting strength = add(new NumSetting("Strength", 80, 20, 100, 1, "%"));
    public final NumSetting lifetime = add(new NumSetting("Duration", 450, 150, 900, 10, " ms"));
    public final BoolSetting playersOnly = add(new BoolSetting("Players Only", false));

    private static final int POOL = 8;
    private final long[] hits = new long[POOL];
    private int head;

    public HitEffectsModule() {
        super("Hit Effects", "Custom vector hit markers around your crosshair", Category.RENDER, false);
    }

    @Override public String suffix() { return style.get(); }

    public void onAttack(MinecraftClient mc, Entity e) {
        if (!isEnabled() || e == null || mc.player == null || e == mc.player) return;
        if (playersOnly.get() && !(e instanceof PlayerEntity)) return;
        hits[head] = System.currentTimeMillis();
        head = (head + 1) % POOL;
    }

    public void render(DrawContext ctx, float dt) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
        long now = System.currentTimeMillis(), newest = 0L;
        for (long t : hits) if (t > 0 && now - t < lifetime.get() && t > newest) newest = t;
        if (newest == 0L) return;
        float t01 = Anim.clamp((now - newest) / (float) lifetime.get());
        float e = Anim.easeOutCubic(t01);
        float fade = 1f - t01;
        int a = (int) (255 * fade * strength.get() / 100.0);
        if (a < 4) return;
        int col = (a << 24) | (Theme.accent() & 0xFFFFFF);
        float cx = ctx.getScaledWindowWidth() / 2f, cy = ctx.getScaledWindowHeight() / 2f;
        boolean full = style.is("Full");

        if (full || style.is("Marker")) {
            for (int i = 0; i < 4; i++) {
                double ang = Math.PI / 4 + i * Math.PI / 2;
                float sx = (float) Math.cos(ang), sy = (float) Math.sin(ang);
                float r0 = 7 + 2 * e, r1 = r0 + 5;
                R.dotLine(ctx, cx + sx * r0, cy + sy * r0, cx + sx * r1, cy + sy * r1, 2.2f, 1.1f, col);
            }
        }
        if (full || style.is("Ring")) {
            float rad = 12 + 20 * e;
            R.dotRing(ctx, cx, cy, rad, 4.5f, 0.3f + 0.8f * fade, R.withAlpha(col, 0.7f * fade));
        }
        if (full || style.is("Sparks")) {
            Random rnd = new Random(newest);
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI * 2 / 8 + rnd.nextDouble() * 0.4;
                float sx = (float) Math.cos(ang), sy = (float) Math.sin(ang);
                float r0 = 4 + 6 * e, r1 = r0 + (8 + rnd.nextFloat() * 14) * e + 2;
                R.dotLine(ctx, cx + sx * r0, cy + sy * r0, cx + sx * r1, cy + sy * r1, 2.6f, 0.9f, R.withAlpha(col, 0.85f));
            }
        }
    }
}
