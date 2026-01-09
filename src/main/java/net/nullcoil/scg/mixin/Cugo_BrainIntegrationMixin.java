package net.nullcoil.scg.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.nullcoil.scg.cugo.CugoBehavior;
import net.nullcoil.scg.cugo.CugoBrain;
import net.nullcoil.scg.util.CugoBrainAccessor; // Import the new interface
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolem.class)
// 1. Implement the interface here
public abstract class Cugo_BrainIntegrationMixin implements CugoBrainAccessor {

    @Unique
    private CugoBehavior scg$brain;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void scg$initBrain(CallbackInfo ci) {
        this.scg$brain = new CugoBrain();
        if (this.scg$brain instanceof CugoBrain brain) {
            brain.onAttach((CopperGolem)(Object)this);
        }
    }

    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void scg$replaceAiStep(ServerLevel level, CallbackInfo ci) {
        if (this.scg$brain != null) {
            this.scg$brain.tick((CopperGolem)(Object)this, level);
        }
        ci.cancel();
    }

    // 2. Implement the accessor method
    @Override
    public CugoBehavior scg$getBrain() {
        return this.scg$brain;
    }
}