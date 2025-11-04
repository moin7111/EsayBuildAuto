package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.elpatronstudio.easybuild.core.model.AcknowledgeStatusCode;
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
) {

    public ServerboundAcknowledgeStatus {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(statusCode, "statusCode");
    }

    public static void encode(ServerboundAcknowledgeStatus message, FriendlyByteBuf buf) {
        buf.writeUUID(message.playerUuid);
        buf.writeUtf(message.jobId);
        buf.writeEnum(message.statusCode);
        buf.writeLong(message.nonce);
    }

    public static ServerboundAcknowledgeStatus decode(FriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        String jobId = buf.readUtf();
        AcknowledgeStatusCode statusCode = buf.readEnum(AcknowledgeStatusCode.class);
        long nonce = buf.readLong();
        return new ServerboundAcknowledgeStatus(playerUuid, jobId, statusCode, nonce);
    }

    public void handle(ServerPlayer player) {
        BuildJobManager.get().acknowledgeStatus(player, this);
    }
}
