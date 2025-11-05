package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;

import java.util.Objects;

/**
 * Server → Client message describing a failed build execution.
 */
public record ClientboundBuildFailed(
        String jobId,
        SchematicRef schematic,
        String clientRequestId,
        String reasonCode,
        String details,
        boolean rollbackPerformed,
        long nonce,
        long serverTime
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("build_failed");
    public static final Type<ClientboundBuildFailed> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundBuildFailed> STREAM_CODEC =
            StreamCodec.of(ClientboundBuildFailed::write, ClientboundBuildFailed::read);

    public ClientboundBuildFailed {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(clientRequestId, "clientRequestId");
        Objects.requireNonNull(reasonCode, "reasonCode");
        if (details == null) {
            details = "";
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundBuildFailed message) {
        buf.writeUtf(message.jobId);
        FriendlyByteBufUtil.writeSchematicRef(buf, message.schematic);
        buf.writeUtf(message.clientRequestId);
        buf.writeUtf(message.reasonCode);
        buf.writeUtf(message.details);
        buf.writeBoolean(message.rollbackPerformed);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    private static ClientboundBuildFailed read(RegistryFriendlyByteBuf buf) {
        String jobId = buf.readUtf();
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        String clientRequestId = buf.readUtf();
        String reason = buf.readUtf();
        String details = buf.readUtf();
        boolean rollback = buf.readBoolean();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundBuildFailed(jobId, schematic, clientRequestId, reason, details, rollback, nonce, serverTime);
    }

    @Override
    public Type<ClientboundBuildFailed> type() {
        return TYPE;
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        EasyBuildClientState.get().recordBuildFailed(this);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("easybuild.job.failed", jobId, reasonCode, details), true);
        }
    }
}
