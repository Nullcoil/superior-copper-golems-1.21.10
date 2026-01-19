package net.nullcoil.cugo;

import net.fabricmc.api.ModInitializer;

import net.nullcoil.cugo.config.ConfigHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopperGolemOptimizations implements ModInitializer {
	public static final String MOD_ID = "cugo";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing SuperiorCopperGolems");

        ConfigHandler.init();
	}
}