package dev.cloud.mixin;

import dev.cloud.module.Modules;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Colours the vanilla glow outline so ESP is not stuck on white. */
@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void cloud$espColor(CallbackInfoReturnable<Integer> cir) {
        if (Modules.ESP == null) return;
        Entity self = (Entity) (Object) this;
        if (Modules.ESP.outline(self)) cir.setReturnValue(Modules.ESP.color());
    }
}
