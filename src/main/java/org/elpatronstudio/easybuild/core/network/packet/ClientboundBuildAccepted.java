package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.elpatronstudio.easybuild.core.model.PasteMode;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;

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
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("build_accepted");
    public static final Type<ClientboundBuildAccepted> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundBuildAccepted> STREAM_CODEC =
            StreamCodec.of(ClientboundBuildAccepted::write, ClientboundBuildAccepted::read);

    public ClientboundBuildAccepted {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(reservationToken, "reservationToken");
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundBuildAccepted message) {
        buf.writeUtf(message.jobId);
        buf.writeEnum(message.mode);
        buf.writeLong(message.estimatedDurationTicks);
        buf.writeUUID(message.reservationToken);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    private static ClientboundBuildAccepted read(RegistryFriendlyByteBuf buf) {
        String jobId = buf.readUtf();
        PasteMode mode = buf.readEnum(PasteMode.class);
        long estimate = buf.readLong();
        UUID token = buf.readUUID();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundBuildAccepted(jobId, mode, estimate, token, nonce, serverTime);
    }

    @Override
    public Type<ClientboundBuildAccepted> type() {
        return TYPE;
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        // TODO: update client job tracker with new job entry.
    }
}
