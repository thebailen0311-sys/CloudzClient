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
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClickGui extends Screen {
    private interface Action { void run(int button, double mx); }

    private record Hit(float x, float y, float w, float h, Action action) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private static Category cat = Category.COMBAT;
    private static float scroll, target;
    private static String query = "";

    private float vel, openT, catY = -1f, ddAnim, maxScroll;
    private boolean closing;
    private Module listening;
    private KeySetting listenKey;
    private NumSetting dragging;
    private float dragX, dragW;
    private ModeSetting dropdown;
    private float ddX, ddY, ddW;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> ddHits = new ArrayList<>();
    private final float[] catHover = new float[Category.values().length];
    private static final List<Module> MOD_SCRATCH = new ArrayList<>();
    private long lastNs = -1;
    private Integer savedBlur;
    private int blurApplied = -1;
    private boolean searchFocus;
    private int mouseX, mouseY;

    public ClickGui() { super(Text.literal("Cloud Client")); }

    @Override
    protected void init() {
        if (savedBlur == null) savedBlur = client.options.getMenuBackgroundBlurriness().getValue();
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

    // ---------------------------------------------------------------- render

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

        openT = Anim.linear(openT, closing ? 0f : 1f, (closing ? 6.5f : 4.2f) * sp, dt);
        if (closing && openT <= 0f) {
            R.alpha = 1f;
            client.setScreen(null);
            return;
        }
        float e = closing ? Anim.easeOutCubic(openT) : Anim.open(openT);
        R.alpha = Anim.easeOutCubic(openT);

        R.beginFrame();
        R.rect(ctx, 0, 0, width, height, (int) (g.dim.get() / 100.0 * 255) << 24);

        hits.clear();
        ddHits.clear();
        int pw = Math.min(480, width - 20), ph = Math.min(310, height - 20);
        int px = (width - pw) / 2, py = (height - ph) / 2;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        float s = 0.92f + 0.08f * e;
        m.translate(width / 2f, height / 2f + (1f - e) * 10f);
        m.scale(s, s);
        m.translate(-width / 2f, -height / 2f);
        R.begin(ctx);

        if (g.uiGlow.get()) R.glow(ctx, px, py, pw, ph, 26, ac, 10, 0.10f * e);

        // panel
        if (g.uiGlow.get()) R.glow(ctx, px, py, pw, ph, 14, ac, 12, 0.35f);
        if (g.frost.get()) {
            R.glow(ctx, px, py, pw, ph, 14, 0xFFFFFFFF, 4, 0.14f);
            R.bordered(ctx, px, py, pw, ph, 14, th.bg(), th.line());
            R.vgrad(ctx, px + 2, py + 2, pw - 4, ph * 0.5f, th.light() ? 0x14FFFFFF : 0x12FFFFFF, 0x00FFFFFF);
            R.rect(ctx, px + 8, py + 1.5f, pw - 16, 1, R.withAlpha(0xFFFFFFFF, 0.15f));
            R.rect(ctx, px + 12, py + ph - 2.5f, pw - 24, 1, R.withAlpha(0xFF000000, 0.30f));
            R.outline(ctx, px + 0.5f, py + 0.5f, pw - 1, ph - 1, R.withAlpha(0xFFFFFFFF, 0.16f));
        } else {
            R.bordered(ctx, px, py, pw, ph, 14, th.bg(), th.line());
            R.vgrad(ctx, px + 2, py + 14, pw - 4, ph * 0.45f, th.light() ? 0x10FFFFFF : 0x0CFFFFFF, 0x00FFFFFF);
        }

        Logo.silhouette(ctx, px + pw - 158, py + ph - 152, 118, R.withAlpha(ac, 0.05f));

        sidebar(ctx, th, ac, px + 8, py + 8, 120, ph - 16, dt);
        content(ctx, th, ac, px + 138, py, pw - 146, ph, dt);
        dropdownLayer(ctx, th, ac, dt);

        if (g.sweep.get()) {
            float cyc = (System.currentTimeMillis() % 9000L) / 9000f;
            float sprog = openT < 1f ? Anim.easeInOut(openT) : (cyc < 0.16f ? cyc / 0.16f : -1f);
            if (sprog >= 0f) R.sweep(ctx, px, py, pw, ph, sprog);
        }

        R.end(ctx);
        m.popMatrix();
        R.alpha = 1f;
    }

    private void sidebar(DrawContext ctx, Theme th, int ac, int x, int y, int w, int h, float dt) {
        R.round(ctx, x, y, w, h, 10, th.side());
        Logo.emblem(ctx, x + w - 32, y + 9, 14, ac, th.bg(), R.withAlpha(ac, 0.55f), System.currentTimeMillis());
        T.draw(ctx, "cloude", x + 12, y + 11, ac, 1.45f);
        T.draw(ctx, "client", x + 12 + T.width("cloude", 1.45f) + 3, y + 15, th.sub());
        T.draw(ctx, "YOUR GAME. YOUR EXPERIENCE.", x + 12, y + 27, th.sub(), 0.6f);
        T.draw(ctx, "MODULES", x + 12, y + 46, th.sub(), 0.65f);

        Category[] cats = Category.values();
        float rowY0 = y + 56;
        float targetY = rowY0 + cat.ordinal() * 24;
        catY = catY < 0 ? targetY : Anim.approach(catY, targetY, 16f * Anim.speed(), dt);
        if (Modules.CLICKGUI.uiGlow.get()) R.glow(ctx, x + 6, catY, w - 12, 20, 6, ac, 4, 0.22f);
        R.round(ctx, x + 6, catY, w - 12, 20, 6, th.cardHover());
        R.rect(ctx, x + 10, catY + 1, w - 20, 1, R.withAlpha(0xFFFFFFFF, 0.08f));
        R.round(ctx, x + 6, catY + 5, 2, 10, 1, ac);

        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            float ry = rowY0 + i * 24;
            boolean hov = in(x + 6, ry, w - 12, 20);
            catHover[i] = Anim.approach(catHover[i], hov ? 1f : 0f, 14f, dt);
            boolean sel = c == cat;
            int col = sel ? th.text() : R.mix(th.sub(), th.text(), catHover[i]);
            glyph(ctx, c, x + 15, ry + 10, sel ? ac : R.withAlpha(col, 0.85f));
            T.draw(ctx, c.label, x + 25 + catHover[i] * 2, ry + 6, col);
            int count = 0;
            for (Module mod : Modules.ALL) if (mod.category == c) count++;
            T.drawRight(ctx, String.valueOf(count), x + w - 14, ry + 7, th.sub(), 0.7f);
            hits.add(new Hit(x + 6, ry, w - 12, 20, (b, mx) -> {
                if (cat != c) { cat = c; scroll = target = 0f; vel = 0f; dropdown = null; }
            }));
        }

        T.draw(ctx, "click: toggle", x + 12, y + h - 34, th.sub(), 0.6f);
        T.draw(ctx, "right click: settings", x + 12, y + h - 26, th.sub(), 0.6f);
        T.draw(ctx, "esc: close", x + 12, y + h - 18, th.sub(), 0.6f);
    }

    /** Tiny custom vector glyph per category, drawn from dots. */
    private void glyph(DrawContext ctx, Category c, float cx, float cy, int col) {
        switch (c) {
            case COMBAT -> {
                R.dotRing(ctx, cx, cy, 3.6f, 2.6f, 0.9f, col);
                R.circle(ctx, cx, cy, 1.1f, col);
            }
            case RENDER -> {
                for (int i = 0; i < 4; i++) {
                    double a = i * Math.PI / 2;
                    R.dotLine(ctx, cx, cy, cx + (float) Math.cos(a) * 4.2f, cy + (float) Math.sin(a) * 4.2f, 1.6f, 0.9f, col);
                }
            }
            default -> {
                R.circle(ctx, cx - 2.6f, cy + 0.9f, 2f, col);
                R.circle(ctx, cx, cy - 1.1f, 2.7f, col);
                R.circle(ctx, cx + 2.6f, cy + 0.9f, 2f, col);
            }
        }
    }

    private void content(DrawContext ctx, Theme th, int ac, int x, int py, int w, int ph, float dt) {
        List<Module> mods = MOD_SCRATCH;
        mods.clear();
        int enabled = 0;
        String q = query.trim().toLowerCase(Locale.ROOT);
        boolean searching = !q.isEmpty();
        for (Module mod : Modules.ALL) {
            boolean match = searching ? mod.name.toLowerCase(Locale.ROOT).contains(q) : mod.category == cat;
            if (!match) continue;
            mods.add(mod);
            if (mod.isEnabled()) enabled++;
        }

        T.draw(ctx, searching ? "search" : cat.label, x + 2, py + 13, th.text(), 1.5f);
        T.draw(ctx, searching ? "\"" + q + "\"  \u00B7  " + mods.size() + " results"
                        : mods.size() + " modules  \u00B7  " + enabled + " enabled",
                x + 2, py + 30, th.sub(), 0.75f);
        String tn = Theme.current().name();
        float tw = T.width(tn, 0.7f) + 14;
        R.bordered(ctx, x + w - tw, py + 14, tw, 14, 7, th.card(), th.line());
        T.draw(ctx, tn, x + w - tw + 7, py + 18, th.sub(), 0.7f);

        // search field
        float searchW = 104;
        float sx0 = x + w - tw - 8 - searchW;
        boolean sf = searchFocus;
        String caret = sf && System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "";
        String shown = query;
        while (T.width(shown + caret, 0.75f) > searchW - 12 && shown.length() > 1) shown = shown.substring(1);
        boolean empty = query.isEmpty() && !sf;
        R.bordered(ctx, sx0, py + 14, searchW, 14, 7, sf ? th.cardHover() : th.card(), sf ? R.withAlpha(ac, 0.7f) : th.line());
        T.draw(ctx, empty ? "search" : shown + caret, sx0 + 7, py + 18, empty ? th.sub() : th.text(), 0.75f);
        if (sf) R.round(ctx, sx0 + 6, py + 25.5f, searchW - 12, 1, 0.5f, R.withAlpha(ac, 0.9f));
        hits.add(new Hit(sx0, py + 14, searchW, 14, (b, mx) -> searchFocus = true));

        R.round(ctx, x + 2, py + 43.5f, w - 4, 1, 0.5f, R.withAlpha(th.text(), 0.10f));
        R.round(ctx, x + 2, py + 43.5f, Math.min(w - 4, 34), 1, 0.5f, R.withAlpha(ac, 0.85f));

        int cy = py + 46, ch = ph - 46 - 8;
        float sp = Anim.speed();

        // momentum scrolling with a soft, slightly delayed follow
        target += vel * dt;
        vel *= (float) Math.exp(-7.5f * dt);
        if (target < 0f) { target = 0f; vel = 0f; }
        if (target > maxScroll) { target = maxScroll; vel = 0f; }
        float smooth = (float) Modules.CLICKGUI.scrollSmooth.get() / 100f;
        scroll = Anim.approach(scroll, target, 30f - 22f * smooth, dt);

        int cw = w - 6;
        R.scissor(ctx, x, cy, x + w, cy + ch);
        float y = cy + 2 - scroll;
        float total = 0f;
        int ci = 0;
        for (Module mod : mods) {
            float ct = openT >= 1f ? 1f : Anim.clamp((openT - 0.2f - ci * 0.045f) / 0.45f);
            float ce = Anim.easeOutCubic(ct);
            float oldA = R.alpha;
            R.alpha = oldA * Math.max(0f, ce);
            float hgt = card(ctx, th, ac, mod, x + (1f - ce) * 16f, y, cw, cy, ch, dt, sp);
            R.alpha = oldA;
            y += hgt + 6;
            total += hgt + 6;
            ci++;
        }
        ctx.disableScissor();
        maxScroll = Math.max(0f, total - ch + 4);

        if (mods.isEmpty()) {
            T.draw(ctx, "no modules found", x + 2, cy + 18, th.sub(), 0.95f);
            T.draw(ctx, "try a different search", x + 2, cy + 32, R.withAlpha(th.sub(), 0.7f), 0.75f);
        }

        // edge fades
        R.vgrad(ctx, x, cy, w, 8, th.bg(), th.bg() & 0xFFFFFF);
        R.vgrad(ctx, x, cy + ch - 8, w, 8, th.bg() & 0xFFFFFF, th.bg());

        if (maxScroll > 0f) {
            float barH = Math.max(20f, ch * ch / (total + 4));
            float barY = cy + (ch - barH) * (scroll / maxScroll);
            R.round(ctx, x + w - 2, barY, 2, barH, 1, R.withAlpha(th.text(), 0.25f));
        }
    }

    private float settingsHeight(Module mod) {
        float hgt = 0f;
        for (Setting st : mod.settings) hgt += rowH(st) * st.vis;
        return hgt + 8f;
    }

    private static float rowH(Setting st) {
        if (st instanceof NumSetting) return 26f;
        if (st instanceof ModeSetting) return 20f;
        return 18f;
    }

    private boolean in(float x, float y, float w, float h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private float card(DrawContext ctx, Theme th, int ac, Module mod, float x, float y, float w,
                       int clipY, int clipH, float dt, float sp) {
        boolean isGui = mod == Modules.CLICKGUI;
        for (Setting st : mod.settings) st.vis = Anim.approach(st.vis, st.visible() ? 1f : 0f, 14f * sp, dt);
        mod.expandAnim = Anim.linear(mod.expandAnim, mod.expanded ? 1f : 0f, 4.5f * sp, dt);
        float ex = Anim.easeInOut(mod.expandAnim);
        float t = Anim.clamp(mod.tog.update(mod.isEnabled() ? 1f : 0f, dt));
        float tRaw = mod.tog.x;
        float head = 36f;
        float sh = settingsHeight(mod) * ex;
        float total = head + sh;

        boolean visible = y + total > clipY && y < clipY + clipH;
        boolean inClip = mouseY >= clipY && mouseY < clipY + clipH;
        boolean hov = inClip && in(x, y, w, head);
        mod.hoverAnim = Anim.approach(mod.hoverAnim, hov ? 1f : 0f, 14f, dt);
        if (!visible) return total;

        int bg = R.mix(th.card(), th.cardHover(), mod.hoverAnim);
        if (Modules.CLICKGUI.uiGlow.get() && t > 0.01f) R.glow(ctx, x, y, w, total, 9, ac, 6, 0.3f * t);
        R.bordered(ctx, x, y, w, total, 9, bg, R.mix(th.line(), R.withAlpha(ac, 0.45f), t));
        float hl = Math.max(t, mod.hoverAnim);
        if (hl > 0.02f) {
            R.outline(ctx, x + 0.5f, y + 0.5f, w - 1, total - 1, R.withAlpha(R.mix(th.line(), 0xFFFFFFFF, hl * 0.8f), 0.30f));
            R.rect(ctx, x + 6, y + 1, w - 12, 1, R.withAlpha(0xFFFFFFFF, 0.05f + 0.07f * hl));
        }
        if (t > 0.02f) R.round(ctx, x + 3, y + head / 2 - 8 * t, 2, 16 * t, 1, ac);

        T.draw(ctx, mod.name, x + 12 + mod.hoverAnim * 1.5f, y + 9, th.text());
        T.draw(ctx, mod.description, x + 12, y + 21, th.sub(), 0.72f);

        // header hit first so pills registered later take priority
        if (inClip) hits.add(new Hit(x, y, w, head, (b, mx) -> {
            if (b == 1 || isGui) mod.expanded = !mod.expanded;
            else if (b == 0) { mod.toggle(); Config.save(); }
        }));

        float rx = x + w - 10;
        if (!isGui) {
            // switch
            float swW = 24, swH = 12, swX = rx - swW, swY = y + head / 2 - swH / 2;
            R.round(ctx, swX, swY, swW, swH, 6, R.mix(th.track(), ac, t));
            if (Modules.CLICKGUI.uiGlow.get() && t > 0.01f) R.glow(ctx, swX, swY, swW, swH, 6, ac, 3, 0.6f * t);
            float kx = swX + 6 + 12 * tRaw;
            R.circle(ctx, kx, swY + 6, 4f, R.mix(th.light() ? 0xFFFFFFFF : 0xFFDADADF, Theme.onAccent(), t));
            if (inClip) hits.add(new Hit(swX - 2, swY - 3, swW + 4, swH + 6, (b, mx) -> {
                if (b == 0) { mod.toggle(); Config.save(); }
            }));
            rx = swX - 8;
        }

        // expand arrow (cross-fades between > and v)
        T.draw(ctx, ">", rx - 6, y + 14, R.withAlpha(th.sub(), 1f - ex));
        T.draw(ctx, "v", rx - 6, y + 13, R.withAlpha(th.sub(), ex));
        if (inClip) hits.add(new Hit(rx - 9, y + 8, 12, 20, (b, mx) -> mod.expanded = !mod.expanded));
        rx -= 14;

        if (!isGui) {
            String mode = mod.hold ? "hold" : "toggle";
            float mw = T.width(mode, 0.7f) + 12;
            float mx0 = rx - mw;
            boolean mh = in(mx0, y + 11, mw, 14);
            R.bordered(ctx, mx0, y + 11, mw, 14, 7, mh ? th.cardHover() : th.card(), th.line());
            T.draw(ctx, mode, mx0 + 6, y + 15, mod.hold ? ac : th.sub(), 0.7f);
            if (inClip) hits.add(new Hit(mx0, y + 11, mw, 14, (b, mx) -> {
                mod.hold = !mod.hold;
                if (mod.hold) mod.setEnabled(false);
                Config.save();
            }));
            rx = mx0 - 4;
        }

        boolean listen = listening == mod;
        String bind = listen ? "press a key" : (mod.key < 0 ? "bind" : mod.bindName());
        float bw = T.width(bind, 0.7f) + 12;
        float bx = rx - bw;
        boolean bh = in(bx, y + 11, bw, 14);
        float pulse = listen ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0) : 0f;
        if (listen) R.glow(ctx, bx, y + 11, bw, 14, 7, ac, 4, 0.4f + 0.4f * pulse);
        R.bordered(ctx, bx, y + 11, bw, 14, 7, bh ? th.cardHover() : th.card(), listen ? R.mix(th.line(), ac, pulse) : th.line());
        T.draw(ctx, bind, bx + 6, y + 15, listen || mod.key >= 0 ? th.text() : th.sub(), 0.7f);
        if (inClip) hits.add(new Hit(bx, y + 11, bw, 14, (b, mx) -> {
            if (b == 1) { mod.key = -1; mod.mouse = false; listening = null; Config.save(); }
            else listening = listening == mod ? null : mod;
        }));

        if (sh > 0.5f) {
            int top = (int) Math.max(clipY, y + head);
            int bot = (int) Math.min(clipY + clipH, y + total);
            if (bot > top) {
                R.scissor(ctx, (int) x, top, (int) (x + w), bot);
                R.rect(ctx, x + 10, y + head - 1, w - 20, 1, th.line());
                float sy = y + head + 4;
                for (Setting st : mod.settings) {
                    if (st.vis < 0.01f) continue;
                    float rh = rowH(st);
                    float oldA = R.alpha;
                    R.alpha = oldA * st.vis;
                    boolean rowClick = mod.expandAnim > 0.9f && st.vis > 0.9f && sy >= top && sy + rh <= bot + 2;
                    setting(ctx, th, ac, st, x + 14, sy, w - 28, rh, dt, rowClick);
                    R.alpha = oldA;
                    sy += rh * st.vis;
                }
                ctx.disableScissor();
            }
        }
        return total;
    }

    private void setting(DrawContext ctx, Theme th, int ac, Setting st, float x, float y, float w, float h, float dt, boolean clickable) {
        boolean hov = clickable && in(x - 4, y, w + 8, h);
        st.hover = Anim.approach(st.hover, hov ? 1f : 0f, 16f, dt);
        if (st.hover > 0.01f) R.round(ctx, x - 5, y + 1, w + 10, h - 2, 5, R.withAlpha(th.cardHover(), st.hover));
        int nameCol = R.mix(th.sub(), th.text(), 0.55f + 0.45f * st.hover);

        if (st instanceof BoolSetting b) {
            float t = Anim.clamp(b.anim.update(b.get() ? 1f : 0f, dt));
            T.draw(ctx, st.name, x, y + h / 2 - 3.5f, nameCol, 0.8f);
            float sw = 18, shh = 9, sx = x + w - sw, sy = y + h / 2 - shh / 2;
            R.round(ctx, sx, sy, sw, shh, 4.5f, R.mix(th.track(), ac, t));
            R.circle(ctx, sx + 4.5f + 9 * b.anim.x, sy + 4.5f, 3f, R.mix(th.light() ? 0xFFFFFFFF : 0xFFDADADF, Theme.onAccent(), t));
            if (clickable) hits.add(new Hit(x - 4, y, w + 8, h, (btn, mx) -> { b.set(!b.get()); Config.save(); }));
        } else if (st instanceof NumSetting n) {
            if (dragging == n) n.setFrac((mouseX - dragX) / dragW);
            float f = (float) n.frac();
            n.anim = n.anim < 0 ? f : Anim.approach(n.anim, f, 22f, dt);
            T.draw(ctx, st.name, x, y + 4, nameCol, 0.8f);
            T.drawRight(ctx, n.display(), x + w, y + 4, dragging == n ? ac : th.sub(), 0.8f);
            float ty = y + 17;
            R.round(ctx, x, ty, w, 3, 1.5f, th.track());
            R.round(ctx, x, ty, Math.max(3, w * n.anim), 3, 1.5f, ac);
            float kx = x + w * n.anim;
            float kr = 3.5f + st.hover * 0.8f + (dragging == n ? 0.6f : 0f);
            if (Modules.CLICKGUI.uiGlow.get()) R.glow(ctx, kx - kr, ty + 1.5f - kr, kr * 2, kr * 2, kr, ac, 3, 0.6f);
            R.circle(ctx, kx, ty + 1.5f, kr, ac);
            if (clickable) hits.add(new Hit(x - 4, y, w + 8, h, (btn, mx) -> {
                dragging = n;
                dragX = x;
                dragW = w;
                n.setFrac((mx - x) / w);
            }));
        } else if (st instanceof ModeSetting md) {
            T.draw(ctx, st.name, x, y + h / 2 - 3.5f, nameCol, 0.8f);
            String v = md.get();
            float pw = T.width(v, 0.75f) + 20;
            float pxx = x + w - pw, pyy = y + 3;
            boolean open = dropdown == md;
            R.bordered(ctx, pxx, pyy, pw, h - 6, 6, open ? th.cardHover() : th.card(), open ? R.withAlpha(ac, 0.6f) : th.line());
            T.draw(ctx, v, pxx + 7, pyy + 4, th.text(), 0.75f);
            T.draw(ctx, "v", pxx + pw - 10, pyy + 3.5f, th.sub(), 0.7f);
            if (clickable) hits.add(new Hit(x - 4, y, w + 8, h, (btn, mx) -> {
                if (btn == 1) { md.setIndex((md.index() + 1) % md.modes.length); Config.save(); return; }
                if (dropdown == md) { dropdown = null; return; }
                dropdown = md;
                ddAnim = 0f;
                ddX = pxx;
                ddY = pyy + h - 4;
                ddW = Math.max(pw, 80);
                for (String s : md.modes) ddW = Math.max(ddW, T.width(s, 0.75f) + 20);
                ddX = x + w - ddW;
            }));
        } else if (st instanceof KeySetting k) {
            T.draw(ctx, st.name, x, y + h / 2 - 3.5f, nameCol, 0.8f);
            boolean l = listenKey == k;
            String v = l ? "press a key" : (k.key < 0 ? "none" : Module.keyName(k.key, k.mouse));
            float pw = T.width(v, 0.75f) + 14;
            float pxx = x + w - pw, pyy = y + 3;
            float pulse = l ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0) : 0f;
            if (l) R.glow(ctx, pxx, pyy, pw, h - 6, 6, ac, 4, 0.4f + 0.4f * pulse);
            R.bordered(ctx, pxx, pyy, pw, h - 6, 6, th.card(), l ? R.mix(th.line(), ac, pulse) : th.line());
            T.draw(ctx, v, pxx + 7, pyy + 4, l || k.key >= 0 ? th.text() : th.sub(), 0.75f);
            if (clickable) hits.add(new Hit(x - 4, y, w + 8, h, (btn, mx) -> {
                if (btn == 1) { k.key = -1; k.mouse = false; listenKey = null; Config.save(); return; }
                listenKey = l ? null : k;
                listening = null;
            }));
        }
    }

    private void dropdownLayer(DrawContext ctx, Theme th, int ac, float dt) {
        if (dropdown == null) return;
        ddAnim = Anim.linear(ddAnim, 1f, 6f * Anim.speed(), dt);
        float e = Anim.open(ddAnim);
        float itemH = 15f;
        float fullH = dropdown.modes.length * itemH + 6;
        float hgt = fullH * Anim.clamp(e);
        float oldA = R.alpha;
        R.alpha = oldA * Anim.clamp(ddAnim * 1.5f);
        R.glow(ctx, ddX, ddY, ddW, hgt, 7, 0xFF000000, 8, 0.8f);
        R.bordered(ctx, ddX, ddY, ddW, Math.max(hgt, 8), 7, R.mix(th.side(), th.light() ? 0xFFFFFFFF : 0xFF000000, 0.15f) | 0xF0000000, th.line());
        R.scissor(ctx, (int) ddX, (int) ddY, (int) (ddX + ddW), (int) (ddY + hgt));
        for (int i = 0; i < dropdown.modes.length; i++) {
            final int idx = i;
            float iy = ddY + 3 + i * itemH;
            boolean sel = i == dropdown.index();
            boolean hov = in(ddX + 3, iy, ddW - 6, itemH);
            if (hov) R.round(ctx, ddX + 3, iy, ddW - 6, itemH, 5, th.cardHover());
            if (sel) R.circle(ctx, ddX + 9, iy + itemH / 2, 1.8f, ac);
            T.draw(ctx, dropdown.modes[i], ddX + 15, iy + 4, sel ? th.text() : th.sub(), 0.75f);
            ddHits.add(new Hit(ddX + 3, iy, ddW - 6, itemH, (b, mx) -> {
                dropdown.setIndex(idx);
                dropdown = null;
                Config.save();
            }));
        }
        ctx.disableScissor();
        R.alpha = oldA;
    }

    // ---------------------------------------------------------------- input

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
            if (b != 0) {
                listening.key = b;
                listening.mouse = true;
                Config.save();
            }
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
        if (dropdown != null || closing) return true;
        vel -= (float) v * 260f;
        target -= (float) v * 14f;
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
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                listening.key = -1;
            } else {
                listening.key = key;
            }
            listening.mouse = false;
            listening = null;
            Config.save();
            return true;
        }
        if (searchFocus) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!query.isEmpty()) { query = ""; scroll = target = 0f; }
                else searchFocus = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { searchFocus = false; return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                scroll = target = 0f;
                return true;
            }
            String add = null;
            if (key == GLFW.GLFW_KEY_SPACE) add = " ";
            else if (key == GLFW.GLFW_KEY_MINUS) add = "-";
            else if (key == GLFW.GLFW_KEY_PERIOD) add = ".";
            else if (key == GLFW.GLFW_KEY_COMMA) add = ",";
            else if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) add = String.valueOf((char) ('a' + (key - GLFW.GLFW_KEY_A)));
            else if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) add = String.valueOf((char) ('0' + (key - GLFW.GLFW_KEY_0)));
            if (add != null && query.length() < 24) query += add;
            scroll = target = 0f;
            return true;
        }
        if (dropdown != null && key == GLFW.GLFW_KEY_ESCAPE) { dropdown = null; return true; }
        Module g = Modules.CLICKGUI;
        if (key == GLFW.GLFW_KEY_ESCAPE || (!g.mouse && g.key == key)) { close(); return true; }
        return super.keyPressed(input);
    }
}
