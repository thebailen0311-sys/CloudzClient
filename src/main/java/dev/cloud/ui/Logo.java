package dev.cloud.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * Vector logo: a crescent "C" with a gradient stroke, a sparkle and an orbital ring.
 * Drawn from primitives so it stays sharp at any size and needs no texture.
 */
public final class Logo {
    private Logo() {}

    /** Crescent only, single colour. Width is ~1.0 * s. */
    public static void silhouette(DrawContext ctx, float x, float y, float s, int col) {
        float cx = x + s * 0.5f, cy = y + s * 0.5f;
        R.arc(ctx, cx, cy, s * 0.42f, 38f, 322f, Math.max(1.2f, s * 0.24f), col, col, Math.max(12, Math.round(s * 1.4f)));
    }

    /**
     * The mark: gradient crescent + sparkle.
     *
     * @param cut colour used to punch the inner notch out (pass the surface colour behind it)
     */
    public static void mark(DrawContext ctx, float x, float y, float s, int col, int cut) {
        float cx = x + s * 0.5f, cy = y + s * 0.5f;
        float r = s * 0.42f;
        R.arc(ctx, cx, cy, r, 34f, 326f, Math.max(1.1f, s * 0.21f), col, R.withAlpha(col, 0.55f),
                Math.max(14, Math.round(s * 1.6f)));
        R.circle(ctx, cx + r * 0.34f, cy - r * 0.06f, r * 0.42f, cut);
        float sx = x + s * 0.87f, sy = y + s * 0.15f, sr = Math.max(0.7f, s * 0.055f);
        R.circle(ctx, sx, sy, sr, col);
        R.circle(ctx, sx, sy, sr * 0.45f, 0xFFFFFFFF);
    }

    /** Mark + orbital ring + travelling satellite with trail. */
    public static void emblem(DrawContext ctx, float x, float y, float s, int col, int cut, int ring, long tMs) {
        float cx = x + s * 0.5f, cy = y + s * 0.5f;
        float rx = s * 0.62f, ry = s * 0.40f;
        int n = Math.max(24, Math.round(rx * 1.5f));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            R.circle(ctx, cx + (float) Math.cos(a) * rx, cy + (float) Math.sin(a) * ry, Math.max(0.4f, s * 0.012f), ring);
        }
        double sa = (tMs % 7000L) / 7000.0 * Math.PI * 2;
        for (int k = 3; k >= 0; k--) {
            double ta = sa - k * 0.10;
            float tr = k == 0 ? Math.max(0.9f, s * 0.045f) : Math.max(0.6f, s * 0.04f) - k * s * 0.008f;
            R.circle(ctx, cx + (float) Math.cos(ta) * rx, cy + (float) Math.sin(ta) * ry, tr,
                    R.withAlpha(col, 1f - k * 0.22f));
        }
        mark(ctx, x, y, s, col, cut);
    }
}
