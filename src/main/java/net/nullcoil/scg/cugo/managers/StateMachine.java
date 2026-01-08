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
}