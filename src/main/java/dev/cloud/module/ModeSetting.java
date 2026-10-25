package dev.cloud.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

public class ModeSetting extends Setting {
    public final String[] modes;
    private int index;

    public ModeSetting(String name, String def, String... modes) {
        super(name);
        this.modes = modes;
        this.index = Math.max(0, Arrays.asList(modes).indexOf(def));
    }

    public ModeSetting showIf(BooleanSupplier s) { setVisible(s); return this; }

    public String get() { return modes[index]; }

    public int index() { return index; }

    public boolean is(String s) { return modes[index].equals(s); }

    public void setIndex(int i) { if (i >= 0 && i < modes.length) index = i; }

    @Override public JsonElement toJson() { return new JsonPrimitive(get()); }

    @Override public void fromJson(JsonElement e) {
        int i = Arrays.asList(modes).indexOf(e.getAsString());
        if (i >= 0) index = i;
    }
}
