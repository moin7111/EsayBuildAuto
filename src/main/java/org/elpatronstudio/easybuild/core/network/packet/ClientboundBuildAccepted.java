package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import org.elpatronstudio.easybuild.core.model.PasteMode;

import java.util.Objects;
import java.util.UUID;

/**
 * Server → Client message acknowledging that a build job has been accepted and queued.
 */
public record ClientboundBuildAccepted(
        String jobId,
        PasteMode mode,
        long estimatedDurationTicks,
        UUID reservationToken,
        long nonce,
        long serverTime
) {

    public ClientboundBuildAccepted {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(reservationToken, "reservationToken");
    }

    public static void encode(ClientboundBuildAccepted message, FriendlyByteBuf buf) {
        buf.writeUtf(message.jobId);
        buf.writeEnum(message.mode);
        buf.writeLong(message.estimatedDurationTicks);
        buf.writeUUID(message.reservationToken);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    public static ClientboundBuildAccepted decode(FriendlyByteBuf buf) {
        String jobId = buf.readUtf();
        PasteMode mode = buf.readEnum(PasteMode.class);
        long estimate = buf.readLong();
        UUID token = buf.readUUID();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundBuildAccepted(jobId, mode, estimate, token, nonce, serverTime);
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        // TODO: update client job tracker with new job entry.
    }
}
