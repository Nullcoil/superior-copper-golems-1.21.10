package net.nullcoil.scg.cugo.managers;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.nullcoil.scg.config.ConfigHandler;
import net.nullcoil.scg.cugo.behaviors.RandomWanderBehavior;
import net.nullcoil.scg.cugo.behaviors.ReturnHomeBehavior;

public class NavigationController {

    private final CopperGolem golem;
    private int cooldownTimer = 0;

    // Instantiate behaviors
    private final RandomWanderBehavior wanderBehavior = new RandomWanderBehavior();
    private final ReturnHomeBehavior homeBehavior = new ReturnHomeBehavior();

    public NavigationController(CopperGolem golem) {
        this.golem = golem;
    }

    public void tick() {
        // 1. If we are in a "Wait State" (Lingering), decrement timer.
        if (cooldownTimer > 0) {
            cooldownTimer--;
            return;
        }

        // 2. If we are currently walking, do nothing (let him walk).
        PathNavigation nav = golem.getNavigation();
        if (!nav.isDone()) {
            return;
        }

        // 3. We are idle (Arrived or Woke up). Decide what to do next.
        decideNextMove();
    }

    private void decideNextMove() {
        ItemStack heldItem = golem.getMainHandItem();
        boolean success = false;

        // PRIORITY 1: Go Home (if empty handed)
        if (heldItem.isEmpty()) {
            success = homeBehavior.run(golem);

            // PRIORITY 2: If not going home, Wander
            if (!success) {
                success = wanderBehavior.run(golem);
            }
        } else {
            // Holding item -> Wander (Placeholder for future work logic)
            success = wanderBehavior.run(golem);
        }

        if (success) {
            handleSuccessCooldown();
        } else {
            // Retry quickly if everything failed (stuck/technical error)
            this.cooldownTimer = 20;
        }
    }

    /**
     * INTELLIGENT COOLDOWN LOGIC:
     * Checks if the behavior started a movement or decided to rest.
     */
    private void handleSuccessCooldown() {
        // Check if the Golem is actually moving
        if (!golem.getNavigation().isDone()) {
            // CASE A: He is WALKING.
            // We do NOT want a long cooldown. We want to check again immediately
            // after he arrives at his destination.
            // We set a tiny delay (5 ticks) just to prevent logic spam,
            // but effectively this makes him "Chain" movements.
            this.cooldownTimer = 5;
        } else {
            // CASE B: He is STATIONARY (Lingering).
            // The behavior returned true, but he isn't moving.
            // This means he decided to take a break. Apply the full wait duration.
            this.cooldownTimer = ConfigHandler.getConfig().wanderDuration * 20;
        }
    }
}