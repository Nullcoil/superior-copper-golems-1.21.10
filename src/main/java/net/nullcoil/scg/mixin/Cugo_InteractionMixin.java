package net.nullcoil.scg.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CopperGolem.class)
public abstract class Cugo_InteractionMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void scg$handleInteraction(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        // We only care about the main hand for consistency
        if (hand != InteractionHand.MAIN_HAND) return;

        CopperGolem cugo = (CopperGolem) (Object) this;
        Level level = cugo.level();
        ItemStack playerHeldItem = player.getItemInHand(hand);
        ItemStack cugoHeldItem = cugo.getMainHandItem();

        // 1. Logic: If Cugo is holding something -> Drop it.
        if (!cugoHeldItem.isEmpty()) {
            if (!level.isClientSide()) {
                // Drop the item at the golem's position
                cugo.spawnAtLocation((ServerLevel) level, cugoHeldItem);

                // Clear the golem's hand
                cugo.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

                // Play a little sound so we know it happened
                cugo.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0f, 1.0f);
            }
            // Success + Swing arm
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        // 2. Logic: If Cugo is empty-handed -> Take item from Player.
        if (!playerHeldItem.isEmpty()) {
            if (!level.isClientSide()) {
                ItemStack toGive;

                // Check for Shift-Click (Sneaking)
                if (player.isShiftKeyDown()) {
                    // GIVE ALL: Take the whole stack
                    toGive = playerHeldItem.copy();
                    player.setItemInHand(hand, ItemStack.EMPTY); // Clear player hand
                } else {
                    // GIVE ONE: Split 1 off the stack
                    toGive = playerHeldItem.split(1);
                    // .split(1) automatically reduces the playerHeldItem count by 1
                }

                // Put it in Cugo's hand
                cugo.setItemInHand(InteractionHand.MAIN_HAND, toGive);
                cugo.playSound(SoundEvents.ITEM_PICKUP, 1.0f, 1.0f);
            }
            // Success + Swing arm
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}