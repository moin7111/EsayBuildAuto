package org.elpatronstudio.easybuild.server.job;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.elpatronstudio.easybuild.core.model.PasteMode;

import java.util.List;

/**
 * Executes a {@link BlockPlacementPlan} over multiple server ticks.
 */
final class BlockPlacementExecutor {

    private final ServerLevel level;
    private final List<BlockPlacement> placements;
    private final PasteMode mode;
    private final int blocksPerTick;

    private int cursor;

    BlockPlacementExecutor(ServerLevel level, BlockPlacementPlan plan, PasteMode mode, int blocksPerTick) {
        this.level = level;
        this.placements = plan.placements();
        this.mode = mode;
        this.blocksPerTick = mode == PasteMode.ATOMIC ? Integer.MAX_VALUE : Math.max(1, blocksPerTick);
        this.cursor = 0;
    }

    int totalBlocks() {
        return placements.size();
    }

    int placedBlocks() {
        return cursor;
    }

    boolean tick() throws BlockPlacementException {
        if (placements.isEmpty()) {
            return true;
        }

        int remaining = placements.size() - cursor;
        int batchSize = Math.min(blocksPerTick, remaining);
        if (batchSize <= 0) {
            return true;
        }

        for (int i = 0; i < batchSize; i++) {
            BlockPlacement placement = placements.get(cursor);
            BlockPos pos = placement.position();
            if (!level.isLoaded(pos)) {
                if (mode == PasteMode.ATOMIC) {
                    throw new BlockPlacementException("CHUNK_UNLOADED", "Chunk nicht geladen bei " + pos);
                }
                return false;
            }

            placeBlock(placement);
            cursor++;
        }

        return cursor >= placements.size();
    }

    private void placeBlock(BlockPlacement placement) {
        BlockPos pos = placement.position();
        if (level.getBlockState(pos).equals(placement.state())) {
            // Already matching, but still counted as placed for progress accuracy
            return;
        }

        level.setBlock(pos, placement.state(), Block.UPDATE_ALL);
    }
}
