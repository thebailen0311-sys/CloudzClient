package dev.cloud.module.impl;

import dev.cloud.module.*;
import dev.cloud.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

public class EspModule extends Module {
    public final BoolSetting players = add(new BoolSetting("Players", true));
    public final BoolSetting mobs = add(new BoolSetting("Hostiles", false));
    public final BoolSetting invis = add(new BoolSetting("Invisibles", true));

    public EspModule() { super("ESP", "Glowing outlines through walls", Category.RENDER, false); }

    public boolean outline(Entity e) {
        if (!isEnabled() || e == null) return false;
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (e == mc.player) return false;
        if (e.isInvisible() && !invis.get()) return false;
        if (e instanceof PlayerEntity) return players.get();
        return mobs.get() && e instanceof Monster;
    }
}
