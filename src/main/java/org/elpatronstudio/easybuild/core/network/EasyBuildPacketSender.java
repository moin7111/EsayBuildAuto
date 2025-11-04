package org.elpatronstudio.easybuild.core.network;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Helper for dispatching EasyBuild payloads to players.
 */
public final class EasyBuildPacketSender {

    private EasyBuildPacketSender() {
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null || player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    public static void sendTo(ServerLevel level, UUID playerUuid, CustomPacketPayload payload) {
        if (level == null) {
            return;
        }
        Optional.ofNullable(level.getServer().getPlayerList().getPlayer(playerUuid))
                .ifPresent(player -> sendTo(player, payload));
    }
}
