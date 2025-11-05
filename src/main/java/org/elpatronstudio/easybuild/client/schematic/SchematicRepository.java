package org.elpatronstudio.easybuild.client.schematic;

import com.mojang.logging.LogUtils;
import org.elpatronstudio.easybuild.client.model.SchematicFileEntry;
import org.elpatronstudio.easybuild.core.model.SchematicRef;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Discovers schematic files in the player's game directory.
 */
public final class SchematicRepository {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("schem", "schematic", "litematic", "nbt");

    private SchematicRepository() {
    }

    public static List<SchematicFileEntry> load(Path gameDir) {
        Path directory = gameDir.resolve("schematics");
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        List<SchematicFileEntry> entries = new ArrayList<>();
        try {
            Files.walk(directory)
                    .filter(path -> Files.isRegularFile(path) && isSupported(path))
                    .forEach(path -> entries.add(toEntry(directory, path)));
        } catch (IOException ex) {
            LOGGER.warn("Failed to scan schematics directory {}: {}", directory, ex.getMessage());
            return List.of();
        }

        entries.sort(Comparator.comparing(SchematicFileEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private static boolean isSupported(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(ext);
    }

    private static SchematicFileEntry toEntry(Path root, Path file) {
        try {
            Path relative = root.relativize(file);
            String id = relative.toString().replace(root.getFileSystem().getSeparator(), "/");
            String displayName = stripExtension(relative.getFileName().toString());
            FileTime time = Files.getLastModifiedTime(file);
            long size = Files.size(file);
            long checksum = computeChecksum(file);
            SchematicRef ref = new SchematicRef(id, 1, checksum);
            return new SchematicFileEntry(id, displayName, file, ref, time.toMillis(), size);
        } catch (IOException ex) {
            LOGGER.warn("Failed to index schematic file {}: {}", file, ex.getMessage());
            SchematicRef ref = new SchematicRef(file.getFileName().toString(), 1, 0L);
            return new SchematicFileEntry(file.getFileName().toString(), file.getFileName().toString(), file, ref, 0L, 0L);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static long computeChecksum(Path file) {
        CRC32 crc = new CRC32();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                crc.update(buffer, 0, read);
            }
        } catch (IOException ex) {
            LOGGER.debug("Failed to compute checksum for {}: {}", file, ex.getMessage());
            return 0L;
        }
        return crc.getValue();
    }
}
