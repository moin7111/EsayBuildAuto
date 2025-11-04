package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.ChestRef;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.model.SchematicRef;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server packet to validate materials for a schematic.
 */
public record ServerboundMaterialCheckRequest(
        UUID playerUuid,
        SchematicRef schematic,
        AnchorPos anchor,
        List<ChestRef> chests,
        List<MaterialStack> clientEstimate,
        long nonce
) {

    public ServerboundMaterialCheckRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(chests, "chests");
        Objects.requireNonNull(clientEstimate, "clientEstimate");
    }

    public static void encode(ServerboundMaterialCheckRequest message, FriendlyByteBuf buf) {
        buf.writeUUID(message.playerUuid);
        FriendlyByteBufUtil.writeSchematicRef(buf, message.schematic);
        FriendlyByteBufUtil.writeAnchor(buf, message.anchor);
        FriendlyByteBufUtil.writeChestList(buf, message.chests);
        FriendlyByteBufUtil.writeMaterialList(buf, message.clientEstimate);
        buf.writeLong(message.nonce);
    }

    public static ServerboundMaterialCheckRequest decode(FriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        AnchorPos anchor = FriendlyByteBufUtil.readAnchor(buf);
        List<ChestRef> chests = FriendlyByteBufUtil.readChestList(buf);
        List<MaterialStack> estimate = FriendlyByteBufUtil.readMaterialList(buf);
        long nonce = buf.readLong();
        return new ServerboundMaterialCheckRequest(playerUuid, schematic, anchor, chests, estimate, nonce);
    }

    public static void handle(ServerboundMaterialCheckRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // TODO: implement server-side material validation pipeline.
        });
        context.setPacketHandled(true);
    }
}
