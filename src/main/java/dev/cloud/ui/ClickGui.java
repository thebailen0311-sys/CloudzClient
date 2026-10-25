package dev.cloud.ui;

import dev.cloud.Config;
import dev.cloud.module.BoolSetting;
import dev.cloud.module.Category;
import dev.cloud.module.KeySetting;
import dev.cloud.module.ModeSetting;
import dev.cloud.module.Module;
import dev.cloud.module.Modules;
import dev.cloud.module.NumSetting;
import dev.cloud.module.Setting;
import dev.cloud.module.impl.ClickGuiModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Three-pane layout: category rail on the left, a card grid in the middle and a
 * settings drawer that slides in from the right. Cards reflow while the drawer animates.
 */
public class ClickGui extends Screen {

    private interface Action { void run(int button, double mx); }

    private record Hit(float x, float y, float w, float h, Action action) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private static Category cat = Category.COMBAT;
    private static Module selected;
    private static String query = "";
    private static float gScroll, gTarget, dScroll, dTarget;

    private float openT, drawerT, gVel, dVel;
    private boolean closing, searchFocus;
    private Module listening;
    private KeySetting listenKey;
    private NumSetting dragging;
    private float dragX, dragW;
    private ModeSetting dropdown;
    private float ddX, ddY, ddW, ddAnim;
    private long lastNs = -1;
    private Integer savedBlur;
    private int blurApplied = -1;
    private int mouseX, mouseY;

    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> ddHits = new ArrayList<>();
    private final float[] catHover = new float[Category.values().length];

    private float gridX, gridY, gridW, gridH, gMax;
    private float drawerX, drawerY, drawerW, drawerH, dMax;
    private boolean overGrid, overDrawer;

    public ClickGui() { super(Text.literal("Cloud Client")); }

    @Override
    protected void init() {
        if (savedBlur == null) savedBlur = client.options.getMenuBackgroundBlurriness().getValue();
        Ver.checkAsync();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        if (!closing) {
            closing = true;
            listening = null;
            dropdown = null;
            dragging = null;
        }
    }

