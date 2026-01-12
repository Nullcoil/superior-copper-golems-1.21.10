package net.nullcoil.cugo.brain.managers;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
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
import net.nullcoil.cugo.config.ConfigHandler;
import net.nullcoil.cugo.brain.CugoBrain;
import net.nullcoil.cugo.brain.behaviors.*;
import net.nullcoil.cugo.util.CugoAnimationAccessor;
import net.nullcoil.cugo.util.CugoBrainAccessor;
import net.nullcoil.cugo.util.CugoHomeAccessor;
import net.nullcoil.cugo.util.Debug;

public class NavigationController {

    private final CopperGolem golem;
    private int cooldownTimer = 0;

    // --- STATE ---
    private StateMachine.Intent currentIntent = StateMachine.Intent.WANDERING;
    private boolean isInteracting = false;
    private boolean forceWander = false;

    // Data
    private BlockPos depositTarget = null;
    private BlockPos pendingChestPos = null;
    private int pendingSlotIndex = -1; // Only used for Pickup now

    // Behaviors
    private final RandomWanderBehavior wanderBehavior = new RandomWanderBehavior();
    private final ReturnHomeBehavior homeBehavior = new ReturnHomeBehavior();
    private final InteractWithChestBehavior takeBehavior = new InteractWithChestBehavior(this);
    private final DepositItemBehavior depositMoveBehavior = new DepositItemBehavior(this);
    private final InteractToDepositBehavior depositActBehavior = new InteractToDepositBehavior(this);
    private final SelfPreservationBehavior selfPreservationBehavior = new SelfPreservationBehavior();

    public NavigationController(CopperGolem golem) {
        this.golem = golem;
    }

