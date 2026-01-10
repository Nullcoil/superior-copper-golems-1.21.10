package net.nullcoil.scg.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState; // The vanilla enum
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.nullcoil.scg.cugo.managers.StateMachine;
import net.nullcoil.scg.util.CugoAnimationAccessor;
import net.nullcoil.scg.util.CugoHomeAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolem.class)
public abstract class Cugo_EntityDataMixin extends Entity implements CugoHomeAccessor, CugoAnimationAccessor {

    // Shadow the existing vanilla method
    @Shadow public abstract void setState(CopperGolemState copperGolemState);
    @Shadow public abstract CopperGolemState getState();

    // --- HOME POSITION DATA (Keep this, vanilla doesn't have it) ---
    @Unique
    private BlockPos scg$homePos;

    public Cugo_EntityDataMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    // ==========================================
    //    ANIMATION MAPPING (The Fix)
    // ==========================================

    @Override
    public void scg$setInteractState(StateMachine.Interact state) {
        if (state == null) {
            this.setState(CopperGolemState.IDLE);
            return;
        }

        // Map your Cugo Enums to the Vanilla CopperGolemState Enums
        switch (state) {
            case GET -> this.setState(CopperGolemState.GETTING_ITEM);
            case NOGET -> this.setState(CopperGolemState.GETTING_NO_ITEM);
            case DROP -> this.setState(CopperGolemState.DROPPING_ITEM);
            case NODROP -> this.setState(CopperGolemState.DROPPING_NO_ITEM);
            default -> this.setState(CopperGolemState.IDLE);
        }
    }

    @Override
    public StateMachine.Interact scg$getInteractState() {
        CopperGolemState vanillaState = this.getState();
        return switch (vanillaState) {
            case GETTING_ITEM -> StateMachine.Interact.GET;
            case GETTING_NO_ITEM -> StateMachine.Interact.NOGET;
            case DROPPING_ITEM -> StateMachine.Interact.DROP;
            case DROPPING_NO_ITEM -> StateMachine.Interact.NODROP;
            default -> null; // Idle or other states
        };
    }

    // ==========================================
    //    HOME POSITION IMPL (Keep existing)
    // ==========================================

    @Override
    public BlockPos scg$getHomePos() {
        return this.scg$homePos;
    }

    @Override
    public void scg$setHomePos(BlockPos pos) {
        this.scg$homePos = pos;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void scg$saveHome(ValueOutput valueOutput, CallbackInfo ci) {
        if (this.scg$homePos != null) {
            valueOutput.store("cugo_home", BlockPos.CODEC, this.scg$homePos);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void scg$loadHome(ValueInput valueInput, CallbackInfo ci) {
        this.scg$homePos = valueInput.read("cugo_home", BlockPos.CODEC).orElse(null);
    }
}