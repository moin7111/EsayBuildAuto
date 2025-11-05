package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import org.elpatronstudio.easybuild.client.ClientHandshakeState;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;

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
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("hello_ack");
    public static final Type<ClientboundHelloAcknowledge> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundHelloAcknowledge> STREAM_CODEC =
            StreamCodec.of(ClientboundHelloAcknowledge::write, ClientboundHelloAcknowledge::read);

    public ClientboundHelloAcknowledge {
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(serverVersion, "serverVersion");
        Objects.requireNonNull(serverCapabilities, "serverCapabilities");
        if (configHash == null) {
            configHash = "";
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundHelloAcknowledge message) {
        buf.writeUtf(message.protocolVersion);
        buf.writeUtf(message.serverVersion);
        FriendlyByteBufUtil.writeStringList(buf, message.serverCapabilities);
        buf.writeUtf(message.configHash);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    private static ClientboundHelloAcknowledge read(RegistryFriendlyByteBuf buf) {
        String protocolVersion = buf.readUtf();
        String serverVersion = buf.readUtf();
        List<String> serverCapabilities = FriendlyByteBufUtil.readStringList(buf);
        String configHash = buf.readUtf();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundHelloAcknowledge(protocolVersion, serverVersion, serverCapabilities, configHash, nonce, serverTime);
    }

    @Override
    public Type<ClientboundHelloAcknowledge> type() {
        return TYPE;
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientHandshakeState.get().recordSuccess(protocolVersion, serverVersion, serverCapabilities, configHash, nonce, serverTime);
        if (minecraft.player != null) {
            String capabilitySummary = serverCapabilities.isEmpty() ? "keine zusätzlichen" : String.join(", ", serverCapabilities);
            minecraft.player.displayClientMessage(Component.literal("[EasyBuild] Handshake ok – Server " + serverVersion
                    + " (Protokoll " + protocolVersion + ") – Features: " + capabilitySummary + "."), false);
        }
    }
}
