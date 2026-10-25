package dev.cloud.ui;

import net.minecraft.client.gui.DrawContext;

/** Small anti-aliased 2D helpers built on DrawContext.fill (no textures, batches well). */
public final class R {
    private R() {}

    /** Global alpha multiplier (used for GUI fade in/out). */
    public static float alpha = 1f;

    /** Physical pixels per logical unit while inside begin()/end(). Drawing happens at native resolution. */
    public static float K = 1f;
    private static int depth = 0;

    /** Glow draws allowed per frame. Stops the GUI from stacking hundreds of soft layers. */
    private static int glowBudget = 0;

    /** Call once at the top of every frame, before any drawing. */
    public static void beginFrame() { glowBudget = 40; }

    /** Switch to native-pixel space so corners, glow and text are rasterized at full resolution (no GUI-scale pixelation). */
    public static void begin(DrawContext ctx) {
        if (depth++ > 0) return;
        K = Math.max(1f, (float) net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor());
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(1f / K, 1f / K);
    }

    public static void end(DrawContext ctx) {
        if (depth == 0 || --depth > 0) return;
        ctx.getMatrices().popMatrix();
        K = 1f;
    }

    public static void scissor(DrawContext ctx, float x1, float y1, float x2, float y2) {
        ctx.enableScissor(Math.round(x1 * K), Math.round(y1 * K), Math.round(x2 * K), Math.round(y2 * K));
    }

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

    public static void rect(DrawContext ctx, float x, float y, float w, float h, int col) {
        col = fade(col);
        if ((col >>> 24) == 0 || w <= 0 || h <= 0) return;
        ctx.fill(Math.round(x * K), Math.round(y * K), Math.round((x + w) * K), Math.round((y + h) * K), col);
    }

    public static void round(DrawContext ctx, float fx, float fy, float fw, float fh, float fr, int col) {
        col = fade(col);
        if ((col >>> 24) == 0) return;
        int x = Math.round(fx * K), y = Math.round(fy * K), w = Math.round(fw * K), h = Math.round(fh * K);
        if (w <= 0 || h <= 0) return;
        int r = Math.min(Math.round(fr * K), Math.min(w, h) / 2);
        if (r <= 0) { ctx.fill(x, y, x + w, y + h, col); return; }
        ctx.fill(x, y + r, x + w, y + h - r, col);
        int a = col >>> 24, rgb = col & 0xFFFFFF;
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double dx = Math.sqrt(Math.max(0, r * r - dy * dy));
            double inset = r - dx;
            int full = (int) Math.ceil(inset);
            float frac = (float) (full - inset);
            int yt = y + i, yb = y + h - 1 - i;
            ctx.fill(x + full, yt, x + w - full, yt + 1, col);
            ctx.fill(x + full, yb, x + w - full, yb + 1, col);
            if (full > 0 && frac > 0.03f) {
                int ac = ((int) (a * frac) << 24) | rgb;
                ctx.fill(x + full - 1, yt, x + full, yt + 1, ac);
                ctx.fill(x + w - full, yt, x + w - full + 1, yt + 1, ac);
                ctx.fill(x + full - 1, yb, x + full, yb + 1, ac);
                ctx.fill(x + w - full, yb, x + w - full + 1, yb + 1, ac);
            }
        }
    }

    /** Rounded rect with a 1px border. */
    public static void bordered(DrawContext ctx, float x, float y, float w, float h, float r, int fill, int border) {
        round(ctx, x, y, w, h, r, border);
        round(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    /** Soft outer glow: stacked rounded rects with quadratic falloff. */
    public static void glow(DrawContext ctx, float x, float y, float w, float h, float r, int col, int size, float strength) {
        if (strength <= 0.01f || size <= 0) return;
        if (glowBudget <= 0) return;
        glowBudget--;
        float base = (col >>> 24) / 255f * strength;
        if (base < 0.01f) return;
        int steps = Math.min(6, Math.max(3, Math.round(size * 0.5f)));
        float unit = (float) size / steps;
        float norm = (float) size / steps; // keep total intensity independent of resolution
        int rgb = col & 0xFFFFFF;
        for (int s = steps; s >= 1; s--) {
            float i = s * unit;
            float f = 1f - (float) s / (steps + 1);
            int a = (int) (255 * base * f * f * 0.22f * norm);
            if (a <= 0) continue;
            round(ctx, x - i, y - i, w + 2 * i, h + 2 * i, r + i, (a << 24) | rgb);
        }
    }

    /** 1px outline with clipped corners (crisp "rounded" look at small sizes). */
    public static void outline(DrawContext ctx, float x, float y, float w, float h, int col) {
        float t = Math.max(1f, Math.round(K * 0.5f)) / K; // crisp hairline at native resolution
        rect(ctx, x + t, y, w - 2 * t, t, col);
        rect(ctx, x + t, y + h - t, w - 2 * t, t, col);
        rect(ctx, x, y + t, t, h - 2 * t, col);
        rect(ctx, x + w - t, y + t, t, h - 2 * t, col);
    }

    public static void vgrad(DrawContext ctx, float x, float y, float w, float h, int top, int bottom) {
        top = fade(top);
        bottom = fade(bottom);
        if (((top | bottom) >>> 24) == 0 || w <= 0 || h <= 0) return;
        ctx.fillGradient(Math.round(x * K), Math.round(y * K), Math.round((x + w) * K), Math.round((y + h) * K), top, bottom);
    }

    public static void circle(DrawContext ctx, float cx, float cy, float rad, int col) {
        round(ctx, cx - rad, cy - rad, rad * 2, rad * 2, rad, col);
    }

    /** Dotted line from (x1,y1) to (x2,y2). Cheap vector stroke built from round dots. */
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

    /** Circle outline built from evenly spaced dots. */
    public static void dotRing(DrawContext ctx, float cx, float cy, float rad, float gap, float dotR, int col) {
        int n = Math.max(6, Math.round((float) (Math.PI * 2 * rad) / Math.max(0.5f, gap)));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            circle(ctx, cx + (float) Math.cos(a) * rad, cy + (float) Math.sin(a) * rad, dotR, col);
        }
    }

    /** Cinematic diagonal light sweep clipped to the rect. progress 0..1, silent outside (0,1). */
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
        int n = 10;
        for (int i = 0; i < n; i++) {
            float f = (i + 0.5f) / n;
            int a = (int) (46 * (float) Math.sin(Math.PI * f));
            if (a <= 0) continue;
            float sx = bx - bw / 2f + i * (bw / n);
            rect(ctx, sx, -h, bw / n + 0.8f, h * 2f, (a << 24) | 0xFFFFFF);
        }
        ctx.getMatrices().popMatrix();
        ctx.disableScissor();
    }
}
