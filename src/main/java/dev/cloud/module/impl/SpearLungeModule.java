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
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;

public class SpearLungeModule extends Module {
    public final KeySetting bind = add(new KeySetting("Lunge Bind"));
    public final ModeSetting mode = add(new ModeSetting("Mode", "Instant", "Instant", "Delayed"));
    public final NumSetting delayMin = add(new NumSetting("Delay Min", 20, 0, 300, 5, " ms").showIf(() -> mode.is("Delayed")));
    public final NumSetting delayMax = add(new NumSetting("Delay Max", 50, 0, 300, 5, " ms").showIf(() -> mode.is("Delayed")));
    public final NumSetting holdTicks = add(new NumSetting("Hold Ticks", 1, 1, 10, 1, " t"));
    public final BoolSetting lungeOnly = add(new BoolSetting("Lunge Spears Only", true));
    public final BoolSetting swapBack = add(new BoolSetting("Swap Back", true));

    private int phase, prev = -1, ticks;
    private long due;

    public SpearLungeModule() {
        super("Spear Lunge", "Press the lunge bind: swap to your spear, jab, swap back", Category.COMBAT, false);
    }

    @Override public String suffix() { return mode.get(); }

    @Override
    public void tick(MinecraftClient mc) {
        boolean pressed = bind.pressed(mc);
        ClientPlayerEntity p = mc.player;
        if (!isEnabled() || p == null || mc.interactionManager == null) { phase = 0; return; }
        if (phase == 2) {
            if (--ticks <= 0) { if (swapBack.get()) Inv.select(p, prev); phase = 0; }
            return;
        }
        if (phase == 0 && pressed) {
            prev = Inv.selected(p);
            if (mode.is("Instant")) fire(mc, p);
            else { phase = 1; due = System.currentTimeMillis() + Inv.rnd(delayMin.get(), delayMax.get()); }
        } else if (phase == 1 && System.currentTimeMillis() >= due) fire(mc, p);
    }

    /** Runs at tick start, so vanilla processes the queued attack later this same tick with the spear held. */
    private void fire(MinecraftClient mc, ClientPlayerEntity p) {
        int best = -1, bestLvl = lungeOnly.get() ? 0 : -1;
        for (int i = 0; i < 9; i++) {
            var s = p.getInventory().getStack(i);
            if (!Inv.id(s).endsWith("_spear")) continue;
            int lvl = Inv.ench(s, "lunge");
            if (lvl > bestLvl) { bestLvl = lvl; best = i; }
        }
        if (best < 0) { phase = 0; return; }
        Inv.select(p, best);
        KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(mc.options.attackKey));
        phase = 2;
        ticks = holdTicks.getInt();
    }
}
