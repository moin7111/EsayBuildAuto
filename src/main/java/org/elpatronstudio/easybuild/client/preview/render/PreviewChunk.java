package org.elpatronstudio.easybuild.client.preview.render;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.elpatronstudio.easybuild.client.schematic.SchematicBlockLoader;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Holds the schematic blocks inside a single 16³ chunk and the lazily built mesh data.
 */
public final class PreviewChunk implements AutoCloseable {

    private final PreviewChunkKey key;
    private final BlockPos origin;
    private final List<SchematicBlockLoader.BlockInstance> blocks;
    private final EnumMap<PreviewTint, PreviewChunkMesh> meshes;
    private final Long2ObjectOpenHashMap<PreviewTint> tintByBlock;
    private boolean dirty;

    private PreviewChunk(PreviewChunkKey key, BlockPos origin, List<SchematicBlockLoader.BlockInstance> blocks) {
        this.key = key;
        this.origin = origin;
        this.blocks = blocks;
        this.meshes = new EnumMap<>(PreviewTint.class);
        this.tintByBlock = new Long2ObjectOpenHashMap<>();
        this.dirty = true;
    }

    public static PreviewChunk of(PreviewChunkKey key, List<SchematicBlockLoader.BlockInstance> blocks) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(blocks, "blocks");
        BlockPos origin = key.toBlockPos();
        return new PreviewChunk(key, origin, List.copyOf(blocks));
    }

    public PreviewChunkKey key() {
        return key;
    }

    public BlockPos origin() {
        return origin;
    }

    public List<SchematicBlockLoader.BlockInstance> blocks() {
        return blocks;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void markClean() {
        this.dirty = false;
    }

    public void setMesh(PreviewTint tint, PreviewChunkMesh mesh) {
        PreviewChunkMesh previous = meshes.put(tint, mesh);
        if (previous != null) {
            previous.close();
        }
    }

    public PreviewChunkMesh mesh(PreviewTint tint) {
        return meshes.get(tint);
    }

    public void clearMeshes() {
        for (Map.Entry<PreviewTint, PreviewChunkMesh> entry : meshes.entrySet()) {
            entry.getValue().close();
        }
        meshes.clear();
    }

    public void resetTintMap() {
        tintByBlock.clear();
    }

    public void putTint(BlockPos pos, PreviewTint tint) {
        long key = pos.asLong();
        if (tint == null) {
            tintByBlock.remove(key);
        } else {
            tintByBlock.put(key, tint);
        }
    }

    public PreviewTint cachedTint(BlockPos pos) {
        return tintByBlock.get(pos.asLong());
    }

    public BlockPos relative(BlockPos worldPos) {
        return new BlockPos(worldPos.getX() - origin.getX(), worldPos.getY() - origin.getY(), worldPos.getZ() - origin.getZ());
    }

    public boolean contains(BlockPos pos) {
        if (pos.getX() >> 4 != key.chunkX() || pos.getY() >> 4 != key.chunkY() || pos.getZ() >> 4 != key.chunkZ()) {
            return false;
        }
        int localX = pos.getX() & 15;
        int localY = Mth.clamp(pos.getY(), 0, 255) & 15;
        int localZ = pos.getZ() & 15;
        return localX >= 0 && localX < 16 && localY >= 0 && localY < 16 && localZ >= 0 && localZ < 16;
    }

    @Override
    public void close() {
        clearMeshes();
        resetTintMap();
    }

    public static final class Builder {

        private final PreviewChunkKey key;
        private final List<SchematicBlockLoader.BlockInstance> blocks = new ArrayList<>();

        public Builder(PreviewChunkKey key) {
            this.key = key;
        }

        public void add(SchematicBlockLoader.BlockInstance block) {
            blocks.add(block);
        }

        public PreviewChunk build() {
            return PreviewChunk.of(key, blocks);
        }
    }
}
