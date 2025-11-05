package org.elpatronstudio.easybuild.core.network;

import java.util.Locale;
import java.util.Objects;

/**
 * Simple semantic version representation used for EasyBuild protocol negotiation.
 */
public record ProtocolVersion(int major, int minor, int patch) implements Comparable<ProtocolVersion> {

    public static final ProtocolVersion ZERO = new ProtocolVersion(0, 0, 0);

    public ProtocolVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version numbers must be non-negative");
        }
    }

    public static ProtocolVersion parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Empty version string");
        }
        String[] parts = trimmed.split("\\.");
        int major = parsePart(parts, 0);
        int minor = parts.length > 1 ? parsePart(parts, 1) : 0;
        int patch = parts.length > 2 ? parsePart(parts, 2) : 0;
        return new ProtocolVersion(major, minor, patch);
    }

    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        String token = parts[index].trim();
        if (token.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!Character.isDigit(c)) {
                throw new IllegalArgumentException("Invalid character '" + c + "' in version part");
            }
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Version part out of range", ex);
        }
    }

    public boolean isCompatibleWith(ProtocolVersion other) {
        return other != null && this.major == other.major;
    }

    public String canonical() {
        if (patch != 0) {
            return String.format(Locale.ROOT, "%d.%d.%d", major, minor, patch);
        }
        if (minor != 0) {
            return String.format(Locale.ROOT, "%d.%d", major, minor);
        }
        return Integer.toString(major);
    }

    @Override
    public int compareTo(ProtocolVersion other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return canonical();
    }
}
