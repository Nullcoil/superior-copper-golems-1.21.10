package net.nullcoil.scg.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.nullcoil.scg.util.CugoWeatheringAccessor;
import net.nullcoil.scg.util.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CopperGolem.class)
public abstract class Cugo_InteractionMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void scg$handleInteraction(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand != InteractionHand.MAIN_HAND) return;

        CopperGolem cugo = (CopperGolem) (Object) this;
        Level level = cugo.level();
        ItemStack playerHeldItem = player.getItemInHand(hand);
        ItemStack cugoHeldItem = cugo.getMainHandItem();

        // --- 1. LOBOTOMY CHECK (HIGHEST PRIORITY) ---
        // We check this before ANY item swapping logic.
        if (playerHeldItem.is(Items.COPPER_NUGGET)) {
            String displayName = playerHeldItem.getHoverName().getString();
            if (displayName.equalsIgnoreCase("Lobotomize")) {
                Debug.log("Interaction: Lobotomy nugget detected. Converting to statue.");
                if (!level.isClientSide()) {
                    CugoWeatheringAccessor weathering = (CugoWeatheringAccessor) cugo;
                    weathering.scg$convertToStatue(true); // From Cugo_WeatheringMixin
                    if (!player.isCreative()) playerHeldItem.shrink(1);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }
        }

        // --- 2. LOGIC: DROP ITEM ---
        if (!cugoHeldItem.isEmpty()) {
            // Only drop if player is NOT trying to give another item (unless player is empty-handed)
            if (playerHeldItem.isEmpty()) {
                if (!level.isClientSide()) {
                    cugo.spawnAtLocation((ServerLevel) level, cugoHeldItem);
                    cugo.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    cugo.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0f, 1.0f);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }
        }

        // --- 3. LOGIC: TAKE ITEM ---
        if (!playerHeldItem.isEmpty() && cugoHeldItem.isEmpty()) {
            // Check for other special items first (Honeycomb/Axe handled by vanilla or other mixins)
            // But for standard items, Cugo takes them.
            if (!playerHeldItem.is(Items.HONEYCOMB) && !playerHeldItem.is(net.minecraft.tags.ItemTags.AXES)) {
                if (!level.isClientSide()) {
                    ItemStack toGive;
                    if (player.isShiftKeyDown()) {
                        toGive = playerHeldItem.copy();
                        player.setItemInHand(hand, ItemStack.EMPTY);
                    } else {
                        toGive = playerHeldItem.split(1);
                    }
                    cugo.setItemInHand(InteractionHand.MAIN_HAND, toGive);
                    cugo.playSound(SoundEvents.ITEM_PICKUP, 1.0f, 1.0f);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }
}