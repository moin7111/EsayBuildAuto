package org.elpatronstudio.easybuild.client.autobuild;

import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.elpatronstudio.easybuild.client.model.SchematicFileEntry;
import org.elpatronstudio.easybuild.client.state.EasyBuildClientState;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.PasteMode;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.elpatronstudio.easybuild.server.job.BlockPlacementException;
import org.elpatronstudio.esaybuildauto.Config;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        Rotation rotation = rotationFor(anchor.facing());
        boolean includeAir = includeAir(config);
        int blocksPerTick = resolveBlocksPerTick(mode, config);
        PlacementPlan plan = buildPlan(player, entry, anchor, rotation, includeAir);

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

    private static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static PlacementPlan buildPlan(LocalPlayer player, SchematicFileEntry entry, AnchorPos anchor, Rotation rotation, boolean includeAir) throws BlockPlacementException {
        Path path = entry.path();
        if (!Files.isRegularFile(path)) {
            throw new BlockPlacementException("SCHEMATIC_FILE_MISSING", "Datei nicht gefunden: " + entry.id());
        }

        CompoundTag rootTag = readSchematicTag(path);
        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".schem")) {
            return loadSpongeFormat(player, rootTag, anchor, rotation, includeAir, entry.displayName());
        }
        throw new BlockPlacementException("SCHEMATIC_FORMAT", "Nicht unterstütztes Format: " + lowerName);
    }

    private static PlacementPlan loadSpongeFormat(LocalPlayer player, CompoundTag root, AnchorPos anchor,
                                                  Rotation rotation, boolean includeAir, String displayName) throws BlockPlacementException {
        CompoundTag paletteTag = root.getCompound("Palette")
                .orElseThrow(() -> new BlockPlacementException("SCHEMATIC_INVALID", "Palette fehlt in der Schematic"));

        if (root.getByteArray("BlockData").isEmpty() && root.getLongArray("BlockData").isEmpty()) {
            throw new BlockPlacementException("SCHEMATIC_INVALID", "BlockData fehlt in der Schematic");
        }

        int width = root.getInt("Width").orElse(0);
        int height = root.getInt("Height").orElse(0);
        int length = root.getInt("Length").orElse(0);
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new BlockPlacementException("SCHEMATIC_INVALID", "Ungültige Dimensionsangaben in der Schematic");
        }

        int offsetX = root.getInt("OffsetX").orElse(0);
        int offsetY = root.getInt("OffsetY").orElse(0);
        int offsetZ = root.getInt("OffsetZ").orElse(0);

        HolderLookup<Block> blockLookup = player.level().registryAccess().lookupOrThrow(Registries.BLOCK);
        Map<Integer, BlockState> palette = parsePalette(paletteTag, blockLookup);
        int volume = width * height * length;
        int[] indices = decodeBlockData(root, volume, palette.size());
        Map<BlockPos, CompoundTag> blockEntities = extractBlockEntities(root);

        BlockPos anchorPos = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        List<ClientPlacement> placements = new ArrayList<>(volume);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos rotatedPos = new BlockPos.MutableBlockPos();

        int localMinX = Integer.MAX_VALUE;
        int localMinY = Integer.MAX_VALUE;
        int localMinZ = Integer.MAX_VALUE;

        List<IntermediatePlacement> intermediate = new ArrayList<>(volume);

        for (int index = 0; index < indices.length; index++) {
            BlockState state = palette.getOrDefault(indices[index], Blocks.AIR.defaultBlockState());
            int x = index % width;
            int temp = index / width;
            int z = temp % length;
            int y = temp / length;

            BlockPos original = new BlockPos(x, y, z);
            mutable.set(x - offsetX, y - offsetY, z - offsetZ);
            BlockPos rotated = rotateAroundOrigin(mutable, rotation, rotatedPos);
            intermediate.add(new IntermediatePlacement(original, new BlockPos(rotated), state));

            localMinX = Math.min(localMinX, rotated.getX());
            localMinY = Math.min(localMinY, rotated.getY());
            localMinZ = Math.min(localMinZ, rotated.getZ());
        }

        BlockPos translation = new BlockPos(-localMinX, -localMinY, -localMinZ);
        for (IntermediatePlacement placement : intermediate) {
            BlockPos translated = placement.rotated().offset(translation);
            BlockState rotatedState = placement.state().rotate(rotation);
            if (!includeAir && rotatedState.isAir()) {
                continue;
            }
            Item requiredItem = rotatedState.getBlock().asItem();
            if (requiredItem == Items.AIR && !rotatedState.isAir()) {
                LOGGER.warn("Überspringe Block {} bei {} – kein Item verfügbar", rotatedState, translated);
                continue;
            }
            BlockPos worldPos = anchorPos.offset(translated);
            CompoundTag beTag = blockEntities.getOrDefault(placement.original(), null);
            if (beTag != null) {
                beTag = beTag.copy();
            }
            placements.add(new ClientPlacement(worldPos, rotatedState, beTag, requiredItem));
        }

        placements.sort(Comparator
                .comparingInt((ClientPlacement placement) -> placement.position.getY())
                .thenComparingInt(placement -> placement.position.getX())
                .thenComparingInt(placement -> placement.position.getZ()));

        return new PlacementPlan(displayName, placements);
    }

    private static CompoundTag readSchematicTag(Path path) throws BlockPlacementException {
        try {
            return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        } catch (IOException ex) {
            try {
                CompoundTag fallback = NbtIo.read(path);
                if (fallback != null) {
                    return fallback;
                }
            } catch (IOException ignored) {
            }
            throw new BlockPlacementException("SCHEMATIC_IO", "Fehler beim Lesen der Schematic: " + ex.getMessage());
        }
    }

    private static Map<Integer, BlockState> parsePalette(CompoundTag paletteTag, HolderLookup<Block> lookup) throws BlockPlacementException {
        Map<Integer, BlockState> palette = new HashMap<>();
        for (Map.Entry<String, Tag> entry : paletteTag.entrySet()) {
            String stateString = entry.getKey();
            int index = entry.getValue().asInt().orElseThrow(() ->
                    new BlockPlacementException("SCHEMATIC_INVALID", "Palette-Index fehlt für " + stateString));
            try {
                BlockStateParser.BlockResult result = BlockStateParser.parseForBlock(lookup, stateString, true);
                BlockState state = result.blockState();
                palette.put(index, state);
            } catch (CommandSyntaxException ex) {
                LOGGER.warn("Palette-Eintrag '{}' konnte nicht gelesen werden: {}", stateString, ex.getMessage());
            }
        }
        if (palette.isEmpty()) {
            throw new BlockPlacementException("SCHEMATIC_INVALID", "Palette konnte nicht gelesen werden");
        }
        return palette;
    }

    private static int[] decodeBlockData(CompoundTag root, int expectedEntries, int paletteSize) throws BlockPlacementException {
        Optional<byte[]> rawBytes = root.getByteArray("BlockData");
        if (rawBytes.isPresent()) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(rawBytes.get()));
            int[] indices = new int[expectedEntries];
            for (int i = 0; i < expectedEntries; i++) {
                if (!buffer.isReadable()) {
                    throw new BlockPlacementException("SCHEMATIC_INVALID", "BlockData ist kürzer als erwartet");
                }
                indices[i] = buffer.readVarInt();
            }
            return indices;
        }

        Optional<long[]> packedData = root.getLongArray("BlockData");
        if (packedData.isPresent()) {
            int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(1, paletteSize) - 1));
            int mask = (1 << bits) - 1;
            long[] packed = packedData.get();
            int[] indices = new int[expectedEntries];
            int entry = 0;
            int longIndex = 0;
            int bitOffset = 0;
            while (entry < expectedEntries) {
                if (longIndex >= packed.length) {
                    throw new BlockPlacementException("SCHEMATIC_INVALID", "BlockData ist kürzer als erwartet");
                }
                long data = packed[longIndex];
                indices[entry] = (int)((data >> bitOffset) & mask);
                bitOffset += bits;
                if (bitOffset >= 64) {
                    longIndex++;
                    bitOffset = 0;
                }
                entry++;
            }
            return indices;
        }

        throw new BlockPlacementException("SCHEMATIC_INVALID", "BlockData fehlt oder hat ein unbekanntes Format");
    }

    private static Map<BlockPos, CompoundTag> extractBlockEntities(CompoundTag root) {
        Map<BlockPos, CompoundTag> map = new HashMap<>();
        for (String key : new String[]{"BlockEntities", "TileEntities"}) {
            Optional<ListTag> optionalList = root.getList(key);
            if (optionalList.isEmpty()) {
                continue;
            }
            for (Tag element : optionalList.get()) {
                Optional<CompoundTag> optionalEntry = element.asCompound();
                if (optionalEntry.isEmpty()) {
                    continue;
                }
                CompoundTag entry = optionalEntry.get();
                BlockPos pos = readLocalPos(entry);
                if (pos == null) {
                    continue;
                }
                CompoundTag data = entry.copy();
                data.getString("Id").ifPresent(id -> data.putString("id", id));
                data.remove("Id");
                data.remove("Pos");
                map.put(pos, data);
            }
            if (!map.isEmpty()) {
                break;
            }
        }
        return map;
    }

    private static BlockPos readLocalPos(CompoundTag tag) {
        Optional<int[]> posArray = tag.getIntArray("Pos");
        if (posArray.isPresent() && posArray.get().length >= 3) {
            int[] arr = posArray.get();
            return new BlockPos(arr[0], arr[1], arr[2]);
        }

        Optional<ListTag> listTag = tag.getList("Pos");
        if (listTag.isPresent()) {
            ListTag list = listTag.get();
            if (list.size() >= 3) {
                Optional<Integer> maybeX = list.get(0).asInt();
                Optional<Integer> maybeY = list.get(1).asInt();
                Optional<Integer> maybeZ = list.get(2).asInt();
                if (maybeX.isPresent() && maybeY.isPresent() && maybeZ.isPresent()) {
                    return new BlockPos(maybeX.get(), maybeY.get(), maybeZ.get());
                }
            }
        }

        int x = tag.getInt("x").orElse(Integer.MIN_VALUE);
        int y = tag.getInt("y").orElse(Integer.MIN_VALUE);
        int z = tag.getInt("z").orElse(Integer.MIN_VALUE);
        if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE && z != Integer.MIN_VALUE) {
            return new BlockPos(x, y, z);
        }
        return null;
    }

    private static BlockPos rotateAroundOrigin(BlockPos pos, Rotation rotation, BlockPos.MutableBlockPos result) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return switch (rotation) {
            case CLOCKWISE_90 -> result.set(-z, y, x);
            case CLOCKWISE_180 -> result.set(-x, y, -z);
            case COUNTERCLOCKWISE_90 -> result.set(z, y, -x);
            default -> result.set(x, y, z);
        };
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
            LOGGER.debug("BlockEntity-Daten werden aktuell nicht clientseitig angewendet ({}).", pos);
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

    private record IntermediatePlacement(BlockPos original, BlockPos rotated, BlockState state) {
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
    }
}