    @Override
    public void removed() {
        if (savedBlur != null) client.options.getMenuBackgroundBlurriness().setValue(savedBlur);
        Config.save();
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        ClickGuiModule g = Modules.CLICKGUI;
        if (g.blur.get() && openT > 0.2f) {
            int want = g.blurStrength.getInt();
            if (blurApplied != want) {
                client.options.getMenuBackgroundBlurriness().setValue(want);
                blurApplied = want;
            }
            ctx.applyBlur();
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        mouseX = mx;
        mouseY = my;
        long now = System.nanoTime();
        float dt = lastNs < 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;

        ClickGuiModule g = Modules.CLICKGUI;
        Theme th = Theme.current();
        int ac = Theme.accent();
        float sp = Anim.speed();

        openT = Anim.linear(openT, closing ? 0f : 1f, (closing ? 7f : 4.6f) * sp, dt);
        if (closing && openT <= 0f) {
            R.alpha = 1f;
            client.setScreen(null);
            return;
        }
        drawerT = Anim.approach(drawerT, selected != null ? 1f : 0f, 13f * sp, dt);

        float e = closing ? Anim.easeOutCubic(openT) : Anim.open(openT);
        R.alpha = Anim.easeOutCubic(openT);

        R.rect(ctx, 0, 0, width, height, (int) (g.dim.get() / 100.0 * 255) << 24);
        hits.clear();
        ddHits.clear();
        R.begin(ctx);

        float pw = Math.min(720, width - 16), ph = Math.min(452, height - 16);
        float px = (width - pw) / 2f, py = (height - ph) / 2f + (1f - e) * 14f;
        float rad = (float) g.radius.get();
        float glowAmt = (float) (g.glowStrength.get() / 100.0);

        if (g.uiGlow.get()) R.glowR(ctx, px, py, pw, ph, rad, ac, 16, 0.34f * glowAmt * e);
        R.shadow(ctx, px, py, pw, ph, rad, 14, 0.9f);
        R.gradRound(ctx, px, py, pw, ph, rad, th.top(), th.bot());
        R.round(ctx, px + 0.5f, py + 0.5f, pw - 1, ph - 1, rad - 0.5f,
                R.withAlpha(0xFFFFFFFF, th.light() ? 0.55f : 0.10f));
        R.round(ctx, px + 1.5f, py + 1.5f, pw - 3, ph - 3, rad - 1.5f, R.withAlpha(th.bg(), 0.30f));
        R.gradRound(ctx, px + 2, py + 2, pw - 4, ph * 0.40f, rad - 2, R.withAlpha(0xFFFFFFFF, 0.045f), 0x00FFFFFF);

        Logo.silhouette(ctx, px + pw - 210, py + ph - 190, 190, R.withAlpha(ac, 0.028f));

        float railW = 138f;
        float railX = px + 10, railY = py + 10, railH = ph - 20;
        float dW = 202f * Anim.easeOutCubic(drawerT);
        float dx = px + pw - 10 - dW;
        float cx = railX + railW + 10;
        float cw = Math.max(120f, dx - 10 - cx);
        float cy = py + 10, ch = ph - 20;

        rail(ctx, th, ac, railX, railY, railW, railH, dt);
        content(ctx, th, ac, cx, cy, cw, ch, dt);
        drawer(ctx, th, ac, dx, cy, dW, ch, dt);
        dropdownLayer(ctx, th, ac, dt);

        if (g.sweep.get()) {
            float cyc = (System.currentTimeMillis() % 11000L) / 11000f;
            float prog = openT < 1f ? Anim.easeInOut(openT) : (cyc < 0.13f ? cyc / 0.13f : -1f);
            if (prog >= 0f) R.sweep(ctx, px, py, pw, ph, prog);
        }

        R.end(ctx);
        R.alpha = 1f;
    }

    // ------------------------------------------------------------------ rail

    private void rail(DrawContext ctx, Theme th, int ac, float x, float y, float w, float h, float dt) {
        R.round(ctx, x, y, w, h, 10, R.withAlpha(th.light() ? 0xFF000000 : 0xFFFFFFFF, 0.035f));

        long t = System.currentTimeMillis();
        Logo.emblem(ctx, x + 13, y + 13, 21, th.text(), R.mix(th.bg(), th.top(), 0.6f),
                R.withAlpha(ac, 0.30f), t);
        T.draw(ctx, "cloude", x + 40, y + 13, th.text(), 1.12f);
        T.draw(ctx, "client", x + 40, y + 26, th.sub(), 0.70f);

        R.round(ctx, x + 12, y + 44, w - 24, 1, 0.5f, R.withAlpha(0xFFFFFFFF, 0.07f));
        T.draw(ctx, "MENU", x + 14, y + 54, R.withAlpha(th.sub(), 0.75f), 0.62f);

        Category[] cats = Category.values();
        float rowY = y + 66;
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            float ry = rowY + i * 28;
            boolean hov = in(x + 8, ry, w - 16, 24);
            catHover[i] = Anim.approach(catHover[i], hov ? 1f : 0f, 16f, dt);
            boolean sel = c == cat;
            if (sel) {
                R.gradRound(ctx, x + 8, ry, w - 16, 24, 7, R.withAlpha(0xFFFFFFFF, th.light() ? 0.55f : 0.075f),
                        R.withAlpha(0xFFFFFFFF, th.light() ? 0.30f : 0.02f));
                R.round(ctx, x + 8, ry + 5, 2, 14, 1, ac);
            } else if (catHover[i] > 0.01f) {
                R.round(ctx, x + 8, ry, w - 16, 24, 7, R.withAlpha(0xFFFFFFFF, 0.035f * catHover[i]));
            }
            int col = sel ? th.text() : R.mix(th.sub(), th.text(), catHover[i]);
            glyph(ctx, c, x + 22, ry + 12, sel ? ac : R.withAlpha(col, 0.85f));
            T.draw(ctx, c.label, x + 34, ry + 8, col, 0.92f);
            int count = 0, on = 0;
            for (Module m : Modules.ALL) {
                if (m.category != c || m.hidden) continue;
                count++;
                if (m.isEnabled()) on++;
            }
            T.drawRight(ctx, on + "/" + count, x + w - 14, ry + 9, R.withAlpha(th.sub(), 0.8f), 0.66f);
            hits.add(new Hit(x + 8, ry, w - 16, 24, (b, mxx) -> {
                if (cat != c) { cat = c; gScroll = gTarget = 0f; gVel = 0f; dropdown = null; }
            }));
        }

        // footer
        float fy = y + h - 58;
        R.round(ctx, x + 12, fy, w - 24, 1, 0.5f, R.withAlpha(0xFFFFFFFF, 0.07f));
        String themeName = Theme.current().name();
        R.bordered(ctx, x + 12, fy + 8, w - 24, 17, 6, R.withAlpha(0xFFFFFFFF, 0.045f),
                R.withAlpha(0xFFFFFFFF, 0.07f));
        R.circle(ctx, x + 21, fy + 16.5f, 2.4f, ac);
        T.draw(ctx, themeName, x + 28, fy + 12, th.sub(), 0.72f);

        String v = Ver.tag();
        T.draw(ctx, v, x + 14, fy + 32, R.withAlpha(th.sub(), 0.85f), 0.70f);
        if (Ver.updateAvailable()) {
            String up = "update";
            float uw = T.width(up, 0.62f) + 10;
            R.round(ctx, x + w - 14 - uw, fy + 30, uw, 12, 6, R.withAlpha(ac, 0.18f));
            T.draw(ctx, up, x + w - 14 - uw + 5, fy + 33, ac, 0.62f);
        }
    }

