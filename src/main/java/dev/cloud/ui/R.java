package dev.cloud.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * 2D primitives with per-pixel coverage anti-aliasing.
 * Everything is rasterised at native resolution (see begin/end) so corners and text stay crisp
 * at any GUI scale. No framebuffer pass, no textures.
 */
public final class R {
    private R() {}

    /** Global alpha multiplier (GUI fade in/out). */
    public static float alpha = 1f;
    /** Physical pixels per logical unit while inside begin()/end(). */
    public static float K = 1f;
    private static int depth = 0;

    public static void begin(DrawContext ctx) {
        if (depth++ > 0) return;
        K = Math.max(1f, (float) MinecraftClient.getInstance().getWindow().getScaleFactor());
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(1f / K, 1f / K);
    }

    public static void end(DrawContext ctx) {
        if (depth == 0 || --depth > 0) return;
        ctx.getMatrices().popMatrix();
        K = 1f;
    }

    public static void scissor(DrawContext ctx, float x1, float y1, float x2, float y2) {
        ctx.enableScissor(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2));
    }
    // ------------------------------------------------------------------ colour

    public static int fade(int c) {
        if (alpha >= 1f) return c;
        int a = (int) ((c >>> 24) * Math.max(0f, alpha));
        return (a << 24) | (c & 0xFFFFFF);
    }

    public static int withAlpha(int c, float mul) {
        int a = (int) Math.max(0, Math.min(255, (c >>> 24) * mul));
        return (a << 24) | (c & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Anim.clamp(t);
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static float lum(int c) {
        return (0.299f * (c >> 16 & 255) + 0.587f * (c >> 8 & 255) + 0.114f * (c & 255)) / 255f;
    }

    private static int clampI(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    // ------------------------------------------------------------------ shapes

    public static void rect(DrawContext ctx, float x, float y, float w, float h, int col) {
        col = fade(col);
        if ((col >>> 24) == 0 || w <= 0 || h <= 0) return;
        ctx.fill(Math.round(x * K), Math.round(y * K), Math.round((x + w) * K), Math.round((y + h) * K), col);
    }

    public static void round(DrawContext ctx, float x, float y, float w, float h, float r, int col) {
        round(ctx, x, y, w, h, r, r, r, r, col);
    }

    /** Rounded rect, per-corner radii, anti-aliased edges. */
    public static void round(DrawContext ctx, float fx, float fy, float fw, float fh,
                             float rTL, float rTR, float rBR, float rBL, int col) {
        col = fade(col);
        if ((col >>> 24) == 0 || fw <= 0f || fh <= 0f) return;
        int x = Math.round(fx * K), y = Math.round(fy * K);
        int w = Math.round(fw * K), h = Math.round(fh * K);
        if (w <= 0 || h <= 0) return;
        int lim = Math.min(w, h) / 2;
        int tl = clampI(Math.round(rTL * K), 0, lim), tr = clampI(Math.round(rTR * K), 0, lim);
        int br = clampI(Math.round(rBR * K), 0, lim), bl = clampI(Math.round(rBL * K), 0, lim);
        int top = Math.max(tl, tr), bot = Math.max(bl, br);
        if (h - top - bot > 0) ctx.fill(x, y + top, x + w, y + h - bot, col);
        int a = col >>> 24, rgb = col & 0xFFFFFF;
        for (int i = 0; i < top; i++) {
            row(ctx, x, y + i, w, tl > i ? arcInset(tl, i) : 0f, tr > i ? arcInset(tr, i) : 0f, a, rgb);
        }
        for (int i = 0; i < bot; i++) {
            row(ctx, x, y + h - 1 - i, w, bl > i ? arcInset(bl, i) : 0f, br > i ? arcInset(br, i) : 0f, a, rgb);
        }
    }

    private static float arcInset(int r, int i) {
        double dy = r - i - 0.5;
        double dx = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
        return (float) (r - dx);
    }

    private static void row(DrawContext ctx, int x, int y, int w, float il, float ir, int a, int rgb) {
        int l0 = (int) Math.ceil(il), r0 = (int) Math.ceil(ir);
        int x0 = x + l0, x1 = x + w - r0;
        if (x1 > x0) ctx.fill(x0, y, x1, y + 1, (a << 24) | rgb);
        float fl = il - (float) Math.floor(il);
        if (fl > 0.03f && l0 > 0) ctx.fill(x0 - 1, y, x0, y + 1, ((int) (a * (1f - fl)) << 24) | rgb);
        float fr = ir - (float) Math.floor(ir);
        if (fr > 0.03f && r0 > 0) ctx.fill(x1, y, x1 + 1, y + 1, ((int) (a * (1f - fr)) << 24) | rgb);
    }

    private static void rowC(DrawContext ctx, int x, int y, int w, float il, float ir, int col) {
        int a = col >>> 24;
        if (a == 0) return;
        row(ctx, x, y, w, il, ir, a, col & 0xFFFFFF);
    }

    public static void bordered(DrawContext ctx, float x, float y, float w, float h, float r, int fill, int border) {
        round(ctx, x, y, w, h, r, border);
        round(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    // ------------------------------------------------------------------ gradients

    public static void vgrad(DrawContext ctx, float x, float y, float w, float h, int top, int bottom) {
        top = fade(top);
        bottom = fade(bottom);
        if (((top | bottom) >>> 24) == 0 || w <= 0 || h <= 0) return;
        ctx.fillGradient(Math.round(x * K), Math.round(y * K), Math.round((x + w) * K), Math.round((y + h) * K), top, bottom);
    }

    /** Horizontal gradient. Implemented by rotating a vertical gradient, so it is a single quad. */
    public static void hgrad(DrawContext ctx, float x, float y, float w, float h, int left, int right) {
        left = fade(left);
        right = fade(right);
        if (((left | right) >>> 24) == 0 || w <= 0 || h <= 0) return;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate((x + w / 2f) * K, (y + h / 2f) * K);
        m.rotate((float) (Math.PI / 2));
        m.translate(-(h / 2f) * K, -(w / 2f) * K);
        ctx.fillGradient(0, 0, Math.round(h * K), Math.round(w * K), right, left);
        m.popMatrix();
    }

    /** Rounded rect filled with a vertical gradient. Corner rows are shaded per row. */
    public static void gradRound(DrawContext ctx, float fx, float fy, float fw, float fh, float fr, int top, int bottom) {
        gradRound(ctx, fx, fy, fw, fh, fr, fr, fr, fr, top, bottom);
    }

    public static void gradRound(DrawContext ctx, float fx, float fy, float fw, float fh,
                                 float rTL, float rTR, float rBR, float rBL, int top, int bottom) {
        top = fade(top);
        bottom = fade(bottom);
        if (((top | bottom) >>> 24) == 0 || fw <= 0f || fh <= 0f) return;
        int x = Math.round(fx * K), y = Math.round(fy * K);
        int w = Math.round(fw * K), h = Math.round(fh * K);
        if (w <= 0 || h <= 0) return;
        int lim = Math.min(w, h) / 2;
        int tl = clampI(Math.round(rTL * K), 0, lim), tr = clampI(Math.round(rTR * K), 0, lim);
        int br = clampI(Math.round(rBR * K), 0, lim), bl = clampI(Math.round(rBL * K), 0, lim);
        int tp = Math.max(tl, tr), bt = Math.max(bl, br);
        if (h - tp - bt > 0) {
            ctx.fillGradient(x, y + tp, x + w, y + h - bt,
                    mix(top, bottom, tp / (float) h), mix(top, bottom, (h - bt) / (float) h));
        }
        for (int i = 0; i < tp; i++) {
            rowC(ctx, x, y + i, w, tl > i ? arcInset(tl, i) : 0f, tr > i ? arcInset(tr, i) : 0f,
                    mix(top, bottom, i / (float) h));
        }
        for (int i = 0; i < bt; i++) {
            rowC(ctx, x, y + h - 1 - i, w, bl > i ? arcInset(bl, i) : 0f, br > i ? arcInset(br, i) : 0f,
                    mix(top, bottom, (h - 1 - i) / (float) h));
        }
    }

    // ------------------------------------------------------------------ effects

    /** Cheap square-cornered bloom: 2-5 fills total. Use for small elements. */
    public static void glow(DrawContext ctx, float x, float y, float w, float h, float r, int col, int size, float strength) {
        if (strength <= 0.01f || size <= 0) return;
        float base = (col >>> 24) / 255f * strength;
        if (base < 0.01f) return;
        int rgb = col & 0xFFFFFF;
        int steps = Math.max(2, Math.min(5, Math.round(size / 3f)));
        for (int s = steps; s >= 1; s--) {
            float i = size * (s / (float) steps);
            float f = 1f - s / (float) (steps + 1);
            int a = (int) (255 * base * f * f * 0.17f);
            if (a <= 1) continue;
            rect(ctx, x - i, y - i, w + 2 * i, h + 2 * i, (a << 24) | rgb);
        }
    }

    /** Rounded bloom, 3 layers. For panels only - it is the expensive one. */
    public static void glowR(DrawContext ctx, float x, float y, float w, float h, float r, int col, int size, float strength) {
        if (strength <= 0.01f || size <= 0) return;
        float base = (col >>> 24) / 255f * strength;
        if (base < 0.01f) return;
        int rgb = col & 0xFFFFFF;
        for (int s = 3; s >= 1; s--) {
            float i = size * (s / 3f);
            float f = 1f - s / 4f;
            int a = (int) (255 * base * f * f * 0.16f);
            if (a <= 1) continue;
            round(ctx, x - i, y - i, w + 2 * i, h + 2 * i, r + i, (a << 24) | rgb);
        }
    }

    /** Soft drop shadow under a panel. */
    public static void shadow(DrawContext ctx, float x, float y, float w, float h, float r, int size, float strength) {
        if (strength <= 0.01f) return;
        for (int s = 3; s >= 1; s--) {
            float i = size * (s / 3f);
            int a = (int) (255 * strength * 0.10f * (4 - s) / 3f);
            if (a <= 1) continue;
            round(ctx, x - i, y - i + 2f, w + 2 * i, h + 2 * i, r + i, (a << 24));
        }
    }

    public static void outline(DrawContext ctx, float x, float y, float w, float h, int col) {
        float t = Math.max(1f, Math.round(K * 0.5f)) / K;
        rect(ctx, x + t, y, w - 2 * t, t, col);
        rect(ctx, x + t, y + h - t, w - 2 * t, t, col);
        rect(ctx, x, y + t, t, h - 2 * t, col);
        rect(ctx, x + w - t, y + t, t, h - 2 * t, col);
    }

    public static void circle(DrawContext ctx, float cx, float cy, float rad, int col) {
        round(ctx, cx - rad, cy - rad, rad * 2, rad * 2, rad, col);
    }

    /** 1px hairline with anti-aliased ends. */
    public static void line(DrawContext ctx, float x1, float y1, float x2, float y2, float thick, int col) {
        col = fade(col);
        if ((col >>> 24) == 0) return;
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        int n = Math.max(1, Math.round(len * K / 1.5f));
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            circle(ctx, x1 + dx * t, y1 + dy * t, thick * 0.5f, col);
        }
    }

    public static void dotLine(DrawContext ctx, float x1, float y1, float x2, float y2, float gap, float rad, int col) {
        float dx = x2 - x1, dy = y2 - y1;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 0.01f) return;
        int n = Math.max(1, Math.round(d / Math.max(0.5f, gap)));
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            circle(ctx, x1 + dx * t, y1 + dy * t, rad, col);
        }
    }

    public static void dotRing(DrawContext ctx, float cx, float cy, float rad, float gap, float dotR, int col) {
        int n = Math.max(6, Math.round((float) (Math.PI * 2 * rad) / Math.max(0.5f, gap)));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            circle(ctx, cx + (float) Math.cos(a) * rad, cy + (float) Math.sin(a) * rad, dotR, col);
        }
    }

    /** Arc stroke with per-dot colour interpolation (used by the logo). */
    public static void arc(DrawContext ctx, float cx, float cy, float rad, float fromDeg, float toDeg,
                           float thick, int c1, int c2, int steps) {
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            double a = Math.toRadians(fromDeg + (toDeg - fromDeg) * t);
            circle(ctx, cx + (float) Math.cos(a) * rad, cy + (float) Math.sin(a) * rad, thick * 0.5f, mix(c1, c2, t));
        }
    }

    /** Cinematic diagonal light sweep clipped to the rect. */
    public static void sweep(DrawContext ctx, float x, float y, float w, float h, float t) {
        t = Anim.clamp(t);
        if (t <= 0f || t >= 1f || w <= 0 || h <= 0) return;
        scissor(ctx, x, y, x + w, y + h);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x + w / 2f, y + h / 2f);
        ctx.getMatrices().rotate(0.26f);
        float span = w + h;
        float bw = Math.min(150f, w * 0.45f);
        float bx = -span / 2f + span * t;
        int n = 8;
        for (int i = 0; i < n; i++) {
            float f = (i + 0.5f) / n;
            int a = (int) (40 * (float) Math.sin(Math.PI * f));
            if (a <= 0) continue;
            float sx = bx - bw / 2f + i * (bw / n);
            rect(ctx, sx, -h, bw / n + 0.8f, h * 2f, (a << 24) | 0xFFFFFF);
        }
        ctx.getMatrices().popMatrix();
        ctx.disableScissor();
    }
}
