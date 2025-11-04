package org.elpatronstudio.easybuild.core.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Reference to a container used for material sourcing.
 */
public record ChestRef(ResourceLocation dimension, BlockPos blockPos) {

    public ChestRef {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(blockPos, "blockPos");
    }
}
