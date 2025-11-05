package org.elpatronstudio.easybuild.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.elpatronstudio.easybuild.client.ClientChestRegistry;
import org.elpatronstudio.easybuild.client.ClientHandshakeState;
import org.elpatronstudio.easybuild.client.gui.SchematicBuilderScreen;
import org.elpatronstudio.easybuild.client.render.ChestSelectionRenderer;
import org.elpatronstudio.easybuild.client.render.PreviewAnchorRenderer;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.network.EasyBuildNetwork;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundHelloHandshake;
import org.elpatronstudio.esaybuildauto.Esaybuildauto;
import org.lwjgl.glfw.GLFW;

import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.elpatronstudio.easybuild.core.model.ChestRef;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client bootstrap hooks for EasyBuild features such as auto-build key handling.
 */
public final class EasyBuildClient {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(Esaybuildauto.MODID, "controls"));
    private static final List<String> CLIENT_CAPABILITIES = List.of("gui", "auto_build", "insta_build");
    private static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "key." + Esaybuildauto.MODID + ".open_gui",
            GLFW.GLFW_KEY_P,
            CATEGORY
    );
    private static final KeyMapping EXIT_SELECTION_KEY = new KeyMapping(
            "key." + Esaybuildauto.MODID + ".exit_selection",
            GLFW.GLFW_KEY_U,
            CATEGORY
    );

    private static boolean handshakeSent;

    private EasyBuildClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(EasyBuildClient::onClientSetup);
        modEventBus.addListener(EasyBuildClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.register(EasyBuildClient.class);
        ChestSelectionRenderer.init();
        PreviewAnchorRenderer.init();
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                ClientChestRegistry.load(minecraft.gameDirectory.toPath());
            }
        });
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
        event.register(EXIT_SELECTION_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ensureHandshake();
        while (OPEN_GUI_KEY.consumeClick()) {
            openGui();
        }
        while (EXIT_SELECTION_KEY.consumeClick()) {
            exitChestSelection();
        }
    }
    private static void openGui() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (minecraft.screen instanceof SchematicBuilderScreen) {
            return;
        }
        minecraft.setScreen(new SchematicBuilderScreen());
    }

    private static void exitChestSelection() {
        EasyBuildClientState state = EasyBuildClientState.get();
        if (state.isChestSelectionActive()) {
            state.setChestSelectionActive(false);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("easybuild.chest_selection.ended"), true);
                if (state.consumeReopenGuiRequest()) {
                    minecraft.setScreen(new SchematicBuilderScreen());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (!EasyBuildClientState.get().isChestSelectionActive()) {
            return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (handleChestSelectionClick()) {
            event.setCanceled(true);
        }
    }

    private static boolean handleChestSelectionClick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.hitResult == null) {
            return false;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult)) {
            return false;
        }
        BlockPos pos = blockHitResult.getBlockPos();
        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        if (!ClientChestRegistry.isContainerBlockEntity(blockEntity)) {
            minecraft.player.displayClientMessage(Component.translatable("easybuild.chest_selection.not_container"), true);
            return true;
        }

        ChestRef ref = new ChestRef(minecraft.level.dimension().location(), pos.immutable());
        boolean alreadySelected = EasyBuildClientState.get().selectedChests().contains(ref);
        EasyBuildClientState.get().toggleChest(ref);
        String key = alreadySelected ? "easybuild.chest_selection.removed" : "easybuild.chest_selection.added";
        minecraft.player.displayClientMessage(Component.translatable(key, pos.getX(), pos.getY(), pos.getZ()), true);
        return true;
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        com.mojang.blaze3d.platform.Window window = minecraft.getWindow();
        boolean altDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
        if (!altDown) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (delta == 0.0D) {
            return;
        }
        boolean shiftDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean controlDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        double step = shiftDown ? 0.25D : (controlDown ? 4.0D : 1.0D);
        double adjustment = delta > 0.0D ? step : -step;
        EasyBuildClientState state = EasyBuildClientState.get();
        state.adjustPreviewForwardOffset(adjustment);
        event.setCanceled(true);
        if (minecraft.player != null) {
            double offset = state.previewForwardOffset();
            minecraft.player.displayClientMessage(
                    Component.translatable("easybuild.preview.offset", String.format(Locale.ROOT, "%.2f", offset)),
                    true
            );
        }
    }


    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientHandshakeState.get().clear();
        EasyBuildClientState.get().reset();
        handshakeSent = false;
    }

    private static void ensureHandshake() {
        if (handshakeSent) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        LocalPlayer player = minecraft.player;
        if (connection == null || player == null) {
            return;
        }

        String clientVersion = minecraft.getVersionType() + "-" + minecraft.getLaunchedVersion();
        long nonce = ThreadLocalRandom.current().nextLong();
        ServerboundHelloHandshake payload = new ServerboundHelloHandshake(
                player.getUUID(),
                clientVersion,
                EasyBuildNetwork.PROTOCOL_VERSION,
                CLIENT_CAPABILITIES,
                nonce
        );
        connection.send(payload);
        handshakeSent = true;
    }

}
