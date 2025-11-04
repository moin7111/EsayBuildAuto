package org.elpatronstudio.easybuild.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.elpatronstudio.easybuild.core.model.ChestRef;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stores user-selected container positions on the client so that EasyBuild can pull materials from them.
 */
public final class ClientChestRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<Entry> ENTRIES = new LinkedHashSet<>();
    private static boolean loaded;

    private ClientChestRegistry() {
    }

    private static Path registryPath(Path gameDir) {
        return gameDir.resolve("config").resolve("easybuild_chests.json");
    }

    public static void load(Path gameDir) {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = registryPath(gameDir);
        if (!Files.exists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("chests")) {
                return;
            }
            JsonArray array = root.getAsJsonArray("chests");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("dimension") || !obj.has("x") || !obj.has("y") || !obj.has("z")) {
                    continue;
                }
                try {
                    ResourceLocation dimension = ResourceLocation.parse(obj.get("dimension").getAsString());
                    int x = obj.get("x").getAsInt();
                    int y = obj.get("y").getAsInt();
                    int z = obj.get("z").getAsInt();
                    ENTRIES.add(new Entry(dimension, new BlockPos(x, y, z)));
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static boolean add(Path gameDir, ResourceLocation dimension, BlockPos pos) {
        load(gameDir);
        Entry entry = new Entry(dimension, pos.immutable());
        if (ENTRIES.add(entry)) {
            save(gameDir);
            return true;
        }
        return false;
    }

    public static boolean remove(Path gameDir, ResourceLocation dimension, BlockPos pos) {
        load(gameDir);
        boolean removed = ENTRIES.remove(new Entry(dimension, pos));
        if (removed) {
            save(gameDir);
        }
        return removed;
    }

    public static List<ChestRef> getForDimension(Path gameDir, ResourceLocation dimension) {
        load(gameDir);
        List<ChestRef> refs = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (entry.dimension.equals(dimension)) {
                refs.add(new ChestRef(entry.dimension, entry.pos));
            }
        }
        return refs;
    }

    public static boolean contains(Path gameDir, ResourceLocation dimension, BlockPos pos) {
        load(gameDir);
        return ENTRIES.contains(new Entry(dimension, pos));
    }

    private static void save(Path gameDir) {
        Path path = registryPath(gameDir);
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException ignored) {
        }
        JsonArray array = new JsonArray();
        for (Entry entry : ENTRIES) {
            JsonObject obj = new JsonObject();
            obj.addProperty("dimension", entry.dimension.toString());
            obj.addProperty("x", entry.pos.getX());
            obj.addProperty("y", entry.pos.getY());
            obj.addProperty("z", entry.pos.getZ());
            array.add(obj);
        }
        JsonObject root = new JsonObject();
        root.add("chests", array);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(root, writer);
        } catch (IOException ignored) {
        }
    }

    public static boolean isContainerBlockEntity(BlockEntity blockEntity) {
        return blockEntity instanceof net.minecraft.world.Container;
    }

    private record Entry(ResourceLocation dimension, BlockPos pos) {
        private Entry {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pos, "pos");
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Entry other)) {
                return false;
            }
            return dimension.equals(other.dimension) && pos.equals(other.pos);
        }

        @Override
        public int hashCode() {
            return 31 * dimension.hashCode() + pos.hashCode();
        }
    }
}
