package dev.cloud.module.impl;

import dev.cloud.module.BoolSetting;
import dev.cloud.module.Category;
import dev.cloud.module.ModeSetting;
import dev.cloud.module.Module;
import dev.cloud.module.NumSetting;

public class WatermarkModule extends Module {
    public final ModeSetting position = add(new ModeSetting("Position", "Top Left", "Top Left", "Top Right", "Bottom Left", "Bottom Right"));
    public final ModeSetting mode = add(new ModeSetting("Mode", "Full", "Full", "Minimal"));
    public final BoolSetting fps = add(new BoolSetting("Show FPS", true));
    public final BoolSetting ping = add(new BoolSetting("Show Ping", true));
    public final BoolSetting clock = add(new BoolSetting("Show Time", false));
    public final NumSetting opacity = add(new NumSetting("Opacity", 80, 20, 100, 1, "%"));

    public WatermarkModule() { super("Watermark", "Client badge with live stats", Category.RENDER, true); }

    @Override public String suffix() { return mode.get(); }
}
