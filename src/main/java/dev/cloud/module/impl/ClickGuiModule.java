package dev.cloud.module.impl;

import dev.cloud.module.BoolSetting;
import dev.cloud.module.Category;
import dev.cloud.module.ModeSetting;
import dev.cloud.module.Module;
import dev.cloud.module.NumSetting;
import dev.cloud.ui.Theme;
import org.lwjgl.glfw.GLFW;

public class ClickGuiModule extends Module {
    public final ModeSetting theme = add(new ModeSetting("Theme", "Void", Theme.NAMES));
    public final ModeSetting accent = add(new ModeSetting("Accent", "White", "White", "Silver", "Ice", "Azure", "Violet", "Rose", "Custom"));
    public final NumSetting red = add(new NumSetting("Red", 140, 0, 255, 1, "").showIf(() -> accent.is("Custom")));
    public final NumSetting green = add(new NumSetting("Green", 170, 0, 255, 1, "").showIf(() -> accent.is("Custom")));
    public final NumSetting blue = add(new NumSetting("Blue", 255, 0, 255, 1, "").showIf(() -> accent.is("Custom")));
    public final BoolSetting blur = add(new BoolSetting("Background Blur", true));
    public final NumSetting blurStrength = add(new NumSetting("Blur Strength", 5, 1, 10, 1, "").showIf(() -> blur.get()));
    public final NumSetting dim = add(new NumSetting("Background Dim", 42, 0, 90, 1, "%"));
    public final BoolSetting uiGlow = add(new BoolSetting("Glow", true));
    public final NumSetting glowStrength = add(new NumSetting("Glow Strength", 55, 0, 100, 1, "%").showIf(() -> uiGlow.get()));
    public final NumSetting radius = add(new NumSetting("Corner Radius", 12, 4, 20, 1, "px"));
    public final BoolSetting frost = add(new BoolSetting("Frost Panels", true));
    public final BoolSetting sweep = add(new BoolSetting("Light Sweep", true));
    public final ModeSetting animStyle = add(new ModeSetting("Animation", "Spring", "Spring", "Smooth", "Snappy"));
    public final NumSetting animSpeed = add(new NumSetting("Animation Speed", 1.0, 0.5, 2.0, 0.05, "x"));
    public final NumSetting scrollSmooth = add(new NumSetting("Scroll Smoothing", 60, 0, 100, 1, "%"));
    public final BoolSetting notifications = add(new BoolSetting("Notifications", true));
    public final ModeSetting notifPos = add(new ModeSetting("Notification Pos", "Bottom Right", "Bottom Right", "Top Right"));

    public ClickGuiModule() {
        super("Click GUI", "Theme, accent, blur and animation settings", Category.CLIENT, false);
        key = GLFW.GLFW_KEY_RIGHT_SHIFT;
        hidden = true;
        expanded = true;
        expandAnim = 1f;
    }
}
