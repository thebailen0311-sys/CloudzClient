package dev.cloud;

import dev.cloud.input.Binds;
import dev.cloud.profile.ProfileStore;
import dev.cloud.server.ServerConfigs;
import dev.cloud.storage.CloudPaths;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import dev.cloud.module.Modules;
import dev.cloud.util.Inv;
import dev.cloud.ui.Hud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

public class CloudClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        dev.cloud.update.UpdateService.start();
        CloudPaths.ensureDirs();
        ProfileStore.load();
        Config.load();

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            Binds.tick(mc);
            for (dev.cloud.module.Module m : Modules.ALL) m.tick(mc);
            WTap.tick(mc);
            Trigger.tick(mc);
            Hud.tickFallback(mc);
        });

        HudElementRegistry.addLast(Identifier.of("cloudclient", "hud"), (ctx, tickCounter) -> Hud.render(ctx));

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var info = client.getCurrentServerEntry();
            ServerConfigs.onJoin(info == null ? null : info.address);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ServerConfigs.onDisconnect());

        // Fires for manual clicks and for triggerbot attacks (both go through attackEntity)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (world.isClient() && player == mc.player) {
                WTap.onAttack(mc);
                Modules.HITFX.onAttack(mc, entity);
                Modules.TARGETHUD.onAttack(entity);
                if (!Inv.internal && !Modules.SHIELD.onAttack(mc, entity)) Modules.MACE.onAttack(mc, entity);
            }
            return ActionResult.PASS;
        });
    }
}