    private void glyph(DrawContext ctx, Category c, float cx, float cy, int col) {
        switch (c) {
            case COMBAT -> {
                R.dotRing(ctx, cx, cy, 3.4f, 2.4f, 0.85f, col);
                R.circle(ctx, cx, cy, 1.0f, col);
            }
            case RENDER -> {
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2 + Math.PI / 4;
                    R.dotLine(ctx, cx, cy, cx + (float) Math.cos(a) * 3.8f, cy + (float) Math.sin(a) * 3.8f, 1.5f, 0.85f, col);
                }
                R.circle(ctx, cx, cy, 1.0f, col);
            }
            default -> {
                R.circle(ctx, cx - 2.4f, cy + 0.8f, 1.9f, col);
                R.circle(ctx, cx, cy - 1.0f, 2.5f, col);
                R.circle(ctx, cx + 2.4f, cy + 0.8f, 1.9f, col);
            }
        }
    }

    // ------------------------------------------------------------------ content

    private void content(DrawContext ctx, Theme th, int ac, float x, float y, float w, float h, float dt) {
        List<Module> mods = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.ROOT);
        boolean searching = !q.isEmpty();
        int on = 0;
        for (Module m : Modules.ALL) {
            if (m.hidden) continue;
            boolean match = searching ? m.name.toLowerCase(Locale.ROOT).contains(q) : m.category == cat;
            if (!match) continue;
            mods.add(m);
            if (m.isEnabled()) on++;
        }

        T.draw(ctx, searching ? "Search" : cat.label, x + 2, y + 6, th.text(), 1.45f);
        T.draw(ctx, searching ? mods.size() + " results" : mods.size() + " modules  \u00B7  " + on + " active",
                x + 2, y + 24, th.sub(), 0.72f);

        // search field
        float sw = 132, sh = 18;
        float sx = x + w - sw;
        boolean sf = searchFocus;
        String caret = sf && System.currentTimeMillis() / 500L % 2L == 0L ? "|" : "";
        String shown = query;
        while (T.width(shown + caret, 0.78f) > sw - 22 && shown.length() > 1) shown = shown.substring(1);
        R.gradRound(ctx, sx, y + 6, sw, sh, 6, R.withAlpha(0xFFFFFFFF, sf ? 0.10f : 0.05f),
                R.withAlpha(0xFFFFFFFF, sf ? 0.05f : 0.02f));
        R.round(ctx, sx + 0.5f, y + 6.5f, sw - 1, sh - 1, 5.5f,
                R.withAlpha(0xFFFFFFFF, sf ? 0.22f : 0.08f));
        R.circle(ctx, sx + 10, y + 15, 2.6f, R.withAlpha(th.sub(), 0.9f));
        R.line(ctx, sx + 12, y + 17, sx + 14.5f, y + 19.5f, 1.1f, R.withAlpha(th.sub(), 0.9f));
        boolean empty = query.isEmpty() && !sf;
        T.draw(ctx, empty ? "search" : shown + caret, sx + 20, y + 11, empty ? R.withAlpha(th.sub(), 0.8f) : th.text(), 0.78f);
        hits.add(new Hit(sx, y + 6, sw, sh, (b, mxx) -> searchFocus = true));

        float gy = y + 34;
        float gh = h - 34;
        gridX = x;
        gridY = gy;
        gridW = w;
        gridH = gh;
        overGrid = in(gridX, gridY, gridW, gridH);

        float smooth = (float) Modules.CLICKGUI.scrollSmooth.get() / 100f;
        gTarget += gVel * dt;
        gVel *= (float) Math.exp(-8f * dt);
        gTarget = Math.max(0f, Math.min(gMax, gTarget));
        gScroll = Anim.approach(gScroll, gTarget, 30f - 22f * smooth, dt);

        R.scissor(ctx, x, gy, x + w, gy + gh);
        float gap = 8f;
        int cols = w > 300 ? 2 : 1;
        float cwid = (w - gap * (cols - 1)) / cols;
        float chg = 46f;
        float yy = gy - gScroll;
        int i = 0;
        for (Module m : mods) {
            float cxx = x + (i % cols) * (cwid + gap);
            float cyy = yy + (i / cols) * (chg + gap);
            float ct = openT >= 1f ? 1f : Anim.clamp((openT - 0.15f - i * 0.03f) / 0.4f);
            float ce = Anim.easeOutCubic(ct);
            float oldA = R.alpha;
            R.alpha = oldA * ce;
            if (cyy + chg > gy - 4 && cyy < gy + gh + 4) {
                card(ctx, th, ac, m, cxx + (1f - ce) * 10f, cyy, cwid, chg, dt);
            }
            R.alpha = oldA;
            i++;
        }
        ctx.disableScissor();

        int rows = (mods.size() + cols - 1) / cols;
        gMax = Math.max(0f, rows * (chg + gap) - gap - gh + 6);

        if (mods.isEmpty()) {
            T.draw(ctx, "nothing here", x + 2, gy + 16, th.sub(), 0.95f);
        }

        R.vgrad(ctx, x, gy, w, 10, th.bg(), th.bg() & 0xFFFFFF);
        R.vgrad(ctx, x, gy + gh - 10, w, 10, th.bg() & 0xFFFFFF, th.bg());

        if (gMax > 0f) {
            float barH = Math.max(18f, gh * gh / (gMax + gh));
            float barY = gy + (gh - barH) * (gScroll / gMax);
            R.round(ctx, x + w - 2, barY, 2, barH, 1, R.withAlpha(th.text(), 0.22f));
        }
    }

    private void card(DrawContext ctx, Theme th, int ac, Module m, float x, float y, float w, float h, float dt) {
        boolean hov = overGrid && in(x, y, w, h);
        m.hoverAnim = Anim.approach(m.hoverAnim, hov ? 1f : 0f, 16f, dt);
        float t = Anim.clamp(m.tog.update(m.isEnabled() ? 1f : 0f, dt));
        boolean sel = selected == m;
        float yy = y - 1.5f * m.hoverAnim;

        int top = R.mix(th.cardTop(), 0xFFFFFFFF, 0.045f * m.hoverAnim);
        int bot = R.mix(th.cardBot(), 0xFFFFFFFF, 0.015f * m.hoverAnim);
        if (sel) { top = R.mix(top, ac, 0.09f); bot = R.mix(bot, ac, 0.03f); }
        R.gradRound(ctx, x, yy, w, h, 9, top, bot);
        R.round(ctx, x + 0.5f, yy + 0.5f, w - 1, h - 1, 8.5f,
                R.withAlpha(0xFFFFFFFF, (th.light() ? 0.45f : 0.06f) + 0.09f * m.hoverAnim + (sel ? 0.10f : 0f)));
        if (t > 0.01f) {
            R.round(ctx, x + 0.5f, yy + 8, 2.2f, (h - 16) * t, 1.1f, ac);
        }

        float textW = w - 52;
        T.draw(ctx, T.fit(m.name, textW, 1f), x + 12, yy + 9, th.text(), 1f);
        String sub = m.suffix() != null ? m.suffix() : m.description;
        T.draw(ctx, T.fit(sub, textW, 0.72f), x + 12, yy + 24, R.withAlpha(th.sub(), 0.9f), 0.72f);

        // switch
        float swW = 24, swH = 12;
        float swX = x + w - 12 - swW, swY = yy + h / 2f - swH / 2f;
        R.round(ctx, swX, swY, swW, swH, 6, R.mix(th.track(), ac, t));
        if (t > 0.02f && Modules.CLICKGUI.uiGlow.get()) R.glow(ctx, swX, swY, swW, swH, 6, ac, 4, 0.5f * t);
        R.circle(ctx, swX + 6 + 12 * Anim.clamp(m.tog.x), swY + 6, 4f,
                R.mix(th.light() ? 0xFFFFFFFF : 0xFFDCDCE2, Theme.onAccent(), t));

        if (overGrid && in(x, y, w, h)) {
            hits.add(new Hit(x, y, w, h, (b, mxx) -> {
                if (b == 1) { m.toggle(); Config.save(); }
                else { selected = m; dScroll = dTarget = 0f; }
            }));
            hits.add(new Hit(swX - 3, swY - 4, swW + 6, swH + 8, (b, mxx) -> {
                m.toggle();
                Config.save();
            }));
        }
    }

    // ------------------------------------------------------------------ drawer

    private void drawer(DrawContext ctx, Theme th, int ac, float x, float y, float w, float h, float dt) {
        if (w < 4f) return;
        float a = Anim.easeOutCubic(drawerT);
        float oldA = R.alpha;
        R.alpha = oldA * a;

        R.round(ctx, x, y, w, h, 10, R.withAlpha(th.light() ? 0xFF000000 : 0xFFFFFFFF, 0.035f));
        R.round(ctx, x + 0.5f, y + 0.5f, w - 1, h - 1, 9.5f, R.withAlpha(0xFFFFFFFF, 0.05f));

        Module m = selected;
        if (m == null) { R.alpha = oldA; return; }

        drawerX = x;
        drawerY = y;
        drawerW = w;
        drawerH = h;
        overDrawer = in(x, y, w, h);

        R.scissor(ctx, x, y, x + w, y + h);
        Logo.silhouette(ctx, x + w - 70, y + 10, 70, R.withAlpha(ac, 0.05f));

        T.draw(ctx, T.fit(m.name, w - 60, 1.15f), x + 14, y + 12, th.text(), 1.15f);
        T.draw(ctx, m.category.label, x + 14, y + 28, R.withAlpha(th.sub(), 0.85f), 0.68f);

        // close
        float cbx = x + w - 26, cby = y + 12;
        boolean chov = in(cbx, cby, 16, 16);
        R.round(ctx, cbx, cby, 16, 16, 5, R.withAlpha(0xFFFFFFFF, chov ? 0.12f : 0.05f));
        R.line(ctx, cbx + 5.5f, cby + 5.5f, cbx + 10.5f, cby + 10.5f, 1.2f, R.withAlpha(th.text(), 0.8f));
        R.line(ctx, cbx + 10.5f, cby + 5.5f, cbx + 5.5f, cby + 10.5f, 1.2f, R.withAlpha(th.text(), 0.8f));
        hits.add(new Hit(cbx, cby, 16, 16, (b, mxx) -> selected = null));

        R.round(ctx, x + 12, y + 44, w - 24, 1, 0.5f, R.withAlpha(0xFFFFFFFF, 0.07f));

        float sy0 = y + 50, sh = h - 50 - 44;
        float smooth = (float) Modules.CLICKGUI.scrollSmooth.get() / 100f;
        dTarget += dVel * dt;
        dVel *= (float) Math.exp(-8f * dt);
        dTarget = Math.max(0f, Math.min(dMax, dTarget));
        dScroll = Anim.approach(dScroll, dTarget, 30f - 22f * smooth, dt);

        R.scissor(ctx, x, sy0, x + w, sy0 + sh);
        float sy = sy0 + 2 - dScroll;
        float rowW = w - 30;
        float total = 0f;
        for (Setting st : m.settings) {
            st.vis = Anim.approach(st.vis, st.visible() ? 1f : 0f, 14f * Anim.speed(), dt);
            if (st.vis < 0.01f) continue;
            float rh = rowH(st);
            float old = R.alpha;
            R.alpha = old * st.vis;
            boolean clickable = st.vis > 0.9f && sy + rh > sy0 && sy < sy0 + sh;
            setting(ctx, th, ac, st, x + 15, sy, rowW, rh, dt, clickable);
            R.alpha = old;
            sy += rh * st.vis;
            total += rh * st.vis;
        }
        ctx.disableScissor();
        dMax = Math.max(0f, total - sh + 8);

        // footer: bind + mode
        float fy = y + h - 40;
        R.round(ctx, x + 12, fy, w - 24, 1, 0.5f, R.withAlpha(0xFFFFFFFF, 0.07f));
        boolean listen = listening == m;
        String bind = listen ? "press a key" : (m.key < 0 ? "unbound" : m.bindName());
        float bw = (w - 24 - 6) / 2f;
        float pulse = listen ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0) : 0f;
        if (listen) R.glow(ctx, x + 12, fy + 8, bw, 20, 6, ac, 4, 0.4f + 0.4f * pulse);
        R.bordered(ctx, x + 12, fy + 8, bw, 20, 6, R.withAlpha(0xFFFFFFFF, 0.05f),
                listen ? R.mix(0x22FFFFFF, ac, pulse) : R.withAlpha(0xFFFFFFFF, 0.08f));
        T.draw(ctx, bind, x + 19, fy + 14, listen || m.key >= 0 ? th.text() : th.sub(), 0.75f);
        hits.add(new Hit(x + 12, fy + 8, bw, 20, (b, mxx) -> {
            if (b == 1) { m.key = -1; m.mouse = false; listening = null; Config.save(); }
            else listening = listening == m ? null : m;
        }));

        String mode = m.hold ? "hold" : "toggle";
        R.bordered(ctx, x + 12 + bw + 6, fy + 8, bw, 20, 6, R.withAlpha(0xFFFFFFFF, 0.05f),
                R.withAlpha(0xFFFFFFFF, 0.08f));
        T.draw(ctx, mode, x + 19 + bw + 6, fy + 14, m.hold ? ac : th.sub(), 0.75f);
        hits.add(new Hit(x + 12 + bw + 6, fy + 8, bw, 20, (b, mxx) -> {
            m.hold = !m.hold;
            if (m.hold) m.setEnabled(false);
            Config.save();
        }));

        R.alpha = oldA;
    }

    private static float rowH(Setting st) {
        if (st instanceof NumSetting) return 30f;
        if (st instanceof ModeSetting) return 24f;
        return 22f;
    }

    private void setting(DrawContext ctx, Theme th, int ac, Setting st, float x, float y, float w, float h, float dt, boolean clickable) {
        boolean hov = clickable && in(x - 4, y, w + 8, h);
        st.hover = Anim.approach(st.hover, hov ? 1f : 0f, 16f, dt);
        if (st.hover > 0.01f) R.round(ctx, x - 5, y + 1, w + 10, h - 2, 5, R.withAlpha(0xFFFFFFFF, 0.04f * st.hover));
        int nameCol = R.mix(th.sub(), th.text(), 0.5f + 0.5f * st.hover);

        if (st instanceof BoolSetting b) {
            float t = Anim.clamp(b.anim.update(b.get() ? 1f : 0f, dt));
            T.draw(ctx, st.name, x, y + h / 2f - 3.5f, nameCol, 0.78f);
            float sw = 20, shh = 10, sx = x + w - sw, sy = y + h / 2f - shh / 2f;
            R.round(ctx, sx, sy, sw, shh, 5, R.mix(th.track(), ac, t));
            R.circle(ctx, sx + 5 + 10 * Anim.clamp(b.anim.x), sy + 5, 3.2f,
                    R.mix(th.light() ? 0xFFFFFFFF : 0xFFDCDCE2, Theme.onAccent(), t));
            if (clickable) hits.add(new Hit(x - 4, y, w + 8, h, (btn, mx) -> { b.set(!b.get()); Config.save(); }));
        } else if (st instanceof NumSetting n) {
            if (dragging == n) n.setFrac((mouseX - dragX) / dragW);
            float f = (float) n.frac();
            n.anim = n.anim < 0 ? f : Anim.approach(n.anim, f, 22f, dt);
            T.draw(ctx, st.name, x, y + 5, nameCol, 0.78f);
            T.drawRight(ctx, n.display(), x + w, y + 5, dragging == n ? ac : R.withAlpha(th.sub(), 0.95f), 0.78f);
            float ty = y + 20;
            R.round(ctx, x, ty, w, 3, 1.5f, th.track());
            R.gradRound(ctx, x, ty, Math.max(3, w * n.anim), 3, 1.5f, ac, Theme.accent2());
            float kx = x + w * n.anim;
            float kr = 3.4f + st.hover * 0.8f + (dragging == n ? 0.6f : 0f);
            if (Modules.CLICKGUI.uiGlow.get()) R.glow(ctx, kx - kr, ty + 1.5f - kr, kr * 2, kr * 2, kr, ac, 3, 0.55f);
            R.circle(ctx, kx, ty + 1.5f, kr, ac);
            if (clickable) hits.add(new Hit(x - 4, y, w + 8, h, (btn, mx) -> {
                dragging = n;
                dragX = x;
                dragW = w;
                n.setFrac((mx - x) / w);
            }));
        } else if (st instanceof ModeSetting md) {
            T.draw(ctx, st.name, x, y + h / 2f - 3.5f, nameCol, 0.78f);
            String v = md.get();
            float pw = T.width(v, 0.74f) + 20;
            float pxx = x + w - pw, pyy = y + 3;
            boolean open = dropdown == md;
            R.bordered(ctx, pxx, pyy, pw, h - 6, 5, R.withAlpha(0xFFFFFFFF, open ? 0.10f : 0.05f),
                    open ? R.withAlpha(ac, 0.55f) : R.withAlpha(0xFFFFFFFF, 0.07f));
            T.draw(ctx, v, pxx + 7, pyy + 4, th.text(), 0.74f);
            T.draw(ctx, "v", pxx + pw - 10, pyy + 3.5f, R.withAlpha(th.sub(), 0.9f), 0.66f);
            if (clickable) hits.add(new Hit(x - 4, y, w + 8, h, (btn, mx) -> {
                if (btn == 1) { md.setIndex((md.index() + 1) % md.modes.length); Config.save(); return; }
                if (dropdown == md) { dropdown = null; return; }
                dropdown = md;
                ddAnim = 0f;
                ddW = Math.max(pw, 90);
                for (String s : md.modes) ddW = Math.max(ddW, T.width(s, 0.74f) + 22);
                ddX = x + w - ddW;
                ddY = pyy + h - 4;
            }));
        } else if (st instanceof KeySetting k) {
            T.draw(ctx, st.name, x, y + h / 2f - 3.5f, nameCol, 0.78f);
            boolean l = listenKey == k;
            String v = l ? "press a key" : (k.key < 0 ? "none" : Module.keyName(k.key, k.mouse));
            float pw = T.width(v, 0.74f) + 14;
            float pxx = x + w - pw, pyy = y + 3;
            float pulse = l ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0) : 0f;
            if (l) R.glow(ctx, pxx, pyy, pw, h - 6, 5, ac, 4, 0.4f + 0.4f * pulse);
            R.bordered(ctx, pxx, pyy, pw, h - 6, 5, R.withAlpha(0xFFFFFFFF, 0.05f),
                    l ? R.mix(0x22FFFFFF, ac, pulse) : R.withAlpha(0xFFFFFFFF, 0.07f));
            T.draw(ctx, v, pxx + 7, pyy + 4, l || k.key >= 0 ? th.text() : th.sub(), 0.74f);
            if (clickable) hits.add(new Hit(x - 4, y, w + 8, h, (btn, mx) -> {
                if (btn == 1) { k.key = -1; k.mouse = false; listenKey = null; Config.save(); return; }
                listenKey = l ? null : k;
                listening = null;
            }));
        }
    }

    private void dropdownLayer(DrawContext ctx, Theme th, int ac, float dt) {
        if (dropdown == null) return;
        ddAnim = Anim.linear(ddAnim, 1f, 7f * Anim.speed(), dt);
        float e = Anim.open(ddAnim);
        float itemH = 16f;
        float fullH = dropdown.modes.length * itemH + 6;
        float hgt = fullH * Anim.clamp(e);
        float oldA = R.alpha;
        R.alpha = oldA * Anim.clamp(ddAnim * 1.6f);
        R.shadow(ctx, ddX, ddY, ddW, hgt, 7, 10, 1f);
        R.gradRound(ctx, ddX, ddY, ddW, Math.max(hgt, 8), 7, th.top(), th.bot());
        R.round(ctx, ddX + 0.5f, ddY + 0.5f, ddW - 1, Math.max(hgt, 8) - 1, 6.5f, R.withAlpha(0xFFFFFFFF, 0.10f));
        R.scissor(ctx, ddX, ddY, ddX + ddW, ddY + hgt);
        for (int i = 0; i < dropdown.modes.length; i++) {
            final int idx = i;
            float iy = ddY + 3 + i * itemH;
            boolean sel = i == dropdown.index();
            boolean hov = in(ddX + 3, iy, ddW - 6, itemH);
            if (hov) R.round(ctx, ddX + 3, iy, ddW - 6, itemH, 4, R.withAlpha(0xFFFFFFFF, 0.07f));
            if (sel) R.circle(ctx, ddX + 9, iy + itemH / 2f, 1.7f, ac);
            T.draw(ctx, dropdown.modes[i], ddX + 15, iy + 4, sel ? th.text() : th.sub(), 0.74f);
            ddHits.add(new Hit(ddX + 3, iy, ddW - 6, itemH, (b, mx) -> {
                dropdown.setIndex(idx);
                dropdown = null;
                Config.save();
            }));
        }
        ctx.disableScissor();
        R.alpha = oldA;
    }

    private boolean in(float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        double mx = click.x(), my = click.y();
        int b = click.button();
        if (listenKey != null) {
            if (b != 0) { listenKey.key = b; listenKey.mouse = true; Config.save(); }
            listenKey = null;
            return true;
        }
        if (listening != null) {
            if (b != 0) { listening.key = b; listening.mouse = true; Config.save(); }
            listening = null;
            return true;
        }
        searchFocus = false;
        if (dropdown != null) {
            for (int i = ddHits.size() - 1; i >= 0; i--) {
                Hit hit = ddHits.get(i);
                if (hit.in(mx, my)) { hit.action.run(b, mx); return true; }
            }
            dropdown = null;
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.in(mx, my)) { hit.action.run(b, mx); return true; }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) { dragging = null; Config.save(); }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (closing) return true;
        if (dropdown != null) { dropdown = null; return true; }
        boolean inDrawer = selected != null && drawerW > 4f
                && mx >= drawerX && mx < drawerX + drawerW && my >= drawerY && my < drawerY + drawerH;
        if (inDrawer) {
            dVel -= (float) v * 240f;
            dTarget -= (float) v * 16f;
        } else {
            gVel -= (float) v * 240f;
            gTarget -= (float) v * 16f;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (listenKey != null) {
            boolean clear = key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE;
            listenKey.key = clear ? -1 : key;
            listenKey.mouse = false;
            listenKey = null;
            Config.save();
            return true;
        }
        if (listening != null) {
            listening.key = (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) ? -1 : key;
            listening.mouse = false;
            listening = null;
            Config.save();
            return true;
        }
        if (searchFocus) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!query.isEmpty()) { query = ""; gScroll = gTarget = 0f; }
                else searchFocus = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { searchFocus = false; return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                gScroll = gTarget = 0f;
                return true;
            }
            String add = null;
            if (key == GLFW.GLFW_KEY_SPACE) add = " ";
            else if (key == GLFW.GLFW_KEY_MINUS) add = "-";
            else if (key == GLFW.GLFW_KEY_PERIOD) add = ".";
            else if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) add = String.valueOf((char) ('a' + (key - GLFW.GLFW_KEY_A)));
            else if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) add = String.valueOf((char) ('0' + (key - GLFW.GLFW_KEY_0)));
            if (add != null && query.length() < 24) query += add;
            gScroll = gTarget = 0f;
            return true;
        }
        if (dropdown != null && key == GLFW.GLFW_KEY_ESCAPE) { dropdown = null; return true; }
        Module g = Modules.CLICKGUI;
        if (key == GLFW.GLFW_KEY_ESCAPE || (!g.mouse && g.key == key)) { close(); return true; }
        return super.keyPressed(input);
    }
}
