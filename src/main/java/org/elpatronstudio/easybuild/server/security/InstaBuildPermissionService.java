package org.elpatronstudio.easybuild.server.security;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.PlayerTeam;
import org.elpatronstudio.esaybuildauto.Config;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundRequestBuild;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates server-side Insta-Build permissions and records audit logs.
 */
public final class InstaBuildPermissionService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final InstaBuildPermissionService INSTANCE = new InstaBuildPermissionService();

    private InstaBuildPermissionService() {
    }

    public static InstaBuildPermissionService get() {
        return INSTANCE;
    }

    public PermissionResult check(ServerPlayer player, ServerboundRequestBuild request) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");

        PermissionResult result = evaluate(player);
        logDecision(player, request, result);
        return result;
    }

    private PermissionResult evaluate(ServerPlayer player) {
        boolean whitelistMatch = isWhitelisted(player);
        boolean roleMatch = hasAllowedRole(player);
        boolean permissionSatisfied = Config.serverInstaBuildMinPermissionLevel <= 0
                || player.hasPermissions(Config.serverInstaBuildMinPermissionLevel);

        if (!Config.serverInstaBuildEnabled) {
            return new PermissionResult(false,
                    "Server hat Insta-Build deaktiviert.",
                    Decision.FEATURE_DISABLED,
                    permissionSatisfied,
                    whitelistMatch,
                    roleMatch);
        }

        if (whitelistMatch) {
            return new PermissionResult(true,
                    "Spieler befindet sich auf der Insta-Build-Whitelist.",
                    Decision.PLAYER_WHITELIST,
                    permissionSatisfied,
                    true,
                    roleMatch);
        }

        if (roleMatch) {
            return new PermissionResult(true,
                    "Spieler erfüllt Rollen- oder Teamkriterium.",
                    Decision.ROLE_MATCH,
                    permissionSatisfied,
                    whitelistMatch,
                    true);
        }

        if (permissionSatisfied && !Config.serverInstaBuildRequireWhitelist) {
            return new PermissionResult(true,
                    "Spieler erfüllt die Mindestberechtigungsstufe.",
                    Decision.PERMISSION_LEVEL,
                    true,
                    false,
                    false);
        }

        if (!permissionSatisfied) {
            return new PermissionResult(false,
                    "Unzureichende Berechtigungsstufe für Insta-Build.",
                    Decision.DENIED_INSUFFICIENT_PERMISSION,
                    false,
                    whitelistMatch,
                    roleMatch);
        }

        return new PermissionResult(false,
                "Whitelist erforderlich, Spieler nicht eingetragen.",
                Decision.DENIED_WHITELIST_REQUIRED,
                true,
                whitelistMatch,
                roleMatch);
    }

    private boolean isWhitelisted(ServerPlayer player) {
        if (Config.serverInstaBuildPlayerWhitelistUuids.contains(player.getUUID())) {
            return true;
        }
        String name = player.getGameProfile().name();
        if (name == null) {
            return false;
        }
        return Config.serverInstaBuildPlayerWhitelistNames.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean hasAllowedRole(ServerPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team != null) {
            String teamName = team.getName();
            if (teamName != null && Config.serverInstaBuildAllowedTeams.contains(teamName.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        if (Config.serverInstaBuildAllowedTags.isEmpty()) {
            return false;
        }

        Set<String> tags = player.getTags();
        for (String tag : tags) {
            if (Config.serverInstaBuildAllowedTags.contains(tag.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void logDecision(ServerPlayer player, ServerboundRequestBuild request, PermissionResult result) {
        String playerName = player.getGameProfile().name();
        String schematicId = request.schematic() != null ? request.schematic().schematicId() : "";
        String decisionSummary = String.format(Locale.ROOT,
                "Insta-Build %s für %s (%s) – Entscheidung=%s, Grund=%s",
                result.allowed() ? "genehmigt" : "verweigert",
                playerName,
                player.getUUID(),
                result.decision(),
                result.reason());

        if (result.allowed()) {
            LOGGER.info("{} (Anfrage={}, Modus={}, Whitelist={}, Rolle={}, PermissionOK={})",
                    decisionSummary,
                    request.requestId(),
                    request.mode(),
                    result.whitelistMatched(),
                    result.roleMatched(),
                    result.permissionSatisfied());
        } else {
            LOGGER.warn("{} (Anfrage={}, Modus={}, Whitelist={}, Rolle={}, PermissionOK={})",
                    decisionSummary,
                    request.requestId(),
                    request.mode(),
                    result.whitelistMatched(),
                    result.roleMatched(),
                    result.permissionSatisfied());
        }

        if (Config.serverInstaBuildAuditLog) {
            writeAuditEntry(player, request, result, schematicId);
        }
    }

    private void writeAuditEntry(ServerPlayer player, ServerboundRequestBuild request, PermissionResult result, String schematicId) {
        try {
            ServerLevel level = player.level() instanceof ServerLevel serverLevel ? serverLevel : null;
            if (level == null || level.getServer() == null) {
                return;
            }
            Path directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("easybuild");
            Files.createDirectories(directory);
            Path file = directory.resolve("insta_build_audit.log");

            String entry = String.format(Locale.ROOT,
                    "%s\t%s\t%s\tdecision=%s\tallowed=%s\treason=%s\trequest=%s\tschematic=%s\tmode=%s\tdimension=%s\tpos=%d,%d,%d%n",
                    Instant.now(),
                      player.getGameProfile().name(),
                    player.getUUID(),
                    result.decision(),
                    result.allowed(),
                    sanitize(result.reason()),
                    request.requestId(),
                    schematicId,
                    request.mode(),
                    request.anchor().dimension(),
                    request.anchor().x(),
                    request.anchor().y(),
                    request.anchor().z());

            Files.writeString(file, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            LOGGER.warn("Konnte Insta-Build-Audit-Log nicht schreiben", ex);
        }
    }

    private String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('\n', ' ').replace('\r', ' ').trim();
    }

    public record PermissionResult(boolean allowed,
                                   String reason,
                                   Decision decision,
                                   boolean permissionSatisfied,
                                   boolean whitelistMatched,
                                   boolean roleMatched) {
    }

    public enum Decision {
        FEATURE_DISABLED,
        PLAYER_WHITELIST,
        ROLE_MATCH,
        PERMISSION_LEVEL,
        DENIED_INSUFFICIENT_PERMISSION,
        DENIED_WHITELIST_REQUIRED
    }
}

