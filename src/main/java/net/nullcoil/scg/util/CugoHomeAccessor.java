package net.nullcoil.scg.util;

import net.minecraft.core.BlockPos;

public interface CugoHomeAccessor {
    BlockPos scg$getHomePos();
    void scg$setHomePos(BlockPos pos);
}