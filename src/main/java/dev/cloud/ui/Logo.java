package dev.cloud.ui;

import net.minecraft.client.gui.DrawContext;

/** Custom vector logo: soft cloud mark with a "C" cutout and a slow orbital ring. Drawn, no textures. */
public final class Logo {
    private Logo() {}

    /** Cloud silhouette only. Width is ~1.15 * s. */
    public static void silhouette(DrawContext ctx, float x, float y, float s, int col) {
        float w = 1.15f * s;
        R.circle(ctx, x + 0.26f * w, y + 0.62f * s, 0.26f * s, col);
        R.circle(ctx, x + 0.50f * w, y + 0.40f * s, 0.31f * s, col);
        R.circle(ctx, x + 0.75f * w, y + 0.60f * s, 0.24f * s, col);
        R.round(ctx, x + 0.04f * w, y + 0.52f * s, 0.92f * w, 0.42f * s, 0.21f * s, col);
    }

    /** Cloud mark with a "C" cutout (pass the background color as cut). */
    public static void mark(DrawContext ctx, float x, float y, float s, int col, int cut) {
        silhouette(ctx, x, y, s, col);
        float cx = x + 0.575f * s, cy = y + 0.56f * s;
        float rr = 0.17f * s, th = Math.max(0.7f, 0.075f * s);
        int n = Math.max(12, Math.round(rr * 4.6f / th));
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(52 + 256.0 * i / n);
            R.circle(ctx, cx + (float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr, th, cut);
        }
    }

    /** Mark + faint orbital ring + traveling satellite with trail. tMs = System.currentTimeMillis(). */
    public static void emblem(DrawContext ctx, float x, float y, float s, int col, int cut, int ring, long tMs) {
        float cx = x + 0.575f * s, cy = y + 0.5f * s;
        float rx = 0.95f * s, ry = 0.60f * s;
        int n = Math.max(30, Math.round(rx * 1.6f));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            R.circle(ctx, cx + (float) Math.cos(a) * rx, cy + (float) Math.sin(a) * ry, 0.55f, ring);
        }
        double sa = (tMs % 6000L) / 6000.0 * Math.PI * 2;
        for (int k = 3; k >= 0; k--) {
            double ta = sa - k * 0.09;
            float tr = k == 0 ? 1.5f : 1.3f - k * 0.25f;
            R.circle(ctx, cx + (float) Math.cos(ta) * rx, cy + (float) Math.sin(ta) * ry, tr,
                    R.withAlpha(col, 1f - k * 0.22f));
        }
        mark(ctx, x, y, s, col, cut);
    }
}