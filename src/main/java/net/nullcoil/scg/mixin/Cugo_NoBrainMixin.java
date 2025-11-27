package net.nullcoil.scg.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CopperGolem.class)
public abstract class Cugo_NoBrainMixin {
    @Inject(method = "makeBrain", at = @At("HEAD"), cancellable = true)
    private void scg$emptyBrain(Dynamic<?> dynamic, CallbackInfoReturnable<Brain<?>> cir) {
        cir.setReturnValue(
                Brain.provider(ImmutableList.of(), ImmutableList.of()).makeBrain(dynamic)
        );
    }
}

