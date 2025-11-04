package org.elpatronstudio.easybuild.server.job;

import java.util.List;

/**
 * Immutable plan containing all block placements for a build job.
 */
public final class BlockPlacementPlan {

    private final List<BlockPlacement> placements;

    public BlockPlacementPlan(List<BlockPlacement> placements) {
        this.placements = List.copyOf(placements);
    }

    public List<BlockPlacement> placements() {
        return placements;
    }

    public int totalBlocks() {
        return placements.size();
    }

    public boolean isEmpty() {
        return placements.isEmpty();
    }
}
