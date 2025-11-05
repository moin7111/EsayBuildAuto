package org.elpatronstudio.easybuild.client.preview.render;

import org.elpatronstudio.easybuild.client.schematic.SchematicBlockLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stores all preview chunks for the currently loaded schematic preview.
 */
public final class PreviewChunkCache implements AutoCloseable {

    private final Map<PreviewChunkKey, PreviewChunk> chunks;

    private PreviewChunkCache(Map<PreviewChunkKey, PreviewChunk> chunks) {
        this.chunks = chunks;
    }

    public static PreviewChunkCache fromBlocks(Collection<SchematicBlockLoader.BlockInstance> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        Map<PreviewChunkKey, PreviewChunk.Builder> builders = new HashMap<>();

        for (SchematicBlockLoader.BlockInstance block : blocks) {
            PreviewChunkKey key = PreviewChunkKey.fromBlockPos(block.position());
            builders.computeIfAbsent(key, PreviewChunk.Builder::new).add(block);
        }

        Map<PreviewChunkKey, PreviewChunk> chunks = new HashMap<>();
        for (Map.Entry<PreviewChunkKey, PreviewChunk.Builder> entry : builders.entrySet()) {
            PreviewChunk chunk = entry.getValue().build();
            if (!chunk.isEmpty()) {
                chunks.put(entry.getKey(), chunk);
            }
        }
        return new PreviewChunkCache(chunks);
    }

    public Collection<PreviewChunk> allChunks() {
        return Collections.unmodifiableCollection(chunks.values());
    }

    public void markAllDirty() {
        chunks.values().forEach(PreviewChunk::markDirty);
    }

    public void markDirty(PreviewChunkKey key) {
        PreviewChunk chunk = chunks.get(key);
        if (chunk != null) {
            chunk.markDirty();
        }
    }

    public PreviewChunk get(PreviewChunkKey key) {
        return chunks.get(key);
    }

    @Override
    public void close() {
        for (PreviewChunk chunk : chunks.values()) {
            chunk.close();
        }
        chunks.clear();
    }
}
