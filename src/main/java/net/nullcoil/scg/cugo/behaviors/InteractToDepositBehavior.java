package net.nullcoil.scg.cugo.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.scg.config.ConfigHandler;
import net.nullcoil.scg.cugo.managers.NavigationController;
import net.nullcoil.scg.cugo.managers.StateMachine;
import net.nullcoil.scg.util.CugoAnimationAccessor;
import net.nullcoil.scg.util.CugoBrainAccessor;
import net.nullcoil.scg.cugo.CugoBrain;
import net.minecraft.core.NonNullList;

public class InteractToDepositBehavior implements Behavior {

    private final NavigationController controller;

    public InteractToDepositBehavior(NavigationController controller) {
        this.controller = controller;
    }

    public boolean canInteract(CopperGolem golem) {
        if (golem.getMainHandItem().isEmpty()) return false;

        BlockPos target = controller.getDepositTarget();
        if (target == null) return false;

        return isInInteractRange(golem, target);
    }

    @Override
    public boolean run(CopperGolem golem) {
        if (!canInteract(golem)) return false;

        BlockPos target = controller.getDepositTarget();

        golem.getNavigation().stop();
        golem.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        CugoAnimationAccessor anim = (CugoAnimationAccessor) golem;
        toggleChestLid(golem.level(), target, true);
        golem.level().playSound(null, target, SoundEvents.COPPER_CHEST_OPEN, SoundSource.BLOCKS, 0.5f, 1.0f);

        updateChestMemory(golem, target);

        boolean success = tryDeposit(golem, target);

        if (success) {
            anim.scg$setInteractState(StateMachine.Interact.DROP);
            // TRIGGER SOUND: Starts immediately with animation
            golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_DROP, 1.0f, 1.0f);
        } else {
            anim.scg$setInteractState(StateMachine.Interact.NODROP);
            // TRIGGER SOUND: Refusal immediately
            golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_NO_DROP, 1.0f, 1.0f);

            controller.markDepositFailed();
        }

        return true;
    }

    // --- BOX DISTANCE LOGIC ---
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
        // ... (Keep existing Purity Check logic from previous step) ...
        Level level = golem.level();
        Container container = getContainer(level, pos);
        if (container == null) return false;

        ItemStack toDeposit = golem.getMainHandItem();
        boolean chestIsEmpty = true;
        boolean chestContainsMatch = false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                chestIsEmpty = false;
                if (ItemStack.isSameItemSameComponents(stack, toDeposit)) chestContainsMatch = true;
            }
        }

        if (!chestIsEmpty && !chestContainsMatch) return false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slotStack = container.getItem(i);
            if (ItemStack.isSameItemSameComponents(slotStack, toDeposit) && slotStack.getCount() < slotStack.getMaxStackSize()) {
                int space = slotStack.getMaxStackSize() - slotStack.getCount();
                int moveAmount = Math.min(space, toDeposit.getCount());
                if (moveAmount > 0) {
                    controller.scheduleDeposit(pos, i);
                    return true;
                }
            }
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                controller.scheduleDeposit(pos, i);
                return true;
            }
        }
        return false;
    }

    private void updateChestMemory(CopperGolem golem, BlockPos pos) {
        Level level = golem.level();
        Container container = getContainer(level, pos);
        if (container == null) return;
        NonNullList<ItemStack> contents = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for(int i=0; i<container.getContainerSize(); i++) contents.set(i, container.getItem(i).copy());
        CugoBrainAccessor brain = (CugoBrainAccessor) golem;
        ((CugoBrain)brain.scg$getBrain()).getMemoryManager().updateMemory(pos, contents);
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