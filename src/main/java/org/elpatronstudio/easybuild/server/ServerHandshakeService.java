package org.elpatronstudio.easybuild.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;
import org.elpatronstudio.easybuild.core.network.EasyBuildPacketSender;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundHandshakeRejected;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundHelloAcknowledge;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundHelloHandshake;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tracks EasyBuild handshake status for connected players.
 */
public final class ServerHandshakeService {

    private static final Map<UUID, HandshakeSession> SESSIONS = new ConcurrentHashMap<>();

    private ServerHandshakeService() {
    }

    public static void handleHello(ServerPlayer player, ServerboundHelloHandshake message) {
        if (player == null) {
            return;
        }

        if (!EasyBuildNetwork.PROTOCOL_VERSION.equals(message.protocolVersion())) {
            EasyBuildPacketSender.sendTo(player, new ClientboundHandshakeRejected(
                    "Protocol mismatch",
                    EasyBuildNetwork.PROTOCOL_VERSION,
                    System.currentTimeMillis()
            ));
            player.sendSystemMessage(Component.literal("[EasyBuild] Netzwerk-Version nicht kompatibel (Server: "
                    + EasyBuildNetwork.PROTOCOL_VERSION + ", Client: " + message.protocolVersion() + ")"));
            return;
        }

        long serverNonce = ThreadLocalRandom.current().nextLong();
        HandshakeSession session = new HandshakeSession(
                Instant.now(),
                message.clientVersion(),
                Set.copyOf(message.clientCapabilities()),
                message.nonce(),
                serverNonce
        );
        SESSIONS.put(player.getUUID(), session);

        EasyBuildPacketSender.sendTo(player, new ClientboundHelloAcknowledge(
                EasyBuildNetwork.PROTOCOL_VERSION,
                getServerVersion(),
                serverCapabilities(),
                currentConfigHash(),
                serverNonce,
                System.currentTimeMillis()
        ));

        player.sendSystemMessage(Component.literal("[EasyBuild] Handshake abgeschlossen (Version "
                + EasyBuildNetwork.PROTOCOL_VERSION + ")"));
    }

    public static HandshakeSession getSession(UUID uuid) {
        return SESSIONS.get(uuid);
    }

    public static void removeSession(UUID uuid) {
        SESSIONS.remove(uuid);
    }

    private static String getServerVersion() {
        // TODO: pull actual mod version from metadata.
        return "dev";
    }

    private static List<String> serverCapabilities() {
        // TODO: compute capabilities based on server configuration and installed integrations.
        return List.of("material_check", "step_paste");
    }

    private static String currentConfigHash() {
        // TODO: hash active EasyBuild server config when available.
        return "";
    }

    public record HandshakeSession(Instant handshakeAt,
                                   String clientVersion,
                                   Set<String> clientCapabilities,
                                   long clientNonce,
                                   long serverNonce) {
    }
}
