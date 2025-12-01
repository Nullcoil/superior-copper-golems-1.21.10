package net.nullcoil.scg.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.nullcoil.scg.SuperiorCopperGolems;

public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> CUGO_CONTAINER_INPUTS = createTag("cugo_container_inputs");
        public static final TagKey<Block> CUGO_CONTAINER_OUTPUTS = createTag("cugo_container_outputs");

        private static TagKey<Block> createTag(String name) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(SuperiorCopperGolems.MOD_ID, name));
        }
    }
}