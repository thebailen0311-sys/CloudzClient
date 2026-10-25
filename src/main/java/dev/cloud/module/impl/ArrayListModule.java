package dev.cloud.module.impl;

import dev.cloud.module.BoolSetting;
import dev.cloud.module.Category;
import dev.cloud.module.ModeSetting;
import dev.cloud.module.Module;
import dev.cloud.module.NumSetting;

public class ArrayListModule extends Module {
    public final ModeSetting position = add(new ModeSetting("Position", "Top Right", "Top Right", "Top Left", "Bottom Right", "Bottom Left"));
    public final ModeSetting textColor = add(new ModeSetting("Text Color", "Accent", "Accent", "White", "Wave"));
    public final BoolSetting lowercase = add(new BoolSetting("Lowercase", true));
    public final BoolSetting suffixes = add(new BoolSetting("Suffixes", true));
    public final BoolSetting glow = add(new BoolSetting("Glow", true));
    public final NumSetting glowStrength = add(new NumSetting("Glow Strength", 70, 0, 100, 1, "%").showIf(() -> glow.get()));
    public final BoolSetting outline = add(new BoolSetting("Outline", true));
    public final NumSetting bgOpacity = add(new NumSetting("Background", 55, 0, 100, 1, "%"));
    public final NumSetting spacing = add(new NumSetting("Spacing", 1, 0, 4, 0.5, "px"));
    public final NumSetting offsetX = add(new NumSetting("Offset X", 4, 0, 40, 1, "px"));
    public final NumSetting offsetY = add(new NumSetting("Offset Y", 4, 0, 40, 1, "px"));

    public ArrayListModule() {
        super("Array List", "Animated list of enabled modules on your HUD", Category.RENDER, true);
    }
}
