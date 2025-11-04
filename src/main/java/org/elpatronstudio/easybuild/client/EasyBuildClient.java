package org.elpatronstudio.easybuild.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.elpatronstudio.easybuild.client.autobuild.ClientPlacementController;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.PasteMode;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.server.job.BlockPlacementException;
import org.elpatronstudio.esaybuildauto.Config;
import org.elpatronstudio.esaybuildauto.Esaybuildauto;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Path;

/**
 * Client bootstrap hooks for EasyBuild features such as auto-build key handling.
 */
public final class EasyBuildClient {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(Esaybuildauto.MODID, "controls"));
    private static final KeyMapping AUTO_BUILD_KEY = new KeyMapping(
            "key." + Esaybuildauto.MODID + ".autobuild",
            GLFW.GLFW_KEY_P,
            CATEGORY
    );
    private static final KeyMapping REGISTER_CHEST_KEY = new KeyMapping(
            "key." + Esaybuildauto.MODID + ".register_chest",
            GLFW.GLFW_KEY_O,
            CATEGORY
    );
    private static final KeyMapping REMOVE_CHEST_KEY = new KeyMapping(
            "key." + Esaybuildauto.MODID + ".remove_chest",
            GLFW.GLFW_KEY_L,
            CATEGORY
    );

    private EasyBuildClient() {
    }

    public static void init(IEventBus modEventBus) {
        ClientChestRegistry.load(Minecraft.getInstance().gameDirectory.toPath());
        modEventBus.addListener(EasyBuildClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.register(EasyBuildClient.class);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(AUTO_BUILD_KEY);
        event.register(REGISTER_CHEST_KEY);
        event.register(REMOVE_CHEST_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (AUTO_BUILD_KEY.consumeClick()) {
            toggleAutoBuild();
        }
        while (REGISTER_CHEST_KEY.consumeClick()) {
            handleChestRegistration(true);
        }
        while (REMOVE_CHEST_KEY.consumeClick()) {
            handleChestRegistration(false);
        }
    }

    private static void toggleAutoBuild() {
        if (!Config.clientAutoBuildEnabled) {
            return;
        }

        ClientPlacementController controller = ClientPlacementController.get();
        if (controller.isRunning()) {
            controller.stop("Abgebrochen");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        SchematicRef schematic = new SchematicRef(Config.clientDefaultSchematic, 1, 0L);
        AnchorPos anchor = new AnchorPos(
                player.level().dimension().location(),
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ(),
                player.getDirection()
        );
        JsonObject options = new JsonObject();
        options.addProperty("placeAir", Config.clientPlaceAir);

        JsonArray chests = new JsonArray();
        for (var ref : ClientChestRegistry.getForDimension(Minecraft.getInstance().gameDirectory.toPath(), player.level().dimension().location())) {
            JsonObject chestObj = new JsonObject();
            chestObj.addProperty("dimension", ref.dimension().toString());
            chestObj.addProperty("x", ref.blockPos().getX());
            chestObj.addProperty("y", ref.blockPos().getY());
            chestObj.addProperty("z", ref.blockPos().getZ());
            chests.add(chestObj);
        }

        int radius = Math.max(0, Math.min(16, Config.clientChestSearchRadius));
        int maxTargets = Math.max(0, Config.clientChestMaxTargets);
        if (radius > 0 && maxTargets > 0) {
            BlockPos origin = player.blockPosition();
            int added = 0;
            outerLoop:
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (added >= maxTargets) {
                            break outerLoop;
                        }
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        BlockEntity blockEntity = player.level().getBlockEntity(candidate);
                        if (blockEntity instanceof Container) {
                            if (ClientChestRegistry.contains(Minecraft.getInstance().gameDirectory.toPath(), player.level().dimension().location(), candidate)) {
                                continue;
                            }
                            JsonObject chestObj = new JsonObject();
                            chestObj.addProperty("dimension", player.level().dimension().location().toString());
                            chestObj.addProperty("x", candidate.getX());
                            chestObj.addProperty("y", candidate.getY());
                            chestObj.addProperty("z", candidate.getZ());
                            chests.add(chestObj);
                            added++;
                        }
                    }
                }
            }
        }

        if (chests.size() > 0) {
            options.add("chests", chests);
        }

        try {
            controller.start(schematic, anchor, Config.clientDefaultPasteMode, options);
        } catch (BlockPlacementException ex) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("[EasyBuild] Autobau fehlgeschlagen: " + ex.getMessage()), false);
        }
    }

    private static void handleChestRegistration(boolean add) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.hitResult == null) {
            return;
        }
        if (!(minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr)) {
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        if (!ClientChestRegistry.isContainerBlockEntity(blockEntity)) {
            minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal("[EasyBuild] Ziel ist kein Container."), true);
            return;
        }
        Path gameDir = minecraft.gameDirectory.toPath();
        ResourceLocation dimension = minecraft.level.dimension().location();
        if (add) {
            boolean added = ClientChestRegistry.add(gameDir, dimension, pos);
            minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(added
                    ? "[EasyBuild] Kiste registriert: " + pos.toShortString()
                    : "[EasyBuild] Kiste war bereits registriert."), true);
        } else {
            boolean removed = ClientChestRegistry.remove(gameDir, dimension, pos);
            minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(removed
                    ? "[EasyBuild] Kiste entfernt: " + pos.toShortString()
                    : "[EasyBuild] Kiste war nicht registriert."), true);
        }
    }
}
