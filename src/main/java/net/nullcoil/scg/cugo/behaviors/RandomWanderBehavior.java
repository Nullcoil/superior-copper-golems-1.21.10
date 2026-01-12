package net.nullcoil.scg.cugo.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.nullcoil.scg.config.ConfigHandler;
import net.nullcoil.scg.cugo.CugoBrain;
import net.nullcoil.scg.cugo.managers.MemoryManager;
import net.nullcoil.scg.util.CugoBrainAccessor;
import net.nullcoil.scg.util.CugoHomeAccessor;
import net.nullcoil.scg.util.DoubleChestHelper;
import net.nullcoil.scg.util.ModTags;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class RandomWanderBehavior implements Behavior {

    private final Random random = new Random();
    private double currentWanderChance = 100.0;
    private int stepIndex = 0;

    @Override
    public boolean run(CopperGolem golem) {
        boolean isUrgent = !golem.getMainHandItem().isEmpty();

        // --- 1. SCANNING & LINGERING ---
        if (isUrgent) {
            // URGENT MODE:
            // Always scan (so we find chests while rushing), but NEVER Stop/Linger.
            scanSurroundings(golem);
        } else {
            // NORMAL MODE:
            // Roll for fatigue. If tired, stop and scan.
            double roll = random.nextDouble() * 100.0;
            if (roll > currentWanderChance || currentWanderChance <= 0) {
                System.out.println(String.format("Cugo Linger: Roll (%.1f) > Chance (%.1f). Taking a break.", roll, currentWanderChance));
                scanSurroundings(golem);
                resetTiredness();
                golem.getNavigation().stop();
                return true; // We are successfully "doing nothing"
            }
        }

        // --- 2. PATHFINDING ---
        int range = ConfigHandler.getConfig().horizontalRange;
        int vertical = ConfigHandler.getConfig().verticalRange;
        PathNavigation nav = golem.getNavigation();
        Vec3 target = null;

        // Attempt 1: Standard Land Pos
        target = LandRandomPos.getPos(golem, range / 2, vertical);

        // Attempt 2: Fallback Random Pos
        if (target == null) {
            target = DefaultRandomPos.getPos(golem, range / 2, vertical);
        }

        // Attempt 3: Desperation (Random Air Block)
        if (target == null) {
            for(int k=0; k<10; k++) {
                BlockPos randomPos = golem.blockPosition().offset(
                        random.nextInt(range) - (range/2),
                        random.nextInt(vertical) - (vertical/2),
                        random.nextInt(range) - (range/2)
                );
                // Simple check for valid spot
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
                    // Only apply fatigue if we are NOT urgent.
                    // If urgent, we run infinitely until the job is done.
                    if (!isUrgent) {
                        applyFatigue();
                    } else {
                        // Optional: Reset tiredness so he is fresh when he finishes working
                        resetTiredness();
                    }
                    return true;
                }
            }
        }

        // If we reached here, we failed to find a path.
        // In Urgent mode, this just means "Try again next tick".
        // In Normal mode, we just stand still.
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

        for (BlockPos pos : BlockPos.betweenClosed(
                golemPos.offset(-range, -vRange, -range),
                golemPos.offset(range, vRange, range))) {

            BlockState state = level.getBlockState(pos);
            boolean isInput = state.is(ModTags.Blocks.CUGO_CONTAINER_INPUTS);
            boolean isOutput = state.is(ModTags.Blocks.CUGO_CONTAINER_OUTPUTS);

            if (isInput || isOutput) {
                BlockPos canonical = DoubleChestHelper.getCanonicalPos(level, pos, state).immutable();
                if (uniqueChests.contains(canonical)) continue;
                uniqueChests.add(canonical);

                if (canAccess(golemPos, canonical)) {
                    // Home Finder
                    if (needsHome && isInput) {
                        double dist = golemPos.distSqr(canonical);
                        if (dist < closestHomeDist) {
                            closestHomeDist = dist;
                            bestHomeCandidate = canonical;
                        }
                    }
                    // Memory & Ping
                    if (!memory.hasMemory(canonical)) {
                        memory.markAsSeen(canonical);
                        drawParticleLine(level, golem.getEyePosition(), Vec3.atCenterOf(canonical));
                    }
                }
            }
        }

        if (bestHomeCandidate != null) {
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
            level.sendParticles(new DustParticleOptions(0xFF0000, 1.0f),
                    p.x, p.y, p.z,
                    1, 0, 0, 0, 0);
        }
    }

    private void applyFatigue() {
        stepIndex++;
        int penalty = getFibonacci(stepIndex);
        currentWanderChance -= penalty;
        if (currentWanderChance < 0) currentWanderChance = 0;
        // System.out.println(String.format("Cugo Fatigue: Penalty -%d. New Wander Chance: %.1f%%", penalty, currentWanderChance));
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