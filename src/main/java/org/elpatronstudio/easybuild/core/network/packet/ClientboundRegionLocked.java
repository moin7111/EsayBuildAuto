package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;

import java.util.Objects;
import java.util.UUID;

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
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("region_locked");
    public static final Type<ClientboundRegionLocked> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRegionLocked> STREAM_CODEC =
            StreamCodec.of(ClientboundRegionLocked::write, ClientboundRegionLocked::read);

    public ClientboundRegionLocked {
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(lockingJobId, "lockingJobId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        if (ownerName == null) {
            ownerName = "";
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundRegionLocked message) {
        FriendlyByteBufUtil.writeSchematicRef(buf, message.schematic);
        buf.writeUtf(message.lockingJobId);
        buf.writeUUID(message.ownerUuid);
        buf.writeUtf(message.ownerName);
        buf.writeLong(message.etaTicks);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    private static ClientboundRegionLocked read(RegistryFriendlyByteBuf buf) {
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        String jobId = buf.readUtf();
        UUID ownerUuid = buf.readUUID();
        String ownerName = buf.readUtf();
        long etaTicks = buf.readLong();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundRegionLocked(schematic, jobId, ownerUuid, ownerName, etaTicks, nonce, serverTime);
    }

    @Override
    public Type<ClientboundRegionLocked> type() {
        return TYPE;
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        String ownerLabel = ownerName().isBlank() ? ownerUuid().toString() : ownerName();
        long etaTicks = etaTicks();
        String etaText = etaTicks <= 0 ? "unbekannt" : etaTicks + " Ticks";

        minecraft.player.displayClientMessage(Component.literal("[EasyBuild] Region gesperrt durch " + ownerLabel + " – voraussichtlich " + etaText + "."), true);
    }
}
