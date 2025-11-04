package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Server → Client message describing a failed build execution.
 */
public record ClientboundBuildFailed(
        String jobId,
        String reasonCode,
        String details,
        boolean rollbackPerformed,
        long nonce,
        long serverTime
) {

    public ClientboundBuildFailed {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(reasonCode, "reasonCode");
        if (details == null) {
            details = "";
        }
    }

    public static void encode(ClientboundBuildFailed message, FriendlyByteBuf buf) {
        buf.writeUtf(message.jobId);
        buf.writeUtf(message.reasonCode);
        buf.writeUtf(message.details);
        buf.writeBoolean(message.rollbackPerformed);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    public static ClientboundBuildFailed decode(FriendlyByteBuf buf) {
        String jobId = buf.readUtf();
        String reason = buf.readUtf();
        String details = buf.readUtf();
        boolean rollback = buf.readBoolean();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundBuildFailed(jobId, reason, details, rollback, nonce, serverTime);
    }

    public static void handle(ClientboundBuildFailed message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            // TODO: display failure dialog / notifications to the user.
        });
        context.setPacketHandled(true);
    }
}
