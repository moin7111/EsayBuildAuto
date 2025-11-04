package org.elpatronstudio.easybuild.core.network;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.NetworkDirection;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.SimpleChannel;
import org.elpatronstudio.esaybuildauto.Esaybuildauto;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundBuildAccepted;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundBuildCompleted;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundBuildFailed;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundHandshakeRejected;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundHelloAcknowledge;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundMaterialCheckResponse;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundMissingMaterials;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundProgressUpdate;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundRegionLocked;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundAcknowledgeStatus;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundCancelBuildRequest;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundHelloHandshake;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundMaterialCheckRequest;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundRequestBuild;

/**
 * Central registration point for EasyBuild network communication.
 */
public final class EasyBuildNetwork {

    public static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(Esaybuildauto.MODID, "core");

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_ID,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private EasyBuildNetwork() {
    }

    public static void register() {
        int id = 0;

        CHANNEL.messageBuilder(ServerboundHelloHandshake.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundHelloHandshake::encode)
                .decoder(ServerboundHelloHandshake::decode)
                .consumerMainThread(ServerboundHelloHandshake::handle)
                .add();

        CHANNEL.messageBuilder(ClientboundHelloAcknowledge.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundHelloAcknowledge::encode)
                .decoder(ClientboundHelloAcknowledge::decode)
                .consumerMainThread(ClientboundHelloAcknowledge::handle)
                .add();

        CHANNEL.messageBuilder(ClientboundHandshakeRejected.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundHandshakeRejected::encode)
                .decoder(ClientboundHandshakeRejected::decode)
                .consumerMainThread(ClientboundHandshakeRejected::handle)
                .add();

        CHANNEL.messageBuilder(ServerboundMaterialCheckRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundMaterialCheckRequest::encode)
                .decoder(ServerboundMaterialCheckRequest::decode)
                .consumerMainThread(ServerboundMaterialCheckRequest::handle)
                .add();

        CHANNEL.messageBuilder(ServerboundRequestBuild.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundRequestBuild::encode)
                .decoder(ServerboundRequestBuild::decode)
                .consumerMainThread(ServerboundRequestBuild::handle)
                .add();

        CHANNEL.messageBuilder(ServerboundCancelBuildRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundCancelBuildRequest::encode)
                .decoder(ServerboundCancelBuildRequest::decode)
                .consumerMainThread(ServerboundCancelBuildRequest::handle)
                .add();

        CHANNEL.messageBuilder(ServerboundAcknowledgeStatus.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundAcknowledgeStatus::encode)
                .decoder(ServerboundAcknowledgeStatus::decode)
                .consumerMainThread(ServerboundAcknowledgeStatus::handle)
                .add();

        CHANNEL.messageBuilder(ClientboundMaterialCheckResponse.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundMaterialCheckResponse::encode)
                .decoder(ClientboundMaterialCheckResponse::decode)
                .consumerMainThread(ClientboundMaterialCheckResponse::handle)
                .add();

        CHANNEL.messageBuilder(ClientboundMissingMaterials.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundMissingMaterials::encode)
                .decoder(ClientboundMissingMaterials::decode)
                .consumerMainThread(ClientboundMissingMaterials::handle)
                .add();

        CHANNEL.messageBuilder(ClientboundBuildAccepted.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundBuildAccepted::encode)
                .decoder(ClientboundBuildAccepted::decode)
                .consumerMainThread(ClientboundBuildAccepted::handle)
                .add();

        CHANNEL.messageBuilder(ClientboundProgressUpdate.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundProgressUpdate::encode)
                .decoder(ClientboundProgressUpdate::decode)
                .consumerMainThread(ClientboundProgressUpdate::handle)
                .add();

        CHANNEL.messageBuilder(ClientboundBuildCompleted.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundBuildCompleted::encode)
                .decoder(ClientboundBuildCompleted::decode)
                .consumerMainThread(ClientboundBuildCompleted::handle)
                .add();

        CHANNEL.messageBuilder(ClientboundBuildFailed.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundBuildFailed::encode)
                .decoder(ClientboundBuildFailed::decode)
                .consumerMainThread(ClientboundBuildFailed::handle)
                .add();

        CHANNEL.messageBuilder(ClientboundRegionLocked.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundRegionLocked::encode)
                .decoder(ClientboundRegionLocked::decode)
                .consumerMainThread(ClientboundRegionLocked::handle)
                .add();
    }

    public static SimpleChannel channel() {
        return CHANNEL;
    }
}
