package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;
import org.elpatronstudio.easybuild.core.model.AcknowledgeStatusCode;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

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

    public static void handle(ServerboundAcknowledgeStatus message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // TODO: update acknowledgement state on server job tracking.
        });
        context.setPacketHandled(true);
    }
}
