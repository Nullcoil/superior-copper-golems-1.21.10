package net.nullcoil.scg.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.nullcoil.scg.util.CugoHomeAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CopperGolem.class)
public abstract class Cugo_EntityDataMixin extends Entity implements CugoHomeAccessor {

    @Unique
    private BlockPos scg$homePos;

    public Cugo_EntityDataMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    // --- Interface Implementation ---

    @Override
    public BlockPos scg$getHomePos() {
        return this.scg$homePos;
    }

    @Override
    public void scg$setHomePos(BlockPos pos) {
        this.scg$homePos = pos;
    }

    // --- Modern NBT Data Saving/Loading (1.21.5+) ---

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void scg$saveHome(ValueOutput valueOutput, CallbackInfo ci) {
        if (this.scg$homePos != null) {
            // Using the BlockPos CODEC to store the data safely
            valueOutput.store("cugo_home", BlockPos.CODEC, this.scg$homePos);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void scg$loadHome(ValueInput valueInput, CallbackInfo ci) {
        // Read using the BlockPos CODEC. If it fails or is missing, orElse returns null.
        this.scg$homePos = valueInput.read("cugo_home", BlockPos.CODEC).orElse(null);
    }

    // --- Spawn Logic ---

    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void scg$onFirstSpawn(ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficultyInstance, EntitySpawnReason entitySpawnReason, SpawnGroupData spawnGroupData, CallbackInfoReturnable<SpawnGroupData> cir) {
        // If we spawned fresh and don't have a home yet, set it to current position
        if (this.scg$homePos == null) {
            this.scg$homePos = this.blockPosition();
            System.out.println("Cugo Spawn: Home established at " + this.scg$homePos);
        }
    }
}