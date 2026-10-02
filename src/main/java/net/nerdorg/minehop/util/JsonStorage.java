package net.nerdorg.minehop.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Crash-safe JSON persistence for all minehop on-disk data (maps, records, replays, anticheat).
 *
 * <p>Writes are atomic and backed up: content goes to a {@code .tmp} sibling, the prior good file
 * is copied to {@code .bak}, then the temp is moved into place with {@code ATOMIC_MOVE}. A crash at
 * any point leaves either the old file or the new file intact — never a truncated half-write (the
 * old in-place {@code Files.write} truncated first, so a crash mid-write corrupted the file and the
 * next load wiped every leaderboard).
 *
 * <p>Reads recover: a corrupt/unparseable file is quarantined to {@code .corrupt} and the
 * {@code .bak} is tried instead, so one bad/hand-edited file never aborts world load.
 *
 * <p>Every payload is wrapped in a small version envelope {@code {"_v":N,"data":...}} so future
 * schema changes can be migrated deliberately. Legacy bare files (no envelope) read as version 0.
 */
public final class JsonStorage {
    /**
     * Vec3d must NOT go through Gson's reflective adapter: reflection uses the RUNTIME field names,
     * which are obfuscated intermediary names in production ({@code field_1352/1351/1350} = x/y/z)
     * and {@code x/y/z} in a dev environment. Data saved by one could not be read by the other (every
     * checkpoint silently loaded as 0,0,0), and a mapping change would do the same to live data.
     * Writes stable {@code x/y/z}; reads either form so existing production files keep working.
     */
    private static final TypeAdapter<Vec3d> VEC3D_ADAPTER = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, Vec3d value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            out.name("x").value(value.x);
            out.name("y").value(value.y);
            out.name("z").value(value.z);
            out.endObject();
        }

        @Override
        public Vec3d read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            double x = 0.0D;
            double y = 0.0D;
            double z = 0.0D;
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "x", "field_1352" -> x = in.nextDouble();
                    case "y", "field_1351" -> y = in.nextDouble();
                    case "z", "field_1350" -> z = in.nextDouble();
                    default -> in.skipValue();
                }
            }
            in.endObject();
            return new Vec3d(x, y, z);
        }
    };

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Vec3d.class, VEC3D_ADAPTER)
            .create();
    private static final String ENVELOPE_VERSION_KEY = "_v";
    private static final String ENVELOPE_DATA_KEY = "data";

    private JsonStorage() {
    }

    /** Result of a recovered read: the inner data element plus the schema version it was stored at. */
    public static final class Loaded {
        public final int version;
        public final JsonElement data;

        public Loaded(int version, JsonElement data) {
            this.version = version;
            this.data = data;
        }
    }

    /**
     * Atomically write {@code data} (serialized as JSON) to {@code file} inside a version envelope.
     * Keeps a {@code .bak} of the previous good file. Returns true on success.
     */
    public static boolean writeAtomic(Path file, int version, Object data) {
        if (file == null) {
            return false;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            JsonObject envelope = new JsonObject();
            envelope.addProperty(ENVELOPE_VERSION_KEY, version);
            envelope.add(ENVELOPE_DATA_KEY, GSON.toJsonTree(data));
            byte[] bytes = GSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);

            Path tmp = siblingSuffix(file, ".tmp");
            Files.write(tmp, bytes);

            // Roll a backup of the prior good file before replacing it.
            if (Files.exists(file)) {
                try {
                    Files.copy(file, siblingSuffix(file, ".bak"), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception backupError) {
                    Minehop.LOGGER.warn("Failed to back up {} before save", file.getFileName(), backupError);
                }
            }

            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            Minehop.LOGGER.error("Failed to save {}", file.getFileName(), e);
            return false;
        }
    }

    /**
     * Read {@code file} with recovery. Returns null if no data exists yet (fresh install). On a
     * corrupt main file, quarantines it to {@code .corrupt} and falls back to {@code .bak}.
     */
    public static Loaded read(Path file) {
        if (file == null) {
            return null;
        }

        if (Files.exists(file)) {
            Loaded parsed = tryParse(file);
            if (parsed != null) {
                return parsed;
            }
            // Main file is unreadable/corrupt: quarantine it and try the backup.
            Minehop.LOGGER.error("Data file {} is corrupt; quarantining to .corrupt and trying backup", file.getFileName());
            quarantine(file);
        }

        Path backup = siblingSuffix(file, ".bak");
        if (Files.exists(backup)) {
            Loaded recovered = tryParse(backup);
            if (recovered != null) {
                Minehop.LOGGER.warn("Recovered {} from backup", file.getFileName());
                return recovered;
            }
            quarantine(backup);
        }
        return null;
    }

    /** Convenience: read and deserialize the inner data to {@code type}, or return null. */
    public static <T> T readData(Path file, java.lang.reflect.Type type) {
        Loaded loaded = read(file);
        if (loaded == null || loaded.data == null) {
            return null;
        }
        try {
            return GSON.fromJson(loaded.data, type);
        } catch (Exception e) {
            Minehop.LOGGER.error("Failed to deserialize {}", file.getFileName(), e);
            return null;
        }
    }

    private static Loaded tryParse(Path file) {
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return null;
            }
            JsonElement root = JsonParser.parseString(content);
            if (root == null || root.isJsonNull()) {
                return null;
            }
            // Versioned envelope { "_v": N, "data": ... }
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has(ENVELOPE_DATA_KEY)) {
                    int version = obj.has(ENVELOPE_VERSION_KEY) ? obj.get(ENVELOPE_VERSION_KEY).getAsInt() : 0;
                    return new Loaded(version, obj.get(ENVELOPE_DATA_KEY));
                }
            }
            // Legacy bare value (pre-envelope) → version 0.
            return new Loaded(0, root);
        } catch (Exception e) {
            return null;
        }
    }

    private static void quarantine(Path file) {
        try {
            Files.move(file, siblingSuffix(file, ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Minehop.LOGGER.warn("Failed to quarantine corrupt file {}", file.getFileName(), e);
        }
    }

    private static Path siblingSuffix(Path file, String suffix) {
        return file.resolveSibling(file.getFileName().toString() + suffix);
    }
}
