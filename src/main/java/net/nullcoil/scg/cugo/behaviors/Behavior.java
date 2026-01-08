package net.nullcoil.scg.cugo.behaviors;

import net.minecraft.world.entity.animal.coppergolem.CopperGolem;

public interface Behavior {
    /**
     * Attempt to run this behavior.
     * @return true if the behavior successfully started an action (like moving), false if it failed or couldn't run.
     */
    boolean run(CopperGolem golem);
}