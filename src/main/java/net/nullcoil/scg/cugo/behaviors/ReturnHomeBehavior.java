package net.nullcoil.scg.cugo.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.nullcoil.scg.config.ConfigHandler;
import net.nullcoil.scg.util.CugoHomeAccessor;
import net.nullcoil.scg.util.ModTags;

public class ReturnHomeBehavior implements Behavior {

    @Override
    public boolean run(CopperGolem golem) {
        if (!golem.getMainHandItem().isEmpty()) return false;

        CugoHomeAccessor accessor = (CugoHomeAccessor) golem;
        BlockPos homePos = accessor.scg$getHomePos();
        if (homePos == null) return false;

        // Validate existence
        BlockState state = golem.level().getBlockState(homePos);
        if (!state.is(ModTags.Blocks.CUGO_CONTAINER_INPUTS)) {
            accessor.scg$setHomePos(null);
            return false;
        }

        // --- NEW LOGIC START ---

        // 1. Are we within Interact Range? If so, STOP. We are done "Returning".
        // The Controller will see we are done and pick the "Interact" behavior next.
        if (isInInteractRange(golem, homePos)) {
            golem.getNavigation().stop();
            return false; // Return false so the Controller keeps looking for the next task (Interaction)
        }

        // 2. Are we standing ON TOP of the chest?
        BlockPos below = golem.blockPosition().below();
        if (below.equals(homePos)) {
            // Step off!
            Vec3 randomStep = DefaultRandomPos.getPosAway(golem, 2, 1, Vec3.atBottomCenterOf(homePos));
            if (randomStep != null) {
                golem.getNavigation().moveTo(randomStep.x, randomStep.y, randomStep.z, 1.0D);
                return true;
            }
        }

        // --- NEW LOGIC END ---

        // Standard Pathing
        golem.getNavigation().moveTo(homePos.getX(), homePos.getY(), homePos.getZ(), 1.0D);
        return true;
    }

    private boolean isInInteractRange(CopperGolem golem, BlockPos target) {
        double hRange = ConfigHandler.getConfig().xzInteractRange;
        double vRange = ConfigHandler.getConfig().yInteractRange;
        double distSqr = golem.blockPosition().distSqr(target);
        double yDiff = Math.abs(golem.getY() - target.getY());
        return yDiff <= vRange && distSqr <= (hRange + 1.5) * (hRange + 1.5);
    }
}