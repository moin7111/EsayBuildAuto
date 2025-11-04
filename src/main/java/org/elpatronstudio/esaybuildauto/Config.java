package org.elpatronstudio.esaybuildauto;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.elpatronstudio.easybuild.core.model.PasteMode;

import java.util.List;
import java.util.Set;
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
    }
}
