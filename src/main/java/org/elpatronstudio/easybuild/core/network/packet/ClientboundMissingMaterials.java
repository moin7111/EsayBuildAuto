package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.NetworkEvent;
import org.elpatronstudio.easybuild.core.model.ChestRef;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.model.SchematicRef;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Server → Client notification that materials are missing for the requested schematic.
 */
public record ClientboundMissingMaterials(
        SchematicRef schematic,
        List<MaterialStack> missing,
        List<ChestRef> suggestedSources,
        long nonce,
        long serverTime
) {

    public ClientboundMissingMaterials {
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(missing, "missing");
        Objects.requireNonNull(suggestedSources, "suggestedSources");
    }

    public static void encode(ClientboundMissingMaterials message, FriendlyByteBuf buf) {
        FriendlyByteBufUtil.writeSchematicRef(buf, message.schematic);
        FriendlyByteBufUtil.writeMaterialList(buf, message.missing);
        FriendlyByteBufUtil.writeChestList(buf, message.suggestedSources);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    public static ClientboundMissingMaterials decode(FriendlyByteBuf buf) {
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        List<MaterialStack> missing = FriendlyByteBufUtil.readMaterialList(buf);
        List<ChestRef> suggested = FriendlyByteBufUtil.readChestList(buf);
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundMissingMaterials(schematic, missing, suggested, nonce, serverTime);
    }

    public static void handle(ClientboundMissingMaterials message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            // TODO: surface missing materials UI update.
        });
        context.setPacketHandled(true);
    }
}
