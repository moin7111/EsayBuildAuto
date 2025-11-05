package org.elpatronstudio.easybuild.core.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.model.ChestRef;
import org.elpatronstudio.easybuild.core.model.MaterialStack;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;

import java.util.List;
import java.util.Objects;

/**
 * Server → Client notification that materials are missing for the requested schematic.
 */
public record ClientboundMissingMaterials(
        SchematicRef schematic,
        List<MaterialStack> missing,
        List<ChestRef> suggestedSources,
        long nonce,
        long serverTime
) implements CustomPacketPayload {

    public static final ResourceLocation ID = EasyBuildNetwork.payloadId("missing_materials");
    public static final Type<ClientboundMissingMaterials> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMissingMaterials> STREAM_CODEC =
            StreamCodec.of(ClientboundMissingMaterials::write, ClientboundMissingMaterials::read);

    public ClientboundMissingMaterials {
        Objects.requireNonNull(schematic, "schematic");
        Objects.requireNonNull(missing, "missing");
        Objects.requireNonNull(suggestedSources, "suggestedSources");
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundMissingMaterials message) {
        FriendlyByteBufUtil.writeSchematicRef(buf, message.schematic);
        FriendlyByteBufUtil.writeMaterialList(buf, message.missing);
        FriendlyByteBufUtil.writeChestList(buf, message.suggestedSources);
        buf.writeLong(message.nonce);
        buf.writeLong(message.serverTime);
    }

    private static ClientboundMissingMaterials read(RegistryFriendlyByteBuf buf) {
        SchematicRef schematic = FriendlyByteBufUtil.readSchematicRef(buf);
        List<MaterialStack> missing = FriendlyByteBufUtil.readMaterialList(buf);
        List<ChestRef> suggested = FriendlyByteBufUtil.readChestList(buf);
        long nonce = buf.readLong();
        long serverTime = buf.readLong();
        return new ClientboundMissingMaterials(schematic, missing, suggested, nonce, serverTime);
    }

    @Override
    public Type<ClientboundMissingMaterials> type() {
        return TYPE;
    }

    public void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        EasyBuildClientState.get().recordMissingMaterials(this);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("easybuild.materials.missing.detail", missing.size(), schematic.schematicId()), true);
        }
    }
}
