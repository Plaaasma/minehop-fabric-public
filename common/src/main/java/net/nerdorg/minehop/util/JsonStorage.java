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
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;

import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final TypeAdapter<Vec3> VEC3D_ADAPTER = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, Vec3 value) throws IOException {
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
        public Vec3 read(JsonReader in) throws IOException {
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
            return new Vec3(x, y, z);
        }
    };

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Vec3.class, VEC3D_ADAPTER)
            .create();
    private static final String ENVELOPE_VERSION_KEY = "_v";
    private static final String ENVELOPE_DATA_KEY = "data";
    /** Files whose last load failed for a reason other than corrupt content; writes to them are refused. */
    private static final Set<Path> LOAD_FAILED = ConcurrentHashMap.newKeySet();

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
     *
     * <p>Streams straight to the temp file (same bytes as building the JSON tree first, without holding
     * the whole document in memory: the replay store is hundreds of MB). Refuses to write a file whose
     * last load failed for a reason other than corrupt content, see {@link #isLoadFailed}.
     */
    public static boolean writeAtomic(Path file, int version, Object data) {
        if (file == null) {
            return false;
        }
        if (isLoadFailed(file)) {
            Minehop.LOGGER.error("Refusing to save {}: it failed to load this session, so the data in memory is incomplete "
                    + "and saving would overwrite the real file. Fix the load error (see earlier log) and restart.", file.getFileName());
            return false;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tmp = siblingSuffix(file, ".tmp");
            try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8);
                 JsonWriter json = GSON.newJsonWriter(out)) {
                json.beginObject();
                json.name(ENVELOPE_VERSION_KEY).value(version);
                json.name(ENVELOPE_DATA_KEY);
                GSON.toJson(data, data == null ? Object.class : data.getClass(), json);
                json.endObject();
            }

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
            Loaded parsed;
            try {
                parsed = tryParse(file);
            } catch (LoadFailedException e) {
                LOAD_FAILED.add(key(file));
                Minehop.LOGGER.error("Failed to load {} (not corrupt; left untouched, saving disabled this session)", file.getFileName(), e.getCause());
                return null;
            }
            if (parsed != null) {
                LOAD_FAILED.remove(key(file));
                return parsed;
            }
            // Main file is unreadable/corrupt: quarantine it and try the backup.
            Minehop.LOGGER.error("Data file {} is corrupt; quarantining to .corrupt and trying backup", file.getFileName());
            quarantine(file);
        }

        Path backup = siblingSuffix(file, ".bak");
        if (Files.exists(backup)) {
            Loaded recovered;
            try {
                recovered = tryParse(backup);
            } catch (LoadFailedException e) {
                LOAD_FAILED.add(key(file));
                Minehop.LOGGER.error("Failed to load backup of {} (left untouched, saving disabled this session)", file.getFileName(), e.getCause());
                return null;
            }
            if (recovered != null) {
                LOAD_FAILED.remove(key(file));
                Minehop.LOGGER.warn("Recovered {} from backup", file.getFileName());
                return recovered;
            }
            quarantine(backup);
        }
        return null;
    }

    /**
     * Read and deserialize the inner data to {@code type}, or return null (no data yet, or nothing
     * readable). Streams the file, so a large store is never held as one string or JSON tree.
     *
     * <p>A corrupt main file (malformed or truncated JSON) is quarantined and the {@code .bak} tried, as
     * before. A load that fails for any other reason (out of memory, an I/O error, valid JSON of the
     * wrong shape) is NOT corruption:
     * nothing is quarantined, null is returned, and the file is marked load-failed so no later write
     * can replace it with the incomplete data in memory. (Gson reports an OutOfMemoryError while
     * parsing as a parse error, which used to quarantine the file and start with no data.)
     */
    public static <T> T readData(Path file, java.lang.reflect.Type type) {
        if (file == null) {
            return null;
        }
        Path key = key(file);
        if (Files.exists(file)) {
            ReadResult<T> main = tryRead(file, type);
            switch (main.status) {
                case OK -> {
                    LOAD_FAILED.remove(key);
                    return main.value;
                }
                case SHAPE -> {
                    // Valid JSON that doesn't match the type: not quarantined, but not saved over either, since the
                    // empty data the caller is left with would replace it.
                    LOAD_FAILED.add(key);
                    Minehop.LOGGER.error("Failed to deserialize {} (left untouched; saving disabled this session)", file.getFileName(), main.error);
                    return null;
                }
                case FAILED -> {
                    LOAD_FAILED.add(key);
                    Minehop.LOGGER.error("Failed to load {} (not corrupt; the file is left untouched and will not be saved "
                            + "over this session)", file.getFileName(), main.error);
                    return null;
                }
                case CORRUPT -> {
                    Minehop.LOGGER.error("Data file {} is corrupt; quarantining to .corrupt and trying backup", file.getFileName());
                    quarantine(file);
                }
            }
        }

        Path backup = siblingSuffix(file, ".bak");
        if (Files.exists(backup)) {
            ReadResult<T> recovered = tryRead(backup, type);
            switch (recovered.status) {
                case OK -> {
                    LOAD_FAILED.remove(key);
                    Minehop.LOGGER.warn("Recovered {} from backup", file.getFileName());
                    return recovered.value;
                }
                case SHAPE -> {
                    LOAD_FAILED.add(key);
                    Minehop.LOGGER.error("Failed to deserialize backup of {} (left untouched; saving disabled this session)",
                            file.getFileName(), recovered.error);
                }
                case FAILED -> {
                    LOAD_FAILED.add(key);
                    Minehop.LOGGER.error("Failed to load backup of {} (left untouched; saving disabled this session)",
                            file.getFileName(), recovered.error);
                }
                case CORRUPT -> quarantine(backup);
            }
        }
        return null;
    }

    /**
     * True if {@code file} failed to load this session for a reason other than corrupt content. Writes
     * to it are refused until a later load succeeds (e.g. after a restart with more memory).
     */
    public static boolean isLoadFailed(Path file) {
        return file != null && LOAD_FAILED.contains(key(file));
    }

    private enum ReadStatus { OK, CORRUPT, SHAPE, FAILED }

    private record ReadResult<T>(ReadStatus status, T value, Throwable error) {
    }

    private static <T> ReadResult<T> tryRead(Path file, java.lang.reflect.Type type) {
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             JsonReader json = GSON.newJsonReader(in)) {
            json.setLenient(true); // JsonParser (the old read path) parses leniently too
            if (json.peek() == JsonToken.BEGIN_OBJECT) {
                // Our envelope is written "_v" first. Anything else (a legacy bare object) takes the tree path.
                json.beginObject();
                if (!json.hasNext() || !ENVELOPE_VERSION_KEY.equals(json.nextName())) {
                    return readTree(file, type);
                }
                json.skipValue();
                if (!json.hasNext() || !ENVELOPE_DATA_KEY.equals(json.nextName())) {
                    return readTree(file, type);
                }
                T value = GSON.fromJson(json, type);
                return new ReadResult<>(ReadStatus.OK, value, null);
            }
            // Legacy bare value (pre-envelope).
            T value = GSON.fromJson(json, type);
            return new ReadResult<>(ReadStatus.OK, value, null);
        } catch (OutOfMemoryError | Exception e) {
            return new ReadResult<>(classify(e), null, e);
        }
    }

    /** Old tree-based read, for files that are not in our streamed envelope shape. */
    private static <T> ReadResult<T> readTree(Path file, java.lang.reflect.Type type) {
        Loaded loaded;
        try {
            loaded = tryParse(file);
        } catch (LoadFailedException e) {
            return new ReadResult<>(ReadStatus.FAILED, null, e.getCause());
        }
        if (loaded == null) {
            return new ReadResult<>(ReadStatus.CORRUPT, null, null);
        }
        if (loaded.data == null) {
            return new ReadResult<>(ReadStatus.OK, null, null);
        }
        try {
            return new ReadResult<>(ReadStatus.OK, GSON.fromJson(loaded.data, type), null);
        } catch (OutOfMemoryError | Exception e) {
            ReadStatus status = classify(e);
            return new ReadResult<>(status == ReadStatus.CORRUPT ? ReadStatus.SHAPE : status, null, e);
        }
    }

    /**
     * Out of memory anywhere in the cause chain, or an I/O error that isn't malformed/truncated JSON:
     * the read failed, the file is not known to be bad. Malformed or truncated JSON: corrupt. Anything
     * else (valid JSON of the wrong shape): a deserialization mismatch.
     */
    private static ReadStatus classify(Throwable error) {
        boolean malformed = false;
        boolean ioError = false;
        for (Throwable cause = error; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof OutOfMemoryError) {
                return ReadStatus.FAILED;
            }
            if (cause instanceof MalformedJsonException || cause instanceof EOFException) {
                malformed = true;
            } else if (cause instanceof IOException) {
                ioError = true;
            }
        }
        if (malformed) {
            return ReadStatus.CORRUPT;
        }
        return ioError ? ReadStatus.FAILED : ReadStatus.SHAPE;
    }

    private static Path key(Path file) {
        return file.toAbsolutePath().normalize();
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
        } catch (OutOfMemoryError | Exception e) {
            if (classify(e) == ReadStatus.FAILED) {
                throw new LoadFailedException(e);
            }
            return null;
        }
    }

    /** A read that failed without the file being known to be corrupt (out of memory, I/O error). */
    private static final class LoadFailedException extends RuntimeException {
        LoadFailedException(Throwable cause) {
            super(cause);
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
