package org.elpatronstudio.easybuild.server.job;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.elpatronstudio.esaybuildauto.Esaybuildauto;
import org.elpatronstudio.easybuild.core.model.AnchorPos;
import org.elpatronstudio.easybuild.core.model.SchematicRef;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Creates block placement plans from schematic references.
 */
public final class BlockPlacementPlanner {

    private BlockPlacementPlanner() {
    }

    static BlockPlacementPlan plan(ServerLevel level, BuildJob job, JsonObject options) throws BlockPlacementException {
        StructureTemplate template = resolveTemplate(level, job.schematic());
        if (template == null) {
            throw new BlockPlacementException("SCHEMATIC_NOT_FOUND", "Keine Strukturvorlage für " + job.schematic().schematicId());
        }

        AnchorPos anchor = job.anchor();
        BlockPos anchorPos = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        Rotation rotation = rotationFor(anchor.facing());
        Mirror mirror = Mirror.NONE;

        boolean includeAir = options != null && options.has("placeAir") && options.get("placeAir").getAsBoolean();

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(mirror)
                .setRotation(rotation)
                .setIgnoreEntities(false);

        List<BlockPlacement> placements = new ArrayList<>();
        List<StructureTemplate.StructureBlockInfo> rawBlocks = extractPrimaryPalette(template);
        List<StructureTemplate.StructureBlockInfo> processed = StructureTemplate.processBlockInfos(level, anchorPos, anchorPos, settings, rawBlocks, template);
        for (StructureTemplate.StructureBlockInfo info : processed) {
            BlockState state = info.state().mirror(mirror).rotate(rotation);
            if (!includeAir && state.isAir()) {
                continue;
            }

            BlockPos worldPos = info.pos().immutable();
            CompoundTag nbt = info.nbt();
            if (nbt != null) {
                nbt = nbt.copy();
                nbt.putInt("x", worldPos.getX());
                nbt.putInt("y", worldPos.getY());
                nbt.putInt("z", worldPos.getZ());
            }
            placements.add(new BlockPlacement(worldPos, state, nbt));
        }

        placements.sort(Comparator
                .comparingInt((BlockPlacement placement) -> placement.position().getY())
                .thenComparingInt(placement -> placement.position().getX())
                .thenComparingInt(placement -> placement.position().getZ()));

        BlockRegion region = BlockRegion.fromPlacements(placements, anchorPos);

        return new BlockPlacementPlan(placements, region);
    }

    private static StructureTemplate resolveTemplate(ServerLevel level, SchematicRef ref) throws BlockPlacementException {
        ResourceLocation structureId = resolveStructureLocation(ref);
        StructureTemplateManager manager = level.getServer().getStructureManager();
        Optional<StructureTemplate> template = manager.get(structureId);
        if (template.isPresent()) {
            return template.get();
        }

        // Attempt to load the template if it was not cached yet
        return manager.getOrCreate(structureId);
    }

    public static ResourceLocation resolveStructureLocation(SchematicRef ref) throws BlockPlacementException {
        String rawId = ref.schematicId();
        if (rawId == null || rawId.isBlank()) {
            throw new BlockPlacementException("SCHEMATIC_ID_MISSING", "Die Schematic-ID fehlt");
        }

        ResourceLocation explicit = ResourceLocation.tryParse(rawId);
        if (explicit != null) {
            return explicit;
        }

        String normalized = rawId.toLowerCase(Locale.ROOT);
        StringBuilder path = new StringBuilder("schematics/");
        path.append(normalized);
        if (ref.version() > 0) {
            path.append("_v").append(ref.version());
        }
        return ResourceLocation.fromNamespaceAndPath(Esaybuildauto.MODID, path.toString());
    }

    private static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    static ResourceKey<Level> resolveDimensionKey(AnchorPos anchor) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, anchor.dimension());
    }

    @SuppressWarnings("unchecked")
    public static List<StructureTemplate.StructureBlockInfo> extractPrimaryPalette(StructureTemplate template) throws BlockPlacementException {
        List<StructureTemplate.Palette> palettes = (List<StructureTemplate.Palette>) getPalettes(template);
        if (palettes.isEmpty()) {
            return List.of();
        }
        return palettes.get(0).blocks();
    }

    private static Object getPalettes(StructureTemplate template) throws BlockPlacementException {
        if (PALETTES_FIELD == null) {
            throw new BlockPlacementException("SCHEMATIC_INTERNAL", "Palettenfeld nicht gefunden");
        }
        try {
            if (!PALETTES_FIELD.trySetAccessible()) {
                throw new IllegalAccessException("Zugriff verweigert");
            }
            return PALETTES_FIELD.get(template);
        } catch (ReflectiveOperationException ex) {
            throw new BlockPlacementException("SCHEMATIC_INTERNAL", "Paletten konnten nicht gelesen werden: " + ex.getMessage());
        }
    }

    private static final Field PALETTES_FIELD;

    static {
        Field field;
        try {
            field = StructureTemplate.class.getDeclaredField("palettes");
        } catch (NoSuchFieldException ex) {
            field = null;
        }
        PALETTES_FIELD = field;
    }
}
