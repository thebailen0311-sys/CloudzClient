package dev.cloud.module.impl;

import dev.cloud.module.*;
import dev.cloud.module.Module;
import dev.cloud.ui.Anim;
import dev.cloud.ui.R;
import dev.cloud.ui.T;
import dev.cloud.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public class TargetHudModule extends Module {
    public final NumSetting posX = add(new NumSetting("X Offset", 30, -300, 300, 1, ""));
    public final NumSetting posY = add(new NumSetting("Y Offset", 30, -200, 200, 1, ""));
    public final NumSetting timeout = add(new NumSetting("Timeout", 3000, 500, 10000, 100, " ms"));
    public final BoolSetting crosshair = add(new BoolSetting("Track Crosshair", true));
    public final BoolSetting glow = add(new BoolSetting("Glow", true));

    private LivingEntity target;
    private long seen;
    private float anim, hp = -1, ghost = -1;

    public TargetHudModule() { super("Target HUD", "Animated panel showing your current target", Category.RENDER, false); }

    public void onAttack(Entity e) {
        if (e instanceof LivingEntity le) { if (le != target) { hp = -1; ghost = -1; } target = le; seen = System.currentTimeMillis(); }
    }

    public void render(DrawContext ctx, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (isEnabled() && crosshair.get() && mc.targetedEntity instanceof LivingEntity le) onAttack(le);
        boolean show = isEnabled() && target != null && target.isAlive() && !target.isRemoved()
                && System.currentTimeMillis() - seen < timeout.get();
        anim = Anim.approach(anim, show ? 1f : 0f, 12f * Anim.speed(), dt);
        if (anim < 0.01f || target == null) return;
        float max = Math.max(1f, target.getMaxHealth());
        float frac = Math.min(1f, Math.max(0f, target.getHealth() / max));
        hp = hp < 0 ? frac : Anim.approach(hp, frac, 10f, dt);
        ghost = ghost < 0 ? frac : Anim.approach(ghost, frac, 2.5f, dt);
        float w = 130, h = 40;
        float x = ctx.getScaledWindowWidth() / 2f + (float) posX.get();
        float y = ctx.getScaledWindowHeight() / 2f + (float) posY.get();
        float s = 0.85f + 0.15f * anim;
        x += (1 - s) * w / 2; y += (1 - s) * h / 2;
        float sw = w * s, sh = h * s;
        int acc = Theme.accent();
        R.alpha = anim;
        if (glow.get()) R.glow(ctx, x, y, sw, sh, 8, R.withAlpha(acc, 0.35f), 8, 0.6f);
        R.bordered(ctx, x, y, sw, sh, 8, 0xE00A0A0A, 0x30FFFFFF);
        T.draw(ctx, target.getName().getString(), x + 8, y + 7, 0xFFFFFFFF, s);
        String info = String.format("%.1f hp  \u00B7  %.1fm", target.getHealth() + target.getAbsorptionAmount(),
                mc.player == null ? 0f : mc.player.distanceTo(target));
        T.draw(ctx, info, x + 8, y + 18 * s, 0xFF9A9A9A, 0.8f * s);
        float bx = x + 8, by = y + sh - 9 * s, bw = sw - 16, bh = 4 * s;
        R.round(ctx, bx, by, bw, bh, 2, 0x40FFFFFF);
        R.round(ctx, bx, by, bw * ghost, bh, 2, 0x60FFFFFF);
        R.round(ctx, bx, by, Math.max(bh, bw * hp), bh, 2, R.mix(0xFFFFFFFF, acc, 0.5f));
        R.alpha = 1f;
    }
}
