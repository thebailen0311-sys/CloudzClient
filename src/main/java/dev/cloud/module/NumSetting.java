package dev.cloud.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;
import java.util.function.BooleanSupplier;

public class NumSetting extends Setting {
    public final double min, max, step;
    public final String suffix;
    private double value;
    public float anim = -1f;

    public NumSetting(String name, double def, double min, double max, double step, String suffix) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
        set(def);
    }

    public NumSetting showIf(BooleanSupplier s) { setVisible(s); return this; }

    public double get() { return value; }

    public int getInt() { return (int) Math.round(value); }

    public void set(double v) {
        v = Math.max(min, Math.min(max, v));
        v = min + Math.round((v - min) / step) * step;
        v = Math.max(min, Math.min(max, v));
        value = Math.round(v * 1e6) / 1e6;
    }

    public double frac() { return (value - min) / (max - min); }

    public void setFrac(double f) { set(min + Math.max(0, Math.min(1, f)) * (max - min)); }

    public String display() {
        String n = step >= 1 ? String.valueOf(Math.round(value))
                : step >= 0.1 ? String.format(Locale.ROOT, "%.1f", value)
                : String.format(Locale.ROOT, "%.2f", value);
        return n + suffix;
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override public void fromJson(JsonElement e) { set(e.getAsDouble()); }
}
