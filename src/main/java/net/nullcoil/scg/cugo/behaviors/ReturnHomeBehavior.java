package net.nullcoil.scg.cugo.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.level.block.state.BlockState;
import net.nullcoil.scg.util.CugoHomeAccessor;
import net.nullcoil.scg.util.ModTags;

public class ReturnHomeBehavior implements Behavior {

    @Override
    public boolean run(CopperGolem golem) {
        // 1. Check if we are holding items. If we are, we probably shouldn't go home yet (we might need to sort them).
        if (!golem.getMainHandItem().isEmpty()) {
            return false;
        }

        // 2. Get Home Position securely
        CugoHomeAccessor accessor = (CugoHomeAccessor) golem;
        BlockPos homePos = accessor.scg$getHomePos();

        // If we have no home, we can't go there.
        if (homePos == null) {
            return false;
        }

        // 3. Validate Home Exists
        // If the block at homePos is NOT a valid copper chest, we must forget it.
        BlockState state = golem.level().getBlockState(homePos);
        if (!state.is(ModTags.Blocks.CUGO_CONTAINER_INPUTS)) {
            System.out.println("Cugo Home: Chest at " + homePos + " is missing/broken. Forgetting home.");
            accessor.scg$setHomePos(null);
            return false;
        }

        // 4. Are we already there? (Within roughly 2 blocks)
        if (golem.blockPosition().distSqr(homePos) <= 4.0D) {
            return false; // We are home, no need to pathfind.
        }

        // 5. Go Home
        System.out.println("Cugo Decision: Returning to home at " + homePos);
        golem.getNavigation().moveTo(homePos.getX(), homePos.getY(), homePos.getZ(), 1.0D);

        return true;
    }
}