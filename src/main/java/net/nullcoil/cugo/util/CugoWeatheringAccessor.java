package net.nullcoil.cugo.util;

import net.minecraft.world.level.block.WeatheringCopper;

public interface CugoWeatheringAccessor {
    WeatheringCopper.WeatherState scg$getWeatherState();
    void scg$setWeatherState(WeatheringCopper.WeatherState state);
    WeatheringCopper.WeatherState scg$getPreviousWeatherState();

    boolean scg$isWaxed();
    void scg$setWaxed(boolean waxed);

    void scg$convertToStatue(boolean randomizePose);

    // NEW: Triggers the death spiral
    void scg$startShutdown();
}