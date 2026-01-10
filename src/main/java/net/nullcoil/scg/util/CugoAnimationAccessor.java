package net.nullcoil.scg.util;

import net.nullcoil.scg.cugo.managers.StateMachine;

public interface CugoAnimationAccessor {
    void scg$setInteractState(StateMachine.Interact state);
    StateMachine.Interact scg$getInteractState();
}