package org.elpatronstudio.easybuild.core.model;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Anchor position describing where a schematic should be aligned.
 */
public record AnchorPos(ResourceLocation dimension, int x, int y, int z, Direction facing) {

    public AnchorPos {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(facing, "facing");
    }

    public Vec3 toVector() {
        return new Vec3(x + 0.5D, y, z + 0.5D);
    }
}
