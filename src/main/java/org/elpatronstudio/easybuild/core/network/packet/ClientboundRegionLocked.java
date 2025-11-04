package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;
import org.elpatronstudio.easybuild.core.model.SchematicRef;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client message informing that a region is currently locked by another job.
 */
public record ClientboundRegionLocked(
        SchematicRef schematic,
        String lockingJobId,
        UUID ownerUuid,
        String ownerName,
        long etaTicks,
        long nonce,
        long serverTime
) {

    public ClientboundRegionLocked {
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(lockingJobId, "lockingJobId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        if (ownerName == null) {
            ownerName = "";
        }
    }

    public static void encode(ClientboundRegionLocked message, FriendlyByteBuf buf) {
        FriendlyByteBufUtil.writeSchematicRef(buf, message.schematic);
        buf.writeUtf(message.lockingJobId);
        buf.writeUUID(message.ownerUuid);
        buf.writeUtf(message.ownerName);
        buf.writeLong(message.etaTicks);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    public static ClientboundRegionLocked decode(FriendlyByteBuf buf) {
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        String jobId = buf.readUtf();
        UUID ownerUuid = buf.readUUID();
        String ownerName = buf.readUtf();
        long etaTicks = buf.readLong();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundRegionLocked(schematic, jobId, ownerUuid, ownerName, etaTicks, nonce, serverTime);
    }

    public static void handle(ClientboundRegionLocked message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            // TODO: inform client about region lock state (e.g., toast or HUD banner).
        });
        context.setPacketHandled(true);
    }
}
