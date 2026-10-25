package dev.cloud.ui;

import dev.cloud.module.Module;
import dev.cloud.module.Modules;
import dev.cloud.module.impl.ArrayListModule;
import dev.cloud.module.impl.ClickGuiModule;
import dev.cloud.module.impl.WatermarkModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Hud {
    private Hud() {}

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private static long lastFrame = -1, lastNs = -1;
    private static final List<Module> SORTED = new ArrayList<>();

    private static final class Notif {
        final String name;
        final boolean on;
        final long start = System.currentTimeMillis();
        float anim;

        Notif(String name, boolean on) { this.name = name; this.on = on; }
    }

    private static final List<Notif> NOTIFS = new ArrayList<>();

    public static void notify(Module m) {
        if (m == null || m.hidden) return;
        if (Modules.CLICKGUI == null || !Modules.CLICKGUI.notifications.get()) return;
        NOTIFS.removeIf(n -> n.name.equals(m.name));
        NOTIFS.add(new Notif(m.name, m.isEnabled()));
        if (NOTIFS.size() > 4) NOTIFS.remove(0);
    }

    /** If the HUD isn't rendering (F1), still drive per-frame modules from the tick. */
    public static void tickFallback(MinecraftClient mc) {
        if (System.currentTimeMillis() - lastFrame > 100) {
            for (Module m : Modules.ALL) m.frame(mc, 0.05f);
        }
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.nanoTime();
        float dt = lastNs < 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        lastFrame = System.currentTimeMillis();
        for (Module m : Modules.ALL) m.frame(mc, dt);
        R.alpha = 1f;
        R.beginFrame();
        R.begin(ctx);
        try {
            if (Modules.AMBIENCE != null) Modules.AMBIENCE.render(ctx, dt);
            watermark(ctx, dt);
            arrayList(ctx, dt);
            notifications(ctx, dt);
            if (Modules.HITFX != null) Modules.HITFX.render(ctx, dt);
            if (Modules.TARGETHUD != null) Modules.TARGETHUD.render(ctx, dt);
        } finally {
            R.end(ctx);
            R.alpha = 1f;
        }
    }

    private static String label(Module m, ArrayListModule a) {
        String s = m.name;
        if (a.lowercase.get()) s = s.toLowerCase(Locale.ROOT);
        return s;
    }

    private static String suffix(Module m, ArrayListModule a) {
        if (!a.suffixes.get()) return null;
        String s = m.suffix();
        if (s == null) return null;
        return a.lowercase.get() ? s.toLowerCase(Locale.ROOT) : s;
    }

    private static void arrayList(DrawContext ctx, float dt) {
        ArrayListModule a = Modules.ARRAYLIST;
        float sp = Anim.speed();
        SORTED.clear();
        for (Module m : Modules.ALL) {
            if (m.hidden) continue;
            boolean show = a.isEnabled() && m.isEnabled();
            m.arrayAnim = Anim.linear(m.arrayAnim, show ? 1f : 0f, 4.5f * sp, dt);
            String suf = suffix(m, a);
            m.arrayTarget = T.width(label(m, a)) + (suf != null ? 4 + T.width(suf) : 0);
            m.arrayWidth = m.arrayWidth < 0 ? m.arrayTarget : Anim.approach(m.arrayWidth, m.arrayTarget, 14f * sp, dt);
            if (m.arrayAnim > 0.001f) SORTED.add(m);
        }
        if (SORTED.isEmpty()) return;
        SORTED.sort(Comparator.comparingDouble((Module m) -> -m.arrayTarget));

        String pos = a.position.get();
        boolean right = pos.contains("Right"), bottom = pos.contains("Bottom");
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float offX = (float) a.offsetX.get(), offY = (float) a.offsetY.get();
        float h = 12f, gap = (float) a.spacing.get();
        float y = bottom ? sh - offY - h : offY;
        int accent = Theme.accent();
        float time = (System.currentTimeMillis() % 100000L) / 1000f;
        int bg = (int) (a.bgOpacity.get() / 100.0 * 255) << 24 | 0x0A0A0C;

        int idx = 0;
        for (Module m : SORTED) {
            float e = m.arrayAnim >= 1f ? 1f : Anim.easeOutCubic(m.arrayAnim);
            float w = m.arrayWidth + 8f;
            float slide = (1f - e) * (w + offX + 8f);
            float x = right ? sw - offX - w + slide : offX - slide;
            int col = switch (a.textColor.get()) {
                case "White" -> 0xFFFFFFFF;
                case "Wave" -> R.mix(accent, 0xFFFFFFFF, 0.5f + 0.5f * (float) Math.sin(time * 3f - idx * 0.6f));
                default -> accent;
            };
            R.alpha = e;
            if (a.glow.get()) R.glow(ctx, x, y, w, h, 3, col, 5, (float) a.glowStrength.get() / 100f);
            R.round(ctx, x, y, w, h, 2, bg);
            if (a.outline.get()) R.outline(ctx, x, y, w, h, R.withAlpha(col, 0.9f));
            R.round(ctx, right ? x + w - 1.5f : x, y + 1.5f, 1.5f, h - 3, 0.75f, col);
            String lab = label(m, a);
            T.draw(ctx, lab, x + 4, y + 2, col);
            String suf = suffix(m, a);
            if (suf != null) T.draw(ctx, suf, x + 4 + T.width(lab) + 4, y + 2, 0xFF9A9AA2);
            y += (bottom ? -1 : 1) * (h + gap) * e;
            idx++;
        }
        R.alpha = 1f;
    }

    private static void watermark(DrawContext ctx, float dt) {
        WatermarkModule wm = Modules.WATERMARK;
        if (wm == null || !wm.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Theme th = Theme.current();
        int ac = Theme.accent();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        boolean minimal = wm.mode.is("Minimal");
        boolean right = wm.position.get().contains("Right");
        boolean bottom = wm.position.get().contains("Bottom");

        List<String> parts = new ArrayList<>();
        if (wm.fps.get()) parts.add(mc.getCurrentFps() + " fps");
        if (wm.ping.get() && mc.player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) parts.add(Math.max(0, entry.getLatency()) + " ms");
        }
        if (wm.clock.get()) parts.add(LocalTime.now().format(CLOCK));
        String stats = String.join("  \u00B7  ", parts);

        float pad = 9f, h = 20f;
        float logoW = minimal ? 0 : 12f;
        float brandW = minimal ? 0 : logoW + 3 + T.width("cloude", 1.1f) + 2 + T.width("client", 1.1f);
        float statsW = stats.isEmpty() ? 0 : T.width(stats, 0.85f);
        float inner = brandW + (brandW > 0 && statsW > 0 ? pad : 0) + statsW;
        if (inner <= 0) return;
        float w = pad * 2 + inner + 8;
        float x = right ? sw - 6 - w : 6;
        float y = bottom ? sh - 6 - h : 6;

        float a = (float) (wm.opacity.get() / 100.0);
        R.glow(ctx, x, y, w, h, 7, ac, 4, 0.25f * a);
        R.bordered(ctx, x, y, w, h, 7, ((int) (230 * a) << 24) | (th.bg() & 0xFFFFFF), th.line());
        R.round(ctx, x + 3, y + 5, 2, h - 10, 1, ac);
        R.glow(ctx, x, y, w, h, 7, 0xFFFFFFFF, 3, 0.10f * a);
        R.rect(ctx, x + 5, y + 1, w - 10, 1, R.withAlpha(0xFFFFFFFF, 0.13f * a));
        R.outline(ctx, x + 0.5f, y + 0.5f, w - 1, h - 1, R.withAlpha(0xFFFFFFFF, 0.18f * a));

        float tx = x + pad;
        if (!minimal) {
            Logo.mark(ctx, tx + 1, y + 5, 10, R.withAlpha(th.text(), a), R.withAlpha(th.bg(), a));
            tx += logoW + 3;
            T.draw(ctx, "cloude", tx, y + 6, th.text(), 1.1f);
            T.draw(ctx, "client", tx + T.width("cloude", 1.1f) + 2, y + 6, th.sub(), 1.1f);
            tx += brandW + pad;
        }
        if (!stats.isEmpty()) T.draw(ctx, stats, tx, y + 7, th.sub(), 0.85f);

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        R.circle(ctx, x + w - 8, y + h / 2, 1.3f + 0.5f * pulse, R.withAlpha(ac, 0.5f + 0.5f * pulse));
    }

    private static void notifications(DrawContext ctx, float dt) {
        if (NOTIFS.isEmpty()) return;
        ClickGuiModule g = Modules.CLICKGUI;
        boolean top = g != null && g.notifPos.is("Top Right");
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        Theme th = Theme.current();
        int ac = Theme.accent();
        long now = System.currentTimeMillis();
        float y = top ? 6f : sh - 54f;
        for (int i = NOTIFS.size() - 1; i >= 0; i--) {
            Notif n = NOTIFS.get(i);
            boolean alive = now - n.start < 1600;
            n.anim = Anim.approach(n.anim, alive ? 1f : 0f, 12f, dt);
            if (!alive && n.anim < 0.01f) { NOTIFS.remove(i); continue; }
            String s = n.name + (n.on ? "  enabled" : "  disabled");
            float w = T.width(s) + 22, h = 18;
            float x = sw - 6 - w + (1f - n.anim) * (w + 10);
            R.alpha = n.anim;
            R.glow(ctx, x, y, w, h, 6, ac, 4, n.on ? 0.5f : 0.2f);
            R.glow(ctx, x, y, w, h, 6, 0xFFFFFFFF, 3, 0.10f * n.anim);
            R.bordered(ctx, x, y, w, h, 6, th.bg(), th.line());
            R.rect(ctx, x + 4, y + 1, w - 8, 1, R.withAlpha(0xFFFFFFFF, 0.12f * n.anim));
            R.circle(ctx, x + 9, y + 9, 2.5f, n.on ? ac : th.sub());
            T.draw(ctx, s, x + 16, y + 5, th.text());
            float life = Anim.clamp((now - n.start) / 1600f);
            R.round(ctx, x + 3, y + h - 2.5f, (w - 6) * (1f - life), 1.5f, 0.75f, R.withAlpha(ac, 0.8f));
            y += top ? (h + 4) * n.anim : -(h + 4) * n.anim;
        }
        R.alpha = 1f;
    }
}
