package net.nullcoil.scg.cugo.behaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
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
        double roll = random.nextDouble() * 100.0;

        // --- LINGER LOGIC ---
        if (roll > currentWanderChance || currentWanderChance <= 0) {
            System.out.println(String.format("Cugo Linger: Roll (%.1f) > Chance (%.1f). Taking a break & scanning.", roll, currentWanderChance));

            scanSurroundings(golem);
            resetTiredness();
            golem.getNavigation().stop();
            return true;
        }

        // --- WANDER LOGIC ---
        int range = ConfigHandler.getConfig().horizontalRange;
        int vertical = ConfigHandler.getConfig().verticalRange;
        PathNavigation nav = golem.getNavigation();

        for (int i = 0; i < 5; i++) {
            Vec3 target = LandRandomPos.getPos(golem, range / 2, vertical);

            if (target != null) {
                Path path = nav.createPath(target.x, target.y, target.z, 0);

                if (path != null && path.canReach()) {
                    System.out.println(String.format("Cugo Wander: Valid path found to %.1f, %.1f, %.1f", target.x, target.y, target.z));
                    nav.moveTo(path, 1.0D);
                    applyFatigue();
                    return true;
                }
            }
        }

        System.out.println("Cugo Wander: Could not find a reachable random location. Lingering instead.");
        return false;
    }

    private void scanSurroundings(CopperGolem golem) {
        ServerLevel level = (ServerLevel) golem.level();
        BlockPos golemPos = golem.blockPosition();
        int range = ConfigHandler.getConfig().horizontalRange;
        int vRange = ConfigHandler.getConfig().verticalRange;

        // 1. Get Accessors
        CugoBrainAccessor brainAccessor = (CugoBrainAccessor) golem;
        CugoHomeAccessor homeAccessor = (CugoHomeAccessor) golem;

        MemoryManager memory = ((CugoBrain) brainAccessor.scg$getBrain()).getMemoryManager();

        // 2. Prep for Home Search
        boolean needsHome = homeAccessor.scg$getHomePos() == null;
        BlockPos bestHomeCandidate = null;
        double closestHomeDist = Double.MAX_VALUE;

        Set<BlockPos> uniqueChests = new HashSet<>();

        // 3. Scan Loop
        for (BlockPos pos : BlockPos.betweenClosed(
                golemPos.offset(-range, -vRange, -range),
                golemPos.offset(range, vRange, range))) {

            BlockState state = level.getBlockState(pos);
            boolean isInput = state.is(ModTags.Blocks.CUGO_CONTAINER_INPUTS); // Copper
            boolean isOutput = state.is(ModTags.Blocks.CUGO_CONTAINER_OUTPUTS); // Wood

            if (isInput || isOutput) {
                BlockPos canonical = DoubleChestHelper.getCanonicalPos(level, pos, state).immutable();

                // Skip duplicates in this specific scan
                if (uniqueChests.contains(canonical)) continue;
                uniqueChests.add(canonical);

                // Math Check: Can we reach it?
                if (canAccess(golemPos, canonical)) {

                    // --- A: HOME FINDER (If Homeless + Copper Chest) ---
                    // We check this BEFORE the memory check, because he might already "know"
                    // the chest exists but forgot it was his home.
                    if (needsHome && isInput) {
                        double dist = golemPos.distSqr(canonical);
                        if (dist < closestHomeDist) {
                            closestHomeDist = dist;
                            bestHomeCandidate = canonical;
                        }
                    }

                    // --- B: MEMORY & PING (If New Discovery) ---
                    if (!memory.hasMemory(canonical)) {
                        System.out.println("Cugo Vision: Spotted NEW chest at " + canonical);
                        memory.markAsSeen(canonical);

                        // Visual Ping
                        drawParticleLine(level, golem.getEyePosition(), Vec3.atCenterOf(canonical));
                    }
                }
            }
        }

        // 4. Apply New Home (if found)
        if (bestHomeCandidate != null) {
            homeAccessor.scg$setHomePos(bestHomeCandidate);
            System.out.println("Cugo Search: Adopted new home at " + bestHomeCandidate);
            // Optional: Play a sound here like SoundEvents.COPPER_PLACE
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
        System.out.println(String.format("Cugo Fatigue: Penalty -%d. New Wander Chance: %.1f%%", penalty, currentWanderChance));
    }

    private void resetTiredness() {
        currentWanderChance = 100.0;
        stepIndex = 0;
        System.out.println("Cugo Fatigue: Rested and reset.");
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