package net.nullcoil.cugo.util;

import net.minecraft.core.BlockPos;

public interface CugoHomeAccessor {
    BlockPos scg$getHomePos();
    void scg$setHomePos(BlockPos pos);
}