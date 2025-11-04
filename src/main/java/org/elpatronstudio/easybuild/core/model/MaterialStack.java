package org.elpatronstudio.easybuild.core.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Simple item stack representation for transport across the wire.
 */
public record MaterialStack(ResourceLocation itemId, int count) {

    public MaterialStack {
        Objects.requireNonNull(itemId, "itemId");
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
    }

    public boolean isEmpty() {
        return count <= 0;
    }
}
