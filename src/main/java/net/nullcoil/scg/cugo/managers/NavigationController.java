package net.nullcoil.scg.cugo.managers; // Moved to managers package

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.nullcoil.scg.config.ConfigHandler;
import net.nullcoil.scg.cugo.behaviors.RandomWanderBehavior;

public class NavigationController {

    private final CopperGolem golem;
    private int cooldownTimer = 0;

    // Instantiate behaviors
    private final RandomWanderBehavior wanderBehavior = new RandomWanderBehavior();

    public NavigationController(CopperGolem golem) {
        this.golem = golem;
    }

    public void tick() {
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return;
        }

        PathNavigation nav = golem.getNavigation();
        if (!nav.isDone()) {
            return;
        }

        decideNextMove();
    }

    private void decideNextMove() {
        ItemStack heldItem = golem.getMainHandItem();
        boolean success;

        if (heldItem.isEmpty()) {
            System.out.println("NavManager: Hand empty. Delegating to RandomWanderBehavior.");
            success = wanderBehavior.run(golem);
        } else {
            System.out.println("NavManager: Holding " + heldItem + ". Delegating to RandomWanderBehavior (Placeholder).");
            success = wanderBehavior.run(golem);
        }

        if (success) {
            // Success means we either moved OR decided to linger.
            // Both require waiting the duration.
            this.cooldownTimer = ConfigHandler.getConfig().wanderDuration * 20;
        } else {
            // Retry quickly if failed
            this.cooldownTimer = 20;
        }
    }
}