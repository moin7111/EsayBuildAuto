package org.elpatronstudio.easybuild.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;

/**
 * Renders a simple anchor marker indicating where the schematic will be positioned when starting a build.
 */
public final class PreviewAnchorRenderer {

    private static final float[] ANCHOR_COLOR = {0.2F, 0.8F, 0.2F};

    private PreviewAnchorRenderer() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.register(PreviewAnchorRenderer.class);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        EasyBuildClientState state = EasyBuildClientState.get();
        if (state.selectedSchematic().isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.level == null || player == null) {
            return;
        }

        Vec3 look = player.getLookAngle().normalize();
        double offset = state.previewForwardOffset();
        BlockPos base = player.blockPosition();
        Vec3 anchorVec = new Vec3(base.getX(), base.getY(), base.getZ()).add(look.scale(offset));
        BlockPos anchorPos = BlockPos.containing(anchorVec);

        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());

        AABB box = new AABB(anchorPos).inflate(0.002D).move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        ShapeRenderer.renderLineBox(poseStack.last(), consumer, box, ANCHOR_COLOR[0], ANCHOR_COLOR[1], ANCHOR_COLOR[2], 1.0F);

        poseStack.popPose();
        buffer.endBatch(RenderType.lines());
    }
}
