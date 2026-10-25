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
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AnchorMacroModule extends Module {
    public final KeySetting placeBind = add(new KeySetting("Place Bind"));
    public final KeySetting explodeBind = add(new KeySetting("Explode Bind"));
    public final NumSetting delayMin = add(new NumSetting("Delay Min", 40, 0, 500, 5, " ms"));
    public final NumSetting delayMax = add(new NumSetting("Delay Max", 80, 0, 500, 5, " ms"));
    public final NumSetting explodeSlot = add(new NumSetting("Explode Slot", 0, 0, 9, 1, ""));
    public final BoolSetting swapBack = add(new BoolSetting("Swap Back", true));
    public final BoolSetting safe = add(new BoolSetting("Safe Anchor", false));
    public final ModeSetting safeBlock = add(new ModeSetting("Safe Block", "Glowstone", "Glowstone", "Obsidian").showIf(() -> safe.get()));
    public final NumSetting limit = add(new NumSetting("Block Limit", 1, 1, 3, 1, "").showIf(() -> safe.get()));
    public final BoolSetting legit = add(new BoolSetting("Legit Mode", false).showIf(() -> safe.get()));
    public final BoolSetting silent = add(new BoolSetting("Silent Aim", true).showIf(() -> safe.get() && !legit.get()));
    public final NumSetting fov = add(new NumSetting("FOV", 180, 30, 360, 5, "\u00B0").showIf(() -> safe.get() && !legit.get()));

    private static final int IDLE = -1, PLACE = 0, CHARGE = 1, SAFE = 2, EXPLODE = 3, RESTORE = 4;
    private int step = IDLE, prev = -1, placed, tries;
    private long next, safeStart;
    private BlockPos anchor, against;
    private Direction side;

    public AnchorMacroModule() {
        super("Anchor Macro", "Place bind: place, charge, detonate. Explode bind: charge + detonate", Category.COMBAT, false);
    }

    @Override protected void onDisable() { step = IDLE; }

    @Override public String suffix() { return safe.get() ? "Safe" : null; }

    @Override
    public void tick(MinecraftClient mc) {
        boolean pp = placeBind.pressed(mc), ep = explodeBind.pressed(mc);
        ClientPlayerEntity p = mc.player;
        if (!isEnabled() || p == null || mc.world == null || mc.interactionManager == null) { step = IDLE; return; }
        if (mc.currentScreen != null) return;
        if (step == IDLE) {
            if (pp) start(mc, p, true);
            else if (ep) start(mc, p, false);
            return;
        }
        if (System.currentTimeMillis() >= next) run(mc, p);
    }

    private void start(MinecraftClient mc, ClientPlayerEntity p, boolean place) {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        BlockState st = mc.world.getBlockState(pos);
        prev = Inv.selected(p);
        placed = 0;
        tries = 0;
        safeStart = -1;
        if (st.isOf(Blocks.RESPAWN_ANCHOR)) {
            anchor = pos;
            step = st.get(RespawnAnchorBlock.CHARGES) > 0 ? afterCharge() : CHARGE;
        } else if (place) {
            anchor = pos.offset(hit.getSide());
            if (!mc.world.getBlockState(anchor).isReplaceable()) return;
            against = pos;
            side = hit.getSide();
            step = PLACE;
        } else return;
        run(mc, p);
    }

    private int afterCharge() { return safe.get() ? SAFE : EXPLODE; }

    private int charges(MinecraftClient mc) {
        BlockState st = mc.world.getBlockState(anchor);
        return st.isOf(Blocks.RESPAWN_ANCHOR) ? st.get(RespawnAnchorBlock.CHARGES) : -1;
    }

    private void run(MinecraftClient mc, ClientPlayerEntity p) {
        switch (step) {
            case PLACE -> {
                int s = Inv.hotbar(p, x -> x.isOf(Items.RESPAWN_ANCHOR));
                if (s < 0) { finish(p); return; }
                Inv.select(p, s);
                Inv.use(mc, against, side);
                step = CHARGE;
            }
            case CHARGE -> {
                if (charges(mc) < 0) { if (++tries > 6) finish(p); return; }
                int s = Inv.hotbar(p, x -> x.isOf(Items.GLOWSTONE));
                if (s < 0) { finish(p); return; }
                Inv.select(p, s);
                Inv.use(mc, anchor, Direction.UP);
                tries = 0;
                step = afterCharge();
            }
            case SAFE -> { if (safeStep(mc, p)) step = EXPLODE; }
            case EXPLODE -> {
                int c = charges(mc);
                if (c < 0) { finish(p); return; }
                if (c == 0) { if (++tries > 6) finish(p); return; }
                int s = pickExplode(p);
                if (s < 0) { finish(p); return; }
                Inv.select(p, s);
                Inv.use(mc, anchor, Direction.UP);
                step = RESTORE;
            }
            default -> { finish(p); return; }
        }
        next = System.currentTimeMillis() + Inv.rnd(delayMin.get(), delayMax.get());
    }

    private void finish(ClientPlayerEntity p) {
        if (swapBack.get() && prev >= 0) Inv.select(p, prev);
        step = IDLE;
    }

    private int pickExplode(ClientPlayerEntity p) {
        int fixed = explodeSlot.getInt();
        if (fixed > 0 && !p.getInventory().getStack(fixed - 1).isOf(Items.GLOWSTONE)) return fixed - 1;
        int s = Inv.hotbar(p, x -> x.isOf(Items.TOTEM_OF_UNDYING));
        if (s < 0) s = Inv.hotbar(p, Weapons::isWeapon);
        if (s < 0) s = Inv.emptyHotbar(p);
        if (s < 0) s = Inv.hotbar(p, x -> !x.isOf(Items.GLOWSTONE));
        return s;
    }

    /** Places blocks between you and the anchor. Returns true when finished (placed, skipped or timed out). */
    private boolean safeStep(MinecraftClient mc, ClientPlayerEntity p) {
        int max = limit.getInt();
        if (placed >= max) return true;
        int s = Inv.hotbar(p, x -> x.isOf(safeBlock.is("Obsidian") ? Items.OBSIDIAN : Items.GLOWSTONE));
        if (s < 0) return true;
        if (legit.get()) {
            long now = System.currentTimeMillis();
            if (safeStart < 0) safeStart = now;
            if (now - safeStart > 1500) return true;
            if (p.getPitch() < 35f || !(mc.crosshairTarget instanceof BlockHitResult h) || h.getType() != HitResult.Type.BLOCK) return false;
            BlockPos at = h.getBlockPos().offset(h.getSide());
            if (h.getBlockPos().equals(anchor) || at.equals(anchor) || !mc.world.getBlockState(at).isReplaceable()
                    || p.getBoundingBox().intersects(new Box(at))) return false;
            Inv.select(p, s);
            if (Inv.use(mc, h.getBlockPos(), h.getSide())) { placed++; safeStart = now; }
            return placed >= max;
        }
        double dx = p.getX() - (anchor.getX() + 0.5), dz = p.getZ() - (anchor.getZ() + 0.5);
        Direction d = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos at = anchor.offset(d).up(placed);
        if (!mc.world.getBlockState(at).isReplaceable()) { placed++; return placed >= max; }
        if (mc.world.getBlockState(at.down()).isReplaceable() || p.getBoundingBox().intersects(new Box(at))) return true;
        Vec3d face = Vec3d.ofCenter(at.down()).add(0, 0.5, 0);
        if (Inv.angle(p, face) > fov.get() / 2.0) return true;
        if (!silent.get()) {
            float[] r = Inv.rot(p, face);
            p.setYaw(r[0]);
            p.setPitch(r[1]);
        }
        Inv.select(p, s);
        Inv.use(mc, at.down(), Direction.UP);
        placed++;
        return placed >= max;
    }
}
