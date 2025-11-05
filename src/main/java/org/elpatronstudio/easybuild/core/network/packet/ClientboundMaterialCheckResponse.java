package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;

import java.util.List;
import java.util.Objects;

/**
 * Server → Client response containing material availability results.
 */
public record ClientboundMaterialCheckResponse(
        SchematicRef schematic,
        boolean ok,
        List<MaterialStack> missing,
        boolean reserved,
        long reservationExpiresAt,
        long nonce,
        long serverTime
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("material_check_response");
    public static final Type<ClientboundMaterialCheckResponse> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMaterialCheckResponse> STREAM_CODEC =
            StreamCodec.of(ClientboundMaterialCheckResponse::write, ClientboundMaterialCheckResponse::read);

    public ClientboundMaterialCheckResponse {
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(missing, "missing");
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundMaterialCheckResponse message) {
        FriendlyByteBufUtil.writeSchematicRef(buf, message.schematic);
        buf.writeBoolean(message.ok);
        FriendlyByteBufUtil.writeMaterialList(buf, message.missing);
        buf.writeBoolean(message.reserved);
        buf.writeLong(message.reservationExpiresAt);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    private static ClientboundMaterialCheckResponse read(RegistryFriendlyByteBuf buf) {
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        boolean ok = buf.readBoolean();
        List<MaterialStack> missing = FriendlyByteBufUtil.readMaterialList(buf);
        boolean reserved = buf.readBoolean();
        long reservationExpiresAt = buf.readLong();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundMaterialCheckResponse(schematic, ok, missing, reserved, reservationExpiresAt, nonce, serverTime);
    }

    @Override
    public Type<ClientboundMaterialCheckResponse> type() {
        return TYPE;
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        EasyBuildClientState.get().recordMaterialCheck(this);
        if (minecraft.player != null) {
            if (ok) {
                minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("easybuild.materials.ok", schematic.schematicId()), false);
            } else {
                minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("easybuild.materials.missing", missing.size(), schematic.schematicId()), false);
            }
        }
    }
}
