package net.nullcoil.scg.util;

import net.nullcoil.scg.SuperiorCopperGolems;
import net.nullcoil.scg.config.ConfigHandler;

public class Debug {
    public static void log(Object obj) {
        if(ConfigHandler.getConfig().debugMode) {
            SuperiorCopperGolems.LOGGER.info("[CuGO DEBUG] " + obj.toString());
        }
    }
}
