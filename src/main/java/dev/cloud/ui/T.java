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

/** Inter Bold text rendering with sub-pixel positioning via the matrix stack. */
public final class T {
    private T() {}

    private static final Style STYLE = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("cloudclient", "inter")));
    private static final Map<String, Text> CACHE = new HashMap<>();

    public static Text text(String s) {
        Text t = CACHE.get(s);
        if (t == null) {
            if (CACHE.size() > 512) CACHE.clear();
            t = Text.literal(s).setStyle(STYLE);
            CACHE.put(s, t);
        }
        return t;
    }

    public static float width(String s) { return MinecraftClient.getInstance().textRenderer.getWidth(text(s)); }

    public static float width(String s, float scale) { return width(s) * scale; }

    public static void draw(DrawContext ctx, String s, float x, float y, int col) { draw(ctx, s, x, y, col, 1f); }

    public static void draw(DrawContext ctx, String s, float x, float y, int col, float scale) {
        col = R.fade(col);
        if ((col >>> 24) < 6 || s.isEmpty()) return;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x * R.K, y * R.K);
        float sc = scale * R.K;
        if (sc != 1f) m.scale(sc, sc);
        ctx.drawText(MinecraftClient.getInstance().textRenderer, text(s), 0, 0, col, false);
        m.popMatrix();
    }

    public static void drawRight(DrawContext ctx, String s, float right, float y, int col, float scale) {
        draw(ctx, s, right - width(s, scale), y, col, scale);
    }
}
