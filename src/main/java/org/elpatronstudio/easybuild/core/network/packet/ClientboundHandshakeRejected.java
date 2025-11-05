package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import org.elpatronstudio.easybuild.client.ClientHandshakeState;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;

import java.util.Objects;

/**
 * Server → Client message indicating that the handshake failed.
 */
public record ClientboundHandshakeRejected(
        String reason,
        String requiredProtocol,
        long serverTime
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("handshake_rejected");
    public static final Type<ClientboundHandshakeRejected> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundHandshakeRejected> STREAM_CODEC =
            StreamCodec.of(ClientboundHandshakeRejected::write, ClientboundHandshakeRejected::read);

    public ClientboundHandshakeRejected {
        Objects.requireNonNull(reason, "reason");
        if (requiredProtocol == null) {
            requiredProtocol = "";
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundHandshakeRejected message) {
        buf.writeUtf(message.reason);
        buf.writeUtf(message.requiredProtocol);
        buf.writeLong(message.serverTime);
    }

    private static ClientboundHandshakeRejected read(RegistryFriendlyByteBuf buf) {
        String reason = buf.readUtf();
        String required = buf.readUtf();
        long serverTime = buf.readLong();
        return new ClientboundHandshakeRejected(reason, required, serverTime);
    }

    @Override
    public Type<ClientboundHandshakeRejected> type() {
        return TYPE;
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientHandshakeState.get().recordFailure(reason, requiredProtocol);
        if (minecraft.player != null) {
            String required = requiredProtocol == null || requiredProtocol.isBlank()
                    ? EasyBuildNetwork.supportedProtocolSummary()
                    : requiredProtocol;
            minecraft.player.displayClientMessage(Component.translatable("message.easybuild.handshake.failed", reason, required), true);
        }
    }
}
