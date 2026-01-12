package net.nullcoil.scg.cugo.managers;

public class StateMachine {
    public enum Behavior {
        IDLE,
        WANDERING,
        WORKING,
        LOBOTOMIZED
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