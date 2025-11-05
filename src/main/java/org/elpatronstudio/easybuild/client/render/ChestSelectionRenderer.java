package org.elpatronstudio.easybuild.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.elpatronstudio.easybuild.client.ClientChestRegistry;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.model.ChestRef;

import java.util.Set;

/**
 * Renders selection highlights for user-linked chest containers while the selection mode is active.
 */
public final class ChestSelectionRenderer {

    private static final float[] SELECTED_COLOR = {1.0F, 0.8F, 0.0F};
    private static final float[] TARGET_COLOR = {0.4F, 0.7F, 1.0F};

    private ChestSelectionRenderer() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.register(ChestSelectionRenderer.class);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        EasyBuildClientState state = EasyBuildClientState.get();
        if (!state.isChestSelectionActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Set<ChestRef> chests = state.selectedChests();
        boolean hasTarget = minecraft.hitResult instanceof BlockHitResult;
        if (chests.isEmpty() && !hasTarget) {
            return;
        }

        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());

        for (ChestRef chest : chests) {
            if (!chest.dimension().equals(minecraft.level.dimension().location())) {
                continue;
            }
            renderBox(poseStack, consumer, cameraPos, chest.blockPos(), SELECTED_COLOR);
        }

        if (minecraft.hitResult instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
            if (ClientChestRegistry.isContainerBlockEntity(blockEntity)) {
                boolean alreadySelected = chests.stream().anyMatch(ref -> ref.blockPos().equals(pos));
                if (!alreadySelected) {
                    renderBox(poseStack, consumer, cameraPos, pos, TARGET_COLOR);
                }
            }
        }

        poseStack.popPose();
        buffer.endBatch(RenderType.lines());
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer consumer, Vec3 cameraPos, BlockPos pos, float[] color) {
        AABB box = new AABB(pos).inflate(0.002D).move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        ShapeRenderer.renderLineBox(poseStack.last(), consumer, box, color[0], color[1], color[2], 1.0F);
    }
}
