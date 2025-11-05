package org.elpatronstudio.easybuild.client.preview;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import org.elpatronstudio.easybuild.client.model.SchematicFileEntry;
import org.elpatronstudio.easybuild.client.schematic.SchematicBlockLoader;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.server.job.BlockPlacementException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks the currently active schematic preview on the client.
 */
public final class SchematicPreviewController {

    private static final SchematicPreviewController INSTANCE = new SchematicPreviewController();

    private Preview current;

    private SchematicPreviewController() {
    }

    public static SchematicPreviewController get() {
        return INSTANCE;
    }

    public synchronized Optional<Preview> currentPreview() {
        return Optional.ofNullable(current);
    }

    public synchronized void clearPreview() {
        current = null;
    }

    public synchronized boolean hasPreview() {
        return current != null;
    }

    public synchronized boolean matchesCurrent(UUID owner, SchematicFileEntry entry, AnchorPos anchor) {
        if (current == null) {
            return false;
        }
        return current.owner().equals(owner)
                && Objects.equals(current.entry(), entry)
                && current.anchor().equals(anchor);
    }

    public synchronized Preview startPreview(LocalPlayer player, SchematicFileEntry entry, AnchorPos anchor, boolean includeAir) throws BlockPlacementException {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(anchor, "anchor");

        SchematicBlockLoader.Result result = SchematicBlockLoader.load(player, entry, anchor, includeAir);
        Preview preview = new Preview(
                player.getUUID(),
                entry,
                anchor,
                result.blocks(),
                result.minCorner(),
                result.maxCorner(),
                includeAir,
                System.currentTimeMillis()
        );
        current = preview;
        return preview;
    }

    public record Preview(UUID owner,
                          SchematicFileEntry entry,
                          AnchorPos anchor,
                          List<SchematicBlockLoader.BlockInstance> blocks,
                          BlockPos minCorner,
                          BlockPos maxCorner,
                          boolean includeAir,
                          long createdAt) {

        public int blockCount() {
            return blocks.size();
        }

        public boolean isOwner(LocalPlayer player) {
            return player != null && owner.equals(player.getUUID());
        }
    }
}
