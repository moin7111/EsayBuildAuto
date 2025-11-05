package org.elpatronstudio.easybuild.client.preview.render;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.RenderType;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds the baked mesh data for a preview chunk for a specific tint category.
 */
public final class PreviewChunkMesh implements AutoCloseable {

    private final PreviewTint tint;
    private final Map<RenderType, MeshData> layers;

    public PreviewChunkMesh(PreviewTint tint, Map<RenderType, MeshData> layers) {
        this.tint = tint;
        this.layers = new HashMap<>(layers);
    }

    public PreviewTint tint() {
        return tint;
    }

    public Map<RenderType, MeshData> layers() {
        return layers;
    }

    public boolean isEmpty() {
        if (layers.isEmpty()) {
            return true;
        }
        for (MeshData mesh : layers.values()) {
            if (mesh != null && mesh.drawState() != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        for (MeshData mesh : layers.values()) {
            if (mesh != null) {
                mesh.close();
            }
        }
        layers.clear();
    }
}
