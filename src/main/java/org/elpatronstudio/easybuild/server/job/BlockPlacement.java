package org.elpatronstudio.easybuild.server.job;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Represents a single block placement operation in world space, including optional block entity data.
 */
record BlockPlacement(BlockPos position, BlockState state, CompoundTag blockEntityTag) {
}
