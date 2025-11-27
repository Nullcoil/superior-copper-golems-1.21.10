package net.nullcoil.scg.cugo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.nullcoil.scg.config.Config;
import net.nullcoil.scg.config.ConfigHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Smart chest-finding brain for Cugo.
 * Wanders, searches for copper chests, and picks up items.
 */
public class CugoBrain implements CugoBehavior {
    // Brain states
    private enum State {
        WANDERING,
        SEARCHING_FOR_CHEST,
        GOING_TO_CHEST,
        INTERACTING_WITH_CHEST
    }

    private State currentState = State.WANDERING;
    private int wanderTicksRemaining = 0;
    private Vec3 wanderTarget = null;

    // Chest tracking
    private BlockPos targetChestPos = null;
    private int interactionTicks = 0;
    private boolean hasRetrievedItem = false;
    private ItemStack pendingStack = null;

    @Override
    public void tick(CopperGolem golem, @NotNull ServerLevel level) {
        // Debug: Print state every 100 ticks
        if (level.getGameTime() % 100 == 0) {
            System.out.println("[CugoBrain] State: " + currentState + " | Position: " + golem.blockPosition());
        }

        switch (currentState) {
            case WANDERING -> tickWandering(golem, level);
            case SEARCHING_FOR_CHEST -> tickSearching(golem, level);
            case GOING_TO_CHEST -> tickGoingToChest(golem, level);
            case INTERACTING_WITH_CHEST -> tickInteracting(golem, level);
        }
    }

    private void tickWandering(@NotNull CopperGolem golem, ServerLevel level) {
        // If the golem is holding an item, skip wandering and start searching for a chest
        if (golem.hasItemInSlot(InteractionHand.MAIN_HAND.asEquipmentSlot())) {
            System.out.println("[CugoBrain] Holding an item, skipping wander, searching for chest to place it in");
            currentState = State.SEARCHING_FOR_CHEST;
            return;
        }

        golem.setState(CopperGolemState.IDLE);

        if (wanderTicksRemaining > 0) {
            wanderTicksRemaining--;

            // Check if we need a new wander target
            if (wanderTarget != null) {
                double distanceToTarget = golem.position().distanceTo(wanderTarget);
                if (distanceToTarget < 2.0 || golem.getNavigation().isDone()) {
                    findNewWanderTarget(golem);
                }
            } else {
                findNewWanderTarget(golem);
            }
        } else {
            // Wander time is up, search for chest
            System.out.println("[CugoBrain] Wander time ended, searching for copper chest");
            currentState = State.SEARCHING_FOR_CHEST;
        }
    }

    private void tickSearching(@NotNull CopperGolem golem, ServerLevel level) {
        Config config = getConfig();
        int horizontalRange = config.horizontalRange;
        int verticalRange = config.verticalRange;

        BlockPos golemPos = golem.blockPosition();
        BlockPos foundChest = null;
        double closestDistance = Double.MAX_VALUE;

        // Search in a box around the golem
        for (int x = -horizontalRange; x <= horizontalRange; x++) {
            for (int y = -verticalRange; y <= verticalRange; y++) {
                for (int z = -horizontalRange; z <= horizontalRange; z++) {
                    BlockPos checkPos = golemPos.offset(x, y, z);

                    // Check if golem has item
                    if(!golem.hasItemInSlot(InteractionHand.MAIN_HAND.asEquipmentSlot())) {
                        // Check if this is a copper chest
                        if (level.getBlockState(checkPos).is(BlockTags.COPPER_CHESTS)) {
                            double distance = golemPos.distSqr(checkPos);
                            if (distance < closestDistance) {
                                closestDistance = distance;
                                foundChest = checkPos;
                            }
                        }
                    } else {
                        if (level.getBlockState(checkPos).is(Blocks.CHEST)) {
                            double distance = golemPos.distSqr(checkPos);
                            if (distance < closestDistance) {
                                closestDistance = distance;
                                foundChest = checkPos;
                            }
                        }
                    }
                }
            }
        }

        if (foundChest != null) {
            System.out.println("[CugoBrain] Found chest at: " + foundChest);
            targetChestPos = foundChest;
            currentState = State.GOING_TO_CHEST;
            golem.getNavigation().moveTo(foundChest.getX(), foundChest.getY(), foundChest.getZ(), 1.0);
        } else {
            System.out.println("[CugoBrain] No chest found, continuing to wander");
            startWandering(golem);
        }
    }

