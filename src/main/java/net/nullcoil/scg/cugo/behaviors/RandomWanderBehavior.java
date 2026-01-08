package net.nullcoil.scg.cugo.behaviors;

import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.phys.Vec3;
import net.nullcoil.scg.config.ConfigHandler;

import java.util.Random;

public class RandomWanderBehavior implements Behavior {

    private final Random random = new Random();

    // Tracks how "tired" the golem is.
    // 100.0 means 100% chance to wander.
    private double currentWanderChance = 100.0;
    private int stepIndex = 0; // Tracks which Fibonacci number we are on

    @Override
    public boolean run(CopperGolem golem) {

        // 1. Roll for "Tiredness"
        // We generate a number between 0 and 100. If it's higher than our current chance, we linger.
        double roll = random.nextDouble() * 100.0;

        System.out.println("Cugo Wander Check: Roll " + String.format("%.2f", roll) + " vs Chance " + String.format("%.2f", currentWanderChance));

        if (roll > currentWanderChance || currentWanderChance <= 0) {
            // LINGER (Rest)
            System.out.println("Cugo Decision: decided to linger (tiredness reset).");
            resetTiredness();
            golem.getNavigation().stop();
            return true;
        }

        // 2. If we passed the check, try to Wander
        int range = ConfigHandler.getConfig().horizontalRange;
        int vertical = ConfigHandler.getConfig().verticalRange;

        System.out.println("Cugo Wander: Searching with Range= " + range + ", Vertical=" + vertical);

        Vec3 target = LandRandomPos.getPos(golem, range / 2, vertical);

        if (target != null) {
            System.out.println("Cugo Decision: Wandering to " + target);
            golem.getNavigation().moveTo(target.x, target.y, target.z, 1.0D);

            // Apply Fatigue for next time
            applyFatigue();
            return true;
        }

        // 3. Failed to find path (Technical failure, not a choice)
        System.out.println("Cugo Decision: Failed to find wander target (Technical).");
        return false;
    }

    private void applyFatigue() {
        // Get the Fibonacci penalty for the NEXT step
        // Sequence: 0, 1, 1, 2, 3, 5, 8, 13...
        // Index starts at 1 because index 0 is 0 penalty.
        stepIndex++;
        int penalty = getFibonacci(stepIndex);

        currentWanderChance -= penalty;

        // Clamp to 0 just in case
        if (currentWanderChance < 0) currentWanderChance = 0;

        System.out.println("Cugo Fatigue: Wander Chance dropped to " + currentWanderChance + "% (Penalty: -" + penalty + ")");
    }

    private void resetTiredness() {
        currentWanderChance = 100.0;
        stepIndex = 0;
        System.out.println("Cugo Fatigue: Energy restored to 100%.");
    }

    /**
     * Simple iterative Fibonacci to avoid recursion stack overflow paranoia
     * (though unlikely at these small numbers).
     */
    private int getFibonacci(int n) {
        if (n <= 1) return n;
        int a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            int sum = a + b;
            a = b;
            b = sum;
        }
        return b;
    }
}