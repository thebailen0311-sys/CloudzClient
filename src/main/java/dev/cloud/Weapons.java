package dev.cloud;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public final class Weapons {
    private Weapons() {}

    private static String path(ItemStack s) {
        return Registries.ITEM.getId(s.getItem()).getPath();
    }

    public static boolean isMace(ItemStack s) {
        return path(s).equals("mace");
    }

    /** Swords, axes, spears, mace, trident. Pickaxes, shovels, hoes are not weapons. */
    public static boolean isWeapon(ItemStack s) {
        if (s.isEmpty()) return false;
        String p = path(s);
        return p.endsWith("_sword") || p.endsWith("_axe") || p.endsWith("_spear")
                || p.equals("mace") || p.equals("trident");
    }

    /** Mirrors vanilla's critical-hit conditions (including "not sprinting"). */
    public static boolean critReady(ClientPlayerEntity p) {
        if (p.isOnGround()) return false;
        if (isMace(p.getMainHandStack())) return p.fallDistance > 1.5;
        return p.fallDistance > 0
                && !p.isClimbing()
                && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && !p.hasVehicle()
                && !p.isSprinting();
    }
}
