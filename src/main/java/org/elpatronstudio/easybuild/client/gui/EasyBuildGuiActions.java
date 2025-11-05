package org.elpatronstudio.easybuild.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import org.elpatronstudio.easybuild.client.ClientChestRegistry;
import org.elpatronstudio.easybuild.client.autobuild.ClientPlacementController;
import org.elpatronstudio.easybuild.client.model.SchematicFileEntry;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState.PendingBuildRequest;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.BuildMode;
import org.elpatronstudio.easybuild.core.model.ChestRef;
import org.elpatronstudio.easybuild.core.model.PasteMode;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundMaterialCheckRequest;
import org.elpatronstudio.easybuild.core.network.packet.ServerboundRequestBuild;
import org.elpatronstudio.easybuild.server.job.BlockPlacementException;
import org.elpatronstudio.esaybuildauto.Config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Collection of GUI-triggered client actions.
 */
public final class EasyBuildGuiActions {

    private EasyBuildGuiActions() {
    }

    public static void handleStart(Minecraft minecraft, SchematicFileEntry schematic, BuildMode mode) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        switch (mode) {
            case SELF -> startPreview(player, schematic);
            case AUTO -> startClientAutoBuild(player, schematic);
            case INSTA -> startServerBuild(minecraft, player, schematic);
        }
    }

    public static void requestMaterialCheck(Minecraft minecraft, SchematicFileEntry schematic) {
        LocalPlayer player = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (player == null || connection == null) {
            return;
        }

        AnchorPos anchor = resolveAnchor(player);
        List<ChestRef> chests = gatherChestRefs(minecraft, player);
        long nonce = ThreadLocalRandom.current().nextLong();

        ServerboundMaterialCheckRequest payload = new ServerboundMaterialCheckRequest(
                player.getUUID(),
                schematic.ref(),
                anchor,
                chests,
                List.of(),
                nonce
        );
        connection.send(payload);
        player.displayClientMessage(Component.translatable("easybuild.materials.requested", schematic.displayName()), true);
    }

    private static void startPreview(LocalPlayer player, SchematicFileEntry schematic) {
        player.displayClientMessage(Component.translatable("easybuild.preview.start", schematic.displayName()), false);
        // A dedicated preview controller will be wired in during the positioning task.
    }

    private static void startClientAutoBuild(LocalPlayer player, SchematicFileEntry schematic) {
        if (!Config.clientAutoBuildEnabled) {
            player.displayClientMessage(Component.translatable("easybuild.autobuild.disabled"), true);
            return;
        }

        ClientPlacementController controller = ClientPlacementController.get();
        if (controller.isRunning()) {
            controller.stop("Client auto-build bereits aktiv – gestoppt");
            return;
        }

        AnchorPos anchor = resolveAnchor(player);
        JsonObject options = buildClientOptions(player);

        try {
            controller.start(schematic.ref(), anchor, Config.clientDefaultPasteMode, options);
        } catch (BlockPlacementException ex) {
            player.displayClientMessage(Component.translatable("easybuild.autobuild.error", ex.getMessage()), false);
        }
    }

    private static void startServerBuild(Minecraft minecraft, LocalPlayer player, SchematicFileEntry schematic) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return;
        }

        AnchorPos anchor = resolveAnchor(player);
        PasteMode pasteMode = resolvePasteMode(BuildMode.INSTA);
        JsonObject options = buildClientOptions(player);
        String requestId = UUID.randomUUID().toString();
        long nonce = ThreadLocalRandom.current().nextLong();

        ServerboundRequestBuild payload = new ServerboundRequestBuild(
                player.getUUID(),
                schematic.ref(),
                anchor,
                pasteMode,
                options,
                requestId,
                nonce
        );

        connection.send(payload);

        EasyBuildClientState.get().registerPendingBuildRequest(new PendingBuildRequest(
                requestId,
                schematic.ref(),
                schematic.displayName(),
                BuildMode.INSTA,
                pasteMode,
                System.currentTimeMillis()
        ));

        player.displayClientMessage(Component.translatable("easybuild.job.requested", schematic.displayName()), false);
    }

    private static AnchorPos resolveAnchor(LocalPlayer player) {
        ResourceLocation dimension = player.level().dimension().location();
        Vec3 look = player.getLookAngle().normalize();
        double offset = EasyBuildClientState.get().previewForwardOffset();
        Vec3 offsetVec = look.scale(offset);
        BlockPos base = player.blockPosition();
        BlockPos target = base.offset(
                Mth.floor(offsetVec.x),
                Mth.floor(offsetVec.y),
                Mth.floor(offsetVec.z)
        );
        return new AnchorPos(dimension, target.getX(), target.getY(), target.getZ(), player.getDirection());
    }

    private static JsonObject buildClientOptions(LocalPlayer player) {
        JsonObject options = new JsonObject();
        options.addProperty("placeAir", Config.clientPlaceAir);
        options.addProperty("blocksPerTick", Config.clientBlocksPerTick);
        JsonArray chests = buildChestArray(player);
        if (chests.size() > 0) {
            options.add("chests", chests);
        }
        return options;
    }

    private static JsonArray buildChestArray(LocalPlayer player) {
        JsonArray array = new JsonArray();
        Minecraft minecraft = Minecraft.getInstance();
        for (ChestRef ref : gatherChestRefs(minecraft, player)) {
            JsonObject chest = new JsonObject();
            chest.addProperty("dimension", ref.dimension().toString());
            chest.addProperty("x", ref.blockPos().getX());
            chest.addProperty("y", ref.blockPos().getY());
            chest.addProperty("z", ref.blockPos().getZ());
            array.add(chest);
        }
        return array;
    }

    private static List<ChestRef> gatherChestRefs(Minecraft minecraft, LocalPlayer player) {
        Set<ChestRef> refs = new LinkedHashSet<>();
        ResourceLocation dimension = player.level().dimension().location();

        EasyBuildClientState.get().selectedChests().stream()
                .filter(ref -> ref.dimension().equals(dimension))
                .forEach(refs::add);

        Path gameDir = minecraft.gameDirectory.toPath();
        ClientChestRegistry.getForDimension(gameDir, dimension).forEach(refs::add);

        int radius = Math.max(0, Math.min(16, Config.clientChestSearchRadius));
        int maxTargets = Math.max(0, Config.clientChestMaxTargets);
        if (radius > 0 && maxTargets > 0) {
            BlockPos origin = player.blockPosition();
            int added = 0;
            outer:
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (added >= maxTargets) {
                            break outer;
                        }
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        BlockEntity entity = player.level().getBlockEntity(candidate);
                        if (entity instanceof Container) {
                            ChestRef ref = new ChestRef(dimension, candidate.immutable());
                            if (refs.add(ref)) {
                                added++;
                            }
                        }
                    }
                }
            }
        }

        return new ArrayList<>(refs);
    }

    private static PasteMode resolvePasteMode(BuildMode mode) {
        return switch (mode) {
            case AUTO -> Config.clientDefaultPasteMode;
            case INSTA -> PasteMode.ATOMIC;
            case SELF -> PasteMode.SIMULATED;
        };
    }
}
