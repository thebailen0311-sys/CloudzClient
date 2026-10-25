package dev.cloud.module.impl;

import dev.cloud.module.BoolSetting;
import dev.cloud.module.Category;
import dev.cloud.module.ModeSetting;
import dev.cloud.module.Module;
import dev.cloud.module.NumSetting;
import dev.cloud.ui.Anim;
import dev.cloud.ui.Lyrics;
import dev.cloud.ui.R;
import dev.cloud.ui.Spotify;
import dev.cloud.ui.T;
import dev.cloud.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/** Now-playing card with karaoke lyrics. */
public class SpotifyModule extends Module {
    public final ModeSetting position = add(new ModeSetting("Position", "Bottom Center",
            "Bottom Center", "Bottom Left", "Bottom Right", "Top Left", "Top Right"));
    public final BoolSetting lyrics = add(new BoolSetting("Lyrics", true));
    public final ModeSetting lyricStyle = add(new ModeSetting("Lyric Style", "Word", "Word", "Line"));
    public final BoolSetting progress = add(new BoolSetting("Progress Bar", true));
    public final BoolSetting vinyl = add(new BoolSetting("Vinyl", true));
    public final NumSetting width = add(new NumSetting("Width", 210, 150, 340, 5, "px"));
    public final NumSetting opacity = add(new NumSetting("Opacity", 88, 20, 100, 1, "%"));
    public final NumSetting offsetX = add(new NumSetting("Offset X", 0, -200, 200, 1, "px"));
    public final NumSetting offsetY = add(new NumSetting("Offset Y", 30, -200, 200, 1, "px"));

    private float anim, spin;

    public SpotifyModule() {
        super("Spotify", "Now playing card with synced lyrics", Category.RENDER, false);
    }

    @Override public String suffix() { return lyrics.get() ? "lyrics" : null; }

    @Override
    public void onEnable() { Spotify.setEnabled(true); }

    @Override
    public void onDisable() { Spotify.setEnabled(false); }

    @Override
    public void tick(MinecraftClient mc) { Spotify.tick(); }

    public void render(DrawContext ctx, float dt) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        Spotify.Track tr = Spotify.track();
        anim = Anim.approach(anim, tr != null ? 1f : 0f, 8f * Anim.speed(), dt);
        if (anim < 0.01f) return;

        Theme th = Theme.current();
        int ac = Theme.accent();
        float sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float w = (float) width.get();
        float h = 46f;
        String pos = position.get();
        boolean right = pos.contains("Right"), bottom = pos.contains("Bottom");
        float x = right ? sw - w - 8 - (float) offsetX.get()
                : pos.contains("Center") ? (sw - w) / 2f + (float) offsetX.get()
                : 8 + (float) offsetX.get();
        float y = bottom ? sh - h - 8 - (float) offsetY.get() : 8 + (float) offsetY.get();

        float a = (float) (opacity.get() / 100.0) * anim;
        float lift = (1f - Anim.easeOutCubic(anim)) * 10f;
        y += lift;

        R.alpha = a;
        R.shadow(ctx, x, y, w, h, 10, 10, 0.9f);
        R.gradRound(ctx, x, y, w, h, 10, th.top(), th.bot());
        R.round(ctx, x + 0.5f, y + 0.5f, w - 1, h - 1, 9.5f, R.withAlpha(0xFFFFFFFF, th.light() ? 0.5f : 0.09f));
        R.round(ctx, x + 1.5f, y + 1.5f, w - 3, h - 3, 8.5f, R.withAlpha(th.bg(), 0.55f));

        float px = x + 10f;
        if (vinyl.get()) {
            spin += dt * (tr != null && tr.playing ? 2.2f : 0.35f);
            float r = 13f;
            R.circle(ctx, px + r, y + h / 2f, r, R.withAlpha(0xFFFFFFFF, 0.06f));
            R.dotRing(ctx, px + r, y + h / 2f, r - 1.5f, 2.4f, 0.35f, R.withAlpha(0xFFFFFFFF, 0.14f));
            R.circle(ctx, px + r, y + h / 2f, 3.4f, R.withAlpha(ac, 0.85f));
            float ax = px + r + (float) Math.cos(spin) * (r - 6f);
            float ay = y + h / 2f + (float) Math.sin(spin) * (r - 6f);
            R.circle(ctx, ax, ay, 1.1f, R.withAlpha(0xFFFFFFFF, 0.9f));
            px += r * 2f + 9f;
        }

