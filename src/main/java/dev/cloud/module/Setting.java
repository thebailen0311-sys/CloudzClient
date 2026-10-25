package dev.cloud.module;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

public abstract class Setting {
    public final String name;
    private BooleanSupplier visible = () -> true;
    public float vis = 1f, hover;

    protected Setting(String name) { this.name = name; }

    protected void setVisible(BooleanSupplier s) { this.visible = s; }

    public boolean visible() { return visible.getAsBoolean(); }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement e);
}
