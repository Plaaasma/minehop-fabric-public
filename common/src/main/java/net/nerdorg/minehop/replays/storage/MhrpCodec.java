package net.nerdorg.minehop.replays.storage;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Encoder and strict decoder for MHRP replay files (one run per file).
 *
 * <pre>
 * File (fixed-width integers big-endian, "u32" read as unsigned):
 *   0   4  magic "MHRP"
 *   4   2  u16 format version (1)
 *   6   4  u32 header length H (at most 65536)
 *   10  H  header, fields in this order (str = u16 byte length + UTF-8):
 *            str replay id | str map name | str player UUID ("" = legacy) | str player name
 *            f64 time (s) | i64 saved_at (epoch ms) | f64 server-measured time (s, NaN = unknown)
 *            i64 client ticks (-1 = unknown) | f32 tick rate (frames/s)
 *            u32 frame count | u32 pre-frames | u32 post-frames | u32 flags (MhrpHeader.FLAG_*)
 *            str anticheat flags | f64 x3 origin | f64 x6 bounds (min xyz, max xyz)
 *            u32 position scale (units/block) | u32 angle scale (units/turn) | u32 speed scale (units per b/t)
 *            u32 efficiency scale (units/percent) | u32 frames per block
 *          A reader skips header bytes after the fields it knows (fields may be appended without a version bump).
 *   ..  4  u32 block count = ceil(frame count / frames per block)
 *   per block (frames [b*F, min((b+1)*F, frame count))):
 *       4  u32 raw length R (inflated)
 *       4  u32 compressed length C
 *       C  zlib (deflate) stream that inflates to exactly R bytes of column data:
 *            x, y, z    n zig-zag varlongs: q = round((pos - origin) * position scale); first value absolute,
 *                       then q[i] - q[i-1]
 *            yaw, pitch n zig-zag varlongs: q = round(degrees * angle scale / 360), not wrapped; delta coded
 *            jumps      n zig-zag varlongs: jump count, delta coded
 *            speed      n zig-zag varlongs: q = round(last jump speed (b/t) * speed scale), delta coded
 *            efficiency n zig-zag varlongs: q = round(efficiency (0..100) * efficiency scale), delta coded
 *            flags      n unsigned varints: flags[i] XOR flags[i-1] (ReplayFrames.FLAG_*)
 *          Every block starts its deltas from 0, so blocks decode independently (seeking, streaming).
 *   ..  4  u32 CRC32 of every byte before it
 * </pre>
 *
 * Positions are exact to 1/8192 block anywhere in the world (origin-relative; absolute float32 is off by up to 0.25
 * blocks at x = 5.4M), angles to 1/131072 turn. The decoder checks the version and CRC before trusting any length,
 * bounds-checks every length and value, never allocates more than the capped frame count allows, and rejects
 * trailing bytes. Pure Java.
 */
public final class MhrpCodec {
    public static final int VERSION = 1;
    public static final int POSITION_SCALE = 4096;
    public static final int ANGLE_SCALE = 65536;
    public static final int SPEED_SCALE = 4096;
    public static final int EFFICIENCY_SCALE = 100;
    public static final int FRAMES_PER_BLOCK = 1024;
    /** 14.5 hours at 20 frames/s. Recording stops at 60 minutes; the longest stored legacy run has 119,775 frames. */
    public static final int MAX_FRAMES = 1 << 20;
    public static final int MAX_HEADER_BYTES = 1 << 16;
    /** Larger than any file this format can produce for {@link #MAX_FRAMES} frames is not read. */
    public static final long MAX_FILE_BYTES = 128L << 20;
    public static final int DEFLATE_LEVEL = 6;

    static final int MAX_ID_BYTES = 256;
    static final int MAX_MAP_BYTES = 1024;
    static final int MAX_UUID_BYTES = 128;
    static final int MAX_NAME_BYTES = 256;
    static final int MAX_AC_FLAGS_BYTES = 16384;
    private static final byte[] MAGIC = {'M', 'H', 'R', 'P'};
    private static final int VALUE_COLUMNS = 8;
    /** 8 varlongs (at most 10 bytes) + the flags varint (at most 5). */
    private static final int MAX_BYTES_PER_FRAME = VALUE_COLUMNS * 10 + 5;
    private static final int MIN_FRAMES_PER_BLOCK = 16;
    private static final int MAX_FRAMES_PER_BLOCK = 8192;
    /** |quantised value| limit: keeps every delta inside a long and every value exactly representable as a double. */
    private static final long MAX_ABS_QUANTISED = 1L << 53;
    private static final int PREFIX_BYTES = 4 + 2 + 4;
    private static final int MIN_FILE_BYTES = PREFIX_BYTES + 4 + 4;