    private void tickGoingToChest(CopperGolem golem, ServerLevel level) {
        if (targetChestPos == null) {
            startWandering(golem);
            return;
        }

        // Check if chest still exists
        if (!golem.hasItemInSlot(InteractionHand.MAIN_HAND.asEquipmentSlot()) && !level.getBlockState(targetChestPos).is(BlockTags.COPPER_CHESTS)) {
            System.out.println("[CugoBrain] Target copper chest disappeared!");
            targetChestPos = null;
            startWandering(golem);
            return;
        }
        if (golem.hasItemInSlot(InteractionHand.MAIN_HAND.asEquipmentSlot()) && !level.getBlockState(targetChestPos).is(Blocks.CHEST)) {
            System.out.println("[CugoBrain] Target chest disappeared!");
            targetChestPos = null;
            startWandering(golem);
            return;
        }

        // Check if we've arrived
        double distance = golem.blockPosition().distSqr(targetChestPos);
        if (distance <= 4.0) { // Within 2 blocks
            System.out.println("[CugoBrain] Arrived at chest, starting interaction");
            currentState = State.INTERACTING_WITH_CHEST;
            interactionTicks = 0;
            hasRetrievedItem = false;
            pendingStack = null;
            golem.getNavigation().stop();
        } else if (golem.getNavigation().isDone()) {
            // Can't reach chest
            System.out.println("[CugoBrain] Can't reach chest, giving up");
            targetChestPos = null;
            startWandering(golem);
        }
    }

