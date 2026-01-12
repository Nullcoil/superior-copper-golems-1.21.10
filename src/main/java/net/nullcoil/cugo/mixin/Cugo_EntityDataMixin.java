package net.nullcoil.cugo.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.animal.coppergolem.CopperGolemState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.nullcoil.cugo.brain.CugoBrain;
import net.nullcoil.cugo.brain.managers.StateMachine;
import net.nullcoil.cugo.util.CugoAnimationAccessor;
import net.nullcoil.cugo.util.CugoBrainAccessor;
import net.nullcoil.cugo.util.CugoHomeAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CopperGolem.class)
public abstract class Cugo_EntityDataMixin extends Entity implements CugoHomeAccessor, CugoAnimationAccessor, CugoBrainAccessor {

    @Shadow public abstract void setState(CopperGolemState copperGolemState);
    @Shadow public abstract CopperGolemState getState();

    @Unique
    private BlockPos scg$homePos;

    public Cugo_EntityDataMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    // --- ANIMATION MAPPING ---
    @Override
    public void scg$setInteractState(StateMachine.Interact state) {
        if (state == null) {
            this.setState(CopperGolemState.IDLE);
            return;
        }
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
            default -> null;
        };
    }

    // --- HOME & MEMORY SAVE/LOAD ---

    @Override
    public BlockPos scg$getHomePos() {
        return this.scg$homePos;
    }

    @Override
    public void scg$setHomePos(BlockPos pos) {
        this.scg$homePos = pos;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void scg$saveData(ValueOutput valueOutput, CallbackInfo ci) {
        // 1. Save Home Pos
        if (this.scg$homePos != null) {
            valueOutput.store("cugo_home", BlockPos.CODEC, this.scg$homePos);
        }

        // 2. Save Memory
        // We generate a CompoundTag manually via the Brain, then store that tag via CODEC
        CugoBrain brain = (CugoBrain) this.scg$getBrain();
        if (brain != null) {
            CompoundTag memoryTag = brain.createMemoryTag(this.registryAccess());
            // Only store if not empty to save space
            if (!memoryTag.isEmpty()) {
                valueOutput.store("cugo_memory", CompoundTag.CODEC, memoryTag);
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void scg$loadData(ValueInput valueInput, CallbackInfo ci) {
        // 1. Load Home Pos
        this.scg$homePos = valueInput.read("cugo_home", BlockPos.CODEC).orElse(null);

        // 2. Load Memory
        // We read the CompoundTag via CODEC, then pass it to the Brain
        CugoBrain brain = (CugoBrain) this.scg$getBrain();
        if (brain != null) {
            valueInput.read("cugo_memory", CompoundTag.CODEC)
                    .ifPresent(tag -> brain.loadMemoryTag(tag, this.registryAccess()));
        }
    }
}