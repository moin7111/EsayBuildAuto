package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server handshake message sent once when joining a modded server.
 */
public record ServerboundHelloHandshake(
        UUID playerUuid,
        String clientVersion,
        String protocolVersion,
        List<String> clientCapabilities,
        long nonce
) {

    public ServerboundHelloHandshake {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(clientVersion, "clientVersion");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(clientCapabilities, "clientCapabilities");
    }

    public static void encode(ServerboundHelloHandshake message, FriendlyByteBuf buf) {
        buf.writeUUID(message.playerUuid);
        buf.writeUtf(message.clientVersion);
        buf.writeUtf(message.protocolVersion);
        FriendlyByteBufUtil.writeStringList(buf, message.clientCapabilities);
        buf.writeLong(message.nonce);
    }

    public static ServerboundHelloHandshake decode(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        String clientVersion = buf.readUtf();
        String protocolVersion = buf.readUtf();
        List<String> capabilities = new ArrayList<>(FriendlyByteBufUtil.readStringList(buf));
        long nonce = buf.readLong();
        return new ServerboundHelloHandshake(uuid, clientVersion, protocolVersion, capabilities, nonce);
    }

    public static void handle(ServerboundHelloHandshake message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // TODO: validate handshake and respond with server capabilities.
        });
        context.setPacketHandled(true);
    }
}
