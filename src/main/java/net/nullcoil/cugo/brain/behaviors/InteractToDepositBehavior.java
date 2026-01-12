package net.nullcoil.cugo.brain.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.cugo.config.ConfigHandler;
import net.nullcoil.cugo.brain.CugoBrain;
import net.nullcoil.cugo.brain.managers.NavigationController;
import net.nullcoil.cugo.brain.managers.StateMachine;
import net.nullcoil.cugo.util.CugoAnimationAccessor;
import net.nullcoil.cugo.util.CugoBrainAccessor;
import net.nullcoil.cugo.util.Debug;

public record InteractToDepositBehavior(NavigationController controller) implements Behavior {

    public boolean canInteract(CopperGolem golem) {
        if (golem.getMainHandItem().isEmpty()) return false;
        BlockPos target = controller.getDepositTarget();
        if (target == null) return false;
        return isInInteractRange(golem, target);
    }

    @Override
    public boolean run(CopperGolem golem) {
        if (!canInteract(golem)) {
            // Debug.log("InteractToDeposit: Cannot interact (range/target check failed).");
            return false;
        }

        BlockPos target = controller.getDepositTarget();

        golem.getNavigation().stop();
        golem.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        CugoAnimationAccessor anim = (CugoAnimationAccessor) golem;
        toggleChestLid(golem.level(), target, true);
        golem.playSound(SoundEvents.CHEST_OPEN, 0.5f, 1.0f);

        updateChestMemory(golem, target);

        // Logic check: Can we deposit?
        boolean success = tryDeposit(golem, target);

        if (success) {
            Debug.log("InteractToDeposit: Deposit verified valid. Animating DROP.");
            anim.scg$setInteractState(StateMachine.Interact.DROP);
            golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_DROP, 1.0f, 1.0f);
        } else {
            Debug.log("InteractToDeposit: Deposit rejected (Contaminated or Full). Animating NODROP.");
            anim.scg$setInteractState(StateMachine.Interact.NODROP);
            golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_NO_DROP, 1.0f, 1.0f);
            controller.markDepositFailed();
        }

        return true;
    }

    // ... (isInInteractRange / Helpers same as before) ...
    private boolean isInInteractRange(CopperGolem golem, BlockPos target) {
        double hRange = ConfigHandler.getConfig().xzInteractRange;
        double vRange = ConfigHandler.getConfig().yInteractRange;
        double xDiff = Math.abs(golem.getX() - (target.getX() + 0.5));
        if (xDiff > (hRange + 0.5)) return false;
        double zDiff = Math.abs(golem.getZ() - (target.getZ() + 0.5));
        if (zDiff > (hRange + 0.5)) return false;
        double yDiff = Math.abs(golem.getY() - (target.getY() + 0.5));
        if (yDiff > (vRange + 0.5)) return false;
        return true;
    }

    private boolean tryDeposit(CopperGolem golem, BlockPos pos) {
        Level level = golem.level();
        Container container = getContainer(level, pos);
        if (container == null) {
            Debug.log("InteractToDeposit: Target is not a container.");
            return false;
        }
        ItemStack toDeposit = golem.getMainHandItem();

        // 1. Purity Check
        boolean chestIsEmpty = true;
        boolean chestContainsMatch = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                chestIsEmpty = false;
                if (ItemStack.isSameItemSameComponents(stack, toDeposit)) chestContainsMatch = true;
            }
        }
        if (!chestIsEmpty && !chestContainsMatch) {
            Debug.log("InteractToDeposit: Contamination detected. Chest has items, but none match held item.");
            return false; // Contaminated
        }

        // 2. Capacity Check
        // Do we have ANY space? (Matching stack with room OR empty slot)
        boolean hasSpace = false;

        // Check for merge space
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slotStack = container.getItem(i);
            if (ItemStack.isSameItemSameComponents(slotStack, toDeposit) && slotStack.getCount() < slotStack.getMaxStackSize()) {
                hasSpace = true;
                break;
            }
        }
        // Check for empty slot
        if (!hasSpace) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).isEmpty()) {
                    hasSpace = true;
                    break;
                }
            }
        }

        if (hasSpace) {
            // Success! The controller will handle the details of moving the items.
            controller.scheduleDeposit(pos);
            return true;
        }

        Debug.log("InteractToDeposit: Chest is full.");
        return false;
    }

    private void updateChestMemory(CopperGolem golem, BlockPos pos) {
        Level level = golem.level();
        Container container = getContainer(level, pos);
        if (container == null) return;
        NonNullList<ItemStack> contents = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < container.getContainerSize(); i++) contents.set(i, container.getItem(i).copy());
        CugoBrainAccessor brain = (CugoBrainAccessor) golem;
        ((CugoBrain) brain.scg$getBrain()).getMemoryManager().updateMemory(pos, contents);
    }

    private void toggleChestLid(Level level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        level.blockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
    }

    private Container getContainer(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chestBe && state.getBlock() instanceof ChestBlock chestBlock) {
            return ChestBlock.getContainer(chestBlock, state, level, pos, true);
        }
        if (be instanceof Container c) return c;
        return null;
    }
}