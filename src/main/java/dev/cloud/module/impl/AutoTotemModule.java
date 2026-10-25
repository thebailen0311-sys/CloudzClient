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
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotemModule extends Module {
    public final ModeSetting mode = add(new ModeSetting("Mode", "Normal", "Normal", "Hover"));
    public final NumSetting delayMin = add(new NumSetting("Delay Min", 60, 0, 1000, 5, " ms"));
    public final NumSetting delayMax = add(new NumSetting("Delay Max", 140, 0, 1000, 5, " ms"));
    public final BoolSetting invOnly = add(new BoolSetting("Inventory Only", false).showIf(() -> mode.is("Normal")));
    public final NumSetting hotbarSlot = add(new NumSetting("Hotbar Slot", 0, 0, 9, 1, "").showIf(() -> mode.is("Hover")));

    private long due = -1;

    public AutoTotemModule() {
        super("Auto Totem", "Keeps a totem in your offhand (normal or inventory hover)", Category.COMBAT, false);
    }

    @Override public String suffix() { return mode.get(); }

    private boolean ready(long now) {
        if (due < 0) { due = now + Inv.rnd(delayMin.get(), delayMax.get()); return false; }
        return now >= due;
    }

    @Override
    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (!isEnabled() || p == null || mc.interactionManager == null) { due = -1; return; }
        long now = System.currentTimeMillis();
        boolean offOk = p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        if (mode.is("Hover")) {
            if (!(mc.currentScreen instanceof InventoryScreen inv)) { due = -1; return; }
            Slot s = hovered(mc, inv);
            if (s == null || !s.getStack().isOf(Items.TOTEM_OF_UNDYING)) { due = -1; return; }
            int hs = hotbarSlot.getInt() - 1, button = -1;
            if (!offOk) button = 40;
            else if (hs >= 0 && s.id != 36 + hs && !p.getInventory().getStack(hs).isOf(Items.TOTEM_OF_UNDYING)) button = hs;
            if (button < 0) { due = -1; return; }
            if (!ready(now)) return;
            mc.interactionManager.clickSlot(inv.getScreenHandler().syncId, s.id, button, SlotActionType.SWAP, p);
            due = -1;
            return;
        }
        if (offOk) { due = -1; return; }
        boolean invOpen = mc.currentScreen instanceof InventoryScreen;
        if ((invOnly.get() && !invOpen) || (mc.currentScreen != null && !invOpen)) return;
        int idx = -1;
        for (int i = 0; i < 36; i++) if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) { idx = i; break; }
        if (idx < 0) { due = -1; return; }
        if (!ready(now)) return;
        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, idx < 9 ? 36 + idx : idx, 40, SlotActionType.SWAP, p);
        due = -1;
    }

    /** Slot under the cursor (assumes the recipe book is closed). */
    private static Slot hovered(MinecraftClient mc, InventoryScreen inv) {
        double mx = mc.mouse.getX() * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
        double my = mc.mouse.getY() * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
        int left = (inv.width - 176) / 2, top = (inv.height - 166) / 2;
        for (Slot s : inv.getScreenHandler().slots) {
            if (mx >= left + s.x - 1 && mx < left + s.x + 17 && my >= top + s.y - 1 && my < top + s.y + 17) return s;
        }
        return null;
    }
}
