package net.nerdorg.minehop.replays.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * File names in the replay store: {@code <map key>/<replay file>}.
 *
 * <p>The map key is the map name lower-cased with anything but {@code [a-z0-9_-]} replaced by '_' (at most 40
 * characters), then '-' and the CRC32 of the exact name in hex: safe on every file system (no reserved names, no case
 * collisions between "Foo" and "foo" on Windows) and still readable. Two names with the same key would just share a
 * folder: every file names its run and map in its header and the run log stores the path, so nothing is ever derived
 * from a folder name. A replay file is the replay id when that is a plain lower-case id (all ids this mod makes are
 * random UUIDs), else a hash of the id.
 */
public final class StorePaths {
    public static final String EXTENSION = ".mhr";
    public static final String STATUS_EXTENSION = ".inv";
    public static final String CORRUPT_EXTENSION = ".mhr.corrupt";
    public static final String TEMP_SUFFIX = ".tmp";
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final Pattern MAP_KEY = Pattern.compile("[a-z0-9_-]{1,40}-[0-9a-f]{8}");
    private static final Pattern FILE_BASE = Pattern.compile("([a-z0-9_-]{1,64}|x[0-9a-f]{32})");

    private StorePaths() {
    }

    public static String mapKey(String mapName) {
        String name = mapName == null ? "" : mapName;
        StringBuilder key = new StringBuilder(Math.min(40, name.length()));
        for (int i = 0; i < name.length() && key.length() < 40; i++) {
            char c = Character.toLowerCase(name.charAt(i));
            key.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' ? c : '_');
        }
        if (key.length() == 0) {
            key.append("map");
        }
        CRC32 crc = new CRC32();
        crc.update(name.getBytes(StandardCharsets.UTF_8));
        return key + "-" + String.format(Locale.ROOT, "%08x", crc.getValue());
    }

    /** The file's name without extension for a replay id. */
    public static String fileBase(String replayId) {
        String id = replayId == null ? "" : replayId;
        if (SAFE_ID.matcher(id).matches()) {
            return id;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            return "x" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "{map key}/{file base}.mhr", the path of a run's file relative to the store root. */
    public static String relativeFile(String mapName, String replayId) {
        return mapKey(mapName) + "/" + fileBase(replayId) + EXTENSION;
    }

    /**
     * Resolves a relative run file path (as stored in the run log) against the store root, or returns null if it is
     * not exactly "{map key}/{file base}.mhr": a damaged or hand-edited log can't make the store read, move or delete
     * any other file.
     */
    public static Path resolveRunFile(Path root, String relative) {
        if (relative == null) {
            return null;
        }
        int slash = relative.indexOf('/');
        if (slash <= 0 || slash != relative.lastIndexOf('/') || !relative.endsWith(EXTENSION)) {
            return null;
        }
        String dir = relative.substring(0, slash);
        String base = relative.substring(slash + 1, relative.length() - EXTENSION.length());
        if (!MAP_KEY.matcher(dir).matches() || !FILE_BASE.matcher(base).matches()) {
            return null;
        }
        Path resolved = root.resolve(dir).resolve(base + EXTENSION).normalize();
        return resolved.startsWith(root.normalize()) ? resolved : null;
    }

    /** True for a store sub-folder holding runs (not "_deleted" or anything else). */
    public static boolean isMapKey(String directoryName) {
        return directoryName != null && MAP_KEY.matcher(directoryName).matches();
    }

    /** True for a run file name without extension, as {@link #fileBase} makes them. */
    public static boolean isFileBase(String base) {
        return base != null && FILE_BASE.matcher(base).matches();
    }

    /** The sibling of a run file with another extension (e.g. its status file). */
    public static Path sibling(Path runFile, String extension) {
        String name = runFile.getFileName().toString();
        String base = name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
        return runFile.resolveSibling(base + extension);
    }
}