    public void tick() {
        if (cooldownTimer > 0) {
            cooldownTimer--;

            if (cooldownTimer == 0) {
                if (isInteracting) {
                    Debug.log("NavController: Interaction timer finished. Finalizing state.");
                    if (currentIntent == StateMachine.Intent.DEPOSITING) {
                        finalizeDeposit();
                        closeChest(depositTarget);
                        this.depositTarget = null;
                    } else {
                        finalizeItemPickup();
                        closeHomeChest();
                    }

                    ((CugoAnimationAccessor) golem).scg$setInteractState(null);
                    isInteracting = false;

                    if(golem.getMainHandItem().isEmpty()) {
                        this.currentIntent = StateMachine.Intent.RETURNING_HOME;
                    } else {
                        this.currentIntent = StateMachine.Intent.WANDERING;
                    }
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

    // ... (decideNextMove same as before) ...
    private void decideNextMove() {
        ItemStack heldItem = golem.getMainHandItem();
        boolean success = false;
        boolean startingInteraction = false;

        if (forceWander) {
            Debug.log("NavController: ForceWander active. Forcing RandomWander.");
            if (wanderBehavior.run(golem)) {
                success = true;
                this.currentIntent = StateMachine.Intent.WANDERING;
                forceWander = false;
            } else {
                this.cooldownTimer = 10;
                return;
            }
        }

        if (!success) {
            if(selfPreservationBehavior.run(golem)) {
                Debug.log("Behavior Triggered: Self Preservation. Pausing logic.");
                this.isInteracting = false;
                this.cooldownTimer = 20;
                return;
            }

            if (heldItem.isEmpty()) {
                // TRYING TO GET ITEM
                if (this.currentIntent == StateMachine.Intent.RETURNING_HOME && takeBehavior.canInteract(golem)) {
                    // Debug.log("NavController: Attempting InteractWithChestBehavior (Take)");
                    if (takeBehavior.run(golem)) { success = true; startingInteraction = true; }
                }
                if (!success && homeBehavior.run(golem)) {
                    // Debug.log("NavController: Attempting ReturnHomeBehavior");
                    success = true;
                    this.currentIntent = StateMachine.Intent.RETURNING_HOME;
                }
            } else {
                // TRYING TO DEPOSIT ITEM
                if (this.currentIntent == StateMachine.Intent.DEPOSITING && depositActBehavior.canInteract(golem)) {
                    // Debug.log("NavController: Attempting InteractToDepositBehavior");
                    if (depositActBehavior.run(golem)) { success = true; startingInteraction = true; }
                }
                if (!success && depositMoveBehavior.run(golem)) {
                    // Debug.log("NavController: Attempting DepositItemBehavior (Move)");
                    success = true;
                    this.currentIntent = StateMachine.Intent.DEPOSITING;
                }
            }
            if (!success) {
                // FALLBACK
                // Debug.log("NavController: Fallback to Wander.");
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
                Debug.log("NavController: Interaction started. Pausing for 60 ticks.");
            } else if (!golem.getNavigation().isDone()) {
                this.isInteracting = false;
                this.cooldownTimer = 5;
            } else {
                this.isInteracting = false;
                this.cooldownTimer = ConfigHandler.getConfig().wanderDuration * 20;
                Debug.log("NavController: Idling for " + this.cooldownTimer + " ticks.");
            }
        } else {
            // Debug.log("NavController: All behaviors failed. Waiting 20 ticks.");
            this.isInteracting = false;
            this.cooldownTimer = 20;
        }
    }

    public void setDepositTarget(BlockPos pos) { this.depositTarget = pos; }
    public BlockPos getDepositTarget() { return this.depositTarget; }
    public void markDepositFailed() {
        Debug.log("NavController: Marking Deposit Failed. Force Wandering.");
        this.forceWander = true;
    }
    public void markInteractionFailed() {
        Debug.log("NavController: Marking Interaction Failed. Force Wandering.");
        this.forceWander = true;
    }
    public void schedulePickup(BlockPos pos, int slotIndex) { this.pendingChestPos = pos; this.pendingSlotIndex = slotIndex; }

    // UPDATED: No slot index needed for deposit
    public void scheduleDeposit(BlockPos pos) {
        this.pendingChestPos = pos;
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
                    Debug.log("NavController: Pickup Finalized. Got " + taken);
                }
            }
            pendingChestPos = null;
            pendingSlotIndex = -1;
        }
    }

    // UPDATED: "Smart Dump" Logic
    private void finalizeDeposit() {
        if (pendingChestPos != null) {
            Level level = golem.level();
            Container container = getContainer(level, pendingChestPos);
            ItemStack toDeposit = golem.getMainHandItem();

            if (container != null && !toDeposit.isEmpty()) {

                // PASS 1: MERGE (Fill existing stacks)
                for (int i = 0; i < container.getContainerSize(); i++) {
                    if (toDeposit.isEmpty()) break;

                    ItemStack slotStack = container.getItem(i);
                    if (ItemStack.isSameItemSameComponents(slotStack, toDeposit)) {
                        int space = slotStack.getMaxStackSize() - slotStack.getCount();
                        int moveAmount = Math.min(space, toDeposit.getCount());

                        if (moveAmount > 0) {
                            slotStack.grow(moveAmount);
                            toDeposit.shrink(moveAmount);
                        }
                    }
                }

                // PASS 2: FILL (Use empty slots for leftovers)
                for (int i = 0; i < container.getContainerSize(); i++) {
                    if (toDeposit.isEmpty()) break;

                    if (container.getItem(i).isEmpty()) {
                        container.setItem(i, toDeposit.copy()); // Moves entire remaining stack
                        toDeposit.setCount(0);
                    }
                }

                Debug.log("NavController: Deposit Finalized. Remaining in Hand: " + toDeposit.getCount());

                // Update Hand
                golem.setItemInHand(InteractionHand.MAIN_HAND, toDeposit.isEmpty() ? ItemStack.EMPTY : toDeposit);

                // Update Memory & Close
                updateChestMemory(golem, pendingChestPos);
            }
            pendingChestPos = null;
            pendingSlotIndex = -1;
        }
    }

    // ... (Helpers same as before) ...
    private void updateChestMemory(CopperGolem golem, BlockPos pos) {
        Level level = golem.level();
        Container container = getContainer(level, pos);
        if (container == null) return;
        net.minecraft.core.NonNullList<ItemStack> contents = net.minecraft.core.NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for(int i=0; i<container.getContainerSize(); i++) contents.set(i, container.getItem(i).copy());
        CugoBrainAccessor brain = (CugoBrainAccessor) golem;
        ((CugoBrain)brain.scg$getBrain()).getMemoryManager().updateMemory(pos, contents);
    }
    private void closeHomeChest() { closeChest(((CugoHomeAccessor) golem).scg$getHomePos()); }
    private void closeChest(BlockPos pos) {
        if (pos != null) {
            Level level = golem.level();
            BlockState state = level.getBlockState(pos);
            level.blockEvent(pos, state.getBlock(), 1, 0);
            if(state.is(BlockTags.COPPER_CHESTS)) {
                level.playSound(null,pos,SoundEvents.COPPER_CHEST_CLOSE,SoundSource.BLOCKS, 0.5f, 1.0f);
            } else {
                level.playSound(null,pos,SoundEvents.CHEST_CLOSE,SoundSource.BLOCKS,0.5f,1.0f);
            }
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