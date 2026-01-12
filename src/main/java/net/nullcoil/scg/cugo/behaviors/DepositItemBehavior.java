package net.nullcoil.scg.cugo.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.nullcoil.scg.cugo.CugoBrain;
import net.nullcoil.scg.cugo.managers.MemoryManager;
import net.nullcoil.scg.cugo.managers.NavigationController;
import net.nullcoil.scg.util.CugoBrainAccessor;
import net.nullcoil.scg.util.Debug;

import java.util.HashSet;
import java.util.Set;

public record DepositItemBehavior(NavigationController controller) implements Behavior {

    @Override
    public boolean run(CopperGolem golem) {
        ItemStack held = golem.getMainHandItem();
        if (held.isEmpty()) {
            // Debug.log("DepositItem: Hand empty, cannot deposit."); // Too spammy likely
            return false;
        }

        CugoBrainAccessor brainAccessor = (CugoBrainAccessor) golem;
        MemoryManager memory = ((CugoBrain) brainAccessor.scg$getBrain()).getMemoryManager();
        BlockPos currentPos = golem.blockPosition();

        // Local Blacklist for this specific decision cycle
        Set<BlockPos> failedPaths = new HashSet<>();

        // Try up to 5 times to find a reachable chest
        for (int attempts = 0; attempts < 5; attempts++) {

            BlockPos target = null;
            String strategy = "None";

            // 1. Strict Merge
            target = memory.findChestWithItem(golem.level(), held, currentPos, failedPaths);
            if (target != null) strategy = "Merge";

            // 2. Empty Chest
            if (target == null) {
                target = memory.findEmptyChest(golem.level(), currentPos, failedPaths);
                if (target != null) strategy = "EmptyChest";
            }

            // 3. Explore Unknown
            if (target == null) {
                target = memory.getNearestSeenChest(golem.level(), currentPos, failedPaths);
                if (target != null) strategy = "ExploreSeen";
            }

            // If absolutely nothing found, stop trying
            if (target == null) {
                Debug.log("DepositItem: No suitable chest found after " + attempts + " attempts.");
                return false;
            }

            // Try to move
            boolean moveSuccess = true;
            if (golem.getNavigation().isDone() ||
                    golem.getNavigation().getTargetPos() == null ||
                    !golem.getNavigation().getTargetPos().equals(target)) {

                moveSuccess = golem.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0D);
            }

            if (moveSuccess) {
                Debug.log("DepositItem: Moving to " + target + " via [" + strategy + "].");
                controller.setDepositTarget(target);
                return true; // Success!
            } else {
                // Failed to reach this specific target. Blacklist it and loop again.
                Debug.log("DepositItem: Pathfinding failed to " + target + ". Blacklisting and retrying...");
                failedPaths.add(target);
            }
        }

        Debug.log("DepositItem: Failed to move to any valid target.");
        return false;
    }
}