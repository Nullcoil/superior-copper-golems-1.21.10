package net.nullcoil.cugo.util;

import net.nullcoil.cugo.brain.managers.StateMachine;

public interface CugoAnimationAccessor {
    void cugo$setInteractState(StateMachine.Interact state);
    StateMachine.Interact cugo$getInteractState();
}