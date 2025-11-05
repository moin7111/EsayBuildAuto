package org.elpatronstudio.easybuild.core.network.packet;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

/**
 * Server → Client message that signals the completion of a build job.
 */
public record ClientboundBuildCompleted(
        String jobId,
        SchematicRef schematic,
        boolean success,
        List<MaterialStack> consumed,
        String logRef,
        long nonce,
        long serverTime
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("build_completed");
    public static final Type<ClientboundBuildCompleted> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundBuildCompleted> STREAM_CODEC =
            StreamCodec.of(ClientboundBuildCompleted::write, ClientboundBuildCompleted::read);

    private static final Logger LOGGER = LogUtils.getLogger();

    public ClientboundBuildCompleted {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(consumed, "consumed");
        if (logRef == null) {
            logRef = "";
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundBuildCompleted message) {
        buf.writeUtf(message.jobId);
        FriendlyByteBufUtil.writeSchematicRef(buf, message.schematic);
        buf.writeBoolean(message.success);
        FriendlyByteBufUtil.writeMaterialList(buf, message.consumed);
        buf.writeUtf(message.logRef);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    private static ClientboundBuildCompleted read(RegistryFriendlyByteBuf buf) {
        String jobId = buf.readUtf();
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        boolean success = buf.readBoolean();
        List<MaterialStack> consumed = FriendlyByteBufUtil.readMaterialList(buf);
        String logRef = buf.readUtf();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundBuildCompleted(jobId, schematic, success, consumed, logRef, nonce, serverTime);
    }

    @Override
    public Type<ClientboundBuildCompleted> type() {
        return TYPE;
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        EasyBuildClientState.get().recordBuildCompleted(this);
        LOGGER.info("[EasyBuild] Job {} completed {}", jobId, success ? "successfully" : "with issues");
        if (minecraft.player != null) {
            Component message = success
                    ? Component.translatable("easybuild.job.completed", jobId, schematic.schematicId())
                    : Component.translatable("easybuild.job.completed.failed", jobId, schematic.schematicId());
            minecraft.player.displayClientMessage(message, false);
        }
    }
}
