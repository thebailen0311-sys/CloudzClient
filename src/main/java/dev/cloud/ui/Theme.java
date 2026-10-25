package dev.cloud.ui;

import dev.cloud.module.Modules;
import dev.cloud.module.impl.ClickGuiModule;

public record Theme(String name, int bg, int side, int card, int cardHover, int line, int text, int sub, int track, boolean light) {
    public static final String[] NAMES = {"Cloude Dark", "Mono Dark", "Graphite", "Midnight", "Obsidian", "Mono Light"};

    private static final Theme[] ALL = {
            new Theme("Cloude Dark", 0xF2050507, 0xD809090B, 0xC80C0C0F, 0xD8141418, 0x22FFFFFF, 0xFFF6F6F8, 0xFF7C7C85, 0xFF1C1C20, false),
            new Theme("Mono Dark", 0xE40B0B0D, 0xC8141417, 0xB4151518, 0xC81E1E22, 0x30FFFFFF, 0xFFF2F2F2, 0xFF8A8A90, 0xFF2A2A2F, false),
            new Theme("Graphite", 0xE41A1B1E, 0xC8222327, 0xB4232428, 0xC82D2E33, 0x2EFFFFFF, 0xFFEDEDED, 0xFF9A9BA0, 0xFF37383D, false),
            new Theme("Midnight", 0xE4080A12, 0xC80E1220, 0xB4101524, 0xC8181E31, 0x30C8D8FF, 0xFFEEF2FF, 0xFF8088A0, 0xFF242A3C, false),
            new Theme("Obsidian", 0xF2000000, 0xD20A0A0A, 0xC40C0C0C, 0xD2171717, 0x26FFFFFF, 0xFFFFFFFF, 0xFF777777, 0xFF232323, false),
            new Theme("Mono Light", 0xECF4F4F6, 0xD8E8E8EB, 0xDCFFFFFF, 0xE8F0F0F3, 0x24000000, 0xFF111114, 0xFF6B6B72, 0xFFD0D0D6, true)
    };

    public static Theme current() {
        int i = Modules.CLICKGUI == null ? 0 : Modules.CLICKGUI.theme.index();
        return ALL[Math.max(0, Math.min(ALL.length - 1, i))];
    }

    public static int accent() {
        ClickGuiModule g = Modules.CLICKGUI;
        int c = g == null ? 0xFFFFFFFF : switch (g.accent.get()) {
            case "Silver" -> 0xFFC8CCD4;
            case "Ice" -> 0xFFBFE6FF;
            case "Azure" -> 0xFF4DA3FF;
            case "Violet" -> 0xFF9B7BFF;
            case "Rose" -> 0xFFFF6B9A;
            case "Custom" -> 0xFF000000 | (g.red.getInt() << 16) | (g.green.getInt() << 8) | g.blue.getInt();
            default -> 0xFFFFFFFF;
        };
        if (current().light && R.lum(c) > 0.75f) return 0xFF151518; // keep contrast on the light theme
        return c;
    }

    /** Readable colour to draw on top of the accent (switch knobs etc). */
    public static int onAccent() {
        return R.lum(accent()) > 0.6f ? 0xFF0E0E10 : 0xFFFFFFFF;
    }
}
