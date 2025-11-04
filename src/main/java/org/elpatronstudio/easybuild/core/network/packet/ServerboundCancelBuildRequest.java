package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.elpatronstudio.easybuild.server.job.BuildJobManager;

import java.util.Objects;
import java.util.UUID;

/**
 * Client → Server packet requesting cancellation of a running build job.
 */
public record ServerboundCancelBuildRequest(
        UUID playerUuid,
        String jobId,
        long nonce
) {

    public ServerboundCancelBuildRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(jobId, "jobId");
    }

    public static void encode(ServerboundCancelBuildRequest message, FriendlyByteBuf buf) {
        buf.writeUUID(message.playerUuid);
        buf.writeUtf(message.jobId);
        buf.writeLong(message.nonce);
    }

    public static ServerboundCancelBuildRequest decode(FriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        String jobId = buf.readUtf();
        long nonce = buf.readLong();
        return new ServerboundCancelBuildRequest(playerUuid, jobId, nonce);
    }

    public void handle(ServerPlayer player) {
        BuildJobManager.get().cancelJob(player, jobId);
    }
}
