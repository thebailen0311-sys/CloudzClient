package dev.cloud.ui;

import dev.cloud.module.Modules;
import dev.cloud.module.impl.ClickGuiModule;

/** Monochrome palettes. Every surface is a black-to-white gradient, never a flat fill. */
public record Theme(String name, int bg, int side, int card, int cardHover, int line, int text, int sub, int track, boolean light) {
    public static final String[] NAMES = {"Void", "Carbon", "Graphite", "Frost", "Obsidian"};

    private static final Theme[] ALL = {
            new Theme("Void", 0xF2070708, 0xE60C0C0E, 0xC8101013, 0xE61A1A1E, 0x1CFFFFFF, 0xFFF7F7F8, 0xFF7E7E86, 0xFF232327, false),
            new Theme("Carbon", 0xF20E0F11, 0xE616171A, 0xC81B1C20, 0xE626272C, 0x1EFFFFFF, 0xFFF2F2F4, 0xFF8A8A92, 0xFF2C2D32, false),
            new Theme("Graphite", 0xF2141518, 0xE61C1D21, 0xC8222328, 0xE62E2F35, 0x22FFFFFF, 0xFFEDEDEF, 0xFF95959D, 0xFF35363C, false),
            new Theme("Frost", 0xF2F6F6F8, 0xE6EDEDF0, 0xD8FFFFFF, 0xE6F2F2F5, 0x1A000000, 0xFF0E0E10, 0xFF6C6C74, 0xFFD8D8DE, true),
            new Theme("Obsidian", 0xF6000000, 0xE6080808, 0xC80B0B0B, 0xE6161616, 0x18FFFFFF, 0xFFFFFFFF, 0xFF6E6E74, 0xFF1E1E1E, false)
    };

    public static Theme current() {
        int i = Modules.CLICKGUI == null ? 0 : Modules.CLICKGUI.theme.index();
        return ALL[Math.max(0, Math.min(ALL.length - 1, i))];
    }

    /** Top of the surface gradient. */
    public int top() {
        return light ? 0xFFFFFFFF : R.mix(bg & 0xFFFFFF | 0xFF000000, 0xFFFFFFFF, 0.055f);
    }

    /** Bottom of the surface gradient. */
    public int bot() {
        return light ? 0xFFE7E7EC : R.mix(bg & 0xFFFFFF | 0xFF000000, 0xFF000000, 0.55f);
    }

    public int cardTop() {
        return light ? 0xFFFFFFFF : R.mix(card & 0xFFFFFF | 0xFF000000, 0xFFFFFFFF, 0.045f);
    }

    public int cardBot() {
        return light ? 0xFFF1F1F4 : R.mix(card & 0xFFFFFF | 0xFF000000, 0xFF000000, 0.35f);
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
        if (current().light && R.lum(c) > 0.75f) return 0xFF151518;
        return c;
    }

    /** Second stop of the accent gradient. */
    public static int accent2() {
        int a = accent();
        return current().light ? R.mix(a, 0xFF000000, 0.35f) : R.mix(a, 0xFF000000, 0.45f);
    }

    public static int onAccent() {
        return R.lum(accent()) > 0.6f ? 0xFF0E0E10 : 0xFFFFFFFF;
    }
}
