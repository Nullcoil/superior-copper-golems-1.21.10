package net.nullcoil.cugo.brain.managers;

public class StateMachine {
    public enum Behavior {
        DEFAULT,
        SORT,
        UPKEEP
    }

    public enum Interact {
        DROP,
        NODROP,
        GET,
        NOGET
    }

    // NEW: Tracks strictly *why* the golem is moving
    public enum Intent {
        WANDERING,
        RETURNING_HOME,
        DEPOSITING
    }
}