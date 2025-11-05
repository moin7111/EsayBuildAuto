package org.elpatronstudio.esaybuildauto;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.elpatronstudio.easybuild.core.model.PasteMode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER.comment("Whether to log the dirt block on common setup").define("logDirtBlock", true);

    private static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER.comment("A magic number").defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER.comment("What you want the introduction message to be for the magic number").define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER.comment("A list of items to log on common setup.").defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    private static final ModConfigSpec.BooleanValue CLIENT_AUTOBUILD_ENABLED = BUILDER
            .comment("Enable client-side auto building when no EasyBuild server is present.")
            .define("client.autoBuildEnabled", true);

    private static final ModConfigSpec.ConfigValue<String> CLIENT_AUTOBUILD_SCHEMATIC = BUILDER
            .comment("Default schematic resource location used for client auto building.")
            .define("client.defaultSchematic", "esaybuildauto:example_structure");

    private static final ModConfigSpec.EnumValue<PasteMode> CLIENT_AUTOBUILD_MODE = BUILDER
            .comment("Default paste mode for client auto building.")
            .defineEnum("client.defaultPasteMode", PasteMode.SIMULATED);

    private static final ModConfigSpec.BooleanValue CLIENT_AUTOBUILD_PLACE_AIR = BUILDER
            .comment("Whether client auto building should place air blocks to clear space.")
            .define("client.placeAir", false);

    private static final ModConfigSpec.IntValue CLIENT_AUTOBUILD_BLOCKS_PER_TICK = BUILDER
            .comment("Maximum constructions attempts per client tick when auto building.")
            .defineInRange("client.blocksPerTick", 1, 1, 8);

    private static final ModConfigSpec.IntValue CLIENT_CHEST_SEARCH_RADIUS = BUILDER
            .comment("Radius in blocks around the player to look for linked containers when auto building on the client.")
            .defineInRange("client.chestSearchRadius", 6, 1, 16);

    private static final ModConfigSpec.IntValue CLIENT_CHEST_MAX_TARGETS = BUILDER
            .comment("Maximum number of nearby containers to register for client auto building.")
            .defineInRange("client.chestMaxTargets", 8, 0, 32);

    private static final ModConfigSpec.BooleanValue SERVER_INSTA_BUILD_ENABLED = BUILDER
            .comment("Enable server-side Insta-Build requests.")
            .define("server.instaBuild.enabled", true);

    private static final ModConfigSpec.BooleanValue SERVER_INSTA_BUILD_REQUIRE_WHITELIST = BUILDER
            .comment("Require Insta-Build users to be explicitly whitelisted (otherwise operator permission level or role membership suffices).")
            .define("server.instaBuild.requireWhitelist", false);

    private static final ModConfigSpec.IntValue SERVER_INSTA_BUILD_MIN_PERMISSION_LEVEL = BUILDER
            .comment("Minimum vanilla permission level required for Insta-Build (0-4). Use 0 to disable the permission level requirement.")
            .defineInRange("server.instaBuild.minPermissionLevel", 2, 0, 4);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> SERVER_INSTA_BUILD_PLAYER_WHITELIST = BUILDER
            .comment("List of player names or UUIDs allowed to use Insta-Build regardless of permission level.")
            .defineListAllowEmpty("server.instaBuild.playerWhitelist", List.of(), Config::validatePlayerIdentifier);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> SERVER_INSTA_BUILD_ALLOWED_TEAMS = BUILDER
            .comment("Scoreboard team names whose members may use Insta-Build.")
            .defineListAllowEmpty("server.instaBuild.allowedTeams", List.of(), Config::validateRoleIdentifier);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> SERVER_INSTA_BUILD_ALLOWED_TAGS = BUILDER
            .comment("Entity tags that grant Insta-Build access when present on the player (via /tag).")
            .defineListAllowEmpty("server.instaBuild.allowedTags", List.of(), Config::validateRoleIdentifier);

    private static final ModConfigSpec.BooleanValue SERVER_INSTA_BUILD_AUDIT_LOG = BUILDER
            .comment("Write Insta-Build permission decisions to easybuild/insta_build_audit.log inside the world save.")
            .define("server.instaBuild.auditLog", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;
    public static boolean clientAutoBuildEnabled;
    public static String clientDefaultSchematic;
    public static PasteMode clientDefaultPasteMode;
    public static boolean clientPlaceAir;
    public static int clientBlocksPerTick;
    public static int clientChestSearchRadius;
    public static int clientChestMaxTargets;
    public static boolean serverInstaBuildEnabled;
    public static boolean serverInstaBuildRequireWhitelist;
    public static int serverInstaBuildMinPermissionLevel;
    public static Set<UUID> serverInstaBuildPlayerWhitelistUuids = Set.of();
    public static Set<String> serverInstaBuildPlayerWhitelistNames = Set.of();
    public static Set<String> serverInstaBuildAllowedTeams = Set.of();
    public static Set<String> serverInstaBuildAllowedTags = Set.of();
    public static boolean serverInstaBuildAuditLog;

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    public static void onLoad(final ModConfigEvent event) {
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        items = ITEM_STRINGS.get().stream().map(itemName -> BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(itemName))).collect(Collectors.toSet());

        clientAutoBuildEnabled = CLIENT_AUTOBUILD_ENABLED.get();
        clientDefaultSchematic = CLIENT_AUTOBUILD_SCHEMATIC.get();
        clientDefaultPasteMode = CLIENT_AUTOBUILD_MODE.get();
        clientPlaceAir = CLIENT_AUTOBUILD_PLACE_AIR.get();
        clientBlocksPerTick = CLIENT_AUTOBUILD_BLOCKS_PER_TICK.get();
        clientChestSearchRadius = CLIENT_CHEST_SEARCH_RADIUS.get();
        clientChestMaxTargets = CLIENT_CHEST_MAX_TARGETS.get();

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
