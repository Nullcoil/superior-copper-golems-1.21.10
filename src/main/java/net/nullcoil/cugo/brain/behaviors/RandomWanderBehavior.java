package net.nullcoil.cugo.brain.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.nullcoil.cugo.config.ConfigHandler;
import net.nullcoil.cugo.brain.CugoBrain;
import net.nullcoil.cugo.brain.managers.MemoryManager;
import net.nullcoil.cugo.util.*;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class RandomWanderBehavior implements Behavior {

    private final Random random = new Random();
    private double currentWanderChance = 100.0;
    private int stepIndex = 0;

    @Override
    public boolean run(CopperGolem golem) {
        CugoHomeAccessor homeAccessor = (CugoHomeAccessor) golem;
        CugoWeatheringAccessor weathering = (CugoWeatheringAccessor) golem;
        boolean isUrgent = !golem.getMainHandItem().isEmpty();

        // --- 0. AUTO-HOME CHECK ---
        // If the Golem doesn't have a home, check the block directly below it.
        // This allows it to "claim" a chest immediately upon spawning/recharging
        // without waiting for a random linger scan.
        if (homeAccessor.scg$getHomePos() == null) {
            BlockPos below = golem.blockPosition().below();
            if (golem.level().getBlockState(below).is(ModTags.Blocks.CUGO_CONTAINER_INPUTS)) {
                BlockPos homePos = below.immutable();
                homeAccessor.scg$setHomePos(homePos);
                Debug.log("Wander: Auto-assigned home to container below: " + homePos);
            }
        }

        // OLD BATTERY CHECK: Oxidized AND Unwaxed
        boolean isOldBattery = weathering.scg$getWeatherState() == WeatheringCopper.WeatherState.OXIDIZED && !weathering.scg$isWaxed();

        // --- 1. SCANNING & LINGERING ---
        if (isUrgent) {
            scanSurroundings(golem);
        } else {
            double roll = random.nextDouble() * 100.0;

            // If we roll higher than our current energy, we stop to rest/scan.
            if (roll > currentWanderChance || currentWanderChance <= 0) {

                if (currentWanderChance < 100) {
                    Debug.log("Wander: Lingering. Roll(" + String.format("%.1f", roll) + ") > Battery(" + String.format("%.1f", currentWanderChance) + "%)");
                }

                scanSurroundings(golem);

                // FATIGUE LOGIC
                if (isOldBattery) {
                    // Lingering in Oxidized state: We log, but do NOT call resetTiredness().
                    if (currentWanderChance <= 0) {
                        Debug.log("Critical Failure: Battery Depleted. Initiating Shutdown.");
                        weathering.scg$startShutdown();
                    }
                } else {
                    // Normal Golem: Resting restores energy.
                    if (currentWanderChance < 100) Debug.log("Wander: Rested. Battery recharged.");
                    resetTiredness();
                }

                golem.getNavigation().stop();
                return true; // Successfully "doing nothing"
            }
        }

        // --- 2. PATHFINDING ---
        int range = ConfigHandler.getConfig().horizontalRange;
        int vertical = ConfigHandler.getConfig().verticalRange;
        PathNavigation nav = golem.getNavigation();
        Vec3 target = null;

        target = LandRandomPos.getPos(golem, range / 2, vertical);
        if (target == null) target = DefaultRandomPos.getPos(golem, range / 2, vertical);
        if (target == null) {
            for(int k=0; k<10; k++) {
                BlockPos randomPos = golem.blockPosition().offset(
                        random.nextInt(range)-(range/2),
                        random.nextInt(vertical)-(vertical/2),
                        random.nextInt(range)-(range/2)
                );
                if(golem.level().isEmptyBlock(randomPos) && golem.level().getBlockState(randomPos.below()).isSolid()) {
                    target = Vec3.atBottomCenterOf(randomPos);
                    break;
                }
            }
        }

        // --- 3. EXECUTE MOVE ---
        if (target != null) {
            Path path = nav.createPath(target.x, target.y, target.z, 0);
            if (path != null && path.canReach()) {
                boolean moved = nav.moveTo(path, 1.0D);
                if (moved) {
                    if (!isUrgent) {
                        applyFatigue(weathering);
                    } else {
                        // Work adrenaline: recharges non-oxidized golems instantly
                        if (!isOldBattery) resetTiredness();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void scanSurroundings(CopperGolem golem) {
        ServerLevel level = (ServerLevel) golem.level();
        BlockPos golemPos = golem.blockPosition();
        int range = ConfigHandler.getConfig().horizontalRange;
        int vRange = ConfigHandler.getConfig().verticalRange;
        CugoBrainAccessor brainAccessor = (CugoBrainAccessor) golem;
        CugoHomeAccessor homeAccessor = (CugoHomeAccessor) golem;
        MemoryManager memory = ((CugoBrain) brainAccessor.scg$getBrain()).getMemoryManager();
        boolean needsHome = homeAccessor.scg$getHomePos() == null;
        BlockPos bestHomeCandidate = null;
        double closestHomeDist = Double.MAX_VALUE;
        Set<BlockPos> uniqueChests = new HashSet<>();

        for (BlockPos pos : BlockPos.betweenClosed(golemPos.offset(-range, -vRange, -range), golemPos.offset(range, vRange, range))) {
            BlockState state = level.getBlockState(pos);
            boolean isInput = state.is(ModTags.Blocks.CUGO_CONTAINER_INPUTS);
            boolean isOutput = state.is(ModTags.Blocks.CUGO_CONTAINER_OUTPUTS);

            if (isInput || isOutput) {
                BlockPos canonical = DoubleChestHelper.getCanonicalPos(level, pos, state).immutable();
                if (uniqueChests.contains(canonical)) continue;
                uniqueChests.add(canonical);
                if (canAccess(golemPos, canonical)) {
                    if (needsHome && isInput) {
                        double dist = golemPos.distSqr(canonical);
                        if (dist < closestHomeDist) {
                            closestHomeDist = dist;
                            bestHomeCandidate = canonical;
                        }
                    }
                    if (!memory.hasMemory(canonical)) {
                        Debug.log("Wander: Discovered Container at " + canonical);
                        memory.markAsSeen(canonical);
                        drawParticleLine(level, golem.getEyePosition(), Vec3.atCenterOf(canonical));
                    }
                }
            }
        }
        if (bestHomeCandidate != null) {
            Debug.log("Wander: Home Assigned: " + bestHomeCandidate);
            homeAccessor.scg$setHomePos(bestHomeCandidate);
        }
    }

    private boolean canAccess(BlockPos golemPos, BlockPos chestPos) {
        int yDiff = Math.abs(golemPos.getY() - chestPos.getY());
        return yDiff <= ConfigHandler.getConfig().yInteractRange;
    }

    private void drawParticleLine(ServerLevel level, Vec3 start, Vec3 end) {
        double distance = start.distanceTo(end);
        int density = Math.max(1, ConfigHandler.getConfig().pingLineDensity);
        int points = (int) (distance * density);
        Vec3 direction = end.subtract(start).normalize();
        double stepSize = 1.0 / density;
        for (int i = 0; i < points; i++) {
            Vec3 p = start.add(direction.scale(i * stepSize));
            level.sendParticles(new DustParticleOptions(0xFF0000, 1.0f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private void applyFatigue(CugoWeatheringAccessor weathering) {
        stepIndex++;

        // NEW LINEAR DRAIN LOGIC:
        // If Oxidized and Unwaxed, drain is fixed at 1% per step.
        // Otherwise, use Fibonacci (which only really matters if the golem stops recharging).
        int penalty;
        if (weathering.scg$getWeatherState() == WeatheringCopper.WeatherState.OXIDIZED && !weathering.scg$isWaxed()) {
            penalty = 1;
        } else {
            penalty = getFibonacci(stepIndex);
        }

        currentWanderChance -= penalty;
        if (currentWanderChance < 0) currentWanderChance = 0;

        Debug.log("Battery: " + String.format("%.1f", currentWanderChance) + "% (-" + penalty + ")");
    }

    private void resetTiredness() {
        currentWanderChance = 100.0;
        stepIndex = 0;
    }

    private int getFibonacci(int n) {
        if (n <= 1) return n;
        int a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            int sum = a + b;
            a = b;
            b = sum;
        }
        return b;
    }
}