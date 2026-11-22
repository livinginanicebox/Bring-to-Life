package com.bringtolife.game;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public record WorldEnclosureView(ServerWorld world) implements EnclosureView {

    @Override
    public boolean isPassable(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = world.getBlockState(pos);
        return state.getCollisionShape(world, pos).isEmpty();
    }

    @Override
    public boolean isLeaky(int x, int y, int z) {
        Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
        return block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock;
    }

    @Override
    public boolean isLoaded(int x, int y, int z) {
        return world.isChunkLoaded(x >> 4, z >> 4);
    }
}
