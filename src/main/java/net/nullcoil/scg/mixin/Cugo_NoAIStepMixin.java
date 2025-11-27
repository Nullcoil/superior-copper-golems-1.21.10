package net.nullcoil.scg.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolem.class)
public abstract class Cugo_NoAIStepMixin {
    @Inject(method = "customServerAiStep",
    at = @At("HEAD"),
    cancellable = true)
    private void scg$disableBrainTick(ServerLevel level, CallbackInfo ci) {
        ci.cancel();
    }
}
