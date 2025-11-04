package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import org.elpatronstudio.easybuild.core.model.MaterialStack;

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
) {

    public ClientboundBuildCompleted {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(consumed, "consumed");
        if (logRef == null) {
            logRef = "";
        }
    }

    public static void encode(ClientboundBuildCompleted message, FriendlyByteBuf buf) {
        buf.writeUtf(message.jobId);
        buf.writeBoolean(message.success);
        FriendlyByteBufUtil.writeMaterialList(buf, message.consumed);
        buf.writeUtf(message.logRef);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    public static ClientboundBuildCompleted decode(FriendlyByteBuf buf) {
        String jobId = buf.readUtf();
        boolean success = buf.readBoolean();
        List<MaterialStack> consumed = FriendlyByteBufUtil.readMaterialList(buf);
        String logRef = buf.readUtf();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundBuildCompleted(jobId, success, consumed, logRef, nonce, serverTime);
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        // TODO: notify client UI of job completion and update logs/materials view.
    }
}
