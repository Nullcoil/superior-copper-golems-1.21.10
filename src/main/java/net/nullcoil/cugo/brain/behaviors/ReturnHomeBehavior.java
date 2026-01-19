package net.nullcoil.cugo.brain.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.nullcoil.cugo.config.ConfigHandler;
import net.nullcoil.cugo.util.CugoHomeAccessor;
import net.nullcoil.cugo.util.Debug;
import net.nullcoil.cugo.util.ModTags;

public class ReturnHomeBehavior implements Behavior {

    @Override
    public boolean run(CopperGolem golem) {
        if (!golem.getMainHandItem().isEmpty()) return false;

        CugoHomeAccessor accessor = (CugoHomeAccessor) golem;
        BlockPos homePos = accessor.cugo$getHomePos();
        if (homePos == null) {
            // Debug.log("ReturnHome: No home assigned.");
            return false;
        }

        BlockState state = golem.level().getBlockState(homePos);
        if (!state.is(ModTags.Blocks.CUGO_CONTAINER_INPUTS)) {
            Debug.log("ReturnHome: Home invalid (block removed or changed). Forgetting home.");
            accessor.cugo$setHomePos(null);
            return false;
        }

        // 1. Box Stop Check
        if (isInInteractRange(golem, homePos)) {
            // Debug.log("ReturnHome: In range of home. Stopping.");
            golem.getNavigation().stop();
            return false;
        }

        // 2. Anti-Stuck
        BlockPos below = golem.blockPosition().below();
        if (below.equals(homePos)) {
            Debug.log("ReturnHome: Standing ON TOP of home. Stepping off.");
            Vec3 randomStep = DefaultRandomPos.getPosAway(golem, 2, 1, Vec3.atBottomCenterOf(homePos));
            if (randomStep != null) {
                golem.getNavigation().moveTo(randomStep.x, randomStep.y, randomStep.z, 1.0D);
                return true;
            }
        }

        // 3. Move Logic
        boolean moveSuccess = true;
        if (golem.getNavigation().isDone() ||
                golem.getNavigation().getTargetPos() == null ||
                !golem.getNavigation().getTargetPos().equals(homePos)) {

            // Debug.log("ReturnHome: Moving towards " + homePos);
            moveSuccess = golem.getNavigation().moveTo(homePos.getX(), homePos.getY(), homePos.getZ(), 1.0D);
        }

        return moveSuccess;
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
}