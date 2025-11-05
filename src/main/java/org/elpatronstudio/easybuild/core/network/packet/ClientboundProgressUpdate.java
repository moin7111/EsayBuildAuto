package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.elpatronstudio.easybuild.core.model.JobPhase;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;

import java.util.Objects;

/**
 * Server → Client message describing the state of an outstanding build job.
 */
public record ClientboundProgressUpdate(
        String jobId,
        SchematicRef schematic,
        int placed,
        int total,
        JobPhase phase,
        String message,
        long nonce,
        long serverTime
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("progress_update");
    public static final Type<ClientboundProgressUpdate> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundProgressUpdate> STREAM_CODEC =
        StreamCodec.of(ClientboundProgressUpdate::write, ClientboundProgressUpdate::read);

    public ClientboundProgressUpdate {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(phase, "phase");
        if (message == null) {
            message = "";
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundProgressUpdate value) {
        buf.writeUtf(value.jobId);
        FriendlyByteBufUtil.writeSchematicRef(buf, value.schematic);
        buf.writeVarInt(value.placed);
        buf.writeVarInt(value.total);
        buf.writeEnum(value.phase);
        buf.writeUtf(value.message);
        buf.writeLong(value.nonce);
        buf.writeLong(value.serverTime);
    }

    private static ClientboundProgressUpdate read(RegistryFriendlyByteBuf buf) {
        String jobId = buf.readUtf();
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        int placed = buf.readVarInt();
        int total = buf.readVarInt();
        JobPhase phase = buf.readEnum(JobPhase.class);
        String message = buf.readUtf();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundProgressUpdate(jobId, schematic, placed, total, phase, message, nonce, serverTime);
    }

    @Override
    public Type<ClientboundProgressUpdate> type() {
        return TYPE;
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        EasyBuildClientState.get().recordProgressUpdate(this);
        if (minecraft.player != null && !message.isBlank()) {
            minecraft.player.displayClientMessage(Component.literal("[EasyBuild] " + message), true);
        }
    }
}
