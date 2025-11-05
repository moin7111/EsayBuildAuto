package org.elpatronstudio.easybuild.client.model;

import org.elpatronstudio.easybuild.core.model.SchematicRef;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Client-side descriptor for a schematic stored on disk.
 */
public record SchematicFileEntry(
        String id,
        String displayName,
        Path path,
        SchematicRef ref,
        long lastModified,
        long fileSize
) {

    public SchematicFileEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(ref, "ref");
    }
}
