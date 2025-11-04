package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.model.SchematicRef;

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
) {

    public ClientboundMaterialCheckResponse {
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(missing, "missing");
    }

    public static void encode(ClientboundMaterialCheckResponse message, FriendlyByteBuf buf) {
        FriendlyByteBufUtil.writeSchematicRef(buf, message.schematic);
        buf.writeBoolean(message.ok);
        FriendlyByteBufUtil.writeMaterialList(buf, message.missing);
        buf.writeBoolean(message.reserved);
        buf.writeLong(message.reservationExpiresAt);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    public static ClientboundMaterialCheckResponse decode(FriendlyByteBuf buf) {
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        boolean ok = buf.readBoolean();
        List<MaterialStack> missing = FriendlyByteBufUtil.readMaterialList(buf);
        boolean reserved = buf.readBoolean();
        long reservationExpiresAt = buf.readLong();
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundMaterialCheckResponse(schematic, ok, missing, reserved, reservationExpiresAt, nonce, serverTime);
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        // TODO: route response to client controller / UI state.
    }
}
