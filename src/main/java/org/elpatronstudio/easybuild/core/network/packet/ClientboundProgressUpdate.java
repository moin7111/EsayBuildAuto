package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import org.elpatronstudio.easybuild.core.model.JobPhase;

import java.util.Objects;

/**
 * Server → Client message describing the state of an outstanding build job.
 */
public record ClientboundProgressUpdate(
        String jobId,
        int placed,
        int total,
        JobPhase phase,
        String message,
        long nonce,
        long serverTime
) {

    public ClientboundProgressUpdate {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(phase, "phase");
        if (message == null) {
            message = "";
        }
    }

    public static void encode(ClientboundProgressUpdate message, FriendlyByteBuf buf) {
        buf.writeUtf(message.jobId);
        buf.writeVarInt(message.placed);
        buf.writeVarInt(message.total);
        buf.writeEnum(message.phase);
        buf.writeUtf(message.message);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    public static ClientboundProgressUpdate decode(FriendlyByteBuf buf) {
        String jobId = buf.readUtf();
        int placed = buf.readVarInt();
        int total = buf.readVarInt();
        JobPhase phase = buf.readEnum(JobPhase.class);
        String message = buf.readUtf();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundProgressUpdate(jobId, placed, total, phase, message, nonce, serverTime);
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        // TODO: update client HUD / progress tracker with new values.
    }
}
