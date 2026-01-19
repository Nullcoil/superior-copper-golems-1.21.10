package net.nullcoil.cugo.util;

import net.minecraft.core.BlockPos;

public interface CugoHomeAccessor {
    BlockPos cugo$getHomePos();
    void cugo$setHomePos(BlockPos pos);
}