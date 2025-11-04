package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.ChestRef;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;
import org.elpatronstudio.easybuild.server.material.MaterialCheckService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("material_check_request");
    public static final Type<ServerboundMaterialCheckRequest> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundMaterialCheckRequest> STREAM_CODEC =
            StreamCodec.of(ServerboundMaterialCheckRequest::write, ServerboundMaterialCheckRequest::read);

    public ServerboundMaterialCheckRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(chests, "chests");
        Objects.requireNonNull(clientEstimate, "clientEstimate");
    }

    private static void write(RegistryFriendlyByteBuf buf, ServerboundMaterialCheckRequest message) {
        buf.writeUUID(message.playerUuid);
        FriendlyByteBufUtil.writeSchematicRef(buf, message.schematic);
        FriendlyByteBufUtil.writeAnchor(buf, message.anchor);
        FriendlyByteBufUtil.writeChestList(buf, message.chests);
        FriendlyByteBufUtil.writeMaterialList(buf, message.clientEstimate);
        buf.writeLong(message.nonce);
    }

    private static ServerboundMaterialCheckRequest read(RegistryFriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        AnchorPos anchor = FriendlyByteBufUtil.readAnchor(buf);
        List<ChestRef> chests = FriendlyByteBufUtil.readChestList(buf);
        List<MaterialStack> estimate = FriendlyByteBufUtil.readMaterialList(buf);
        long nonce = buf.readLong();
        return new ServerboundMaterialCheckRequest(playerUuid, schematic, anchor, chests, estimate, nonce);
    }

    @Override
    public Type<ServerboundMaterialCheckRequest> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        MaterialCheckService.get().handle(player, this);
    }
}
