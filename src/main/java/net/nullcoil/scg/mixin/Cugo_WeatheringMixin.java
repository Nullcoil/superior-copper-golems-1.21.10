package net.nullcoil.scg.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.nullcoil.scg.util.CugoWeatheringAccessor;
import net.nullcoil.scg.util.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CopperGolem.class)
public abstract class Cugo_WeatheringMixin extends Entity implements CugoWeatheringAccessor {

    @Shadow public abstract WeatheringCopper.WeatherState getWeatherState();
    @Shadow public abstract void setWeatherState(WeatheringCopper.WeatherState state);

    @Unique
    private boolean isDying = false;
    @Unique
    private int shutdownTimer = 0;

    public Cugo_WeatheringMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    // --- CRITICAL FIX START ---
    // The CopperGolem class has a private method `canTurnToStatue(Level level)`
    // which returns true if RNG hits. We Inject here to FORCE it to return FALSE.
    // This effectively lobotomizes the vanilla statue conversion logic.
    @Inject(method = "canTurnToStatue", at = @At("HEAD"), cancellable = true)
    private void scg$disableVanillaStatueRNG(Level level, CallbackInfoReturnable<Boolean> cir) {
        // We take full control. Disable vanilla RNG statuing.
        cir.setReturnValue(false);
    }
    // --- CRITICAL FIX END ---

    @Inject(method = "tick", at = @At("TAIL"))
    private void scg$tickWeathering(CallbackInfo ci) {
        if (this.level().isClientSide()) return;

        // --- 1. SHUTDOWN SEQUENCE ---
        if (isDying) {
            handleShutdownSequence();
            return;
        }

        if (scg$isWaxed()) return;

        // --- 2. CUSTOM OXIDATION LOGIC ---
        // If Oxidized, we stop here. We don't run our accelerator.
        // The vanilla logic will still run (slowly), but `canTurnToStatue` forced to false prevents death.
        if (getWeatherState() == WeatheringCopper.WeatherState.OXIDIZED) {
            return;
        }

        // --- 3. ACCELERATED OXIDATION ---
        if (this.tickCount % 20 == 0) {
            if (this.random.nextFloat() < 0.000833f) {
                Debug.log("Natural Oxidation Roll Passed! Advancing Stage from " + getWeatherState());
                scg$advanceStage();
            }
        }
    }

    @Unique
    private void handleShutdownSequence() {
        shutdownTimer++;

        CopperGolem self = (CopperGolem) (Object) this;
        self.getNavigation().stop();

        // Effects (Creaking)
        if (shutdownTimer % 20 == 0) {
            this.playSound(SoundEvents.COPPER_GOLEM_STEP, 1.0f, 0.5f);
            for(int i=0; i<5; i++) {
                this.level().addParticle(ParticleTypes.SCRAPE, this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5), 0, 0, 0);
            }
        }

