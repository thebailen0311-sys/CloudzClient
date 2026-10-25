package dev.cloud.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Shared hotbar / interaction helpers for combat modules. */
public final class Inv {
    private Inv() {}

    /** True while a module is attacking, so the attack callback ignores our own hits. */
    public static boolean internal;

    public static String id(ItemStack s) { return Registries.ITEM.getId(s.getItem()).getPath(); }

    public static int hotbar(ClientPlayerEntity p, Predicate<ItemStack> f) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && f.test(s)) return i;
        }
        return -1;
    }

    public static int emptyHotbar(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isEmpty()) return i;
        return -1;
    }

    public static int selected(ClientPlayerEntity p) { return p.getInventory().getSelectedSlot(); }

    public static void select(ClientPlayerEntity p, int slot) {
        if (slot >= 0 && slot < 9 && selected(p) != slot) p.getInventory().setSelectedSlot(slot);
    }

    /** Enchantment level by id path (e.g. "breach", "density", "lunge"). */
    public static int ench(ItemStack s, String path) {
        for (var e : EnchantmentHelper.getEnchantments(s).getEnchantmentEntries()) {
            if (e.getKey().getKey().map(k -> k.getValue().getPath().equals(path)).orElse(false)) return e.getIntValue();
        }
        return 0;
    }

    /** Random delay in ms between min and max (order-independent). */
    public static long rnd(double a, double b) {
        long lo = (long) Math.min(a, b), hi = (long) Math.max(a, b);
        return hi <= lo ? lo : ThreadLocalRandom.current().nextLong(lo, hi + 1);
    }

    public static boolean chance(double pct) { return ThreadLocalRandom.current().nextDouble(100.0) < pct; }

    public static boolean use(MinecraftClient mc, BlockPos pos, Direction side) {
        Vec3d c = Vec3d.ofCenter(pos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(c, side, pos, false));
        if (r.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
        return r.isAccepted();
    }

    public static void attack(MinecraftClient mc, Entity e) {
        internal = true;
        try {
            mc.interactionManager.attackEntity(mc.player, e);
            mc.player.swingHand(Hand.MAIN_HAND);
        } finally {
            internal = false;
        }
    }

    public static float[] rot(ClientPlayerEntity p, Vec3d t) {
        double dx = t.x - p.getX(), dy = t.y - p.getEyeY(), dz = t.z - p.getZ();
        double dh = Math.sqrt(dx * dx + dz * dz);
        return new float[]{(float) Math.toDegrees(Math.atan2(dz, dx)) - 90f, (float) -Math.toDegrees(Math.atan2(dy, dh))};
    }

    public static float angle(ClientPlayerEntity p, Vec3d t) {
        float[] r = rot(p, t);
        float dy = MathHelper.wrapDegrees(r[0] - p.getYaw()), dp = r[1] - p.getPitch();
        return (float) Math.sqrt(dy * dy + dp * dp);
    }
}
