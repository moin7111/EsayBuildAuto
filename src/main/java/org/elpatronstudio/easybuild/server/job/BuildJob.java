package org.elpatronstudio.easybuild.server.job;

import com.google.gson.JsonObject;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.PasteMode;
import org.elpatronstudio.easybuild.core.model.SchematicRef;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable description of a server-side build job request.
 */
public record BuildJob(
        String jobId,
        UUID ownerUuid,
        SchematicRef schematic,
        AnchorPos anchor,
        PasteMode mode,
        JsonObject options,
        long createdAt,
        String clientRequestId
) {

    public BuildJob {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(clientRequestId, "clientRequestId");
        if (options == null) {
            options = new JsonObject();
        } else {
            options = options.deepCopy();
        }
    }
}
