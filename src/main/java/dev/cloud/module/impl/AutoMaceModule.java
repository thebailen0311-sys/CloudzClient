package dev.cloud.module.impl;

import dev.cloud.Weapons;
import dev.cloud.module.*;
import dev.cloud.module.Module;
import dev.cloud.util.Inv;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;

public class AutoMaceModule extends Module {
    public final NumSetting minFall = add(new NumSetting("Min Fall", 1.5, 0.5, 20, 0.5, " b"));
    public final BoolSetting autoHit = add(new BoolSetting("Auto Hit", true));
    public final ModeSetting mode = add(new ModeSetting("Mace", "Auto", "Auto", "Density", "Breach"));
    public final NumSetting armor = add(new NumSetting("Breach Armor", 12, 0, 20, 1, "").showIf(() -> mode.is("Auto")));
    public final BoolSetting shieldAxe = add(new BoolSetting("Axe On Shield", true));
    public final NumSetting swapBack = add(new NumSetting("Swap Back", 50, 0, 500, 5, " ms"));
    public final BoolSetting playersOnly = add(new BoolSetting("Players Only", false));

    private int prev = -1;
    private long restoreAt = -1;
    private boolean fired;

    public AutoMaceModule() {
        super("Auto Mace", "Swaps to the best mace for falling hits, axe on shields", Category.COMBAT, false);
    }

    @Override public String suffix() { return mode.get(); }

    @Override
    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        long now = System.currentTimeMillis();
        if (restoreAt >= 0 && now >= restoreAt) { Inv.select(p, prev); prev = -1; restoreAt = -1; }
        if (!isEnabled()) return;
        if (p.isOnGround()) fired = false;
        if (!autoHit.get() || fired || mc.currentScreen != null || p.fallDistance < minFall.get()) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult h) || !valid(p, h.getEntity())) return;
        LivingEntity t = (LivingEntity) h.getEntity();
        int mace = pick(p, t);
        if (mace < 0) return;
        if (prev < 0) prev = Inv.selected(p);
        if (shieldAxe.get() && t.isBlocking()) {
            int axe = Inv.hotbar(p, s -> Inv.id(s).endsWith("_axe"));
            if (axe >= 0) { Inv.select(p, axe); Inv.attack(mc, t); }
        }
        Inv.select(p, mace);
        Inv.attack(mc, t);
        fired = true;
        restoreAt = now + swapBack.getInt();
    }

    /** Manual hits: swap to the mace right before vanilla sends the attack (attribute swap). */
    public void onAttack(MinecraftClient mc, Entity e) {
        ClientPlayerEntity p = mc.player;
        if (!isEnabled() || p == null || p.isOnGround() || p.fallDistance < minFall.get() || !valid(p, e)) return;
        int mace = pick(p, (LivingEntity) e);
        if (mace < 0) return;
        if (prev < 0) prev = Inv.selected(p);
        Inv.select(p, mace);
        restoreAt = System.currentTimeMillis() + swapBack.getInt();
    }

    private boolean valid(ClientPlayerEntity p, Entity e) {
        return e instanceof LivingEntity le && e != p && le.isAlive() && (!playersOnly.get() || e instanceof PlayerEntity);
    }

    private int pick(ClientPlayerEntity p, LivingEntity t) {
        boolean breach = mode.is("Breach") || (mode.is("Auto") && t.getArmor() >= armor.getInt());
        int best = -1, bestScore = -1;
        for (int i = 0; i < 9; i++) {
            var s = p.getInventory().getStack(i);
            if (!s.isOf(Items.MACE)) continue;
            int br = Inv.ench(s, "breach"), de = Inv.ench(s, "density");
            int score = breach ? br * 10 + de : de * 10 + br;
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }
}
