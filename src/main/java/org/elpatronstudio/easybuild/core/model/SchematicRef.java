package org.elpatronstudio.easybuild.core.model;

import java.util.Objects;

/**
 * Lightweight identifier for schematics shared between client and server.
 */
public record SchematicRef(String schematicId, int version, long checksum) {

    public SchematicRef {
        Objects.requireNonNull(schematicId, "schematicId");
    }

    public static SchematicRef empty() {
        return new SchematicRef("", 0, 0L);
    }
}
