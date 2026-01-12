package net.nullcoil.scg.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.nullcoil.scg.config.ConfigHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AxeItem.class)
public class Cugo_AxeStatueMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void scg$interceptAxeUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof CopperGolemStatueBlock statueBlock) {
            if (ConfigHandler.getConfig().rechargeableStatues) {
                // Only block if it's UNWAXED and UNAFFECTED
                // Check if it's NOT a waxed block
                boolean isWaxed = block == Blocks.WAXED_COPPER_GOLEM_STATUE ||
                        block == Blocks.WAXED_EXPOSED_COPPER_GOLEM_STATUE ||
                        block == Blocks.WAXED_WEATHERED_COPPER_GOLEM_STATUE ||
                        block == Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE;

                if (!isWaxed && statueBlock.getWeatheringState() == WeatheringCopper.WeatherState.UNAFFECTED) {
                    System.out.println("[CuGO-AXE] Manually triggering block interaction for unwaxed unaffected statue");

                    BlockHitResult hitResult = new BlockHitResult(
                            context.getClickLocation(),
                            context.getClickedFace(),
                            pos,
                            false
                    );

                    InteractionResult result = state.useItemOn(
                            context.getItemInHand(),
                            level,
                            context.getPlayer(),
                            context.getHand(),
                            hitResult
                    );

                    cir.setReturnValue(result);
                }
            }
        }
    }
}