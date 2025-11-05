package org.elpatronstudio.easybuild.client.preview.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.elpatronstudio.easybuild.client.schematic.SchematicBlockLoader;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds cached mesh representations for preview chunks.
 */
public final class PreviewMeshBuilder {

    private PreviewMeshBuilder() {
    }

    public static void rebuildChunk(Level level, BlockRenderDispatcher dispatcher, PreviewChunk chunk) {
        chunk.clearMeshes();
        chunk.resetTintMap();

        EnumMap<PreviewTint, RecordingBufferSource> recorders = new EnumMap<>(PreviewTint.class);
        PoseStack poseStack = new PoseStack();

        for (SchematicBlockLoader.BlockInstance block : chunk.blocks()) {
            BlockState current = level.getBlockState(block.position());
            PreviewTint tint = determineTint(current, block.state());
            chunk.putTint(block.position(), tint);
            if (tint == null) {
                continue;
            }

            RecordingBufferSource recorder = recorders.computeIfAbsent(tint, RecordingBufferSource::new);

            poseStack.pushPose();
            BlockPos pos = block.position();
            BlockPos origin = chunk.origin();
            poseStack.translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
            dispatcher.renderSingleBlock(block.state(), poseStack, recorder, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        for (Map.Entry<PreviewTint, RecordingBufferSource> entry : recorders.entrySet()) {
            PreviewChunkMesh mesh = entry.getValue().finish(entry.getKey());
            if (mesh != null && !mesh.isEmpty()) {
                chunk.setMesh(entry.getKey(), mesh);
            }
        }

        chunk.markClean();
    }

    public static PreviewTint determineTint(BlockState world, BlockState target) {
        if (world.equals(target)) {
            return null;
        }
        if (world.isAir()) {
            return PreviewTint.MISSING;
        }
        return PreviewTint.CONFLICT;
    }

    private static final class RecordingBufferSource implements MultiBufferSource {

        private final Map<RenderType, BuilderEntry> builders = new HashMap<>();
        private final PreviewTint tint;

        RecordingBufferSource(PreviewTint tint) {
            this.tint = tint;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return builders.computeIfAbsent(renderType, key -> new BuilderEntry(key, tint)).consumer;
        }

        PreviewChunkMesh finish(PreviewTint tint) {
        Map<RenderType, MeshData> layers = new HashMap<>();
            for (Map.Entry<RenderType, BuilderEntry> entry : builders.entrySet()) {
                MeshData data = entry.getValue().build();
                if (data != null) {
                    layers.put(entry.getKey(), data);
                }
            }
            if (layers.isEmpty()) {
                return null;
            }
            return new PreviewChunkMesh(tint, layers);
        }
    }

    private static final class BuilderEntry {

        private final BufferBuilder builder;
        private final TintedVertexConsumer consumer;

        private BuilderEntry(RenderType renderType, PreviewTint tint) {
            this.builder = new BufferBuilder(new com.mojang.blaze3d.vertex.ByteBufferBuilder(renderType.bufferSize()), renderType.mode(), renderType.format());
            this.consumer = new TintedVertexConsumer(builder, tint);
        }

        MeshData build() {
            MeshData mesh = builder.build();
            if (mesh == null || mesh.drawState() == null) {
                if (mesh != null) {
                    mesh.close();
                }
                return null;
            }
            return mesh;
        }
    }

    private static final class TintedVertexConsumer implements VertexConsumer {

        private final VertexConsumer delegate;
        private final PreviewTint tint;

        private TintedVertexConsumer(VertexConsumer delegate, PreviewTint tint) {
            this.delegate = delegate;
            this.tint = tint;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(scale(red, tint.red()), scale(green, tint.green()), scale(blue, tint.blue()), scale(alpha, tint.alpha()));
            return this;
        }

        @Override
        public VertexConsumer setColor(float red, float green, float blue, float alpha) {
            delegate.setColor(clamp(red * tint.red()), clamp(green * tint.green()), clamp(blue * tint.blue()), clamp(alpha * tint.alpha()));
            return this;
        }

        @Override
        public VertexConsumer setColor(int packed) {
            delegate.setColor(packed);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setOverlay(int overlay) {
            delegate.setOverlay(overlay);
            return this;
        }

        @Override
        public VertexConsumer setLight(int light) {
            delegate.setLight(light);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        private static int scale(int value, float factor) {
            return Math.min(255, Math.max(0, Math.round(value * factor)));
        }

        private static float clamp(float value) {
            return Math.max(0.0F, Math.min(1.0F, value));
        }
    }
}
