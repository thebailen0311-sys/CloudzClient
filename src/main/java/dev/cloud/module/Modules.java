package dev.cloud.module;

import dev.cloud.module.impl.*;

import java.util.ArrayList;
import java.util.List;

public final class Modules {
    private Modules() {}

    public static final List<Module> ALL = new ArrayList<>();
    public static ClickGuiModule CLICKGUI;
    public static AimAssistModule AIM;
    public static TriggerbotModule TRIGGER;
    public static WTapModule WTAP;
    public static ArrayListModule ARRAYLIST;
    public static AutoMaceModule MACE;
    public static ShieldBreakerModule SHIELD;
    public static AutoHitCrystalModule CRYSTAL;
    public static AnchorMacroModule ANCHOR;
    public static AutoTotemModule TOTEM;
    public static SpearLungeModule LUNGE;
    public static HitEffectsModule HITFX;
    public static EspModule ESP;
    public static TargetHudModule TARGETHUD;
    public static AmbienceModule AMBIENCE;
    public static WatermarkModule WATERMARK;
    public static SpotifyModule SPOTIFY;

    public static void init() {
        if (!ALL.isEmpty()) return;
        CLICKGUI = new ClickGuiModule(); // first: other code reads theme/animation settings from it
        AIM = new AimAssistModule();
        TRIGGER = new TriggerbotModule();
        WTAP = new WTapModule();
        ARRAYLIST = new ArrayListModule();
        MACE = new AutoMaceModule();
        SHIELD = new ShieldBreakerModule();
        CRYSTAL = new AutoHitCrystalModule();
        ANCHOR = new AnchorMacroModule();
        TOTEM = new AutoTotemModule();
        LUNGE = new SpearLungeModule();
        HITFX = new HitEffectsModule();
        ESP = new EspModule();
        TARGETHUD = new TargetHudModule();
        AMBIENCE = new AmbienceModule();
        WATERMARK = new WatermarkModule();
        SPOTIFY = new SpotifyModule();
        ALL.add(AIM);
        ALL.add(MACE);
        ALL.add(SHIELD);
        ALL.add(CRYSTAL);
        ALL.add(ANCHOR);
        ALL.add(TOTEM);
        ALL.add(LUNGE);
        ALL.add(TRIGGER);
        ALL.add(WTAP);
        ALL.add(HITFX);
        ALL.add(ESP);
        ALL.add(TARGETHUD);
        ALL.add(AMBIENCE);
        ALL.add(WATERMARK);
        ALL.add(SPOTIFY);
        ALL.add(ARRAYLIST);
        ALL.add(CLICKGUI);
    }
}
