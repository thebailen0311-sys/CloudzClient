package dev.cloud.mixin;

import dev.cloud.module.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void cloud$outline(Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (Modules.ESP != null && Modules.ESP.outline(e)) cir.setReturnValue(true);
    }
}
