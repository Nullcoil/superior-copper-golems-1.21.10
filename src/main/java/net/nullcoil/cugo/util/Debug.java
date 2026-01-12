package net.nullcoil.cugo.util;

import net.nullcoil.cugo.CopperGolemOptimizations;
import net.nullcoil.cugo.config.ConfigHandler;

public class Debug {
    public static void log(Object obj) {
        if(ConfigHandler.getConfig().debugMode) {
            CopperGolemOptimizations.LOGGER.info("[CuGO DEBUG] " + obj.toString());
        }
    }
}
