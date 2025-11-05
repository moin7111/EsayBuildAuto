package org.elpatronstudio.easybuild.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.elpatronstudio.easybuild.client.preview.SchematicPreviewController;
import org.elpatronstudio.easybuild.client.preview.SchematicPreviewController.Preview;
import org.elpatronstudio.easybuild.client.schematic.SchematicBlockLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Renders the active schematic preview with translucent block models to aid placement without obstructing vision.
 */
public final class SchematicPreviewRenderer {

    private static final float MISSING_ALPHA = 0.55F;
    private static final float CONFLICT_ALPHA = 0.45F;
    private static final float[] CONFLICT_TINT = {1.0F, 0.55F, 0.55F};
    private static final int SAMPLE_LIMIT = 32;

    private static CompiledPreview cached;

    private SchematicPreviewRenderer() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.register(SchematicPreviewRenderer.class);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        Level level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            clearCache();
            return;
        }

        Optional<Preview> maybePreview = SchematicPreviewController.get().currentPreview()
                .filter(preview -> preview.isOwner(player))
                .filter(preview -> preview.anchor().dimension().equals(level.dimension().location()));
        if (maybePreview.isEmpty()) {
            clearCache();
            return;
        }

        Preview preview = maybePreview.get();
        CompiledPreview compiled = ensureCompiled(preview, level);
        if (compiled.isEmpty()) {
            return;
        }

        if (compiled.needsRebuild(level)) {
            clearCache();
            compiled = ensureCompiled(preview, level);
            if (compiled.isEmpty()) {
                return;
            }
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        renderBlocks(compiled.missingBlocks(), dispatcher, poseStack, cameraPos,
                new TintedBufferSource(buffer, 1.0F, 1.0F, 1.0F, MISSING_ALPHA));
        renderBlocks(compiled.conflictBlocks(), dispatcher, poseStack, cameraPos,
                new TintedBufferSource(buffer, CONFLICT_TINT[0], CONFLICT_TINT[1], CONFLICT_TINT[2], CONFLICT_ALPHA));
        poseStack.popPose();

        buffer.endBatch();
    }

    private static void renderBlocks(List<SchematicBlockLoader.BlockInstance> blocks,
                                     BlockRenderDispatcher dispatcher,
                                     PoseStack poseStack,
                                     Vec3 cameraPos,
                                     TintedBufferSource bufferSource) {
        if (blocks.isEmpty()) {
            return;
        }

        for (SchematicBlockLoader.BlockInstance block : blocks) {
            poseStack.pushPose();
            BlockPos pos = block.position();
            poseStack.translate(pos.getX() - cameraPos.x,
                    pos.getY() - cameraPos.y,
                    pos.getZ() - cameraPos.z);
            dispatcher.renderSingleBlock(block.state(), poseStack, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }

    private static CompiledPreview ensureCompiled(Preview preview, Level level) {
        if (cached != null && cached.matches(preview)) {
            return cached;
        }
        cached = compilePreview(preview, level);
        return cached;
    }

    private static CompiledPreview compilePreview(Preview preview, Level level) {
        List<SchematicBlockLoader.BlockInstance> missing = new ArrayList<>();
        List<SchematicBlockLoader.BlockInstance> conflicts = new ArrayList<>();

        for (SchematicBlockLoader.BlockInstance block : preview.blocks()) {
            BlockState worldState = level.getBlockState(block.position());
            if (worldState.equals(block.state())) {
                continue;
            }
            if (worldState.isAir()) {
                missing.add(block);
            } else {
                conflicts.add(block);
            }
        }

        return new CompiledPreview(preview, List.copyOf(missing), List.copyOf(conflicts));
    }

    private static void clearCache() {
        cached = null;
    }

    private static final class CompiledPreview {

        private final Preview preview;
        private final List<SchematicBlockLoader.BlockInstance> missingBlocks;
        private final List<SchematicBlockLoader.BlockInstance> conflictBlocks;

        private CompiledPreview(Preview preview,
                                List<SchematicBlockLoader.BlockInstance> missingBlocks,
                                List<SchematicBlockLoader.BlockInstance> conflictBlocks) {
            this.preview = preview;
            this.missingBlocks = missingBlocks;
            this.conflictBlocks = conflictBlocks;
        }

        boolean matches(Preview other) {
            return this.preview == other;
        }

        boolean isEmpty() {
            return missingBlocks.isEmpty() && conflictBlocks.isEmpty();
        }

        boolean needsRebuild(Level level) {
            return sample(level, missingBlocks, true) || sample(level, conflictBlocks, false);
        }

        private boolean sample(Level level, List<SchematicBlockLoader.BlockInstance> blocks, boolean expectAir) {
            if (blocks.isEmpty()) {
                return false;
            }
            int sampleSize = Math.min(SAMPLE_LIMIT, blocks.size());
            int step = Math.max(1, blocks.size() / sampleSize);
            for (int i = 0; i < blocks.size(); i += step) {
                SchematicBlockLoader.BlockInstance block = blocks.get(i);
                BlockState current = level.getBlockState(block.position());
                if (current.equals(block.state())) {
                    return true;
                }
                if (expectAir != current.isAir()) {
                    return true;
                }
            }
            return false;
        }

        List<SchematicBlockLoader.BlockInstance> missingBlocks() {
            return missingBlocks;
        }

        List<SchematicBlockLoader.BlockInstance> conflictBlocks() {
            return conflictBlocks;
        }
    }

    private static final class TintedBufferSource implements MultiBufferSource {

        private final MultiBufferSource delegate;
        private final float redFactor;
        private final float greenFactor;
        private final float blueFactor;
        private final float alphaFactor;
        private final Map<RenderType, VertexConsumer> cache = new HashMap<>();

        private TintedBufferSource(MultiBufferSource delegate,
                                   float redFactor,
                                   float greenFactor,
                                   float blueFactor,
                                   float alphaFactor) {
            this.delegate = delegate;
            this.redFactor = redFactor;
            this.greenFactor = greenFactor;
            this.blueFactor = blueFactor;
            this.alphaFactor = alphaFactor;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return cache.computeIfAbsent(renderType, type -> new TintedVertexConsumer(delegate.getBuffer(type), redFactor, greenFactor, blueFactor, alphaFactor));
        }
    }

    private static final class TintedVertexConsumer implements VertexConsumer {

        private final VertexConsumer delegate;
        private final float redFactor;
        private final float greenFactor;
        private final float blueFactor;
        private final float alphaFactor;

        private TintedVertexConsumer(VertexConsumer delegate,
                                     float redFactor,
                                     float greenFactor,
                                     float blueFactor,
                                     float alphaFactor) {
            this.delegate = delegate;
            this.redFactor = redFactor;
            this.greenFactor = greenFactor;
            this.blueFactor = blueFactor;
            this.alphaFactor = alphaFactor;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(scale(red, redFactor),
                    scale(green, greenFactor),
                    scale(blue, blueFactor),
                    scale(alpha, alphaFactor));
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
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(float red, float green, float blue, float alpha) {
            delegate.setColor(clamp(red * redFactor),
                    clamp(green * greenFactor),
                    clamp(blue * blueFactor),
                    clamp(alpha * alphaFactor));
            return this;
        }

        @Override
        public VertexConsumer setColor(int packed) {
            delegate.setColor(packed);
            return this;
        }

        @Override
        public VertexConsumer setLight(int light) {
            delegate.setLight(light);
            return this;
        }

        @Override
        public VertexConsumer setOverlay(int overlay) {
            delegate.setOverlay(overlay);
            return this;
        }

        private static int scale(int value, float factor) {
            return Mth.clamp(Math.round(value * factor), 0, 255);
        }

        private static float clamp(float value) {
            return Mth.clamp(value, 0.0F, 1.0F);
        }
    }
}
