package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;
import org.elpatronstudio.easybuild.server.job.BuildJobManager;

import java.util.Objects;
import java.util.UUID;

/**
 * Client → Server packet requesting cancellation of a running build job.
 */
public record ServerboundCancelBuildRequest(
        UUID playerUuid,
        String jobId,
        long nonce
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("cancel_build_request");
    public static final Type<ServerboundCancelBuildRequest> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundCancelBuildRequest> STREAM_CODEC =
            StreamCodec.of(ServerboundCancelBuildRequest::write, ServerboundCancelBuildRequest::read);

    public ServerboundCancelBuildRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(jobId, "jobId");
    }

    private static void write(RegistryFriendlyByteBuf buf, ServerboundCancelBuildRequest message) {
        buf.writeUUID(message.playerUuid);
        buf.writeUtf(message.jobId);
        buf.writeLong(message.nonce);
    }

    private static ServerboundCancelBuildRequest read(RegistryFriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        String jobId = buf.readUtf();
        long nonce = buf.readLong();
        return new ServerboundCancelBuildRequest(playerUuid, jobId, nonce);
    }

    @Override
    public Type<ServerboundCancelBuildRequest> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        BuildJobManager.get().cancelJob(player, this);
    }
}
