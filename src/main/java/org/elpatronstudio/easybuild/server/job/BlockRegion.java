package org.elpatronstudio.easybuild.server.job;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * Axis-aligned block region used for region locking and overlap detection.
 */
public record BlockRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public BlockRegion {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid bounding box: min must be <= max for all axes");
        }
    }

    public static BlockRegion single(BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        return new BlockRegion(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockRegion encompassing(BlockPos first, BlockPos second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        return new BlockRegion(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static BlockRegion fromPlacements(Iterable<BlockPlacement> placements, BlockPos fallbackAnchor) {
        Objects.requireNonNull(fallbackAnchor, "fallbackAnchor");
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        boolean found = false;
        for (BlockPlacement placement : placements) {
            if (placement == null) {
                continue;
            }
            BlockPos pos = placement.position();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            found = true;
        }

        if (!found) {
            return single(fallbackAnchor);
        }

        return new BlockRegion(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean intersects(BlockRegion other) {
        Objects.requireNonNull(other, "other");
        return this.maxX >= other.minX && this.minX <= other.maxX
                && this.maxY >= other.minY && this.minY <= other.maxY
                && this.maxZ >= other.minZ && this.minZ <= other.maxZ;
    }

    public BlockPos min() {
        return new BlockPos(minX, minY, minZ);
    }

    public BlockPos max() {
        return new BlockPos(maxX, maxY, maxZ);
    }
}
