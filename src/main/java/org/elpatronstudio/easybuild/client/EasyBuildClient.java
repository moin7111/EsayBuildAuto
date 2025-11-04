package org.elpatronstudio.easybuild.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.elpatronstudio.easybuild.client.build.ClientPlacementController;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.PasteMode;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.server.job.BlockPlacementException;
import org.elpatronstudio.esaybuildauto.Config;
import org.elpatronstudio.esaybuildauto.Esaybuildauto;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;

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

    private EasyBuildClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(EasyBuildClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.register(EasyBuildClient.class);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(AUTO_BUILD_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (AUTO_BUILD_KEY.consumeClick()) {
            toggleAutoBuild();
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

        int radius = Math.max(0, Math.min(16, Config.clientChestSearchRadius));
        int maxTargets = Math.max(0, Config.clientChestMaxTargets);
        if (radius > 0 && maxTargets > 0) {
            JsonArray chests = new JsonArray();
            int added = 0;
            BlockPos origin = player.blockPosition();
            for (int dx = -radius; dx <= radius && added < maxTargets; dx++) {
                for (int dy = -radius; dy <= radius && added < maxTargets; dy++) {
                    for (int dz = -radius; dz <= radius && added < maxTargets; dz++) {
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        BlockEntity blockEntity = player.level().getBlockEntity(candidate);
                        if (blockEntity instanceof Container) {
                            JsonObject chestObj = new JsonObject();
                            chestObj.addProperty("dimension", player.level().dimension().location().toString());
                            chestObj.addProperty("x", candidate.getX());
                            chestObj.addProperty("y", candidate.getY());
                            chestObj.addProperty("z", candidate.getZ());
                            chests.add(chestObj);
                            added++;
                            if (added >= maxTargets) {
                                break;
                            }
                        }
                    }
                }
            }
            if (chests.size() > 0) {
                options.add("chests", chests);
            }
        }

        try {
            controller.start(schematic, anchor, Config.clientDefaultPasteMode, options);
        } catch (BlockPlacementException ex) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("[EasyBuild] Autobau fehlgeschlagen: " + ex.getMessage()), false);
        }
    }
}
