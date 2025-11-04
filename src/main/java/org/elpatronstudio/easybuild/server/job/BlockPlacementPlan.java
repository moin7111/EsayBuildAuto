package org.elpatronstudio.easybuild.server.job;

import java.util.List;

/**
 * Immutable plan containing all block placements for a build job.
 */
final class BlockPlacementPlan {

    private final List<BlockPlacement> placements;

    BlockPlacementPlan(List<BlockPlacement> placements) {
        this.placements = List.copyOf(placements);
    }

    List<BlockPlacement> placements() {
        return placements;
    }

    int totalBlocks() {
        return placements.size();
    }

    boolean isEmpty() {
        return placements.isEmpty();
    }
}
