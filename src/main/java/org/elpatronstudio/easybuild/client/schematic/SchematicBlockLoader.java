package org.elpatronstudio.easybuild.client.schematic;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.elpatronstudio.easybuild.client.model.SchematicFileEntry;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.server.job.BlockPlacementException;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lightweight schematic parser that converts local schematic data to world block instances
 * relative to an anchor position.
 */
public final class SchematicBlockLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SchematicBlockLoader() {
    }

    public static Result load(LocalPlayer player, SchematicFileEntry entry, AnchorPos anchor, boolean includeAir) throws BlockPlacementException {
        Path path = entry.path();
        if (!Files.isRegularFile(path)) {
            throw new BlockPlacementException("SCHEMATIC_FILE_MISSING", "Datei nicht gefunden: " + entry.id());
        }

        CompoundTag rootTag = readSchematicTag(path);
        Rotation rotation = rotationFor(anchor.facing());
        BlockPos anchorPos = new BlockPos(anchor.x(), anchor.y(), anchor.z());

        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".schem")) {
            return loadSpongeFormat(player, rootTag, anchorPos, rotation, includeAir, entry.displayName());
        }
        if (lowerName.endsWith(".nbt")) {
            return loadStructureFormat(player, rootTag, anchorPos, rotation, includeAir, entry.displayName());
        }
        if (lowerName.endsWith(".litematic")) {
            return loadLitematicFormat(player, rootTag, anchorPos, rotation, includeAir, entry.displayName());
        }
        throw new BlockPlacementException("SCHEMATIC_FORMAT", "Nicht unterstütztes Format: " + lowerName);
    }

    private static Result loadSpongeFormat(LocalPlayer player, CompoundTag root, BlockPos anchorPos,
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

        List<IntermediatePlacement> intermediate = new ArrayList<>(indices.length);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos rotatedPos = new BlockPos.MutableBlockPos();

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
        }

        return finalizePlacements(intermediate, blockEntities, anchorPos, rotation, includeAir, displayName);
    }

    private static Result loadStructureFormat(LocalPlayer player, CompoundTag root, BlockPos anchorPos,
                                              Rotation rotation, boolean includeAir, String displayName) throws BlockPlacementException {
        ListTag sizeTag = root.getListOrEmpty("size");
        int width = sizeTag.getIntOr(0, 0);
        int height = sizeTag.getIntOr(1, 0);
        int length = sizeTag.getIntOr(2, 0);
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new BlockPlacementException("SCHEMATIC_INVALID", "Ungültige Dimensionsangaben in der Struktur");
        }

        HolderLookup.Provider registries = player.level().registryAccess();
        HolderLookup<Block> blockLookup = registries.lookupOrThrow(Registries.BLOCK);
        List<BlockState> palette = new ArrayList<>();
        Optional<ListTag> paletteList = root.getList("palettes");
        ListTag primaryPalette = paletteList.filter(tag -> !tag.isEmpty()).map(tag -> tag.getListOrEmpty(0)).orElseGet(() -> root.getListOrEmpty("palette"));
        for (int i = 0; i < primaryPalette.size(); i++) {
            palette.add(net.minecraft.nbt.NbtUtils.readBlockState(blockLookup, primaryPalette.getCompoundOrEmpty(i)));
        }
        if (palette.isEmpty()) {
            throw new BlockPlacementException("SCHEMATIC_INVALID", "Palette konnte nicht gelesen werden");
        }

        ListTag blocksTag = root.getListOrEmpty("blocks");
        if (blocksTag.isEmpty()) {
            throw new BlockPlacementException("SCHEMATIC_INVALID", "Blockliste ist leer");
        }

        List<IntermediatePlacement> intermediate = new ArrayList<>(blocksTag.size());
        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        BlockPos.MutableBlockPos rotatedBuffer = new BlockPos.MutableBlockPos();

        for (int i = 0; i < blocksTag.size(); i++) {
            Tag element = blocksTag.get(i);
            Optional<CompoundTag> blockTagOptional = element.asCompound();
            if (blockTagOptional.isEmpty()) {
                continue;
            }
            CompoundTag blockTag = blockTagOptional.get();
            ListTag posList = blockTag.getListOrEmpty("pos");
            BlockPos original = new BlockPos(posList.getIntOr(0, 0), posList.getIntOr(1, 0), posList.getIntOr(2, 0));
            int paletteIndex = blockTag.getIntOr("state", 0);
            if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                throw new BlockPlacementException("SCHEMATIC_INVALID", "Palette-Index " + paletteIndex + " außerhalb des gültigen Bereichs");
            }
            BlockState state = palette.get(paletteIndex);
            BlockPos rotated = rotateAroundOrigin(original, rotation, rotatedBuffer);
            intermediate.add(new IntermediatePlacement(original, new BlockPos(rotated), state));
            blockTag.getCompound("nbt").ifPresent(nbt -> blockEntities.put(original, nbt));
        }

        return finalizePlacements(intermediate, blockEntities, anchorPos, rotation, includeAir, displayName);
    }

    private static Result loadLitematicFormat(LocalPlayer player, CompoundTag root, BlockPos anchorPos,
                                              Rotation rotation, boolean includeAir, String displayName) throws BlockPlacementException {
        HolderLookup<Block> blockLookup = player.level().registryAccess().lookupOrThrow(Registries.BLOCK);
        CompoundTag regionsTag = root.getCompound("Regions")
                .orElseThrow(() -> new BlockPlacementException("SCHEMATIC_INVALID", "Regions fehlt in der Litematic"));

        List<IntermediatePlacement> intermediate = new ArrayList<>();
        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        BlockPos.MutableBlockPos rotatedBuffer = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (Map.Entry<String, Tag> entry : regionsTag.entrySet()) {
            Optional<CompoundTag> regionOptional = entry.getValue().asCompound();
            if (regionOptional.isEmpty()) {
                continue;
            }
            CompoundTag regionTag = regionOptional.get();
            BlockPos regionOrigin = readBlockPos(regionTag, "Position");
            BlockPos regionSize = readBlockPos(regionTag, "Size");

            int width = Math.abs(regionSize.getX());
            int height = Math.abs(regionSize.getY());
            int length = Math.abs(regionSize.getZ());
            if (width <= 0 || height <= 0 || length <= 0) {
                throw new BlockPlacementException("SCHEMATIC_INVALID", "Ungültige Regiongröße in " + entry.getKey());
            }

            List<BlockState> palette = readLitematicPalette(regionTag, blockLookup);
            if (palette.isEmpty()) {
                continue;
            }

            long[] packedStates = regionTag.getLongArray("BlockStates")
                    .orElseThrow(() -> new BlockPlacementException("SCHEMATIC_INVALID", "BlockStates fehlen in Region " + entry.getKey()));
            int expected = width * height * length;
            int[] indices = unpackBlockData(packedStates, expected, palette.size());

            for (int index = 0; index < indices.length; index++) {
                BlockState state = palette.get(indices[index]);
                int x = index % width;
                int temp = index / width;
                int z = temp % length;
                int y = temp / length;

                int localX = regionOrigin.getX() + x;
                int localY = regionOrigin.getY() + y;
                int localZ = regionOrigin.getZ() + z;

                BlockPos original = new BlockPos(localX, localY, localZ);
                mutable.set(localX, localY, localZ);
                BlockPos rotated = rotateAroundOrigin(mutable, rotation, rotatedBuffer);
                intermediate.add(new IntermediatePlacement(original, new BlockPos(rotated), state));
            }

            blockEntities.putAll(extractBlockEntitiesFromLitematic(regionTag, regionOrigin));
        }

        return finalizePlacements(intermediate, blockEntities, anchorPos, rotation, includeAir, displayName);
    }

    private static List<BlockState> readLitematicPalette(CompoundTag regionTag, HolderLookup<Block> lookup) throws BlockPlacementException {
        ListTag paletteTag = regionTag.getListOrEmpty("BlockStatePalette");
        if (paletteTag.isEmpty()) {
            throw new BlockPlacementException("SCHEMATIC_INVALID", "Palette fehlt oder ist leer");
        }
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompoundOrEmpty(i);
            palette.add(net.minecraft.nbt.NbtUtils.readBlockState(lookup, entry));
        }
        return palette;
    }

    private static Map<BlockPos, CompoundTag> extractBlockEntitiesFromLitematic(CompoundTag regionTag, BlockPos regionOrigin) {
        Map<BlockPos, CompoundTag> result = new HashMap<>();
        for (String key : new String[]{"BlockEntities", "TileEntities"}) {
            Optional<ListTag> listOptional = regionTag.getList(key);
            if (listOptional.isEmpty()) {
                continue;
            }
            for (Tag element : listOptional.get()) {
                Optional<CompoundTag> tagOptional = element.asCompound();
                if (tagOptional.isEmpty()) {
                    continue;
                }
                CompoundTag entry = tagOptional.get();
                BlockPos localPos = readLocalPos(entry);
                if (localPos == null) {
                    continue;
                }
                BlockPos absolute = localPos.offset(regionOrigin.getX(), regionOrigin.getY(), regionOrigin.getZ());
                CompoundTag data = entry.copy();
                data.getString("Id").ifPresent(id -> data.putString("id", id));
                data.remove("Id");
                data.remove("Pos");
                result.put(absolute, data);
            }
            if (!result.isEmpty()) {
                break;
            }
        }
        return result;
    }

    private static BlockPos readBlockPos(CompoundTag tag, String key) throws BlockPlacementException {
        Optional<CompoundTag> compound = tag.getCompound(key);
        if (compound.isPresent()) {
            CompoundTag posTag = compound.get();
            int x = posTag.getInt("x").orElse(Integer.MIN_VALUE);
            int y = posTag.getInt("y").orElse(Integer.MIN_VALUE);
            int z = posTag.getInt("z").orElse(Integer.MIN_VALUE);
            if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE && z != Integer.MIN_VALUE) {
                return new BlockPos(x, y, z);
            }
        }

        ListTag list = tag.getListOrEmpty(key);
        if (!list.isEmpty()) {
            int x = list.getIntOr(0, Integer.MIN_VALUE);
            int y = list.getIntOr(1, Integer.MIN_VALUE);
            int z = list.getIntOr(2, Integer.MIN_VALUE);
            if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE && z != Integer.MIN_VALUE) {
                return new BlockPos(x, y, z);
            }
        }

        Optional<int[]> array = tag.getIntArray(key);
        if (array.isPresent()) {
            int[] values = array.get();
            if (values.length >= 3) {
                return new BlockPos(values[0], values[1], values[2]);
            }
        }

        throw new BlockPlacementException("SCHEMATIC_INVALID", "Tag '" + key + "' fehlt oder ist ungültig");
    }

    private static int[] unpackBlockData(long[] packed, int expectedEntries, int paletteSize) throws BlockPlacementException {
        int[] indices = new int[expectedEntries];
        if (paletteSize <= 0) {
            return indices;
        }

        int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(1, paletteSize) - 1));
        int mask = (1 << bits) - 1;
        int bitIndex = 0;

        for (int entry = 0; entry < expectedEntries; entry++) {
            int longIndex = bitIndex >> 6;
            if (longIndex >= packed.length) {
                throw new BlockPlacementException("SCHEMATIC_INVALID", "BlockData ist kürzer als erwartet");
            }

            int bitOffset = bitIndex & 63;
            int endBit = bitOffset + bits;
            long base = packed[longIndex] >>> bitOffset;

            if (endBit > 64) {
                int overflowBits = endBit - 64;
                if (longIndex + 1 >= packed.length) {
                    throw new BlockPlacementException("SCHEMATIC_INVALID", "BlockData ist kürzer als erwartet");
                }
                long next = packed[longIndex + 1] & ((1L << overflowBits) - 1);
                base |= next << (bits - overflowBits);
            }

            indices[entry] = (int) (base & mask);
            bitIndex += bits;
        }

        return indices;
    }

    private static Result finalizePlacements(List<IntermediatePlacement> intermediate,
                                             Map<BlockPos, CompoundTag> blockEntities,
                                             BlockPos anchorPos,
                                             Rotation rotation,
                                             boolean includeAir,
                                             String displayName) {
        if (intermediate.isEmpty()) {
            return new Result(displayName, List.of(), anchorPos, anchorPos);
        }

        int localMinX = Integer.MAX_VALUE;
        int localMinY = Integer.MAX_VALUE;
        int localMinZ = Integer.MAX_VALUE;

        for (IntermediatePlacement placement : intermediate) {
            BlockPos rotated = placement.rotated();
            localMinX = Math.min(localMinX, rotated.getX());
            localMinY = Math.min(localMinY, rotated.getY());
            localMinZ = Math.min(localMinZ, rotated.getZ());
        }

        BlockPos translation = new BlockPos(-localMinX, -localMinY, -localMinZ);
        List<BlockInstance> placements = new ArrayList<>(intermediate.size());
        BlockPos minWorld = null;
        BlockPos maxWorld = null;

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
            CompoundTag beTag = blockEntities.get(placement.original());
            if (beTag != null) {
                beTag = beTag.copy();
            }
            placements.add(new BlockInstance(worldPos.immutable(), rotatedState, beTag, requiredItem));
            if (minWorld == null) {
                minWorld = worldPos;
                maxWorld = worldPos;
            } else {
                minWorld = new BlockPos(Math.min(minWorld.getX(), worldPos.getX()), Math.min(minWorld.getY(), worldPos.getY()), Math.min(minWorld.getZ(), worldPos.getZ()));
                maxWorld = new BlockPos(Math.max(maxWorld.getX(), worldPos.getX()), Math.max(maxWorld.getY(), worldPos.getY()), Math.max(maxWorld.getZ(), worldPos.getZ()));
            }
        }

        placements.sort((a, b) -> {
            int cmp = Integer.compare(a.position().getY(), b.position().getY());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(a.position().getX(), b.position().getX());
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(a.position().getZ(), b.position().getZ());
        });

        if (minWorld == null || maxWorld == null) {
            minWorld = anchorPos;
            maxWorld = anchorPos;
        }

        return new Result(displayName, List.copyOf(placements), minWorld, maxWorld);
    }

    private static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
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
            return unpackBlockData(packedData.get(), expectedEntries, paletteSize);
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

    public record BlockInstance(BlockPos position, BlockState state, CompoundTag blockEntityTag, Item requiredItem) {
    }

    public record Result(String displayName, List<BlockInstance> blocks, BlockPos minCorner, BlockPos maxCorner) {
    }

    private record IntermediatePlacement(BlockPos original, BlockPos rotated, BlockState state) {
    }
}
