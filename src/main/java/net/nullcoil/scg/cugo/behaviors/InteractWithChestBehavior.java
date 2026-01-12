package net.nullcoil.scg.cugo.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.nullcoil.scg.config.ConfigHandler;
import net.nullcoil.scg.cugo.managers.NavigationController;
import net.nullcoil.scg.cugo.managers.StateMachine;
import net.nullcoil.scg.util.CugoAnimationAccessor;
import net.nullcoil.scg.util.CugoHomeAccessor;

public class InteractWithChestBehavior implements Behavior {

    private final NavigationController controller;

    public InteractWithChestBehavior(NavigationController controller) {
        this.controller = controller;
    }

    public boolean canInteract(CopperGolem golem) {
        if (!golem.getMainHandItem().isEmpty()) return false;

        CugoHomeAccessor homeAccessor = (CugoHomeAccessor) golem;
        BlockPos homePos = homeAccessor.scg$getHomePos();
        if (homePos == null) return false;

        // 1. Box Distance Check
        if (!isInInteractRange(golem, homePos)) return false;

        // 2. Line of Sight
        Vec3 start = golem.getEyePosition();
        Vec3 end = Vec3.atCenterOf(homePos);
        BlockHitResult result = golem.level().clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                golem
        ));
        if (result.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = result.getBlockPos();
            if (!hitPos.equals(homePos)) return false;
        }

        return true;
    }

    @Override
    public boolean run(CopperGolem golem) {
        if (!canInteract(golem)) return false;

        CugoHomeAccessor homeAccessor = (CugoHomeAccessor) golem;
        BlockPos homePos = homeAccessor.scg$getHomePos();

        golem.getNavigation().stop();
        golem.getLookControl().setLookAt(homePos.getX() + 0.5, homePos.getY() + 0.5, homePos.getZ() + 0.5);

        int foundSlot = scanForItems(golem, homePos);
        boolean success = (foundSlot != -1);

        CugoAnimationAccessor anim = (CugoAnimationAccessor) golem;
        toggleChestLid(golem.level(), homePos, true);
        golem.playSound(SoundEvents.CHEST_OPEN, 0.5f, 1.0f);

        if (success) {
            anim.scg$setInteractState(StateMachine.Interact.GET);
            // TRIGGER SOUND: Starts immediately with animation
            golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_GET, 1.0f, 1.0f);

            controller.schedulePickup(homePos, foundSlot);
        } else {
            anim.scg$setInteractState(StateMachine.Interact.NOGET);
            // TRIGGER SOUND: Disappointment immediately
            golem.playSound(SoundEvents.COPPER_GOLEM_ITEM_NO_GET, 1.0f, 1.0f);

            controller.markInteractionFailed();
        }

        return true;
    }

    // --- BOX DISTANCE LOGIC ---
    private boolean isInInteractRange(CopperGolem golem, BlockPos target) {
        double hRange = ConfigHandler.getConfig().xzInteractRange;
        double vRange = ConfigHandler.getConfig().yInteractRange;

        // We check X, Y, and Z differences independently (Axis-Aligned Box)

        // Horizontal (X)
        double xDiff = Math.abs(golem.getX() - (target.getX() + 0.5));
        if (xDiff > (hRange + 0.5)) return false;

        // Horizontal (Z)
        double zDiff = Math.abs(golem.getZ() - (target.getZ() + 0.5));
        if (zDiff > (hRange + 0.5)) return false;

        // Vertical (Y) - Golem feet vs Block center (approx)
        double yDiff = Math.abs(golem.getY() - (target.getY() + 0.5));
        if (yDiff > (vRange + 0.5)) return false;

        return true;
    }

    private int scanForItems(CopperGolem golem, BlockPos pos) {
        Level level = golem.level();
        Container container = getContainer(level, pos);
        if (container == null) return -1;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) return i;
        }
        return -1;
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