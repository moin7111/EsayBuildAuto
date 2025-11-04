package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;
import org.elpatronstudio.easybuild.server.ServerHandshakeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Client → Server handshake message sent once when joining a modded server.
 */
public record ServerboundHelloHandshake(
        UUID playerUuid,
        String clientVersion,
        String protocolVersion,
        List<String> clientCapabilities,
        long nonce
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("hello_handshake");
    public static final Type<ServerboundHelloHandshake> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundHelloHandshake> STREAM_CODEC =
            StreamCodec.of(ServerboundHelloHandshake::write, ServerboundHelloHandshake::read);

    public ServerboundHelloHandshake {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(clientVersion, "clientVersion");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(clientCapabilities, "clientCapabilities");
    }

    private static void write(RegistryFriendlyByteBuf buf, ServerboundHelloHandshake message) {
        buf.writeUUID(message.playerUuid);
        buf.writeUtf(message.clientVersion);
        buf.writeUtf(message.protocolVersion);
        FriendlyByteBufUtil.writeStringList(buf, message.clientCapabilities);
        buf.writeLong(message.nonce);
    }

    private static ServerboundHelloHandshake read(RegistryFriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        String clientVersion = buf.readUtf();
        String protocolVersion = buf.readUtf();
        List<String> capabilities = new ArrayList<>(FriendlyByteBufUtil.readStringList(buf));
        long nonce = buf.readLong();
        return new ServerboundHelloHandshake(uuid, clientVersion, protocolVersion, capabilities, nonce);
    }

    @Override
    public Type<ServerboundHelloHandshake> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        ServerHandshakeService.handleHello(player, this);
    }
}