    private MhrpCodec() {
    }

    /** A decoded file. */
    public record Decoded(MhrpHeader header, ReplayFrames frames) {
    }

    // ------------------------------------------------------------------------------------------
    // Encoding
    // ------------------------------------------------------------------------------------------

    /**
     * Encodes a run. The frame count, origin, bounds, quantisation and block size of {@code header} are filled in
     * from the frames (the caller's header object is not modified); everything else is written as given. Frames with
     * non-finite or out-of-range values are stored with the previous frame's value and {@link ReplayFrames#FLAG_REPAIRED}.
     *
     * @throws IllegalArgumentException if there are too many frames or a text field is too long to store.
     */
    public static byte[] encode(MhrpHeader header, ReplayFrames frames) {
        return encode(header, frames, FRAMES_PER_BLOCK, DEFLATE_LEVEL);
    }

    /** {@link #encode(MhrpHeader, ReplayFrames)} with another block size (16..8192 frames) or deflate level (0..9). */
    public static byte[] encode(MhrpHeader header, ReplayFrames frames, int framesPerBlock, int deflateLevel) {
        if (framesPerBlock < MIN_FRAMES_PER_BLOCK || framesPerBlock > MAX_FRAMES_PER_BLOCK) {
            throw new IllegalArgumentException("frames per block " + framesPerBlock);
        }
        if (frames == null) {
            frames = ReplayFrames.EMPTY;
        }
        int n = frames.size();
        if (n > MAX_FRAMES) {
            throw new IllegalArgumentException("Too many frames for one replay file: " + n + " (max " + MAX_FRAMES + ")");
        }
        MhrpHeader h = header.copy();
        h.frameCount = n;
        h.preFrames = Math.max(0, Math.min(h.preFrames, n));
        h.postFrames = Math.max(0, Math.min(h.postFrames, n - h.preFrames));
        h.positionScale = POSITION_SCALE;
        h.angleScale = ANGLE_SCALE;
        h.speedScale = SPEED_SCALE;
        h.efficiencyScale = EFFICIENCY_SCALE;
        h.framesPerBlock = framesPerBlock;
        if (!Float.isFinite(h.tickRate) || h.tickRate <= 0.0F || h.tickRate > 1000.0F) {
            h.tickRate = 20.0F;
        }
        if (!Double.isFinite(h.time)) {
            throw new IllegalArgumentException("Run time is not finite: " + h.time);
        }
        if (Double.isInfinite(h.serverTime)) {
            h.serverTime = Double.NaN;
        }
        setOriginAndBounds(h, frames);

        Quantiser quantiser = new Quantiser(h, frames);
        if (quantiser.anyRepaired) {
            h.flags |= MhrpHeader.FLAG_REPAIRED_FRAMES;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(256 + n * 3);
        out.writeBytes(MAGIC);
        writeU16(out, VERSION);
        byte[] headerBytes = encodeHeader(h);
        writeU32(out, headerBytes.length);
        out.writeBytes(headerBytes);

        int blocks = blockCount(n, h.framesPerBlock);
        writeU32(out, blocks);
        GrowableBytes raw = new GrowableBytes(Math.min(n, h.framesPerBlock) * 24 + 16);
        Deflater deflater = new Deflater(deflateLevel);
        try {
            byte[] chunk = new byte[8192];
            for (int b = 0; b < blocks; b++) {
                int start = b * h.framesPerBlock;
                int count = Math.min(h.framesPerBlock, n - start);
                raw.reset();
                quantiser.writeBlock(raw, start, count);
                writeU32(out, raw.size());
                deflater.reset();
                deflater.setInput(raw.array(), 0, raw.size());
                deflater.finish();
                ByteArrayOutputStream compressed = new ByteArrayOutputStream(raw.size() / 2 + 16);
                while (!deflater.finished()) {
                    int produced = deflater.deflate(chunk);
                    compressed.write(chunk, 0, produced);
                }
                writeU32(out, compressed.size());
                out.writeBytes(compressed.toByteArray());
            }
        } finally {
            deflater.end();
        }
        byte[] body = out.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(body, 0, body.length);
        byte[] file = new byte[body.length + 4];
        System.arraycopy(body, 0, file, 0, body.length);
        putU32(file, body.length, crc.getValue());
        return file;
    }

    private static void setOriginAndBounds(MhrpHeader h, ReplayFrames frames) {
        h.originX = 0.0D;
        h.originY = 0.0D;
        h.originZ = 0.0D;
        for (int i = 0; i < frames.size(); i++) {
            if (Double.isFinite(frames.x(i)) && Double.isFinite(frames.y(i)) && Double.isFinite(frames.z(i))) {
                h.originX = frames.x(i);
                h.originY = frames.y(i);
                h.originZ = frames.z(i);
                break;
            }
        }
        double[] bounds = frames.bounds();
        if (bounds == null) {
            bounds = new double[]{h.originX, h.originY, h.originZ, h.originX, h.originY, h.originZ};
        }
        if (frames.isEmpty()) {
            bounds = new double[6];
        }
        h.minX = bounds[0];
        h.minY = bounds[1];
        h.minZ = bounds[2];
        h.maxX = bounds[3];
        h.maxY = bounds[4];
        h.maxZ = bounds[5];
    }

    private static byte[] encodeHeader(MhrpHeader h) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        writeString(out, h.replayId, MAX_ID_BYTES, "replay id", false);
        writeString(out, h.mapName, MAX_MAP_BYTES, "map name", false);
        writeString(out, h.playerUuid, MAX_UUID_BYTES, "player UUID", false);
        writeString(out, h.playerName, MAX_NAME_BYTES, "player name", false);
        writeU64(out, Double.doubleToLongBits(h.time));
        writeU64(out, h.savedAt);
        writeU64(out, Double.doubleToLongBits(h.serverTime));
        writeU64(out, h.clientTicks);
        writeU32(out, Float.floatToIntBits(h.tickRate));
        writeU32(out, h.frameCount);
        writeU32(out, h.preFrames);
        writeU32(out, h.postFrames);
        writeU32(out, h.flags);
        writeString(out, h.acFlags, MAX_AC_FLAGS_BYTES, "anticheat flags", true);
        writeU64(out, Double.doubleToLongBits(h.originX));
        writeU64(out, Double.doubleToLongBits(h.originY));
        writeU64(out, Double.doubleToLongBits(h.originZ));
        writeU64(out, Double.doubleToLongBits(h.minX));
        writeU64(out, Double.doubleToLongBits(h.minY));
        writeU64(out, Double.doubleToLongBits(h.minZ));
        writeU64(out, Double.doubleToLongBits(h.maxX));
        writeU64(out, Double.doubleToLongBits(h.maxY));
        writeU64(out, Double.doubleToLongBits(h.maxZ));
        writeU32(out, h.positionScale);
        writeU32(out, h.angleScale);
        writeU32(out, h.speedScale);
        writeU32(out, h.efficiencyScale);
        writeU32(out, h.framesPerBlock);
        return out.toByteArray();
    }

