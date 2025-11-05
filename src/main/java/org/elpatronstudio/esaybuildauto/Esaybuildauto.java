package org.elpatronstudio.esaybuildauto;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.elpatronstudio.easybuild.client.EasyBuildClient;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;
import org.elpatronstudio.easybuild.server.ServerLifecycleEvents;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Esaybuildauto.MODID)
public class Esaybuildauto {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "esaybuildauto";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Esaybuildauto(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(EasyBuildNetwork::onRegisterPayloadHandlers);
        modEventBus.addListener(Config::onLoad);

        if (FMLEnvironment.getDist().isClient()) {
            modEventBus.addListener(this::onClientSetup);
            modEventBus.addListener(EasyBuildNetwork::onRegisterClientPayloadHandlers);
            EasyBuildClient.init(modEventBus);
        }

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (Esaybuildauto) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        ServerLifecycleEvents.register();

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("EasyBuild common setup complete (clientAutoBuild={}, serverInstaBuild={})",
                Config.clientAutoBuildEnabled,
                Config.serverInstaBuildEnabled);
    }
    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("HELLO FROM CLIENT SETUP");
        event.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.getUser() != null) {
                LOGGER.info("MINECRAFT NAME >> {}", minecraft.getUser().getName());
            }
        });
    }
}
