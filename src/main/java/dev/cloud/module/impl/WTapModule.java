package dev.cloud.module.impl;

import dev.cloud.module.BoolSetting;
import dev.cloud.module.Category;
import dev.cloud.module.Module;
import dev.cloud.module.NumSetting;

public class WTapModule extends Module {
    public final BoolSetting weaponOnly = add(new BoolSetting("Weapon Only", true));
    public final BoolSetting onlySprinting = add(new BoolSetting("Only When Sprinting", true));
    public final BoolSetting resprint = add(new BoolSetting("Re-Sprint After", true));
    public final NumSetting reactMin = add(new NumSetting("React Min", 20, 0, 500, 1, " ms"));
    public final NumSetting reactMax = add(new NumSetting("React Max", 60, 0, 500, 1, " ms"));
    public final NumSetting holdMin = add(new NumSetting("Hold Min", 60, 50, 400, 1, " ms"));
    public final NumSetting holdMax = add(new NumSetting("Hold Max", 110, 50, 400, 1, " ms"));

    public WTapModule() {
        super("W-Tap", "Releases W after your hits to reset sprint knockback", Category.COMBAT, true);
    }

    @Override
    public String suffix() {
        return holdMin.getInt() + "-" + Math.max(holdMin.getInt(), holdMax.getInt()) + "ms";
    }
}
