package org.elpatronstudio.easybuild.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.elpatronstudio.easybuild.client.preview.SchematicPreviewController;
import org.elpatronstudio.easybuild.client.preview.SchematicPreviewController.Preview;
import org.elpatronstudio.easybuild.client.preview.render.PreviewChunk;
import org.elpatronstudio.easybuild.client.preview.render.PreviewChunkCache;
import org.elpatronstudio.easybuild.client.preview.render.PreviewChunkMesh;
import org.elpatronstudio.easybuild.client.preview.render.PreviewMeshBuilder;
import org.elpatronstudio.easybuild.client.preview.render.PreviewTint;
import org.elpatronstudio.easybuild.client.schematic.SchematicBlockLoader;

import java.util.Map;
import java.util.Optional;

/**
 * Renders the schematic hologram using cached chunk meshes similar to Litematica.
 */
public final class SchematicPreviewRenderer {

    private static final int SAMPLE_LIMIT = 32;

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
            return;
        }

        Optional<Preview> maybePreview = SchematicPreviewController.get().currentPreview()
                .filter(preview -> preview.isOwner(player))
                .filter(preview -> preview.anchor().dimension().equals(level.dimension().location()));
        if (maybePreview.isEmpty()) {
            return;
        }

        Preview preview = maybePreview.get();
        PreviewChunkCache cache = preview.chunkCache();
        if (cache.allChunks().isEmpty()) {
            return;
        }

        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;

        for (PreviewChunk chunk : cache.allChunks()) {
            if (shouldRebuild(level, chunk)) {
                PreviewMeshBuilder.rebuildChunk(level, dispatcher, chunk);
            }
            renderChunk(chunk, poseStack, cameraPos);
        }
    }

    private static boolean shouldRebuild(Level level, PreviewChunk chunk) {
        if (chunk.isDirty()) {
            return true;
        }

        boolean hasMesh = false;
        for (PreviewTint tint : PreviewTint.values()) {
            PreviewChunkMesh mesh = chunk.mesh(tint);
            if (mesh != null && !mesh.isEmpty()) {
                hasMesh = true;
                break;
            }
        }
        if (!hasMesh) {
            return true;
        }

        if (chunk.blocks().isEmpty()) {
            return false;
        }

        int sampleCount = Math.min(SAMPLE_LIMIT, chunk.blocks().size());
        int step = Math.max(1, chunk.blocks().size() / sampleCount);
        for (int i = 0; i < chunk.blocks().size(); i += step) {
            SchematicBlockLoader.BlockInstance block = chunk.blocks().get(i);
            BlockState worldState = level.getBlockState(block.position());
            PreviewTint actual = PreviewMeshBuilder.determineTint(worldState, block.state());
            PreviewTint cached = chunk.cachedTint(block.position());
            if (actual != cached) {
                chunk.markDirty();
                return true;
            }
        }

        return false;
    }

    private static void renderChunk(PreviewChunk chunk, PoseStack poseStack, Vec3 cameraPos) {
        double dx = chunk.origin().getX() - cameraPos.x;
        double dy = chunk.origin().getY() - cameraPos.y;
        double dz = chunk.origin().getZ() - cameraPos.z;

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        Matrix4f transform = new Matrix4f(poseStack.last().pose());

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(transform);
        RenderSystem.applyModelViewMatrix();

        for (PreviewTint tint : PreviewTint.values()) {
            PreviewChunkMesh mesh = chunk.mesh(tint);
            if (mesh == null || mesh.isEmpty()) {
                continue;
            }
            for (Map.Entry<RenderType, MeshData> entry : mesh.layers().entrySet()) {
                MeshData data = entry.getValue();
                if (data != null) {
                    entry.getKey().draw(data);
                }
            }
        }

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        poseStack.popPose();
    }
}
