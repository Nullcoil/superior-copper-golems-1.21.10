package net.nullcoil.scg.cugo.managers;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.scg.config.ConfigHandler;
import net.nullcoil.scg.cugo.behaviors.InteractWithChestBehavior;
import net.nullcoil.scg.cugo.behaviors.RandomWanderBehavior;
import net.nullcoil.scg.cugo.behaviors.ReturnHomeBehavior;
import net.nullcoil.scg.util.CugoAnimationAccessor;
import net.nullcoil.scg.util.CugoHomeAccessor;

public class NavigationController {

    private final CopperGolem golem;
    private int cooldownTimer = 0;

    // --- STATE ---
    private StateMachine.Intent currentIntent = StateMachine.Intent.WANDERING; // Default
    private boolean isInteracting = false;

    // --- TASK STATE ---
    private boolean forceWander = false;
    private BlockPos pendingChestPos = null;
    private int pendingSlotIndex = -1;

    private final RandomWanderBehavior wanderBehavior = new RandomWanderBehavior();
    private final ReturnHomeBehavior homeBehavior = new ReturnHomeBehavior();
    private final InteractWithChestBehavior interactBehavior = new InteractWithChestBehavior(this);

    public NavigationController(CopperGolem golem) {
        this.golem = golem;
    }

    public void tick() {
        if (cooldownTimer > 0) {
            cooldownTimer--;

            if (cooldownTimer == 0) {
                // Animation Finished Logic
                if (isInteracting) {
                    finalizeItemPickup();
                    ((CugoAnimationAccessor) golem).scg$setInteractState(null);
                    closeHomeChest();
                    isInteracting = false;

                    // Reset Intent: We finished the job, now we wander.
                    this.currentIntent = StateMachine.Intent.WANDERING;
                }
            }
            return;
        }

        PathNavigation nav = golem.getNavigation();
        if (!nav.isDone()) {
            return;
        }

        decideNextMove();
    }

    private void decideNextMove() {
        ItemStack heldItem = golem.getMainHandItem();
        boolean success = false;
        boolean startingInteraction = false;

        // 1. FORCE WANDER
        if (forceWander) {
            if (wanderBehavior.run(golem)) {
                success = true;
                this.currentIntent = StateMachine.Intent.WANDERING; // Explicit
                forceWander = false;
            } else {
                this.cooldownTimer = 10;
                return;
            }
        }

        // 2. STANDARD LOGIC
        if (!success) {
            if (heldItem.isEmpty()) {

                // Priority 1: Interact
                // Condition: Must be in range AND Intent must be RETURNING_HOME
                if (this.currentIntent == StateMachine.Intent.RETURNING_HOME && interactBehavior.canInteract(golem)) {
                    if (interactBehavior.run(golem)) {
                        success = true;
                        startingInteraction = true;
                    }
                }

                // Priority 2: Go Home
                if (!success) {
                    if(homeBehavior.run(golem)) {
                        success = true;
                        this.currentIntent = StateMachine.Intent.RETURNING_HOME; // Set Intent
                    }
                }

                // Priority 3: Wander
                if (!success) {
                    if (wanderBehavior.run(golem)) {
                        success = true;
                        this.currentIntent = StateMachine.Intent.WANDERING; // Set Intent
                    }
                }
            } else {
                // Holding Item -> Wander
                if (wanderBehavior.run(golem)) {
                    success = true;
                    this.currentIntent = StateMachine.Intent.WANDERING;
                }
            }
        }

        if (success) {
            if (startingInteraction) {
                this.isInteracting = true;
                this.cooldownTimer = 60;
            } else if (!golem.getNavigation().isDone()) {
                this.isInteracting = false;
                this.cooldownTimer = 5;
            } else {
                this.isInteracting = false;
                this.cooldownTimer = ConfigHandler.getConfig().wanderDuration * 20;
            }
        } else {
            this.isInteracting = false;
            this.cooldownTimer = 20;
        }
    }

    public void markInteractionFailed() {
        this.forceWander = true;
    }

    public void schedulePickup(BlockPos pos, int slotIndex) {
        this.pendingChestPos = pos;
        this.pendingSlotIndex = slotIndex;
    }

    private void finalizeItemPickup() {
        if (pendingChestPos != null && pendingSlotIndex != -1) {
            Level level = golem.level();
            Container container = getContainer(level, pendingChestPos);
            if (container != null && container.getContainerSize() > pendingSlotIndex) {
                ItemStack stack = container.getItem(pendingSlotIndex);
                if (!stack.isEmpty()) {
                    ItemStack taken = container.removeItem(pendingSlotIndex, stack.getCount());
                    golem.setItemInHand(InteractionHand.MAIN_HAND, taken);
                    golem.playSound(SoundEvents.ITEM_PICKUP, 1.0f, 1.0f);
                }
            }
            pendingChestPos = null;
            pendingSlotIndex = -1;
        }
    }

    private void closeHomeChest() {
        BlockPos homePos = ((CugoHomeAccessor) golem).scg$getHomePos();
        if (homePos != null) {
            Level level = golem.level();
            BlockState state = level.getBlockState(homePos);
            level.blockEvent(homePos, state.getBlock(), 1, 0);
            golem.playSound(SoundEvents.CHEST_CLOSE, 0.5f, 1.0f);
        }
    }

    private Container getContainer(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity && state.getBlock() instanceof ChestBlock chestBlock) {
            return ChestBlock.getContainer(chestBlock, state, level, pos, true);
        }
        if (be instanceof Container c) return c;
        return null;
    }
}