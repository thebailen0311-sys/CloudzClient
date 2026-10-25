package dev.cloud.module.impl;

import dev.cloud.module.BoolSetting;
import dev.cloud.module.Category;
import dev.cloud.module.ModeSetting;
import dev.cloud.module.Module;
import dev.cloud.module.NumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Glow ESP. The colour is injected through Entity#getTeamColorValue, so it costs nothing extra. */
public class EspModule extends Module {
    public final BoolSetting players = add(new BoolSetting("Players", true));
    public final BoolSetting mobs = add(new BoolSetting("Hostiles", false));
    public final BoolSetting animals = add(new BoolSetting("Animals", false));
    public final BoolSetting invis = add(new BoolSetting("Invisibles", true));
    public final ModeSetting colorMode = add(new ModeSetting("Color", "Mono", "Mono", "Accent", "Custom"));
    public final NumSetting red = add(new NumSetting("Red", 255, 0, 255, 1, "").showIf(() -> colorMode.is("Custom")));
    public final NumSetting green = add(new NumSetting("Green", 255, 0, 255, 1, "").showIf(() -> colorMode.is("Custom")));
    public final NumSetting blue = add(new NumSetting("Blue", 255, 0, 255, 1, "").showIf(() -> colorMode.is("Custom")));

    public EspModule() { super("ESP", "Glowing outlines through walls", Category.RENDER, false); }

    @Override public String suffix() { return colorMode.get(); }

    public boolean outline(Entity e) {
        if (!isEnabled() || e == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (e == mc.player) return false;
        if (e.isInvisible() && !invis.get()) return false;
        if (e instanceof PlayerEntity) return players.get();
        if (e instanceof Monster) return mobs.get();
        return animals.get() && e instanceof AnimalEntity;
    }

    /** RGB used for the glow outline. */
    public int color() {
        return switch (colorMode.get()) {
            case "Accent" -> dev.cloud.ui.Theme.accent() & 0xFFFFFF;
            case "Custom" -> (red.getInt() << 16) | (green.getInt() << 8) | blue.getInt();
            default -> 0xFFFFFF;
        };
    }
}
