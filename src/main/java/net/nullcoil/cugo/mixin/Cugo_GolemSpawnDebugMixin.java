package net.nullcoil.cugo.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.level.Level;
import net.nullcoil.cugo.config.ConfigHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolem.class)
public class Cugo_GolemSpawnDebugMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void scg$debugSpawn(EntityType entityType, Level level, CallbackInfo ci) {
        if (!level.isClientSide() && ConfigHandler.getConfig().debugMode) {
            System.out.println("[CuGO-SPAWN] ========== COPPER GOLEM CREATED ==========");
            System.out.println("[CuGO-SPAWN] Stack trace:");
            Thread.dumpStack();
        }
    }
}