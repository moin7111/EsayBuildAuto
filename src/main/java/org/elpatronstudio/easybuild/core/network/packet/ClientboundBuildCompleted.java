package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;

import java.util.List;
import java.util.Objects;

/**
 * Server → Client message that signals the completion of a build job.
 */
public record ClientboundBuildCompleted(
        String jobId,
        boolean success,
        List<MaterialStack> consumed,
        String logRef,
        long nonce,
        long serverTime
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("build_completed");
    public static final Type<ClientboundBuildCompleted> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundBuildCompleted> STREAM_CODEC =
            StreamCodec.of(ClientboundBuildCompleted::write, ClientboundBuildCompleted::read);

    public ClientboundBuildCompleted {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(consumed, "consumed");
        if (logRef == null) {
            logRef = "";
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundBuildCompleted message) {
        buf.writeUtf(message.jobId);
        buf.writeBoolean(message.success);
        FriendlyByteBufUtil.writeMaterialList(buf, message.consumed);
        buf.writeUtf(message.logRef);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    private static ClientboundBuildCompleted read(RegistryFriendlyByteBuf buf) {
        String jobId = buf.readUtf();
        boolean success = buf.readBoolean();
        List<MaterialStack> consumed = FriendlyByteBufUtil.readMaterialList(buf);
        String logRef = buf.readUtf();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundBuildCompleted(jobId, success, consumed, logRef, nonce, serverTime);
    }

    @Override
    public Type<ClientboundBuildCompleted> type() {
        return TYPE;
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        // TODO: notify client UI of job completion and update logs/materials view.
    }
}
