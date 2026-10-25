package dev.cloud.module.impl;

import dev.cloud.Weapons;
import dev.cloud.module.*;
import dev.cloud.module.Module;
import dev.cloud.util.Inv;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AutoHitCrystalModule extends Module {
    public final BoolSetting weaponOnly = add(new BoolSetting("Weapon Only", true));
    public final BoolSetting placeObsidian = add(new BoolSetting("Place Obsidian", true));
    public final NumSetting delayMin = add(new NumSetting("Delay Min", 30, 0, 300, 5, " ms"));
    public final NumSetting delayMax = add(new NumSetting("Delay Max", 60, 0, 300, 5, " ms"));
    public final BoolSetting swapBack = add(new BoolSetting("Swap Back", true));

    private int weapon = -1;
    private long next;
    private boolean obbyDone;

    public AutoHitCrystalModule() {
        super("Auto Hit Crystal", "Hold right click with a weapon: obsidian, crystal, hit", Category.COMBAT, false);
    }

    @Override
    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;
        boolean held = isEnabled() && mc.currentScreen == null && mc.options.useKey.isPressed();
        if (!held) {
            if (weapon >= 0 && swapBack.get()) Inv.select(p, weapon);
            weapon = -1;
            obbyDone = false;
            return;
        }
        if (weapon < 0) {
            if (weaponOnly.get() && !Weapons.isWeapon(p.getMainHandStack())) return;
            weapon = Inv.selected(p);
        }
        long now = System.currentTimeMillis();
        if (now < next) return;

        if (mc.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystalEntity c) {
            Inv.select(p, weapon);
            Inv.attack(mc, c);
        } else if (mc.crosshairTarget instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bh.getBlockPos();
            BlockState st = mc.world.getBlockState(pos);
            boolean base = st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.BEDROCK);
            if (base) {
                if (!mc.world.getBlockState(pos.up()).isAir()) return;
                int cs = Inv.hotbar(p, s -> s.isOf(Items.END_CRYSTAL));
                if (cs < 0) return;
                Inv.select(p, cs);
                Inv.use(mc, pos, Direction.UP);
            } else if (placeObsidian.get() && !obbyDone) {
                int os = Inv.hotbar(p, s -> s.isOf(Items.OBSIDIAN));
                if (os < 0) return;
                Inv.select(p, os);
                Inv.use(mc, pos, bh.getSide());
                obbyDone = true;
            } else return;
        } else return;
        next = now + Inv.rnd(delayMin.get(), delayMax.get());
    }
}
