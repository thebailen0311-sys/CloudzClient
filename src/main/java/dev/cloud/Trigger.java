package dev.cloud;

import dev.cloud.module.Modules;
import dev.cloud.module.impl.TriggerbotModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

public final class Trigger {
    private Trigger() {}

    private static long readySince = -1;
    private static long critWaitSince = -1;
    private static int delay;

    private static void reset() {
        readySince = -1;
        critWaitSince = -1;
    }

    public static void tick(MinecraftClient mc) {
        TriggerbotModule c = Modules.TRIGGER;
        ClientPlayerEntity p = mc.player;

        if (!c.isEnabled() || p == null || mc.world == null || mc.interactionManager == null
                || mc.currentScreen != null || p.isUsingItem() || p.isSpectator()) {
            reset();
            return;
        }
        if (c.weaponOnly.get() && !Weapons.isWeapon(p.getMainHandStack())) { reset(); return; }

        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) { reset(); return; }
        Entity e = hit.getEntity();
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e == p || le.isSpectator()
                || le.isInvulnerable() || (c.playersOnly.get() && !(e instanceof PlayerEntity))) {
            reset();
            return;
        }

        if (p.getAttackCooldownProgress(0.0f) < c.minCharge.getInt() / 100.0f) {
            readySince = -1;
            critWaitSince = -1;
            return;
        }

        long now = System.currentTimeMillis();

        if (c.critMode.get() && !Weapons.critReady(p)) {
            if (critWaitSince < 0) critWaitSince = now;
            if (c.critDropSprint.get() && p.isSprinting() && !p.isOnGround() && !WTap.busy()) {
                WTap.releaseNow();
            }
            int timeout = c.critFallback.getInt();
            boolean timedOut = timeout > 0 && now - critWaitSince >= timeout;
            if (!timedOut) {
                readySince = -1;
                return;
            }
        }

        if (readySince < 0) {
            readySince = now;
            delay = WTap.rand(c.reactMin.getInt(), c.reactMax.getInt());
        }
        if (now - readySince < delay) return;

        mc.interactionManager.attackEntity(p, e);
        p.swingHand(Hand.MAIN_HAND);
        reset();
    }
}
