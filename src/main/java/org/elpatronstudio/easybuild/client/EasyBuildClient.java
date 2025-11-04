package org.elpatronstudio.easybuild.client;

import com.google.gson.JsonObject;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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

        try {
            controller.start(schematic, anchor, Config.clientDefaultPasteMode, options);
        } catch (BlockPlacementException ex) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("[EasyBuild] Autobau fehlgeschlagen: " + ex.getMessage()), false);
        }
    }
}
