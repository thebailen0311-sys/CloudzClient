package dev.cloud.module;

public enum Category {
    COMBAT("Combat"), RENDER("Render"), CLIENT("Client");

    public final String label;

    Category(String label) { this.label = label; }
}
