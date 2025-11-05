package org.elpatronstudio.easybuild.server;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;
import org.elpatronstudio.easybuild.core.network.EasyBuildPacketSender;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundHandshakeRejected;
import org.elpatronstudio.easybuild.core.network.packet.ClientboundHelloAcknowledge;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundHelloHandshake;
import org.elpatronstudio.easybuild.server.security.RequestSecurityManager;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tracks EasyBuild handshake status for connected players.
 */
public final class ServerHandshakeService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, HandshakeSession> SESSIONS = new ConcurrentHashMap<>();

    private ServerHandshakeService() {
    }

    public static void handleHello(ServerPlayer player, ServerboundHelloHandshake message) {
        if (player == null) {
            return;
        }

        if (!player.getUUID().equals(message.playerUuid())) {
            reject(player, "Identity mismatch", "HANDSHAKE_IDENTITY_MISMATCH");
            return;
        }

        long now = System.currentTimeMillis();
        RequestSecurityManager security = RequestSecurityManager.get();
        RequestSecurityManager.RateLimitResult rate = security.checkRateLimit(player.getUUID(), RequestSecurityManager.RequestType.HANDSHAKE, now);
        if (!rate.allowed()) {
            reject(player, "Rate limited (versuche es in " + formatSeconds(rate.retryAfterMs()) + "s erneut)", "HANDSHAKE_RATE_LIMIT");
            return;
        }

        RequestSecurityManager.NonceResult nonce = security.verifyNonce(player.getUUID(), RequestSecurityManager.RequestType.HANDSHAKE, message.nonce());
        if (!nonce.valid()) {
            reject(player, "Ungültiger Handshake-Nonce: " + nonce.reason(), "HANDSHAKE_REPLAY");
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

    private static void reject(ServerPlayer player, String detail, String logCode) {
        long now = System.currentTimeMillis();
        EasyBuildPacketSender.sendTo(player, new ClientboundHandshakeRejected(detail, EasyBuildNetwork.PROTOCOL_VERSION, now));
        player.sendSystemMessage(Component.literal("[EasyBuild] Handshake fehlgeschlagen: " + detail));
        LOGGER.debug("Handshake rejected for {} ({}): {}", player.getGameProfile().name(), logCode, detail);
    }

    private static String formatSeconds(long millis) {
        if (millis <= 0L) {
            return "0";
        }
        return String.format(Locale.ROOT, "%.1f", millis / 1000.0);
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
