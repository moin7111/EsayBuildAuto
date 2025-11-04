package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.elpatronstudio.easybuild.core.model.AcknowledgeStatusCode;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;
import org.elpatronstudio.easybuild.server.job.BuildJobManager;

import java.util.Objects;
import java.util.UUID;

/**
 * Client → Server acknowledgement of a received status update.
 */
public record ServerboundAcknowledgeStatus(
        UUID playerUuid,
        String jobId,
        AcknowledgeStatusCode statusCode,
        long nonce
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("ack_status");
    public static final Type<ServerboundAcknowledgeStatus> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundAcknowledgeStatus> STREAM_CODEC =
            StreamCodec.of(ServerboundAcknowledgeStatus::write, ServerboundAcknowledgeStatus::read);

    public ServerboundAcknowledgeStatus {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(statusCode, "statusCode");
    }

    private static void write(RegistryFriendlyByteBuf buf, ServerboundAcknowledgeStatus message) {
        buf.writeUUID(message.playerUuid);
        buf.writeUtf(message.jobId);
        buf.writeEnum(message.statusCode);
        buf.writeLong(message.nonce);
    }

    private static ServerboundAcknowledgeStatus read(RegistryFriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        String jobId = buf.readUtf();
        AcknowledgeStatusCode statusCode = buf.readEnum(AcknowledgeStatusCode.class);
        long nonce = buf.readLong();
        return new ServerboundAcknowledgeStatus(playerUuid, jobId, statusCode, nonce);
    }

    @Override
    public Type<ServerboundAcknowledgeStatus> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        BuildJobManager.get().acknowledgeStatus(player, this);
    }
}
