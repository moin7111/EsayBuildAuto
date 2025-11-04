package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

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

    public static void handle(ServerboundCancelBuildRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // TODO: cancel the referenced job on the server-side job queue.
        });
        context.setPacketHandled(true);
    }
}
