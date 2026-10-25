package dev.cloud.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.cloud.ui.Anim;

import java.util.function.BooleanSupplier;

public class BoolSetting extends Setting {
    private boolean value;
    public final Anim.Spring anim;

    public BoolSetting(String name, boolean def) {
        super(name);
        value = def;
        anim = new Anim.Spring(def ? 1f : 0f);
    }

    public BoolSetting showIf(BooleanSupplier s) { setVisible(s); return this; }

    public boolean get() { return value; }

    public void set(boolean v) { value = v; }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override public void fromJson(JsonElement e) { value = e.getAsBoolean(); anim.x = value ? 1f : 0f; }
}
