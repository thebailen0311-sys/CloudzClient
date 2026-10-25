package dev.cloud.module.impl;

import dev.cloud.module.BoolSetting;
import dev.cloud.module.Category;
import dev.cloud.module.Module;
import dev.cloud.module.NumSetting;

public class TriggerbotModule extends Module {
    public final BoolSetting weaponOnly = add(new BoolSetting("Weapon Only", true));
    public final BoolSetting playersOnly = add(new BoolSetting("Players Only", true));
    public final NumSetting reactMin = add(new NumSetting("React Min", 60, 0, 500, 1, " ms"));
    public final NumSetting reactMax = add(new NumSetting("React Max", 120, 0, 500, 1, " ms"));
    public final NumSetting minCharge = add(new NumSetting("Min Charge", 95, 50, 100, 1, "%"));
    public final BoolSetting critMode = add(new BoolSetting("Crit Mode", false));
    public final BoolSetting critDropSprint = add(new BoolSetting("Crit: Drop Sprint", false).showIf(() -> critMode.get()));
    public final NumSetting critFallback = add(new NumSetting("Crit Fallback", 0, 0, 2000, 10, " ms").showIf(() -> critMode.get()));

    public TriggerbotModule() {
        super("Triggerbot", "Attacks when your crosshair is on a valid target", Category.COMBAT, false);
    }

    @Override
    public String suffix() { return critMode.get() ? "Crit" : "Normal"; }
}
