package dev.cloud.ui;

import dev.cloud.module.Modules;

public final class Anim {
    private Anim() {}

    public static float clamp(float t) { return t < 0f ? 0f : (t > 1f ? 1f : t); }

    /** Frame-rate independent exponential approach. */
    public static float approach(float cur, float target, float speed, float dt) {
        float k = 1f - (float) Math.exp(-speed * dt);
        float v = cur + (target - cur) * k;
        return Math.abs(target - v) < 0.0005f ? target : v;
    }

    public static float linear(float cur, float target, float speed, float dt) {
        float step = speed * dt;
        if (Math.abs(target - cur) <= step) return target;
        return cur + Math.signum(target - cur) * step;
    }

    public static float easeOutCubic(float t) { t = clamp(t); return 1f - (float) Math.pow(1f - t, 3); }

    public static float easeOutQuint(float t) { t = clamp(t); return 1f - (float) Math.pow(1f - t, 5); }

    public static float easeInOut(float t) {
        t = clamp(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    public static float backOut(float t) {
        t = clamp(t);
        float c1 = 1.3f, c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
    }

    public static float speed() {
        return Modules.CLICKGUI == null ? 1f : (float) Modules.CLICKGUI.animSpeed.get();
    }

    public static String style() {
        return Modules.CLICKGUI == null ? "Spring" : Modules.CLICKGUI.animStyle.get();
    }

    /** Ease used for opening / expanding, depends on the selected animation style. */
    public static float open(float t) {
        return switch (style()) {
            case "Smooth" -> easeInOut(t);
            case "Snappy" -> easeOutQuint(t);
            default -> backOut(t);
        };
    }

    public static final class Spring {
        public float x, v;

        public Spring(float x) { this.x = x; }

        public float update(float target, float dt) {
            float s = speed();
            float k, d;
            switch (style()) {
                case "Smooth" -> { k = 110f; d = 21f; }
                case "Snappy" -> { k = 520f; d = 46f; }
                default -> { k = 260f; d = 19f; }
            }
            k *= s * s;
            d *= s;
            float left = Math.min(dt, 0.1f);
            while (left > 0f) {
                float h = Math.min(left, 0.004f);
                v += (k * (target - x) - d * v) * h;
                x += v * h;
                left -= h;
            }
            if (Math.abs(target - x) < 0.001f && Math.abs(v) < 0.01f) { x = target; v = 0f; }
            return x;
        }
    }
}
