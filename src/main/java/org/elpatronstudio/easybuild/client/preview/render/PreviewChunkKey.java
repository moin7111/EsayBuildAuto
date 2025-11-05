package org.elpatronstudio.easybuild.client.preview.render;

import net.minecraft.core.BlockPos;

/**
 * Identifies a schematic preview chunk (16³ volume).
 */
public record PreviewChunkKey(int chunkX, int chunkY, int chunkZ) {

    public static PreviewChunkKey fromBlockPos(BlockPos pos) {
        return new PreviewChunkKey(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    public BlockPos toBlockPos() {
        return new BlockPos(chunkX << 4, chunkY << 4, chunkZ << 4);
    }
}
