package net.nullcoil.cugo.util;

import net.nullcoil.cugo.brain.managers.StateMachine;

public interface CugoAnimationAccessor {
    void scg$setInteractState(StateMachine.Interact state);
    StateMachine.Interact scg$getInteractState();
}