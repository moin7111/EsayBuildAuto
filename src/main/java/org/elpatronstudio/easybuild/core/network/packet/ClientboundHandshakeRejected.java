package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Server → Client message indicating that the handshake failed.
 */
public record ClientboundHandshakeRejected(
        String reason,
        String requiredProtocol,
        long serverTime
) {

    public ClientboundHandshakeRejected {
        Objects.requireNonNull(reason, "reason");
        if (requiredProtocol == null) {
            requiredProtocol = "";
        }
    }

    public static void encode(ClientboundHandshakeRejected message, FriendlyByteBuf buf) {
        buf.writeUtf(message.reason);
        buf.writeUtf(message.requiredProtocol);
        buf.writeLong(message.serverTime);
    }

    public static ClientboundHandshakeRejected decode(FriendlyByteBuf buf) {
        String reason = buf.readUtf();
        String required = buf.readUtf();
        long serverTime = buf.readLong();
        return new ClientboundHandshakeRejected(reason, required, serverTime);
    }

    public static void handle(ClientboundHandshakeRejected message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            // TODO: show error to user and disable server-backed features.
        });
        context.setPacketHandled(true);
    }
}
