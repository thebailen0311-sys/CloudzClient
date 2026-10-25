package dev.cloud.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

/** Inter text rendering: sub-pixel placement, cached widths, bloom and gradient fills. */
public final class T {
    private T() {}

    private static final Style STYLE = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("cloudclient", "inter")));
    private static final Map<String, Text> CACHE = new HashMap<>();
    private static final Map<String, Float> WCACHE = new HashMap<>();

    public static Text text(String s) {
        Text t = CACHE.get(s);
        if (t == null) {
            if (CACHE.size() > 768) CACHE.clear();
            t = Text.literal(s).setStyle(STYLE);
            CACHE.put(s, t);
        }
        return t;
    }

    public static float width(String s) {
        Float w = WCACHE.get(s);
        if (w == null) {
            if (WCACHE.size() > 1536) WCACHE.clear();
            w = (float) MinecraftClient.getInstance().textRenderer.getWidth(text(s));
            WCACHE.put(s, w);
        }
        return w;
    }

    public static float width(String s, float scale) { return width(s) * scale; }

    public static void draw(DrawContext ctx, String s, float x, float y, int col) { draw(ctx, s, x, y, col, 1f); }

    public static void draw(DrawContext ctx, String s, float x, float y, int col, float scale) {
        col = R.fade(col);
        if ((col >>> 24) < 4 || s.isEmpty()) return;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(Math.round(x * R.K * 4f) / 4f, Math.round(y * R.K * 4f) / 4f);
        float sc = scale * R.K;
        if (sc != 1f) m.scale(sc, sc);
        ctx.drawText(MinecraftClient.getInstance().textRenderer, text(s), 0, 0, col, false);
        m.popMatrix();
    }

    /** Text with a soft bloom. 5 quads per glyph, still one batch. */
    public static void glow(DrawContext ctx, String s, float x, float y, int col, float scale, float strength) {
        if (strength <= 0.02f || s.isEmpty()) return;
        int a = (int) (255 * Math.min(1f, strength) * 0.26f);
        int c = (a << 24) | (col & 0xFFFFFF);
        float o = 0.7f;
        draw(ctx, s, x - o, y, c, scale);
        draw(ctx, s, x + o, y, c, scale);
        draw(ctx, s, x, y - o, c, scale);
        draw(ctx, s, x, y + o, c, scale);
        draw(ctx, s, x, y, col, scale);
    }

    /** Horizontal gradient across the string, one draw per character. */
    public static void grad(DrawContext ctx, String s, float x, float y, int c1, int c2, float scale) {
        if (s.isEmpty()) return;
        float total = Math.max(1f, width(s));
        float cx = x;
        float acc = 0f;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            float w = width(ch);
            int c = R.mix(c1, c2, acc / total);
            draw(ctx, ch, cx, y, c, scale);
            cx += w * scale;
            acc += w;
        }
    }

    public static void drawRight(DrawContext ctx, String s, float right, float y, int col, float scale) {
        draw(ctx, s, right - width(s, scale), y, col, scale);
    }

    public static void drawCentered(DrawContext ctx, String s, float cx, float y, int col, float scale) {
        draw(ctx, s, cx - width(s, scale) / 2f, y, col, scale);
    }

    /** Truncates with an ellipsis so it never overflows the given width. */
    public static String fit(String s, float max, float scale) {
        if (width(s, scale) <= max) return s;
        String out = s;
        while (out.length() > 1 && width(out + "...", scale) > max) out = out.substring(0, out.length() - 1);
        return out + "...";
    }
}