    /** Quantises the frames column by column, replacing values that can't be stored by the previous good one. */
    private static final class Quantiser {
        private final MhrpHeader h;
        private final ReplayFrames f;
        private final long[] lastGood = new long[VALUE_COLUMNS];
        private final boolean[] repairedInBlock;
        private boolean anyRepaired;

        Quantiser(MhrpHeader h, ReplayFrames f) {
            this.h = h;
            this.f = f;
            this.repairedInBlock = new boolean[Math.max(1, h.framesPerBlock)];
            // Dry run to know up front whether any frame needs repairing (it goes in the header flags).
            long[] last = new long[VALUE_COLUMNS];
            for (int i = 0; i < f.size() && !this.anyRepaired; i++) {
                for (int c = 0; c < VALUE_COLUMNS; c++) {
                    if (quantise(c, i) == Long.MIN_VALUE) {
                        this.anyRepaired = true;
                        break;
                    }
                }
            }
        }

        /** The stored value of column c at frame i, or Long.MIN_VALUE if it can't be stored. */
        private long quantise(int column, int i) {
            double value = switch (column) {
                case 0 -> (this.f.x(i) - this.h.originX) * this.h.positionScale;
                case 1 -> (this.f.y(i) - this.h.originY) * this.h.positionScale;
                case 2 -> (this.f.z(i) - this.h.originZ) * this.h.positionScale;
                case 3 -> this.f.yaw(i) * (double) this.h.angleScale / 360.0D;
                case 4 -> this.f.pitch(i) * (double) this.h.angleScale / 360.0D;
                case 5 -> this.f.jumpCount(i);
                case 6 -> this.f.lastJumpSpeed(i) * (double) this.h.speedScale;
                default -> this.f.efficiency(i) * (double) this.h.efficiencyScale;
            };
            if (!Double.isFinite(value) || Math.abs(value) > MAX_ABS_QUANTISED) {
                return Long.MIN_VALUE;
            }
            return Math.round(value);
        }