        String title = tr == null ? "nothing playing" : tr.title;
        String artist = tr == null ? Spotify.status() : tr.artist;
        float textW = x + w - px - 10f;
        T.draw(ctx, T.fit(title, textW, 1f), px, y + 11, th.text(), 1f);
        T.draw(ctx, T.fit(artist.toLowerCase(Locale.ROOT), textW, 0.78f), px, y + 24, th.sub(), 0.78f);

        if (progress.get() && tr != null && tr.duration > 0) {
            float f = Anim.clamp(Spotify.position() / (float) tr.duration);
            float by = y + h - 11f, bw = w - 20f;
            R.round(ctx, x + 10, by, bw, 2.5f, 1.25f, R.withAlpha(0xFFFFFFFF, 0.10f));
            R.gradRound(ctx, x + 10, by, Math.max(2.5f, bw * f), 2.5f, 1.25f, ac, Theme.accent2());
            R.circle(ctx, x + 10 + bw * f, by + 1.25f, 2.2f, ac);
        }

        if (lyrics.get() && tr != null) drawLyrics(ctx, th, ac, x, y, w, h, bottom);
        R.alpha = 1f;
    }

    private void drawLyrics(DrawContext ctx, Theme th, int ac, float x, float y, float w, float h, boolean below) {
        Lyrics lyr = Spotify.lyrics();
        if (lyr == null || lyr.isEmpty()) return;
        long pos = Spotify.position();
        int idx = lyr.indexAt(pos);
        Lyrics.Line line = lyr.line(idx);
        if (line == null) return;
        Lyrics.Line next = lyr.line(idx + 1);
        float cx = x + w / 2f;
        float baseY = below ? y - 26f : y + h + 12f;

        boolean word = lyricStyle.is("Word");
        if (!word) {
            T.drawCentered(ctx, T.fit(line.text, w - 12, 1.05f), cx, baseY, R.withAlpha(0xFFFFFFFF, 0.95f), 1.05f);
            if (next != null) {
                T.drawCentered(ctx, T.fit(next.text, w - 12, 0.85f), cx, baseY + 14f, R.withAlpha(th.sub(), 0.55f), 0.85f);
            }
            return;
        }

        // word mode: lay the line out, then highlight the word being sung
        float scale = 1.02f;
        float total = 0f;
        for (int i = 0; i < line.words.length; i++) total += T.width(line.words[i], scale) + T.width(" ", scale);
        float startX = cx - total / 2f;
        float cur = startX;
        for (int i = 0; i < line.words.length; i++) {
            String wd = line.words[i];
            float ww = T.width(wd, scale);
            boolean sung = pos >= line.wordTimes[i];
            boolean active = sung && (i + 1 >= line.words.length || pos < line.wordTimes[i + 1]);
            float s = active ? scale * 1.16f : scale;
            float dy = active ? -2.2f : 0f;
            float wx = cur + (ww - T.width(wd, s)) / 2f;
            int col = active ? 0xFFFFFFFF : sung ? R.withAlpha(0xFFFFFFFF, 0.82f) : R.withAlpha(0xFFFFFFFF, 0.34f);
            if (active) {
                T.glow(ctx, wd, wx, baseY + dy, col, s, 0.85f);
            } else {
                T.draw(ctx, wd, wx, baseY + dy, col, s);
            }
            cur += ww + T.width(" ", scale);
        }
        if (next != null) {
            T.drawCentered(ctx, T.fit(next.text, w - 12, 0.85f), cx, baseY + 15f, R.withAlpha(th.sub(), 0.5f), 0.85f);
        }
    }
}
