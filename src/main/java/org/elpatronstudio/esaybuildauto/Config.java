package org.elpatronstudio.esaybuildauto;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import org.elpatronstudio.easybuild.core.model.PasteMode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue CLIENT_AUTOBUILD_ENABLED;
    private static final ModConfigSpec.ConfigValue<String> CLIENT_AUTOBUILD_SCHEMATIC;
    private static final ModConfigSpec.EnumValue<PasteMode> CLIENT_AUTOBUILD_MODE;
    private static final ModConfigSpec.BooleanValue CLIENT_AUTOBUILD_PLACE_AIR;
    private static final ModConfigSpec.IntValue CLIENT_AUTOBUILD_BLOCKS_PER_TICK;
    private static final ModConfigSpec.IntValue CLIENT_CHEST_SEARCH_RADIUS;
    private static final ModConfigSpec.IntValue CLIENT_CHEST_MAX_TARGETS;
    private static final DoubleValue CLIENT_PREVIEW_SCROLL_FINE_STEP;
    private static final DoubleValue CLIENT_PREVIEW_SCROLL_NORMAL_STEP;
    private static final DoubleValue CLIENT_PREVIEW_SCROLL_COARSE_STEP;
    private static final ModConfigSpec.BooleanValue SERVER_INSTA_BUILD_ENABLED;
    private static final ModConfigSpec.BooleanValue SERVER_INSTA_BUILD_REQUIRE_WHITELIST;
    private static final ModConfigSpec.IntValue SERVER_INSTA_BUILD_MIN_PERMISSION_LEVEL;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SERVER_INSTA_BUILD_PLAYER_WHITELIST;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SERVER_INSTA_BUILD_ALLOWED_TEAMS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SERVER_INSTA_BUILD_ALLOWED_TAGS;
    private static final ModConfigSpec.BooleanValue SERVER_INSTA_BUILD_AUDIT_LOG;

    public static final ModConfigSpec SPEC;

    public static boolean clientAutoBuildEnabled;
    public static String clientDefaultSchematic;
    public static PasteMode clientDefaultPasteMode;
    public static boolean clientPlaceAir;
    public static int clientBlocksPerTick;
    public static int clientChestSearchRadius;
    public static int clientChestMaxTargets;
    public static double clientPreviewScrollFineStep;
    public static double clientPreviewScrollNormalStep;
    public static double clientPreviewScrollCoarseStep;
    public static boolean serverInstaBuildEnabled;
    public static boolean serverInstaBuildRequireWhitelist;
    public static int serverInstaBuildMinPermissionLevel;
    public static Set<UUID> serverInstaBuildPlayerWhitelistUuids = Set.of();
    public static Set<String> serverInstaBuildPlayerWhitelistNames = Set.of();
    public static Set<String> serverInstaBuildAllowedTeams = Set.of();
    public static Set<String> serverInstaBuildAllowedTags = Set.of();
    public static boolean serverInstaBuildAuditLog;

    static {
        BUILDER.comment("Client settings").push("client");

        CLIENT_AUTOBUILD_ENABLED = BUILDER
                .comment("Enable client-side auto building when no EasyBuild server is present.")
                .define("autoBuildEnabled", true);

        CLIENT_AUTOBUILD_SCHEMATIC = BUILDER
                .comment("Default schematic resource location used for client auto building.")
                .define("defaultSchematic", "");

        CLIENT_AUTOBUILD_MODE = BUILDER
                .comment("Default paste mode for client auto building.")
                .defineEnum("defaultPasteMode", PasteMode.SIMULATED);

        CLIENT_AUTOBUILD_PLACE_AIR = BUILDER
                .comment("Whether client auto building should place air blocks to clear space.")
                .define("placeAir", false);

        CLIENT_AUTOBUILD_BLOCKS_PER_TICK = BUILDER
                .comment("Maximum block placement attempts per client tick when auto building.")
                .defineInRange("blocksPerTick", 4, 1, 32);

        CLIENT_CHEST_SEARCH_RADIUS = BUILDER
                .comment("Radius in blocks around the player to look for linked containers when auto building on the client.")
                .defineInRange("chestSearchRadius", 6, 1, 16);

        CLIENT_CHEST_MAX_TARGETS = BUILDER
                .comment("Maximum number of nearby containers to register for client auto building.")
                .defineInRange("chestMaxTargets", 8, 0, 32);

        BUILDER.comment("ALT + mouse wheel preview offset step sizes in blocks.").push("previewScroll");

        CLIENT_PREVIEW_SCROLL_FINE_STEP = BUILDER
                .comment("Step applied when holding Shift while adjusting the preview offset with ALT + scroll.")
                .defineInRange("fineStep", 0.25D, 0.0625D, 32.0D);

        CLIENT_PREVIEW_SCROLL_NORMAL_STEP = BUILDER
                .comment("Step applied when adjusting the preview offset with ALT + scroll without modifiers.")
                .defineInRange("normalStep", 1.0D, 0.0625D, 32.0D);

        CLIENT_PREVIEW_SCROLL_COARSE_STEP = BUILDER
                .comment("Step applied when holding Control while adjusting the preview offset with ALT + scroll.")
                .defineInRange("coarseStep", 4.0D, 0.0625D, 64.0D);

        BUILDER.pop();
        BUILDER.pop();

        BUILDER.comment("Server Insta-Build settings").push("server").push("instaBuild");

        SERVER_INSTA_BUILD_ENABLED = BUILDER
                .comment("Enable server-side Insta-Build requests.")
                .define("enabled", true);

        SERVER_INSTA_BUILD_REQUIRE_WHITELIST = BUILDER
                .comment("Require Insta-Build users to be explicitly whitelisted (otherwise operator permission level or role membership suffices).")
                .define("requireWhitelist", false);

        SERVER_INSTA_BUILD_MIN_PERMISSION_LEVEL = BUILDER
                .comment("Minimum vanilla permission level required for Insta-Build (0-4). Use 0 to disable the permission level requirement.")
                .defineInRange("minPermissionLevel", 2, 0, 4);

        SERVER_INSTA_BUILD_PLAYER_WHITELIST = BUILDER
                .comment("List of player names or UUIDs allowed to use Insta-Build regardless of permission level.")
                .defineListAllowEmpty("playerWhitelist", List.of(), Config::validatePlayerIdentifier);

        SERVER_INSTA_BUILD_ALLOWED_TEAMS = BUILDER
                .comment("Scoreboard team names whose members may use Insta-Build.")
                .defineListAllowEmpty("allowedTeams", List.of(), Config::validateRoleIdentifier);

        SERVER_INSTA_BUILD_ALLOWED_TAGS = BUILDER
                .comment("Entity tags that grant Insta-Build access when present on the player (via /tag).")
                .defineListAllowEmpty("allowedTags", List.of(), Config::validateRoleIdentifier);

        SERVER_INSTA_BUILD_AUDIT_LOG = BUILDER
                .comment("Write Insta-Build permission decisions to easybuild/insta_build_audit.log inside the world save.")
                .define("auditLog", true);

        BUILDER.pop();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        clientAutoBuildEnabled = CLIENT_AUTOBUILD_ENABLED.get();
        clientDefaultSchematic = CLIENT_AUTOBUILD_SCHEMATIC.get();
        clientDefaultPasteMode = CLIENT_AUTOBUILD_MODE.get();
        clientPlaceAir = CLIENT_AUTOBUILD_PLACE_AIR.get();
        clientBlocksPerTick = CLIENT_AUTOBUILD_BLOCKS_PER_TICK.get();
        clientChestSearchRadius = CLIENT_CHEST_SEARCH_RADIUS.get();
        clientChestMaxTargets = CLIENT_CHEST_MAX_TARGETS.get();
        clientPreviewScrollFineStep = CLIENT_PREVIEW_SCROLL_FINE_STEP.get();
        clientPreviewScrollNormalStep = CLIENT_PREVIEW_SCROLL_NORMAL_STEP.get();
        clientPreviewScrollCoarseStep = CLIENT_PREVIEW_SCROLL_COARSE_STEP.get();

        serverInstaBuildEnabled = SERVER_INSTA_BUILD_ENABLED.get();
        serverInstaBuildRequireWhitelist = SERVER_INSTA_BUILD_REQUIRE_WHITELIST.get();
        serverInstaBuildMinPermissionLevel = SERVER_INSTA_BUILD_MIN_PERMISSION_LEVEL.get();
        serverInstaBuildPlayerWhitelistUuids = SERVER_INSTA_BUILD_PLAYER_WHITELIST.get().stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(Config::parseUuidOrNull)
                .filter(uuid -> uuid != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        serverInstaBuildPlayerWhitelistNames = SERVER_INSTA_BUILD_PLAYER_WHITELIST.get().stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .filter(entry -> !isUuid(entry))
                .map(entry -> entry.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        serverInstaBuildAllowedTeams = SERVER_INSTA_BUILD_ALLOWED_TEAMS.get().stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> entry.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        serverInstaBuildAllowedTags = SERVER_INSTA_BUILD_ALLOWED_TAGS.get().stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> entry.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        serverInstaBuildAuditLog = SERVER_INSTA_BUILD_AUDIT_LOG.get();
    }

    private static boolean validatePlayerIdentifier(final Object obj) {
        if (!(obj instanceof String entry)) {
            return false;
        }
        String trimmed = entry.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (isUuid(trimmed)) {
            return true;
        }
        return trimmed.matches("^[A-Za-z0-9_.-]{3,16}$");
    }

    private static boolean validateRoleIdentifier(final Object obj) {
        if (!(obj instanceof String entry)) {
            return false;
        }
        return !entry.trim().isEmpty();
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static UUID parseUuidOrNull(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