        void writeBlock(GrowableBytes raw, int start, int count) {
            java.util.Arrays.fill(this.repairedInBlock, 0, count, false);
            for (int c = 0; c < VALUE_COLUMNS; c++) {
                long previous = 0L;
                for (int k = 0; k < count; k++) {
                    int i = start + k;
                    long q = quantise(c, i);
                    if (q == Long.MIN_VALUE) {
                        q = this.lastGood[c];
                        this.repairedInBlock[k] = true;
                    } else {
                        this.lastGood[c] = q;
                    }
                    writeVarLong(raw, zigZag(q - previous));
                    previous = q;
                }
            }
            int previousFlags = 0;
            for (int k = 0; k < count; k++) {
                int flags = this.f.flags(start + k);
                if (this.repairedInBlock[k]) {
                    flags |= ReplayFrames.FLAG_REPAIRED;
                }
                writeVarInt(raw, flags ^ previousFlags);
                previousFlags = flags;
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Decoding
    // ------------------------------------------------------------------------------------------

    /**
     * Decodes a whole file: version, CRC, header and every frame are checked. The frames carry the header's layout
     * (pre/post frames, tick-stream flag).
     */
    public static Decoded decode(byte[] file) throws MhrpFormatException {
        Reader in = checkedBody(file, true);
        MhrpHeader h = readHeaderSection(in);
        ReplayFrames frames = readBlocks(in, h);
        if (in.position() != file.length - 4) {
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "trailing bytes after the last block");
        }
        return new Decoded(h, frames.withLayout(h.preFrames, h.postFrames, (h.flags & MhrpHeader.FLAG_TICK_STREAM) != 0));
    }

    /**
     * Reads only the header. With {@code verifyCrc} the whole file must be given and its CRC must match; without it a
     * prefix that holds the header is enough (used to identify a file that fails its CRC).
     */
    public static MhrpHeader decodeHeader(byte[] file, boolean verifyCrc) throws MhrpFormatException {
        Reader in = checkedBody(file, verifyCrc);
        return readHeaderSection(in);
    }

    /**
     * The same file with an edited header: the column blocks are copied byte for byte (not decoded or re-encoded) and
     * the CRC is recomputed. {@code edit} gets a copy of the file's (checked) header. Whatever the blocks are read with
     * (frame count, pre/post frames, the tick-stream flag, origin, bounds, quantisation scales, frames per block) is
     * put back afterwards, so an edit can only change the descriptive fields. Used to stream a stored run to players'
     * clients without its server-only fields (see MhrpHeader#forClients).
     *
     * @throws MhrpFormatException if {@code file} is not a valid MHRP file (its CRC is checked), or the edited header
     *                             can't be stored
     */
    public static byte[] rewriteHeader(byte[] file, java.util.function.UnaryOperator<MhrpHeader> edit) throws MhrpFormatException {
        Reader in = checkedBody(file, true);
        MhrpHeader original = readHeaderSection(in);
        int blocksStart = in.position();
        int blocksEnd = file.length - 4;
        MhrpHeader edited = edit.apply(original.copy());
        if (edited == null) {
            edited = original.copy();
        }
        edited.frameCount = original.frameCount;
        edited.preFrames = original.preFrames;
        edited.postFrames = original.postFrames;
        edited.flags = (edited.flags & ~MhrpHeader.FLAG_TICK_STREAM) | (original.flags & MhrpHeader.FLAG_TICK_STREAM);
        edited.originX = original.originX;
        edited.originY = original.originY;
        edited.originZ = original.originZ;
        edited.minX = original.minX;
        edited.minY = original.minY;
        edited.minZ = original.minZ;
        edited.maxX = original.maxX;
        edited.maxY = original.maxY;
        edited.maxZ = original.maxZ;
        edited.positionScale = original.positionScale;
        edited.angleScale = original.angleScale;
        edited.speedScale = original.speedScale;
        edited.efficiencyScale = original.efficiencyScale;
        edited.framesPerBlock = original.framesPerBlock;
        byte[] headerBytes;
        try {
            headerBytes = encodeHeader(edited);
        } catch (IllegalArgumentException e) {
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "edited header can't be stored: " + e.getMessage(), e);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(PREFIX_BYTES + headerBytes.length + (blocksEnd - blocksStart) + 4);
        out.writeBytes(MAGIC);
        writeU16(out, VERSION);
        writeU32(out, headerBytes.length);
        out.writeBytes(headerBytes);
        out.write(file, blocksStart, blocksEnd - blocksStart);
        byte[] body = out.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(body, 0, body.length);
        byte[] rewritten = new byte[body.length + 4];
        System.arraycopy(body, 0, rewritten, 0, body.length);
        putU32(rewritten, body.length, crc.getValue());
        return rewritten;
    }

    /** Number of column blocks for a frame count (see {@link #decodeBlockColumns}). */
    public static int blockCount(int frameCount, int framesPerBlock) {
        return frameCount <= 0 ? 0 : (frameCount + framesPerBlock - 1) / framesPerBlock;
    }

    /**
     * Decodes one inflated block of column data holding {@code count} frames and appends them to {@code out}. Blocks
     * are independent, so a client can be streamed a replay block by block (phase 4) and decode each on arrival; the
     * caller applies the header's layout ({@link ReplayFrames#withLayout}) to the frames it builds.
     */
    public static void decodeBlockColumns(MhrpHeader h, byte[] raw, int offset, int length, int count, ReplayFrames.Builder out)
            throws MhrpFormatException {
        if (offset < 0 || length < 0 || offset > raw.length - length || count < 0 || count > MAX_FRAMES_PER_BLOCK) {
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "bad block bounds");
        }
        Reader in = new Reader(raw, offset, offset + length);
        long[][] columns = new long[VALUE_COLUMNS][count];
        for (int c = 0; c < VALUE_COLUMNS; c++) {
            long value = 0L;
            long[] column = columns[c];
            for (int k = 0; k < count; k++) {
                try {
                    value = Math.addExact(value, unZigZag(in.readVarLong()));
                } catch (ArithmeticException overflow) {
                    throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "column value overflow");
                }
                column[k] = value;
            }
        }
        int[] flags = new int[count];
        int previousFlags = 0;
        for (int k = 0; k < count; k++) {
            previousFlags ^= in.readVarInt();
            flags[k] = previousFlags;
        }
        if (in.remaining() != 0) {
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "block has " + in.remaining() + " unread bytes");
        }
        double posScale = h.positionScale;
        double angle = 360.0D / h.angleScale;
        double speedScale = h.speedScale;
        double efficiencyScale = h.efficiencyScale;
        for (int k = 0; k < count; k++) {
            long jumps = columns[5][k];
            if (jumps < Integer.MIN_VALUE || jumps > Integer.MAX_VALUE) {
                throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "jump count out of range");
            }
            out.add(h.originX + columns[0][k] / posScale,
                    h.originY + columns[1][k] / posScale,
                    h.originZ + columns[2][k] / posScale,
                    (float) (columns[3][k] * angle),
                    (float) (columns[4][k] * angle),
                    (int) jumps,
                    (float) (columns[6][k] / speedScale),
                    (float) (columns[7][k] / efficiencyScale),
                    flags[k]);
        }
    }

    /** Checks length, magic, version and (optionally) the CRC; returns a reader positioned after the version. */
    private static Reader checkedBody(byte[] file, boolean verifyCrc) throws MhrpFormatException {
        if (file == null || file.length < 4) {
            throw new MhrpFormatException(MhrpFormatException.Kind.TRUNCATED, "file is " + (file == null ? 0 : file.length) + " bytes");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (file[i] != MAGIC[i]) {
                throw new MhrpFormatException(MhrpFormatException.Kind.BAD_MAGIC, "not an MHRP file");
            }
        }
        if (file.length < PREFIX_BYTES) {
            throw new MhrpFormatException(MhrpFormatException.Kind.TRUNCATED, "file is " + file.length + " bytes");
        }
        if (file.length > MAX_FILE_BYTES) {
            throw new MhrpFormatException(MhrpFormatException.Kind.TOO_LARGE, "file is " + file.length + " bytes");
        }
        int version = ((file[4] & 0xFF) << 8) | (file[5] & 0xFF);
        if (version != VERSION) {
            throw new MhrpFormatException(MhrpFormatException.Kind.UNSUPPORTED_VERSION, "format version " + version
                    + " (this version reads " + VERSION + ")");
        }
        int limit = file.length;
        if (verifyCrc) {
            if (file.length < MIN_FILE_BYTES) {
                throw new MhrpFormatException(MhrpFormatException.Kind.TRUNCATED, "file is " + file.length + " bytes");
            }
            CRC32 crc = new CRC32();
            crc.update(file, 0, file.length - 4);
            long stored = getU32(file, file.length - 4);
            if (crc.getValue() != stored) {
                throw new MhrpFormatException(MhrpFormatException.Kind.BAD_CRC, String.format(java.util.Locale.ROOT,
                        "stored %08x, computed %08x", stored, crc.getValue()));
            }
            limit = file.length - 4;
        }
        Reader in = new Reader(file, 0, limit);
        in.skip(6);
        return in;
    }

    private static MhrpHeader readHeaderSection(Reader in) throws MhrpFormatException {
        long headerLength = in.readU32();
        if (headerLength > MAX_HEADER_BYTES) {
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "header length " + headerLength);
        }
        if (headerLength > in.remaining()) {
            throw new MhrpFormatException(MhrpFormatException.Kind.TRUNCATED, "header length " + headerLength
                    + " but " + in.remaining() + " bytes left");
        }
        int headerEnd = in.position() + (int) headerLength;
        Reader hr = in.slice((int) headerLength);
        MhrpHeader h = new MhrpHeader();
        h.replayId = hr.readString(MAX_ID_BYTES);
        h.mapName = hr.readString(MAX_MAP_BYTES);
        h.playerUuid = hr.readString(MAX_UUID_BYTES);
        h.playerName = hr.readString(MAX_NAME_BYTES);
        h.time = Double.longBitsToDouble(hr.readU64());
        h.savedAt = hr.readU64();
        h.serverTime = Double.longBitsToDouble(hr.readU64());
        h.clientTicks = hr.readU64();
        h.tickRate = Float.intBitsToFloat((int) hr.readU32());
        h.frameCount = boundedInt(hr.readU32(), MAX_FRAMES, "frame count");
        h.preFrames = boundedInt(hr.readU32(), MAX_FRAMES, "pre-frame count");
        h.postFrames = boundedInt(hr.readU32(), MAX_FRAMES, "post-frame count");
        h.flags = (int) hr.readU32();
        h.acFlags = hr.readString(MAX_AC_FLAGS_BYTES);
        h.originX = Double.longBitsToDouble(hr.readU64());
        h.originY = Double.longBitsToDouble(hr.readU64());
        h.originZ = Double.longBitsToDouble(hr.readU64());
        h.minX = Double.longBitsToDouble(hr.readU64());
        h.minY = Double.longBitsToDouble(hr.readU64());
        h.minZ = Double.longBitsToDouble(hr.readU64());
        h.maxX = Double.longBitsToDouble(hr.readU64());
        h.maxY = Double.longBitsToDouble(hr.readU64());
        h.maxZ = Double.longBitsToDouble(hr.readU64());
        h.positionScale = boundedInt(hr.readU32(), 1 << 20, "position scale");
        h.angleScale = boundedInt(hr.readU32(), 1 << 24, "angle scale");
        h.speedScale = boundedInt(hr.readU32(), 1 << 20, "speed scale");
        h.efficiencyScale = boundedInt(hr.readU32(), 1 << 16, "efficiency scale");
        h.framesPerBlock = boundedInt(hr.readU32(), MAX_FRAMES_PER_BLOCK, "frames per block");
        // Bytes after the known fields belong to a later minor revision of the header: skipped.
        in.seek(headerEnd);
        validate(h);
        return h;
    }

    private static void validate(MhrpHeader h) throws MhrpFormatException {
        String problem = null;
        if (h.replayId.isEmpty()) {
            problem = "empty replay id";
        } else if (h.mapName.isEmpty()) {
            problem = "empty map name";
        } else if (!Double.isFinite(h.time)) {
            problem = "time is not finite";
        } else if (!Float.isFinite(h.tickRate) || h.tickRate <= 0.0F || h.tickRate > 1000.0F) {
            problem = "tick rate " + h.tickRate;
        } else if ((long) h.preFrames + h.postFrames > h.frameCount) {
            problem = "pre/post frames exceed the frame count";
        } else if (!Double.isFinite(h.originX) || !Double.isFinite(h.originY) || !Double.isFinite(h.originZ)) {
            problem = "origin is not finite";
        } else if (!Double.isFinite(h.minX) || !Double.isFinite(h.minY) || !Double.isFinite(h.minZ)
                || !Double.isFinite(h.maxX) || !Double.isFinite(h.maxY) || !Double.isFinite(h.maxZ)) {
            problem = "bounds are not finite";
        } else if (h.positionScale < 1 || h.angleScale < 360 || h.speedScale < 1 || h.efficiencyScale < 1) {
            problem = "bad quantisation scales";
        } else if (h.framesPerBlock < MIN_FRAMES_PER_BLOCK) {
            problem = "frames per block " + h.framesPerBlock;
        }
        if (problem != null) {
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, problem);
        }
    }

    private static ReplayFrames readBlocks(Reader in, MhrpHeader h) throws MhrpFormatException {
        int expectedBlocks = blockCount(h.frameCount, h.framesPerBlock);
        long blocks = in.readU32();
        if (blocks != expectedBlocks) {
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, blocks + " blocks for " + h.frameCount
                    + " frames (expected " + expectedBlocks + ")");
        }
        // Deflate can't compress better than 1032:1 and every frame takes at least one byte per column, so the data
        // left in the file bounds the frame count: a short file can't make us allocate for a million frames.
        if ((long) h.frameCount * (VALUE_COLUMNS + 1) > (in.remaining() + 1L) * 1032L) {
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, h.frameCount + " frames can't fit in "
                    + in.remaining() + " bytes");
        }
        ReplayFrames.Builder frames = ReplayFrames.builder(h.frameCount);
        Inflater inflater = new Inflater();
        try {
            for (int b = 0; b < expectedBlocks; b++) {
                int count = Math.min(h.framesPerBlock, h.frameCount - b * h.framesPerBlock);
                long rawLength = in.readU32();
                long compressedLength = in.readU32();
                if (rawLength < (long) count * (VALUE_COLUMNS + 1) || rawLength > (long) count * MAX_BYTES_PER_FRAME) {
                    throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "block " + b + " raw length " + rawLength
                            + " for " + count + " frames");
                }
                if (compressedLength > in.remaining()) {
                    throw new MhrpFormatException(MhrpFormatException.Kind.TRUNCATED, "block " + b + " compressed length "
                            + compressedLength + " but " + in.remaining() + " bytes left");
                }
                byte[] raw = inflateExactly(inflater, in.array(), in.position(), (int) compressedLength, (int) rawLength, b);
                in.skip((int) compressedLength);
                decodeBlockColumns(h, raw, 0, raw.length, count, frames);
            }
        } finally {
            inflater.end();
        }
        return frames.build();
    }

    private static byte[] inflateExactly(Inflater inflater, byte[] source, int offset, int length, int rawLength, int block)
            throws MhrpFormatException {
        inflater.reset();
        inflater.setInput(source, offset, length);
        // One spare byte: a stream that inflates to more than the declared length is caught instead of cut off.
        byte[] out = new byte[rawLength + 1];
        int produced = 0;
        int stalls = 0;
        try {
            while (!inflater.finished() && produced < out.length) {
                int n = inflater.inflate(out, produced, out.length - produced);
                if (n == 0 && !inflater.finished() && (inflater.needsInput() || inflater.needsDictionary() || ++stalls > 2)) {
                    throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "block " + block + " compressed data ends early");
                }
                produced += n;
            }
        } catch (DataFormatException e) {
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "block " + block + " compressed data: " + e.getMessage(), e);
        }
        if (!inflater.finished() || produced != rawLength || inflater.getRemaining() != 0) {
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "block " + block + " inflates to "
                    + (inflater.finished() ? produced : "more than " + rawLength) + " bytes, declared " + rawLength
                    + (inflater.getRemaining() != 0 ? " (with " + inflater.getRemaining() + " unused input bytes)" : ""));
        }
        return produced == out.length ? out : java.util.Arrays.copyOf(out, rawLength);
    }

    private static int boundedInt(long value, int max, String what) throws MhrpFormatException {
        if (value < 0 || value > max) {
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, what + " " + value + " out of range (max " + max + ")");
        }
        return (int) value;
    }

    // ------------------------------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------------------------------

    static long zigZag(long value) {
        return (value << 1) ^ (value >> 63);
    }

    static long unZigZag(long value) {
        return (value >>> 1) ^ -(value & 1L);
    }

    private static void writeVarLong(GrowableBytes out, long value) {
        while ((value & ~0x7FL) != 0L) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

    private static void writeVarInt(GrowableBytes out, int value) {
        writeVarLong(out, value & 0xFFFFFFFFL);
    }

    private static void writeU16(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeU32(ByteArrayOutputStream out, long value) {
        out.write((int) (value >>> 24) & 0xFF);
        out.write((int) (value >>> 16) & 0xFF);
        out.write((int) (value >>> 8) & 0xFF);
        out.write((int) value & 0xFF);
    }

    private static void writeU64(ByteArrayOutputStream out, long value) {
        writeU32(out, value >>> 32);
        writeU32(out, value & 0xFFFFFFFFL);
    }

    private static void putU32(byte[] target, int offset, long value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static long getU32(byte[] source, int offset) {
        return ((source[offset] & 0xFFL) << 24) | ((source[offset + 1] & 0xFFL) << 16)
                | ((source[offset + 2] & 0xFFL) << 8) | (source[offset + 3] & 0xFFL);
    }

    private static void writeString(ByteArrayOutputStream out, String value, int maxBytes, String what, boolean truncate) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            if (!truncate) {
                throw new IllegalArgumentException(what + " is too long to store (" + bytes.length + " bytes, max " + maxBytes + ")");
            }
            // Cut at a character boundary.
            int end = maxBytes;
            while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
                end--;
            }
            bytes = java.util.Arrays.copyOf(bytes, end);
        }
        writeU16(out, bytes.length);
        out.writeBytes(bytes);
    }

    /** A growable byte array (ByteArrayOutputStream without the synchronisation and copies). */
    static final class GrowableBytes {
        private byte[] data;
        private int size;

        GrowableBytes(int capacity) {
            this.data = new byte[Math.max(16, capacity)];
        }

        void write(int b) {
            if (this.size == this.data.length) {
                this.data = java.util.Arrays.copyOf(this.data, this.data.length * 2);
            }
            this.data[this.size++] = (byte) b;
        }

        void reset() {
            this.size = 0;
        }

        int size() {
            return this.size;
        }

        byte[] array() {
            return this.data;
        }
    }

    /** Bounds-checked big-endian reader over a byte range; every overrun is a format error, never an exception. */
    private static final class Reader {
        private static final ThreadLocal<CharsetDecoder> UTF8 = ThreadLocal.withInitial(() -> StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT));
        private final byte[] data;
        private int position;
        private final int limit;

        Reader(byte[] data, int position, int limit) {
            this.data = data;
            this.position = position;
            this.limit = limit;
        }

        byte[] array() {
            return this.data;
        }

        int position() {
            return this.position;
        }

        int remaining() {
            return this.limit - this.position;
        }

        void skip(int n) throws MhrpFormatException {
            need(n);
            this.position += n;
        }

        void seek(int position) throws MhrpFormatException {
            if (position < 0 || position > this.limit) {
                throw new MhrpFormatException(MhrpFormatException.Kind.TRUNCATED, "seek past the end");
            }
            this.position = position;
        }

        Reader slice(int length) throws MhrpFormatException {
            need(length);
            return new Reader(this.data, this.position, this.position + length);
        }

        private void need(int n) throws MhrpFormatException {
            if (n < 0 || n > this.limit - this.position) {
                throw new MhrpFormatException(MhrpFormatException.Kind.TRUNCATED, "needed " + n + " bytes, " + remaining() + " left");
            }
        }

        int readU8() throws MhrpFormatException {
            need(1);
            return this.data[this.position++] & 0xFF;
        }

        int readU16() throws MhrpFormatException {
            need(2);
            int value = ((this.data[this.position] & 0xFF) << 8) | (this.data[this.position + 1] & 0xFF);
            this.position += 2;
            return value;
        }

        long readU32() throws MhrpFormatException {
            need(4);
            long value = getU32(this.data, this.position);
            this.position += 4;
            return value;
        }

        long readU64() throws MhrpFormatException {
            long high = readU32();
            long low = readU32();
            return (high << 32) | low;
        }

        String readString(int maxBytes) throws MhrpFormatException {
            int length = readU16();
            if (length > maxBytes) {
                throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "string of " + length + " bytes (max " + maxBytes + ")");
            }
            need(length);
            try {
                CharBuffer chars = UTF8.get().reset().decode(ByteBuffer.wrap(this.data, this.position, length));
                this.position += length;
                return chars.toString();
            } catch (CharacterCodingException e) {
                throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "string is not valid UTF-8", e);
            }
        }

        long readVarLong() throws MhrpFormatException {
            long result = 0L;
            for (int shift = 0; shift < 64; shift += 7) {
                int b = readU8();
                if (shift == 63 && (b & 0xFE) != 0) {
                    throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "varlong too long");
                }
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
            }
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "varlong too long");
        }

        int readVarInt() throws MhrpFormatException {
            long result = 0L;
            for (int shift = 0; shift < 35; shift += 7) {
                int b = readU8();
                if (shift == 28 && (b & 0xF0) != 0) {
                    throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "varint too long");
                }
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return (int) result;
                }
            }
            throw new MhrpFormatException(MhrpFormatException.Kind.CORRUPT, "varint too long");
        }
    }
}
