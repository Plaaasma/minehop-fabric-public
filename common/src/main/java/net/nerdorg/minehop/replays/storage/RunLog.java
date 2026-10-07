package net.nerdorg.minehop.replays.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The run log ({@code runs.jsonl}): one JSON object per line, appended as things happen and compacted (rewritten as
 * one "run" line per stored run, current status folded in) atomically in the background.
 *
 * <ul>
 *   <li>{@code {"op":"run", ...}}: a finished run whose file was written (every field of {@link Line}).</li>
 *   <li>{@code {"op":"inv","id":..,"invalidated":".."}}: the run was invalidated (also written to its status file).</li>
 *   <li>{@code {"op":"del","id":..}}: the run was removed (its files were moved to _deleted).</li>
 * </ul>
 *
 * Appending is crash safe for every earlier line: a torn last line (power loss mid-append) fails to parse and is
 * skipped on load, and the store compacts the log before appending to one that doesn't end in a newline. Anything
 * the log loses is rebuilt from the run files' headers and status files (see ReplayStore).
 */
public final class RunLog {
    public static final String FILE = "runs.jsonl";
    public static final String OP_RUN = "run";
    public static final String OP_INVALIDATE = "inv";
    public static final String OP_DELETE = "del";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private RunLog() {
    }

    /** One line. Unset fields are left out. */
    public static final class Line {
        public int v = 1;
        public String op;
        public String id;
        public String map;
        public String uuid;
        public String name;
        public Double time;
        public Long saved_at;
        public Integer frames;
        public String ac_flags;
        /** Path of the run file relative to the store root ("{map key}/{id}.mhr"). */
        public String file;
        /** {minX, minY, minZ, maxX, maxY, maxZ} of the route. */
        public double[] bounds;
        public Double server_time;
        public Long client_ticks;
        public Integer pre;
        public Integer post;
        public String invalidated;

        public static Line delete(String id) {
            Line line = new Line();
            line.op = OP_DELETE;
            line.id = id;
            return line;
        }

        public static Line invalidate(String id, String reason) {
            Line line = new Line();
            line.op = OP_INVALIDATE;
            line.id = id;
            line.invalidated = reason == null ? "" : reason;
            return line;
        }

        /** True if this is a usable line of a known kind (a damaged line that still parses as JSON is dropped). */
        public boolean isValid() {
            if (this.op == null || this.id == null || this.id.isBlank()) {
                return false;
            }
            return switch (this.op) {
                case OP_RUN -> this.map != null && !this.map.isBlank() && this.time != null && Double.isFinite(this.time)
                        && this.saved_at != null && this.frames != null && this.frames >= 0 && this.file != null
                        && (this.bounds == null || this.bounds.length == 6);
                case OP_INVALIDATE -> this.invalidated != null;
                case OP_DELETE -> true;
                default -> false;
            };
        }
    }

    public record ReadResult(List<Line> lines, int badLines, boolean existed, boolean endsWithNewline) {
    }

    /** Reads every line; lines that aren't valid JSON or valid entries are counted and skipped. */
    public static ReadResult read(Path file) throws IOException {
        List<Line> lines = new ArrayList<>();
        int bad = 0;
        boolean endsWithNewline = true;
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size > 0) {
                ByteBuffer last = ByteBuffer.allocate(1);
                channel.read(last, size - 1);
                endsWithNewline = last.get(0) == '\n';
            }
        } catch (NoSuchFileException e) {
            return new ReadResult(lines, 0, false, true);
        }
        // Undecodable bytes become U+FFFD: that line then fails to parse instead of failing the whole read.
        try (BufferedReader in = new BufferedReader(new InputStreamReader(Files.newInputStream(file),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE)), 1 << 16)) {
            String text;
            while ((text = in.readLine()) != null) {
                if (text.isBlank()) {
                    continue;
                }
                Line line;
                try {
                    line = GSON.fromJson(text, Line.class);
                } catch (RuntimeException e) {
                    line = null;
                }
                if (line == null || !line.isValid()) {
                    bad++;
                    continue;
                }
                lines.add(line);
            }
        }
        return new ReadResult(lines, bad, true, endsWithNewline);
    }

    /** Appends lines and forces them to disk. */
    public static void append(Path file, List<Line> lines) throws IOException {
        StringBuilder text = new StringBuilder();
        for (Line line : lines) {
            text.append(GSON.toJson(line)).append('\n');
        }
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
            ByteBuffer bytes = ByteBuffer.wrap(text.toString().getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(false);
        }
    }

    /**
     * Replaces the log with these lines: written to a temp file and forced, the old log copied to {@code .bak}, then
     * the temp file moved into place, so a crash leaves the old or the new log, never a mix.
     */
    public static void writeAll(Path file, List<Line> lines) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            StringBuilder text = new StringBuilder(256);
            for (int i = 0; i < lines.size(); i++) {
                text.append(GSON.toJson(lines.get(i))).append('\n');
                if (text.length() > (1 << 16) || i == lines.size() - 1) {
                    ByteBuffer bytes = ByteBuffer.wrap(text.toString().getBytes(StandardCharsets.UTF_8));
                    while (bytes.hasRemaining()) {
                        channel.write(bytes);
                    }
                    text.setLength(0);
                }
            }
            channel.force(true);
        }
        if (Files.exists(file)) {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        moveAtomically(tmp, file);
    }

    static void moveAtomically(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
