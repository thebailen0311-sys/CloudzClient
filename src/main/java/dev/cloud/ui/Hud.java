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
        R.begin(ctx);
        try {
            if (Modules.AMBIENCE != null) Modules.AMBIENCE.render(ctx, dt);
            if (Modules.SPOTIFY != null) Modules.SPOTIFY.render(ctx, dt);
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
        return a.lowercase.get() ? s.toLowerCase(Locale.ROOT) : s;
    }

    private static String suffix(Module m, ArrayListModule a) {
        if (!a.suffixes.get()) return null;
        String s = m.suffix();
        if (s == null) return null;
        return a.lowercase.get() ? s.toLowerCase(Locale.ROOT) : s;
    }

    // ------------------------------------------------------------------ array list

    private static void arrayList(DrawContext ctx, float dt) {
        ArrayListModule a = Modules.ARRAYLIST;
        if (a == null) return;
        float sp = Anim.speed();
        SORTED.clear();
        for (Module m : Modules.ALL) {
            if (m.hidden) continue;
            boolean show = a.isEnabled() && m.isEnabled();
            m.arrayAnim = Anim.linear(m.arrayAnim, show ? 1f : 0f, 5f * sp, dt);
            String suf = suffix(m, a);
            m.arrayTarget = T.width(label(m, a)) + (suf != null ? 6 + T.width(suf, 0.80f) : 0);
            m.arrayWidth = m.arrayWidth < 0 ? m.arrayTarget : Anim.approach(m.arrayWidth, m.arrayTarget, 16f * sp, dt);
            if (m.arrayAnim > 0.001f) SORTED.add(m);
        }
        if (SORTED.isEmpty()) return;
        SORTED.sort(Comparator.comparingDouble((Module m) -> -m.arrayTarget));

        String pos = a.position.get();
        boolean right = pos.contains("Right"), bottom = pos.contains("Bottom");
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float offX = (float) a.offsetX.get(), offY = (float) a.offsetY.get();
        float h = 14f, gap = (float) a.spacing.get();
        int ac = Theme.accent();
        Theme th = Theme.current();
        float time = (System.currentTimeMillis() % 100000L) / 1000f;
        int bgA = (int) (a.bgOpacity.get() / 100.0 * 255);
        float glowAmt = (float) (a.glowStrength.get() / 100.0);

        float y = bottom ? sh - offY - h : offY;
        int idx = 0;
        for (Module m : SORTED) {
            float e = Anim.easeOutCubic(m.arrayAnim);
            float w = m.arrayWidth + 13f;
            float slide = (1f - e) * (w + offX + 10f);
            float x = right ? sw - offX - w + slide : offX - slide;

            float ty = y;
            m.arrayY = Float.isNaN(m.arrayY) ? ty : Anim.approach(m.arrayY, ty, 20f * sp, dt);
            float dy = m.arrayY;

            int col = switch (a.textColor.get()) {
                case "White" -> 0xFFFFFFFF;
                case "Accent" -> ac;
                default -> R.mix(0xFFFFFFFF, R.withAlpha(0xFFFFFFFF, 0.55f), 0f);
            };

            R.alpha = e;
            if (bgA > 0) {
                if (a.gradient.get()) {
                    R.gradRound(ctx, x, dy, w, h, 4,
                            (bgA << 24) | 0x0C0C0E, ((int) (bgA * 0.55f) << 24) | 0x050506);
                } else {
                    R.round(ctx, x, dy, w, h, 4, (bgA << 24) | 0x0A0A0C);
                }
            }
            if (a.outline.get()) {
                R.round(ctx, x + 0.5f, dy + 0.5f, w - 1, h - 1, 3.5f, R.withAlpha(0xFFFFFFFF, 0.07f));
            }
            if (a.bar.get()) {
                float bx = right ? x + w - 2f : x;
                R.gradRound(ctx, bx, dy + 2, 2f, h - 4, 1f, ac, Theme.accent2());
            }

            String lab = label(m, a);
            float tx = x + (right ? 6f : 8f);
            if (a.textColor.get().equals("Gradient")) {
                int c1 = 0xFFFFFFFF;
                int c2 = R.withAlpha(0xFFFFFFFF, 0.62f);
                if (a.textGlow.get()) {
                    T.glow(ctx, lab, tx, dy + 3, c1, 1f, glowAmt * 0.7f);
                } else {
                    T.grad(ctx, lab, tx, dy + 3, c1, c2, 1f);
                }
            } else if (a.textGlow.get()) {
                T.glow(ctx, lab, tx, dy + 3, col, 1f, glowAmt);
            } else {
                T.draw(ctx, lab, tx, dy + 3, col, 1f);
            }

            String suf = suffix(m, a);
            if (suf != null) {
                T.draw(ctx, suf, tx + T.width(lab) + 6, dy + 3.5f, R.withAlpha(th.sub(), 0.95f), 0.80f);
            }

            y += (bottom ? -1 : 1) * (h + gap) * e;
            idx++;
        }
        R.alpha = 1f;
    }

    // ------------------------------------------------------------------ watermark

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

        float pad = 10f, h = 22f;
        float markW = minimal ? 0f : 14f;
        float brandW = minimal ? 0f : markW + 6 + T.width("cloude", 1.05f) + 3 + T.width("client", 0.78f);
        float verW = T.width(Ver.tag(), 0.62f) + 12;
        float statsW = stats.isEmpty() ? 0f : T.width(stats, 0.78f);
        float inner = brandW + (brandW > 0 && statsW > 0 ? pad : 0) + statsW + (statsW > 0 ? pad : 0) + verW;
        if (inner <= 0) return;
        float w = pad * 2 + inner;
        float x = right ? sw - 6 - w : 6;
        float y = bottom ? sh - 6 - h : 6;

        float a = (float) (wm.opacity.get() / 100.0);
        R.alpha = a;
        R.shadow(ctx, x, y, w, h, 7, 8, 0.7f);
        R.gradRound(ctx, x, y, w, h, 7, th.top(), th.bot());
        R.round(ctx, x + 0.5f, y + 0.5f, w - 1, h - 1, 6.5f, R.withAlpha(0xFFFFFFFF, th.light() ? 0.5f : 0.10f));
        R.gradRound(ctx, x + 1.5f, y + 1.5f, w - 3, h * 0.55f, 5.5f, R.withAlpha(0xFFFFFFFF, 0.05f), 0x00FFFFFF);

        float tx = x + pad;
        if (!minimal) {
            Logo.mark(ctx, tx, y + 4, 14, th.text(), R.mix(th.bg(), th.top(), 0.5f));
            tx += markW + 6;
            T.draw(ctx, "cloude", tx, y + 6, th.text(), 1.05f);
            tx += T.width("cloude", 1.05f) + 3;
            T.draw(ctx, "client", tx, y + 8, R.withAlpha(th.sub(), 0.95f), 0.78f);
            tx += T.width("client", 0.78f) + pad;
        }
        if (!stats.isEmpty()) {
            T.draw(ctx, stats, tx, y + 7, R.withAlpha(th.sub(), 0.95f), 0.78f);
            tx += statsW + pad;
        }
        float vx = x + w - pad - verW;
        R.round(ctx, vx, y + 5, verW, 12, 6, R.withAlpha(0xFFFFFFFF, 0.06f));
        T.draw(ctx, Ver.tag(), vx + 6, y + 8, R.withAlpha(th.text(), 0.9f), 0.62f);

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 320.0);
        R.circle(ctx, x + w - 4, y + 4, 1.6f + 0.5f * pulse, R.withAlpha(ac, 0.35f + 0.5f * pulse));
        R.alpha = 1f;
    }

    // ------------------------------------------------------------------ notifications

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
            boolean alive = now - n.start < 1700;
            n.anim = Anim.approach(n.anim, alive ? 1f : 0f, 13f, dt);
            if (!alive && n.anim < 0.01f) { NOTIFS.remove(i); continue; }
            String s = n.name + (n.on ? "  on" : "  off");
            float w = T.width(s) + 26, h = 20;
            float x = sw - 6 - w + (1f - n.anim) * (w + 12);
            R.alpha = n.anim;
            R.shadow(ctx, x, y, w, h, 6, 8, 0.6f);
            R.gradRound(ctx, x, y, w, h, 6, th.top(), th.bot());
            R.round(ctx, x + 0.5f, y + 0.5f, w - 1, h - 1, 5.5f, R.withAlpha(0xFFFFFFFF, 0.10f));
            R.circle(ctx, x + 10, y + 10, 2.6f, n.on ? ac : R.withAlpha(th.sub(), 0.6f));
            if (n.on) R.glow(ctx, x + 7, y + 7, 6, 6, 3, ac, 4, 0.6f);
            T.draw(ctx, s, x + 18, y + 6, th.text(), 0.92f);
            float life = Anim.clamp((now - n.start) / 1700f);
            R.round(ctx, x + 3, y + h - 2.5f, (w - 6) * (1f - life), 1.5f, 0.75f, R.withAlpha(ac, 0.75f));
            y += top ? (h + 4) * n.anim : -(h + 4) * n.anim;
        }
        R.alpha = 1f;
    }
}
