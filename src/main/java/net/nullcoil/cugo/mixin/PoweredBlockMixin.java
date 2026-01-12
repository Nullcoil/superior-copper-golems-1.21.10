package net.nullcoil.cugo.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.cugo.config.ConfigHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class PoweredBlockMixin {
    @Inject(method = "stepOn", at = @At("HEAD"), cancellable = true)
    public void scg$copperGolemStep(Level level, BlockPos blockPos, BlockState blockState, Entity entity, CallbackInfo ci) {
        if(level.getBlockState(blockPos).is(Blocks.REDSTONE_BLOCK) && ConfigHandler.getConfig().redstoneBoost && entity instanceof CopperGolem cugo) {
            cugo.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 0, false, true, false));
        }
        ci.cancel();
    }
}
