package dev.cloud;

import dev.cloud.module.Modules;
import dev.cloud.module.impl.WTapModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;

import java.util.concurrent.ThreadLocalRandom;

public final class WTap {
    private WTap() {}

    private static long pendingAt = -1;
    private static long releaseUntil = -1;
    private static int holdMs;
    private static boolean releasing;

    static int rand(int a, int b) {
        return b <= a ? a : ThreadLocalRandom.current().nextInt(a, b + 1);
    }

    /** Called right before one of your attacks goes out (manual or triggerbot). */
    public static void onAttack(MinecraftClient mc) {
        WTapModule c = Modules.WTAP;
        ClientPlayerEntity p = mc.player;
        if (!c.isEnabled() || p == null || releasing || pendingAt >= 0) return;
        if (c.weaponOnly.get() && !Weapons.isWeapon(p.getMainHandStack())) return;
        if (c.onlySprinting.get() && !p.isSprinting()) return;
        pendingAt = System.currentTimeMillis() + rand(c.reactMin.getInt(), c.reactMax.getInt());
        holdMs = rand(c.holdMin.getInt(), c.holdMax.getInt());
    }

    /** Starts a release immediately (used by the crit sprint-cancel). */
    public static void releaseNow() {
        if (releasing || pendingAt >= 0) return;
        WTapModule c = Modules.WTAP;
        holdMs = rand(c.holdMin.getInt(), c.holdMax.getInt());
        pendingAt = System.currentTimeMillis();
    }

    public static boolean busy() {
        return releasing || pendingAt >= 0;
    }

    /** Runs at the START of every client tick, before player input is read. */
    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) { pendingAt = -1; releasing = false; return; }
        long now = System.currentTimeMillis();

        if (pendingAt >= 0 && now >= pendingAt) {
            pendingAt = -1;
            releasing = true;
            releaseUntil = now + holdMs;
        }
        if (releasing) {
            if (now < releaseUntil) {
                mc.options.forwardKey.setPressed(false);
            } else {
                releasing = false;
                KeyBinding.updatePressedStates(); // restore real keyboard state
                if (Modules.WTAP.resprint.get() && mc.options.forwardKey.isPressed() && !p.isSprinting()
                        && (p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().allowFlying)
                        && !p.isTouchingWater() && !p.hasVehicle()) {
                    p.setSprinting(true);
                }
            }
        }
    }
}
