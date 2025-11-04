package org.elpatronstudio.easybuild.core.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Handles payload registration for EasyBuild's networking.
 */
public final class EasyBuildNetwork {

    public static final String PROTOCOL_VERSION = "1";

    private EasyBuildNetwork() {
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(ServerboundHelloHandshake.TYPE, ServerboundHelloHandshake.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> payload.handle((ServerPlayer) context.player())));

        registrar.playToServer(ServerboundMaterialCheckRequest.TYPE, ServerboundMaterialCheckRequest.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> payload.handle((ServerPlayer) context.player())));

        registrar.playToServer(ServerboundRequestBuild.TYPE, ServerboundRequestBuild.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> payload.handle((ServerPlayer) context.player())));

        registrar.playToServer(ServerboundCancelBuildRequest.TYPE, ServerboundCancelBuildRequest.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> payload.handle((ServerPlayer) context.player())));

        registrar.playToServer(ServerboundAcknowledgeStatus.TYPE, ServerboundAcknowledgeStatus.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> payload.handle((ServerPlayer) context.player())));

        registrar.playToClient(ClientboundHelloAcknowledge.TYPE, ClientboundHelloAcknowledge.STREAM_CODEC);
        registrar.playToClient(ClientboundHandshakeRejected.TYPE, ClientboundHandshakeRejected.STREAM_CODEC);
        registrar.playToClient(ClientboundMaterialCheckResponse.TYPE, ClientboundMaterialCheckResponse.STREAM_CODEC);
        registrar.playToClient(ClientboundMissingMaterials.TYPE, ClientboundMissingMaterials.STREAM_CODEC);
        registrar.playToClient(ClientboundBuildAccepted.TYPE, ClientboundBuildAccepted.STREAM_CODEC);
        registrar.playToClient(ClientboundProgressUpdate.TYPE, ClientboundProgressUpdate.STREAM_CODEC);
        registrar.playToClient(ClientboundBuildCompleted.TYPE, ClientboundBuildCompleted.STREAM_CODEC);
        registrar.playToClient(ClientboundBuildFailed.TYPE, ClientboundBuildFailed.STREAM_CODEC);
        registrar.playToClient(ClientboundRegionLocked.TYPE, ClientboundRegionLocked.STREAM_CODEC);
    }

    public static void onRegisterClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ClientboundHelloAcknowledge.TYPE, (payload, context) -> context.enqueueWork(payload::handleClient));
        event.register(ClientboundHandshakeRejected.TYPE, (payload, context) -> context.enqueueWork(payload::handleClient));
        event.register(ClientboundMaterialCheckResponse.TYPE, (payload, context) -> context.enqueueWork(payload::handleClient));
        event.register(ClientboundMissingMaterials.TYPE, (payload, context) -> context.enqueueWork(payload::handleClient));
        event.register(ClientboundBuildAccepted.TYPE, (payload, context) -> context.enqueueWork(payload::handleClient));
        event.register(ClientboundProgressUpdate.TYPE, (payload, context) -> context.enqueueWork(payload::handleClient));
        event.register(ClientboundBuildCompleted.TYPE, (payload, context) -> context.enqueueWork(payload::handleClient));
        event.register(ClientboundBuildFailed.TYPE, (payload, context) -> context.enqueueWork(payload::handleClient));
        event.register(ClientboundRegionLocked.TYPE, (payload, context) -> context.enqueueWork(payload::handleClient));
    }

    public static ResourceLocation payloadId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Esaybuildauto.MODID, path);
    }
}
