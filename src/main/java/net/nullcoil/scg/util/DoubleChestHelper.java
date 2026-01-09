package net.nullcoil.scg.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class DoubleChestHelper {
    public static BlockPos getCanonicalPos(ServerLevel level, BlockPos pos, BlockState state) {
        if(!(state.getBlock() instanceof ChestBlock)) return pos;

        ChestType type = state.getValue(ChestBlock.TYPE);

        if(type == ChestType.SINGLE) return pos; // already canon
        if(type == ChestType.LEFT) return pos;   // arbitrarily canon

        Direction connectedDir = ChestBlock.getConnectedDirection(state);
        return pos.relative(connectedDir);
    }
}