    private void tickInteracting(CopperGolem golem, ServerLevel level) {
        if (targetChestPos == null) {
            startWandering(golem);
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(targetChestPos);
        if (!(blockEntity instanceof Container container)) {
            System.out.println("[CugoBrain] Chest is not a container!");
            targetChestPos = null;
            startWandering(golem);
            return;
        }

        interactionTicks++;

        boolean holdingItem = !golem.getMainHandItem().isEmpty();
        boolean animationStarted = interactionTicks == 1;
        boolean interactionComplete = interactionTicks >= getConfig().interactionTime * 20;

        if (animationStarted) {
            container.startOpen(golem);
            golem.setOpenedChestPos(targetChestPos);

            if (!holdingItem) {
                // Taking items from copper chest
                pendingStack = peekItem(golem, container);
                if (pendingStack != null) {
                    golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_GET);
                    golem.setState(CopperGolemState.GETTING_ITEM);
                    System.out.println("[CugoBrain] Opening chest... Item found: " + pendingStack.getCount() + "x " + pendingStack.getItem());
                } else {
                    golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_NO_GET);
                    golem.setState(CopperGolemState.GETTING_NO_ITEM);
                    System.out.println("[CugoBrain] Opening chest... No items found");
                }
            } else {
                // Placing items into wooden chest
                boolean itemExists = searchForItem(golem.getMainHandItem().getItem(), golem, container);
                if (itemExists) {
                    golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_DROP);
                    golem.setState(CopperGolemState.DROPPING_ITEM);
                    System.out.println("[CugoBrain] Dropping item into chest: " + golem.getMainHandItem());
                } else {
                    golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_NO_DROP);
                    golem.setState(CopperGolemState.DROPPING_NO_ITEM);
                    System.out.println("[CugoBrain] Can't drop item, chest has no matching stack");
                }
            }
        }

        if (!holdingItem && !hasRetrievedItem && interactionTicks == getConfig().interactionTime * 20) {
            // Take item from copper chest
            if (pendingStack != null) {
                ItemStack taken = extractItem(golem, container);
                if (taken != null) {
                    golem.setItemInHand(InteractionHand.MAIN_HAND, taken);
                    container.setChanged();
                    System.out.println("[CugoBrain] Retrieved " + taken.getCount() + "x " + taken.getItem());
                }
            }
            hasRetrievedItem = true;
        }

        if (holdingItem && interactionTicks == getConfig().interactionTime * 20) {
            // Place item into wooden chest if allowed
            if (searchForItem(golem.getMainHandItem().getItem(), golem, container)) {
                placeOrMergeItem(golem, container);
            }
            // If item not present, we just fail silently (animation still plays)
        }

        if (interactionComplete) {
            container.stopOpen(golem);
            golem.clearOpenedChestPos();
            targetChestPos = null;
            if(hasRetrievedItem) currentState = State.SEARCHING_FOR_CHEST;
            startWandering(golem);
        }
    }

    private void placeOrMergeItem(CopperGolem golem, Container container) {
        ItemStack held = golem.getItemBySlot(InteractionHand.MAIN_HAND.asEquipmentSlot());
        if(held.isEmpty()) return;

        int maxStackSize = ConfigHandler.getConfig().maxHeldItemStackSize;

        for(int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stackInChest = container.getItem(i);

            if(!stackInChest.isEmpty() && stackInChest.getItem() == held.getItem()) {
                int spaceLeft = stackInChest.getMaxStackSize() - stackInChest.getCount();
                if(spaceLeft > 0) {
                    int transferAmount = Math.min(spaceLeft, held.getCount());

                    stackInChest.grow(transferAmount);
                    held.shrink(transferAmount);

                    container.setItem(i, stackInChest);
                    container.setChanged();
                    if(held.isEmpty()) {
                        golem.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        return;
                    }
                }
            }
        }

        if (!held.isEmpty()) {
            for(int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stackInChest = container.getItem(i);

                if(stackInChest.isEmpty()) {
                    container.setItem(i, held.copy());
                    golem.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    container.setChanged();
                    return;
                }
            }
        }
    }

    private @Nullable ItemStack peekItem(@NotNull CopperGolem golem, Container container) {
        Config config = getConfig();
        int maxStackSize = config.maxHeldItemStackSize;

        // Check if golem already has items
        if (!golem.getMainHandItem().isEmpty()) {
            return null;
        }

        // Find first non-empty slot and return what we WOULD take (without modifying)
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stackInChest = container.getItem(i);

            if (!stackInChest.isEmpty()) {
                // Calculate how much we'll take
                int amountToTake = Math.min(stackInChest.getCount(), maxStackSize);

                // Return a copy of what we'll take (don't modify the container yet)
                ItemStack preview = stackInChest.copy();
                preview.setCount(amountToTake);

                return preview;
            }
        }

        return null;
    }

    private @Nullable ItemStack extractItem(@NotNull CopperGolem golem, Container container) {
        Config config = getConfig();
        int maxStackSize = config.maxHeldItemStackSize;

        // Check if golem already has items
        if (!golem.getMainHandItem().isEmpty()) {
            return null;
        }

        // Find first non-empty slot and actually extract items
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stackInChest = container.getItem(i);

            if (!stackInChest.isEmpty()) {
                // Calculate how much to take
                int amountToTake = Math.min(stackInChest.getCount(), maxStackSize);

                // Create what we're taking
                ItemStack takenStack = stackInChest.copy();
                takenStack.setCount(amountToTake);

                // Remove from the container
                stackInChest.shrink(amountToTake);
                container.setItem(i, stackInChest);

                return takenStack;
            }
        }

        return null;
    }

    private boolean searchForItem(Item item, CopperGolem golem, Container container) {
        if (golem.getMainHandItem().isEmpty()) return false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stackInChest = container.getItem(i);

            if (stackInChest.getItem() == item) {
                System.out.println("Item found in chest: " + item);
                return true;
            }
        }
        System.out.println("No similar items found in chest");
        return false;
    }

    private void startWandering(CopperGolem golem) {
        currentState = State.WANDERING;
        wanderTicksRemaining = getWanderDuration() * 20;
        findNewWanderTarget(golem);
        golem.setState(CopperGolemState.IDLE);
    }

    private void findNewWanderTarget(CopperGolem golem) {
        Vec3 randomPos = DefaultRandomPos.getPos(golem, 10, 7);

        if (randomPos != null) {
            wanderTarget = randomPos;
            golem.getNavigation().moveTo(randomPos.x, randomPos.y, randomPos.z, 1.0);
        } else {
            golem.getNavigation().stop();
            wanderTarget = null;
        }
    }

    private @NotNull Config getConfig() {
        Config config = ConfigHandler.getConfig();
        return config != null ? config : new Config();
    }

    private int getWanderDuration() { return getConfig().wanderDuration; }

    public void onAttach(@NotNull CopperGolem golem) {
        System.out.println("[CugoBrain] Brain attached to golem: " + golem.getId());
        startWandering(golem);
    }

    public void onDetach(@NotNull CopperGolem golem) {
        golem.getNavigation().stop();
        if (targetChestPos != null) {
            BlockEntity blockEntity = golem.level().getBlockEntity(targetChestPos);
            if (blockEntity instanceof Container container) {
                container.stopOpen(golem);
            }
            golem.clearOpenedChestPos();
        }
    }
}