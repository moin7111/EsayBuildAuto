package org.elpatronstudio.easybuild.client.autobuild;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.elpatronstudio.easybuild.client.model.SchematicFileEntry;
import org.elpatronstudio.easybuild.client.schematic.SchematicBlockLoader;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.PasteMode;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.server.job.BlockPlacementException;
import org.elpatronstudio.esaybuildauto.Config;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Client-side placement pipeline that replays schematics block-by-block by steering the local
 * player, selecting the required inventory items and issuing the correct placement interactions.
 */
public final class ClientPlacementController {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ClientPlacementController INSTANCE = new ClientPlacementController();
    private static final Direction[] PLACEMENT_SCAN_ORDER = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
    };
    private static final int DEFAULT_MAX_RETRIES = 8;
    private static final int MAX_IDLE_PASSES = 4;

    private PlacementSession activeSession;

    private ClientPlacementController() {
    }

    public static ClientPlacementController get() {
        return INSTANCE;
    }

    public boolean isRunning() {
        return activeSession != null && activeSession.isRunning();
    }

    public void start(SchematicFileEntry entry, AnchorPos anchor, PasteMode mode, JsonObject options) throws BlockPlacementException {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(mode, "mode");

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            throw new BlockPlacementException("CLIENT_PLAYER_MISSING", "Kein Spieler verfügbar");
        }

        if (!player.level().dimension().location().equals(anchor.dimension())) {
            throw new BlockPlacementException("DIMENSION_MISMATCH", "Spieler befindet sich nicht in der Zieldimension");
        }

        if (activeSession != null) {
            activeSession.stop("Vorheriger Client-Autobau ersetzt");
            activeSession = null;
        }

        JsonObject config = options == null ? new JsonObject() : options.deepCopy();
        boolean includeAir = includeAir(config);
        int blocksPerTick = resolveBlocksPerTick(mode, config);
        SchematicBlockLoader.Result parsed = SchematicBlockLoader.load(player, entry, anchor, includeAir);
        PlacementPlan plan = new PlacementPlan(parsed.displayName(), toClientPlacements(parsed.blocks()));

        if (plan.placements().isEmpty()) {
            throw new BlockPlacementException("SCHEMATIC_EMPTY", "Die Schematic enthält keine platzierbaren Blöcke");
        }

        activeSession = new PlacementSession(player.getUUID(), anchor, mode, plan, blocksPerTick);
        activeSession.start();
    }

    public void start(SchematicRef schematic, AnchorPos anchor, PasteMode mode, JsonObject options) throws BlockPlacementException {
        Objects.requireNonNull(schematic, "schematic");
        Optional<SchematicFileEntry> entry = EasyBuildClientState.get().findSchematic(schematic);
        if (entry.isEmpty()) {
            throw new BlockPlacementException("SCHEMATIC_NOT_INDEXED", "Schematic " + schematic.schematicId() + " konnte nicht gefunden werden");
        }
        start(entry.get(), anchor, mode, options);
    }

    public void tick() {
        PlacementSession session = this.activeSession;
        if (session == null) {
            return;
        }
        session.tick();
        if (!session.isRunning()) {
            this.activeSession = null;
        }
    }

    public void stop(String reason) {
        PlacementSession session = this.activeSession;
        if (session == null) {
            return;
        }
        session.stop(reason);
        this.activeSession = null;
    }

    private static boolean includeAir(JsonObject options) {
        if (options == null || !options.has("placeAir")) {
            return Config.clientPlaceAir;
        }
        return options.get("placeAir").getAsBoolean();
    }

    private static int resolveBlocksPerTick(PasteMode mode, JsonObject options) {
        if (mode == PasteMode.ATOMIC) {
            return Integer.MAX_VALUE;
        }
        if (options != null && options.has("blocksPerTick")) {
            try {
                int value = options.get("blocksPerTick").getAsInt();
                return Mth.clamp(value, 1, 128);
            } catch (Exception ignored) {
                // fall back to defaults
            }
        }
        return Mth.clamp(Config.clientBlocksPerTick, 1, 32);
    }

    private static List<ClientPlacement> toClientPlacements(List<SchematicBlockLoader.BlockInstance> blocks) {
        List<ClientPlacement> placements = new ArrayList<>(blocks.size());
        for (SchematicBlockLoader.BlockInstance block : blocks) {
            placements.add(new ClientPlacement(block));
        }
        return placements;
    }

    private static final class PlacementSession {

        private final UUID ownerUuid;
        private final AnchorPos anchor;
        private final PasteMode mode;
        private final PlacementPlan plan;
        private final Deque<ClientPlacement> queue;
        private final Deque<ClientPlacement> deferred;
        private final int blocksPerTick;
        private final int maxRetries;

        private boolean running;
        private int placed;
        private int failed;
        private int idlePasses;
        private long lastMessageTime;

        PlacementSession(UUID ownerUuid, AnchorPos anchor, PasteMode mode, PlacementPlan plan,
                         int blocksPerTick) {
            this.ownerUuid = ownerUuid;
            this.anchor = anchor;
            this.mode = mode;
            this.plan = plan;
            this.queue = new ArrayDeque<>(plan.placements());
            this.deferred = new ArrayDeque<>();
            this.blocksPerTick = blocksPerTick;
            this.maxRetries = DEFAULT_MAX_RETRIES;
        }

        boolean isRunning() {
            return running;
        }

        void start() {
            this.running = true;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.literal("[EasyBuild] Client-Autobau gestartet: " + plan.displayName() + " (" + queue.size() + " Blöcke)"), false);
            }
        }

        void stop(String reason) {
            if (!running) {
                return;
            }
            this.running = false;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                Component message = (reason == null || reason.isBlank())
                        ? Component.literal("[EasyBuild] Client-Autobau gestoppt.")
                        : Component.literal("[EasyBuild] " + reason);
                player.displayClientMessage(message, false);
            }
        }

        void tick() {
            if (!running) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || !player.getUUID().equals(ownerUuid)) {
                stop("Spieler nicht verfügbar");
                return;
            }

            if (!player.level().dimension().location().equals(anchor.dimension())) {
                stop("Falsche Dimension – Autobau abgebrochen");
                return;
            }

            int quota = Math.max(1, blocksPerTick);
            if (mode == PasteMode.ATOMIC) {
                quota = Integer.MAX_VALUE;
            }

            boolean progress = false;
            int processed = 0;

            while (processed < quota && running) {
                ClientPlacement placement = queue.pollFirst();
                if (placement == null) {
                    break;
                }

                PlacementResult result = attemptPlacement(player, placement);
                switch (result) {
                    case SUCCESS, ALREADY_DONE -> {
                        placed++;
                        progress = true;
                    }
                    case RETRY -> deferred.addLast(placement);
                    case FAILED -> {
                        failed++;
                        progress = true;
                    }
                }
                processed++;
            }

            if (queue.isEmpty() && !deferred.isEmpty()) {
                if (!progress) {
                    idlePasses++;
                    if (idlePasses > MAX_IDLE_PASSES) {
                        stop("Auto-Build blockiert: " + deferred.size() + " Platzierungen nicht möglich");
                        return;
                    }
                } else {
                    idlePasses = 0;
                }
                queue.addAll(deferred);
                deferred.clear();
            }

            if (placed + failed >= plan.placements().size()) {
                stop("Auto-Build abgeschlossen: " + placed + " / " + plan.placements().size());
                return;
            }

            long now = System.currentTimeMillis();
            if (progress || now - lastMessageTime > 1500L) {
                lastMessageTime = now;
                sendProgressHud(player);
            }
        }

        private void sendProgressHud(LocalPlayer player) {
            int total = plan.placements().size();
            int remaining = Math.max(0, total - placed - failed);
            Component hud = Component.literal(String.format(Locale.ROOT,
                    "[EasyBuild] %d/%d Blöcke (%d fehlgeschlagen, %d offen)", placed, total, failed, remaining));
            player.displayClientMessage(hud, true);
        }

        private PlacementResult attemptPlacement(LocalPlayer player, ClientPlacement placement) {
            Level level = player.level();
            BlockPos targetPos = placement.position;

            if (!level.isLoaded(targetPos)) {
                return retryOrFail(placement);
            }

            BlockState current = level.getBlockState(targetPos);
            if (current.equals(placement.state)) {
                return PlacementResult.ALREADY_DONE;
            }

            if (!current.canBeReplaced()) {
                return retryOrFail(placement);
            }

            if (!ensureHeldItem(player, placement.requiredItem)) {
                return retryOrFail(placement);
            }

            PlacementFace face = findPlacementFace(level, targetPos);
            if (face == null) {
                return retryOrFail(placement);
            }

            Vec3 hitLocation = face.hitResult().getLocation();
            player.lookAt(EntityAnchorArgument.Anchor.EYES, hitLocation);

            MultiPlayerGameMode controller = Minecraft.getInstance().gameMode;
            if (controller == null) {
                return retryOrFail(placement);
            }

            InteractionResult result = controller.useItemOn(player, InteractionHand.MAIN_HAND, face.hitResult());
            if (!result.consumesAction()) {
                return retryOrFail(placement);
            }

            player.swing(InteractionHand.MAIN_HAND, true);
            applyBlockEntityData(level, targetPos, placement.blockEntityTag);
            return PlacementResult.SUCCESS;
        }

        private PlacementResult retryOrFail(ClientPlacement placement) {
            placement.attempts++;
            if (placement.attempts >= maxRetries) {
                LOGGER.debug("Placement bei {} nach {} Versuchen aufgegeben", placement.position, placement.attempts);
                return PlacementResult.FAILED;
            }
            return PlacementResult.RETRY;
        }

        private static boolean ensureHeldItem(LocalPlayer player, Item item) {
            if (item == Items.AIR) {
                return true;
            }
            Inventory inventory = player.getInventory();
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty() && held.is(item)) {
                return true;
            }
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && stack.is(item)) {
                    inventory.setSelectedSlot(slot);
                    if (Minecraft.getInstance().getConnection() != null) {
                        Minecraft.getInstance().getConnection().send(new ServerboundSetCarriedItemPacket(slot));
                    }
                    return true;
                }
            }
            return false;
        }

        private static PlacementFace findPlacementFace(Level level, BlockPos targetPos) {
            for (Direction face : PLACEMENT_SCAN_ORDER) {
                BlockPos supportPos = targetPos.relative(face.getOpposite());
                if (!level.isLoaded(supportPos)) {
                    continue;
                }
                BlockState supportState = level.getBlockState(supportPos);
                if (supportState.isAir() || supportState.canBeReplaced()) {
                    continue;
                }
                Vec3 hit = Vec3.atCenterOf(targetPos).add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
                BlockHitResult hitResult = new BlockHitResult(hit, face, supportPos, false);
                return new PlacementFace(hitResult);
            }
            return null;
        }

        private static void applyBlockEntityData(Level level, BlockPos pos, CompoundTag tag) {
            if (tag == null) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                LOGGER.debug("Kein BlockEntity an Position {} gefunden – NBT wird verworfen.", pos);
                return;
            }

            CompoundTag data = tag.copy();
            data.putInt("x", pos.getX());
            data.putInt("y", pos.getY());
            data.putInt("z", pos.getZ());

            HolderLookup.Provider registries = level.registryAccess();
            try (ProblemReporter.ScopedCollector collector = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
                blockEntity.loadWithComponents(TagValueInput.create(collector, registries, data));
            } catch (Exception ex) {
                LOGGER.warn("BlockEntity-Daten konnten nicht angewendet werden bei {}: {}", pos, ex.getMessage());
            }

            blockEntity.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
    }

    private record PlacementFace(BlockHitResult hitResult) {
    }

    private enum PlacementResult {
        SUCCESS,
        ALREADY_DONE,
        RETRY,
        FAILED
    }

    private record PlacementPlan(String displayName, List<ClientPlacement> placements) {
    }

    private static final class ClientPlacement {
        final BlockPos position;
        final BlockState state;
        final CompoundTag blockEntityTag;
        final Item requiredItem;
        int attempts;

        ClientPlacement(BlockPos position, BlockState state, CompoundTag blockEntityTag, Item requiredItem) {
            this.position = position;
            this.state = state;
            this.blockEntityTag = blockEntityTag;
            this.requiredItem = requiredItem;
            this.attempts = 0;
        }

        ClientPlacement(SchematicBlockLoader.BlockInstance block) {
            this(block.position(), block.state(), block.blockEntityTag(), block.requiredItem());
        }
    }
}
