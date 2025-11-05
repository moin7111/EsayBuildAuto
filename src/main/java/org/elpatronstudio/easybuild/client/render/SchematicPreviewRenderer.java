package org.elpatronstudio.easybuild.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.elpatronstudio.easybuild.client.preview.SchematicPreviewController;
import org.elpatronstudio.easybuild.client.preview.SchematicPreviewController.Preview;
import org.elpatronstudio.easybuild.client.schematic.SchematicBlockLoader;

import java.util.Optional;

/**
 * Renders the active schematic preview as a translucent wireframe overlay.
 */
public final class SchematicPreviewRenderer {

    private static final float[] COLOR_MISSING = {0.2F, 0.72F, 1.0F};
    private static final float[] COLOR_CONFLICT = {1.0F, 0.45F, 0.35F};

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
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());

        int rendered = 0;
        for (SchematicBlockLoader.BlockInstance block : preview.blocks()) {
            BlockPos pos = block.position();
            if (level.getBlockState(pos).equals(block.state())) {
                continue;
            }

            AABB box = new AABB(pos).inflate(0.002D).move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            float[] color = level.getBlockState(pos).isAir() ? COLOR_MISSING : COLOR_CONFLICT;
            ShapeRenderer.renderLineBox(poseStack.last(), consumer, box, color[0], color[1], color[2], 1.0F);
            rendered++;
        }

        poseStack.popPose();

        buffer.endBatch(RenderType.lines());
    }
}
