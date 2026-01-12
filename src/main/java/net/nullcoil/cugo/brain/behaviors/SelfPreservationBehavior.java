package net.nullcoil.cugo.brain.behaviors;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.WeatheringCopper;
import net.nullcoil.cugo.util.CugoWeatheringAccessor;
import net.nullcoil.cugo.util.Debug;

import java.util.Random;

public record SelfPreservationBehavior() implements Behavior {

    private static final Random random = new Random();

    @Override
    public boolean run(CopperGolem golem) {
        ItemStack held = golem.getMainHandItem();
        if (held.isEmpty()) return false;

        CugoWeatheringAccessor weathering = (CugoWeatheringAccessor) golem;
        WeatheringCopper.WeatherState currentState = weathering.scg$getWeatherState();
        boolean isWaxed = weathering.scg$isWaxed();

        // 1. HONEYCOMB (Waxing)
        if (held.is(Items.HONEYCOMB)) {
            if (!isWaxed) {
                Debug.log("SelfPreservation: Applying Honeycomb (Waxing)...");
                weathering.scg$setWaxed(true);
                golem.playSound(SoundEvents.HONEYCOMB_WAX_ON, 1.0f, 1.0f);
                spawnParticles(golem, ParticleTypes.WAX_ON);
                held.shrink(1);
                return true;
            }
        }

        // 2. AXE (Scraping)
        if (held.getItem() instanceof AxeItem) {
            // UPDATED: Must have MORE than 5 durability left (so it doesn't break + buffer)
            int currentDmg = held.getDamageValue();
            int maxDmg = held.getMaxDamage();
            int remaining = maxDmg - currentDmg;

            if (remaining > 5) {
                boolean hasOxidation = currentState != WeatheringCopper.WeatherState.UNAFFECTED;

                if (!isWaxed && hasOxidation) {
                    WeatheringCopper.WeatherState previous = weathering.scg$getPreviousWeatherState();
                    if (previous != null) {
                        Debug.log("SelfPreservation: Scraping Oxidation: " + currentState + " -> " + previous);
                        weathering.scg$setWeatherState(previous);
                        golem.playSound(SoundEvents.AXE_SCRAPE, 1.0f, 1.0f);
                        spawnParticles(golem, ParticleTypes.SCRAPE);
                        damageAxe(golem, held);
                        return true;
                    }
                }
            } else {
                Debug.log("SelfPreservation: Refusing to Scrape. Axe Durability Low: " + remaining);
            }
        }
        return false;
    }

    private void damageAxe(CopperGolem golem, ItemStack axe) {
        axe.hurtAndBreak(1, golem, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    }
    private void spawnParticles(CopperGolem golem, net.minecraft.core.particles.SimpleParticleType type) {
        for (int i = 0; i < 7; ++i) {
            double d = random.nextGaussian() * 0.02;
            double e = random.nextGaussian() * 0.02;
            double f = random.nextGaussian() * 0.02;
            golem.level().addParticle(type, golem.getRandomX(1.0), golem.getRandomY() + 0.5, golem.getRandomZ(1.0), d, e, f);
        }
    }
}