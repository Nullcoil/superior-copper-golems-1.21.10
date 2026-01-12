package net.nullcoil.scg.cugo.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.nullcoil.scg.cugo.CugoBrain;
import net.nullcoil.scg.cugo.managers.MemoryManager;
import net.nullcoil.scg.cugo.managers.NavigationController;
import net.nullcoil.scg.util.CugoBrainAccessor;

import java.util.HashSet;
import java.util.Set;

public class DepositItemBehavior implements Behavior {

    private final NavigationController controller;

    public DepositItemBehavior(NavigationController controller) {
        this.controller = controller;
    }

    @Override
    public boolean run(CopperGolem golem) {
        ItemStack held = golem.getMainHandItem();
        if (held.isEmpty()) return false;

        CugoBrainAccessor brainAccessor = (CugoBrainAccessor) golem;
        MemoryManager memory = ((CugoBrain) brainAccessor.scg$getBrain()).getMemoryManager();
        BlockPos currentPos = golem.blockPosition();

        // Local Blacklist for this specific decision cycle
        Set<BlockPos> failedPaths = new HashSet<>();

        // Try up to 5 times to find a reachable chest
        for(int attempts = 0; attempts < 5; attempts++) {

            BlockPos target = null;

            // 1. Strict Merge
            target = memory.findChestWithItem(golem.level(), held, currentPos, failedPaths);

            // 2. Empty Chest
            if (target == null) {
                target = memory.findEmptyChest(golem.level(), currentPos, failedPaths);
            }

            // 3. Explore Unknown
            if (target == null) {
                target = memory.getNearestSeenChest(golem.level(), currentPos, failedPaths);
            }

            // If absolutely nothing found, stop trying
            if (target == null) return false;

            // Try to move
            boolean moveSuccess = true;
            if (golem.getNavigation().isDone() ||
                    golem.getNavigation().getTargetPos() == null ||
                    !golem.getNavigation().getTargetPos().equals(target)) {

                moveSuccess = golem.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0D);
            }

            if (moveSuccess) {
                controller.setDepositTarget(target);
                return true; // Success!
            } else {
                // Failed to reach this specific target. Blacklist it and loop again.
                // System.out.println("Cugo Pathfinding: Failed to reach " + target + ". Retrying next best option...");
                failedPaths.add(target);
            }
        }

        return false;
    }
}