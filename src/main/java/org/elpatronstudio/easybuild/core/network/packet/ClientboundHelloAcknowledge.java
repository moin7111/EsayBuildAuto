package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Objects;

/**
 * Server → Client acknowledgement of the EasyBuild handshake.
 */
public record ClientboundHelloAcknowledge(
        String protocolVersion,
        String serverVersion,
        List<String> serverCapabilities,
        String configHash,
        long nonce,
        long serverTime
) {

    public ClientboundHelloAcknowledge {
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(serverVersion, "serverVersion");
        Objects.requireNonNull(serverCapabilities, "serverCapabilities");
        if (configHash == null) {
            configHash = "";
        }
    }

    public static void encode(ClientboundHelloAcknowledge message, FriendlyByteBuf buf) {
        buf.writeUtf(message.protocolVersion);
        buf.writeUtf(message.serverVersion);
        FriendlyByteBufUtil.writeStringList(buf, message.serverCapabilities);
        buf.writeUtf(message.configHash);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    public static ClientboundHelloAcknowledge decode(FriendlyByteBuf buf) {
        String protocolVersion = buf.readUtf();
        String serverVersion = buf.readUtf();
        List<String> serverCapabilities = FriendlyByteBufUtil.readStringList(buf);
        String configHash = buf.readUtf();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundHelloAcknowledge(protocolVersion, serverVersion, serverCapabilities, configHash, nonce, serverTime);
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        // TODO: store server capabilities and notify client controllers of connected state.
    }
}