        // Freeze
        if (shutdownTimer > 100) {
            Debug.log("System Halted. Converting to Statue.");
            scg$convertToStatue(false);
        }
    }

    @Override
    public void scg$startShutdown() {
        if (!this.isDying) {
            Debug.log("Shutdown Sequence Triggered via Accessor.");
            this.isDying = true;
            this.shutdownTimer = 0;
        }
    }

    // --- NBT SAVING ---
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void scg$saveWeathering(ValueOutput valueOutput, CallbackInfo ci) {
        valueOutput.putBoolean("IsDying", this.isDying);
        valueOutput.putInt("ShutdownTimer", this.shutdownTimer);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void scg$loadWeathering(ValueInput valueInput, CallbackInfo ci) {
        this.isDying = valueInput.getBooleanOr("IsDying", false);
        this.shutdownTimer = valueInput.getIntOr("ShutdownTimer", 0);
    }

    // --- LOBOTOMY ---
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void scg$onInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.COPPER_NUGGET)) {
            if (stack.getHoverName().getString().equalsIgnoreCase("Lobotomize")) {
                Debug.log("Lobotomy Interaction Validated. Manually converting to statue.");
                if (!this.level().isClientSide()) {
                    scg$convertToStatue(true);
                    if (!player.isCreative()) stack.shrink(1);
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }

    // --- ACCESSORS / HELPERS ---
    @Unique
    private void scg$advanceStage() {
        WeatheringCopper.WeatherState current = getWeatherState();
        switch (current) {
            case UNAFFECTED -> setWeatherState(WeatheringCopper.WeatherState.EXPOSED);
            case EXPOSED -> setWeatherState(WeatheringCopper.WeatherState.WEATHERED);
            case WEATHERED -> setWeatherState(WeatheringCopper.WeatherState.OXIDIZED);
        }
    }

    @Override
    public WeatheringCopper.WeatherState scg$getWeatherState() { return getWeatherState(); }
    @Override
    public void scg$setWeatherState(WeatheringCopper.WeatherState state) { setWeatherState(state); }
    @Override
    public WeatheringCopper.WeatherState scg$getPreviousWeatherState() {
        WeatheringCopper.WeatherState current = this.getWeatherState();
        return switch (current) {
            case UNAFFECTED -> WeatheringCopper.WeatherState.UNAFFECTED;
            case EXPOSED -> WeatheringCopper.WeatherState.UNAFFECTED;
            case WEATHERED -> WeatheringCopper.WeatherState.EXPOSED;
            case OXIDIZED -> WeatheringCopper.WeatherState.WEATHERED;
        };
    }

    @Unique
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> IS_WAXED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(CopperGolem.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void scg$defineWaxData(net.minecraft.network.syncher.SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(IS_WAXED, false);
    }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void scg$saveWax(net.minecraft.world.level.storage.ValueOutput output, CallbackInfo ci) {
        output.putBoolean("Waxed", this.entityData.get(IS_WAXED));
    }
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void scg$loadWax(net.minecraft.world.level.storage.ValueInput input, CallbackInfo ci) {
        this.entityData.set(IS_WAXED, input.getBooleanOr("Waxed", false));
    }
    @Override public boolean scg$isWaxed() { return this.entityData.get(IS_WAXED); }
    @Override public void scg$setWaxed(boolean waxed) { this.entityData.set(IS_WAXED, waxed); }

    @Override
    public void scg$convertToStatue(boolean randomizePose) {
        if (this.isRemoved()) return;
        BlockPos pos = this.blockPosition();
        if (!this.level().getBlockState(pos).canBeReplaced()) pos = pos.above();
        Block statueBlock = scg$getStatueBlock();
        if (statueBlock == null) return;
        BlockState state = statueBlock.defaultBlockState().setValue(CopperGolemStatueBlock.FACING, this.getDirection());
        if (randomizePose) {
            CopperGolemStatueBlock.Pose[] poses = CopperGolemStatueBlock.Pose.values();
            state = state.setValue(CopperGolemStatueBlock.POSE, poses[this.random.nextInt(poses.length)]);
        }
        this.level().setBlock(pos, state, 3);
        this.level().gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this, state));
        this.level().playSound(null, pos, SoundEvents.COPPER_GOLEM_BECOME_STATUE, this.getSoundSource(), 1.0f, 1.0f);
        this.discard();
    }

    @Unique
    private Block scg$getStatueBlock() {
        WeatheringCopper.WeatherState state = getWeatherState();
        boolean waxed = scg$isWaxed();
        if (waxed) {
            return switch (state) {
                case UNAFFECTED -> Blocks.WAXED_COPPER_GOLEM_STATUE;
                case EXPOSED -> Blocks.WAXED_EXPOSED_COPPER_GOLEM_STATUE;
                case WEATHERED -> Blocks.WAXED_WEATHERED_COPPER_GOLEM_STATUE;
                case OXIDIZED -> Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE;
            };
        } else {
            return switch (state) {
                case UNAFFECTED -> Blocks.COPPER_GOLEM_STATUE;
                case EXPOSED -> Blocks.EXPOSED_COPPER_GOLEM_STATUE;
                case WEATHERED -> Blocks.WEATHERED_COPPER_GOLEM_STATUE;
                case OXIDIZED -> Blocks.OXIDIZED_COPPER_GOLEM_STATUE;
            };
        }
    }
}