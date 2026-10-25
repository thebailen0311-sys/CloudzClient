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

public class ShieldBreakerModule extends Module {
    public final ModeSetting mode = add(new ModeSetting("Mode", "Auto", "Auto", "On Click"));
    public final NumSetting reactMin = add(new NumSetting("React Min", 40, 0, 500, 5, " ms").showIf(() -> mode.is("Auto")));
    public final NumSetting reactMax = add(new NumSetting("React Max", 90, 0, 500, 5, " ms").showIf(() -> mode.is("Auto")));
    public final NumSetting swapBack = add(new NumSetting("Swap Back", 60, 0, 500, 5, " ms"));
    public final BoolSetting stun = add(new BoolSetting("Stun Slam", false).showIf(() -> mode.is("Auto")));
    public final NumSetting stunChance = add(new NumSetting("Stun Chance", 60, 0, 100, 1, "%").showIf(() -> mode.is("Auto") && stun.get()));

    private int prev = -1, delay;
    private long seen = -1, restoreAt = -1;

    public ShieldBreakerModule() {
        super("Shield Breaker", "Disables blocking players' shields with an axe", Category.COMBAT, false);
    }

    @Override public String suffix() { return mode.get(); }

    private static int axe(ClientPlayerEntity p) { return Inv.hotbar(p, s -> Inv.id(s).endsWith("_axe")); }

    @Override
    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        long now = System.currentTimeMillis();
        if (restoreAt >= 0 && now >= restoreAt) { Inv.select(p, prev); prev = -1; restoreAt = -1; }
        if (!isEnabled() || mc.currentScreen != null || !mode.is("Auto")) { seen = -1; return; }
        if (!(mc.crosshairTarget instanceof EntityHitResult h) || !(h.getEntity() instanceof PlayerEntity t) || !t.isBlocking()) { seen = -1; return; }
        if (seen < 0) { seen = now; delay = (int) Inv.rnd(reactMin.get(), reactMax.get()); }
        if (now - seen < delay) return;
        int a = axe(p);
        if (a < 0) return;
        if (prev < 0) prev = Inv.selected(p);
        Inv.select(p, a);
        Inv.attack(mc, t);
        if (stun.get() && Inv.chance(stunChance.get())) {
            int m = Inv.hotbar(p, s -> s.isOf(Items.MACE));
            if (m >= 0) { Inv.select(p, m); Inv.attack(mc, t); }
        }
        restoreAt = now + swapBack.getInt();
        seen = -1;
    }

    /** Returns true if it swapped to an axe for this manual hit. */
    public boolean onAttack(MinecraftClient mc, Entity e) {
        ClientPlayerEntity p = mc.player;
        if (!isEnabled() || p == null || !mode.is("On Click") || !(e instanceof LivingEntity le) || !le.isBlocking()) return false;
        int a = axe(p);
        if (a < 0) return false;
        if (prev < 0) prev = Inv.selected(p);
        Inv.select(p, a);
        restoreAt = System.currentTimeMillis() + swapBack.getInt();
        return true;
    }
}
