package net.nerdorg.minehop.replays.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Property and corruption tests for {@link MhrpCodec}. DEV ONLY: run by ReplayStoreHarness ({@code -Preplaytest}) or
 * standalone ({@code java ... CodecSelfTest [seed]}, the package has no Minecraft dependencies). Never used by the game.
 *
 * <p>Checked: random runs round-trip within the quantisation bounds (positions 1/8192 block also at x = 5.4M, angles
 * 1/131072 turn) with exact flags, header fields and layout (pre/post frames, tick stream); encoding is deterministic; non-finite values are repaired and
 * flagged; every truncation, every single-bit flip and random garbage are rejected with a {@link MhrpFormatException};
 * structurally invalid files that carry a VALID CRC (huge lengths, frame counts, bad compressed data, overlong
 * varints, bad UTF-8, trailing bytes) are rejected without large allocations; a newer version is reported as
 * unsupported, not corrupt.
 */
public final class CodecSelfTest {
    private static final double POSITION_TOLERANCE = 0.5D / MhrpCodec.POSITION_SCALE + 1.0E-9D;
    private static final double ANGLE_TOLERANCE = 180.0D / MhrpCodec.ANGLE_SCALE + 1.0E-3D * 0.01D;
    private static final double SPEED_TOLERANCE = 0.5D / MhrpCodec.SPEED_SCALE + 1.0E-6D;
    private static final double EFFICIENCY_TOLERANCE = 0.5D / MhrpCodec.EFFICIENCY_SCALE + 1.0E-5D;

    private final Random random;
    private final Consumer<String> log;
    private int checks;
    private final List<String> failures = new ArrayList<>();

    private CodecSelfTest(long seed, Consumer<String> log) {
        this.random = new Random(seed);
        this.log = log;
    }

    /** Runs every test; returns the failures (empty = pass). Progress and a summary go to {@code log}. */
    public static List<String> run(long seed, Consumer<String> log) {
        CodecSelfTest test = new CodecSelfTest(seed, log);
        long start = System.nanoTime();
        test.roundTrips();
        test.determinism();
        test.repairs();
        test.headerOnly();
        test.headerRewrites();
        test.truncations();
        test.bitFlips();
        test.garbage();
        test.versionAndMagic();
        test.forgedStructure();
        test.mutationFuzz();
        log.accept(String.format(java.util.Locale.ROOT, "codec self-test: %d checks, %d failures, %.1f s (seed %d)",
                test.checks, test.failures.size(), (System.nanoTime() - start) / 1.0E9D, seed));
        return test.failures;
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20261007L;
        List<String> failures = run(seed, System.out::println);
        for (String failure : failures) {
            System.out.println("FAIL " + failure);
        }
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private void check(boolean ok, String what) {
        this.checks++;
        if (!ok && this.failures.size() < 200) {
            this.failures.add(what);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Generators
    // ------------------------------------------------------------------------------------------

    private MhrpHeader header(int n) {
        MhrpHeader h = new MhrpHeader();
        h.replayId = java.util.UUID.randomUUID().toString();
        h.mapName = this.random.nextInt(4) == 0 ? "Surf_Mesa ünïcödé ✓ " + n : "map_" + this.random.nextInt(100);
        h.playerUuid = this.random.nextInt(5) == 0 ? "" : java.util.UUID.randomUUID().toString();
        h.playerName = "Player" + this.random.nextInt(10000);
        h.time = this.random.nextDouble() * 6000.0D;
        h.savedAt = 1_700_000_000_000L + this.random.nextInt(1_000_000_000);
        h.serverTime = this.random.nextBoolean() ? Double.NaN : h.time + this.random.nextGaussian() * 0.01D;
        h.clientTicks = this.random.nextBoolean() ? -1L : this.random.nextInt(200000);
        h.acFlags = this.random.nextInt(6) == 0 ? "Speed x2, Fly x1" : "";
        h.flags = (this.random.nextBoolean() ? MhrpHeader.FLAG_MIGRATED : 0)
                | (this.random.nextBoolean() ? MhrpHeader.FLAG_TICK_STREAM : 0);
        if (n > 0 && this.random.nextBoolean()) {
            h.preFrames = this.random.nextInt(Math.min(31, n + 1));
            h.postFrames = this.random.nextInt(Math.min(21, n - h.preFrames + 1));
        }
        return h;
    }

    /** A random walk somewhere in the world (up to 5.4M blocks out), with unwrapped yaw and random flag bits. */
    private ReplayFrames frames(int n) {
        ReplayFrames.Builder b = ReplayFrames.builder(n);
        double scale = switch (this.random.nextInt(4)) {
            case 0 -> 5_415_703.0D;
            case 1 -> 30_000_000.0D;
            case 2 -> 1000.0D;
            default -> 0.0D;
        };
        double x = (this.random.nextDouble() * 2.0D - 1.0D) * scale;
        double y = -64.0D + this.random.nextDouble() * 384.0D;
        double z = (this.random.nextDouble() * 2.0D - 1.0D) * scale;
        float yaw = (float) (this.random.nextDouble() * 360.0D - 180.0D);
        int jumps = 0;
        boolean wildYaw = this.random.nextInt(3) == 0;
        for (int i = 0; i < n; i++) {
            if (this.random.nextInt(500) == 0) {
                // A teleport.
                x += (this.random.nextDouble() - 0.5D) * 2000.0D;
                z += (this.random.nextDouble() - 0.5D) * 2000.0D;
            }
            x += this.random.nextGaussian() * 0.8D;
            y += this.random.nextGaussian() * 0.3D;
            z += this.random.nextGaussian() * 0.8D;
            yaw += (float) (this.random.nextGaussian() * (wildYaw ? 40.0D : 4.0D));
            float pitch = (float) Math.max(-90.0D, Math.min(90.0D, this.random.nextGaussian() * 30.0D));
            if (this.random.nextInt(15) == 0) {
                jumps++;
            }
            float speed = (float) (this.random.nextDouble() * 3.0D);
            float efficiency = (float) (this.random.nextDouble() * 100.0D);
            int flags = this.random.nextInt(8) == 0 ? this.random.nextInt(1 << 11) : 0;
            b.add(x, y, z, yaw, pitch, jumps, speed, efficiency, flags);
        }
        return b.build();
    }

    // ------------------------------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------------------------------

    private void roundTrips() {
        int[] fixed = {0, 1, 2, 1023, 1024, 1025, 2048, 4097};
        int cases = 0;
        for (int n : fixed) {
            roundTrip(header(n), frames(n), "size " + n);
            cases++;
        }
        for (int k = 0; k < 300; k++) {
            int n = this.random.nextInt(3) == 0 ? this.random.nextInt(20) : this.random.nextInt(6000);
            roundTrip(header(n), frames(n), "random #" + k + " (" + n + " frames)");
            cases++;
        }
        roundTrip(header(119_775), frames(119_775), "longest stored legacy run (119775 frames)");
        cases++;
        this.log.accept("round trips: " + cases + " runs");
    }

    private void roundTrip(MhrpHeader h, ReplayFrames f, String what) {
        byte[] file = MhrpCodec.encode(h, f);
        MhrpCodec.Decoded d;
        try {
            d = MhrpCodec.decode(file);
        } catch (MhrpFormatException e) {
            check(false, what + ": decode failed: " + e.getMessage());
            return;
        }
        MhrpHeader g = d.header();
        check(g.replayId.equals(h.replayId) && g.mapName.equals(h.mapName) && g.playerUuid.equals(h.playerUuid)
                && g.playerName.equals(h.playerName) && g.acFlags.equals(h.acFlags), what + ": header strings");
        check(Double.compare(g.time, h.time) == 0 && g.savedAt == h.savedAt && Double.compare(g.serverTime, h.serverTime) == 0
                && g.clientTicks == h.clientTicks && g.flags == h.flags && g.frameCount == f.size()
                && g.preFrames == h.preFrames && g.postFrames == h.postFrames, what + ": header numbers");
        ReplayFrames e = d.frames();
        check(e.preFrames() == h.preFrames && e.postFrames() == h.postFrames
                && e.tickStream() == ((h.flags & MhrpHeader.FLAG_TICK_STREAM) != 0)
                && e.runStart() == h.preFrames && e.runEnd() == f.size() - h.postFrames, what + ": frame layout");
        if (e.size() != f.size()) {
            check(false, what + ": frame count " + e.size() + " != " + f.size());
            return;
        }
        double maxPos = 0.0D;
        double maxAngle = 0.0D;
        boolean exact = true;
        for (int i = 0; i < f.size(); i++) {
            maxPos = Math.max(maxPos, Math.max(Math.abs(e.x(i) - f.x(i)), Math.max(Math.abs(e.y(i) - f.y(i)), Math.abs(e.z(i) - f.z(i)))));
            maxAngle = Math.max(maxAngle, Math.max(Math.abs(e.yaw(i) - f.yaw(i)), Math.abs(e.pitch(i) - f.pitch(i))));
            exact &= e.jumpCount(i) == f.jumpCount(i) && e.flags(i) == f.flags(i)
                    && Math.abs(e.lastJumpSpeed(i) - f.lastJumpSpeed(i)) <= SPEED_TOLERANCE
                    && Math.abs(e.efficiency(i) - f.efficiency(i)) <= EFFICIENCY_TOLERANCE;
        }
        check(maxPos <= POSITION_TOLERANCE, what + ": position error " + maxPos);
        // Angles are stored unwrapped; a float yaw of e.g. 9000 degrees has a coarser float step than the quantum.
        double angleTolerance = ANGLE_TOLERANCE + maxUlp(f);
        check(maxAngle <= angleTolerance, what + ": angle error " + maxAngle + " > " + angleTolerance);
        check(exact, what + ": jumps/flags/speed/efficiency");
    }

    /**
     * rewriteHeader (replay streaming): the client copy has no server-only fields, its blocks are the stored bytes,
     * it decodes to exactly the stored frames, an edit can't change the frame layout, and a bad file is refused.
     */
    private void headerRewrites() {
        int cases = 0;
        for (int k = 0; k < 60; k++) {
            int n = k < 4 ? new int[]{0, 1, 1024, 2049}[k] : this.random.nextInt(5000);
            MhrpHeader h = header(n);
            h.acFlags = "Speed x2, Fly x1";
            h.serverTime = 12.5D;
            h.clientTicks = 250L;
            h.flags |= MhrpHeader.FLAG_TICK_TIME_MISMATCH;
            byte[] file = MhrpCodec.encode(h, frames(n));
            String what = "rewrite #" + k + " (" + n + " frames)";
            try {
                MhrpCodec.Decoded stored = MhrpCodec.decode(file);
                byte[] client = MhrpCodec.rewriteHeader(file, MhrpHeader::forClients);
                MhrpCodec.Decoded sent = MhrpCodec.decode(client);
                MhrpHeader g = sent.header();
                check(g.acFlags.isEmpty() && Double.isNaN(g.serverTime) && g.clientTicks == -1L
                        && (g.flags & ~MhrpHeader.CLIENT_VISIBLE_FLAGS) == 0
                        && (g.flags & MhrpHeader.FLAG_TICK_STREAM) == (h.flags & MhrpHeader.FLAG_TICK_STREAM), what + ": sanitised header");
                check(g.replayId.equals(h.replayId) && g.mapName.equals(h.mapName) && g.playerName.equals(h.playerName)
                        && Double.compare(g.time, h.time) == 0 && g.preFrames == stored.header().preFrames
                        && g.postFrames == stored.header().postFrames, what + ": kept header fields");
                check(sent.frames().sameAs(stored.frames()) && sent.frames().tickStream() == stored.frames().tickStream()
                        && sent.frames().runStart() == stored.frames().runStart()
                        && sent.frames().runEnd() == stored.frames().runEnd(), what + ": same frames");
                int storedTail = file.length - blocksOffset(file);
                int sentTail = client.length - blocksOffset(client);
                check(storedTail == sentTail && java.util.Arrays.equals(file, blocksOffset(file), file.length - 4,
                        client, blocksOffset(client), client.length - 4), what + ": blocks copied byte for byte");
                byte[] forged = MhrpCodec.rewriteHeader(file, header -> {
                    header.frameCount = 7;
                    header.framesPerBlock = 16;
                    header.originX += 100.0D;
                    header.flags ^= MhrpHeader.FLAG_TICK_STREAM;
                    return header;
                });
                check(MhrpCodec.decode(forged).frames().sameAs(stored.frames()), what + ": layout edits are ignored");
            } catch (MhrpFormatException e) {
                check(false, what + ": " + e.getMessage());
            }
            cases++;
        }
        byte[] file = MhrpCodec.encode(header(100), frames(100));
        file[file.length / 2] ^= 0x10;
        boolean refused;
        try {
            MhrpCodec.rewriteHeader(file, MhrpHeader::forClients);
            refused = false;
        } catch (MhrpFormatException e) {
            refused = e.kind() == MhrpFormatException.Kind.BAD_CRC;
        }
        check(refused, "rewrite of a corrupt file is refused");
        this.log.accept("header rewrites: " + cases + " runs");
    }

    /** Offset of the block count in a valid file (after magic, version, header length and header). */
    private static int blocksOffset(byte[] file) {
        long headerLength = ((file[6] & 0xFFL) << 24) | ((file[7] & 0xFFL) << 16) | ((file[8] & 0xFFL) << 8) | (file[9] & 0xFFL);
        return 10 + (int) headerLength;
    }

    private static double maxUlp(ReplayFrames f) {
        double ulp = 0.0D;
        for (int i = 0; i < f.size(); i++) {
            ulp = Math.max(ulp, Math.max(Math.ulp(f.yaw(i)), Math.ulp(f.pitch(i))));
        }
        return ulp;
    }

    private void determinism() {
        for (int k = 0; k < 20; k++) {
            int n = this.random.nextInt(3000);
            MhrpHeader h = header(n);
            ReplayFrames f = frames(n);
            check(java.util.Arrays.equals(MhrpCodec.encode(h, f), MhrpCodec.encode(h, f)), "determinism #" + k);
        }
    }

    private void repairs() {
        ReplayFrames.Builder b = ReplayFrames.builder(6);
        b.add(1.0D, 2.0D, 3.0D, 10.0F, 5.0F, 0, 0.5F, 50.0F, 0);
        b.add(Double.NaN, 2.0D, 3.0D, 10.0F, 5.0F, 0, 0.5F, 50.0F, 0);
        b.add(1.5D, Double.POSITIVE_INFINITY, 3.0D, Float.NaN, 5.0F, 1, Float.NaN, 50.0F, ReplayFrames.FLAG_ON_GROUND);
        b.add(1e300D, 2.0D, 3.0D, 10.0F, 5.0F, 1, 0.5F, Float.POSITIVE_INFINITY, 0);
        b.add(2.0D, 2.5D, 3.5D, 11.0F, 6.0F, 2, 0.6F, 60.0F, 0);
        ReplayFrames f = b.build();
        try {
            MhrpCodec.Decoded d = MhrpCodec.decode(MhrpCodec.encode(header(f.size()), f));
            ReplayFrames e = d.frames();
            boolean finite = true;
            for (int i = 0; i < e.size(); i++) {
                finite &= Double.isFinite(e.x(i)) && Double.isFinite(e.y(i)) && Double.isFinite(e.z(i))
                        && Float.isFinite(e.yaw(i)) && Float.isFinite(e.lastJumpSpeed(i)) && Float.isFinite(e.efficiency(i));
            }
            check(finite, "repairs: decoded values finite");
            check((d.header().flags & MhrpHeader.FLAG_REPAIRED_FRAMES) != 0, "repairs: header flag");
            check((e.flags(0) & ReplayFrames.FLAG_REPAIRED) == 0 && (e.flags(1) & ReplayFrames.FLAG_REPAIRED) != 0
                    && (e.flags(2) & ReplayFrames.FLAG_REPAIRED) != 0 && (e.flags(3) & ReplayFrames.FLAG_REPAIRED) != 0
                    && (e.flags(4) & ReplayFrames.FLAG_REPAIRED) == 0, "repairs: per-frame flags");
            check((e.flags(2) & ReplayFrames.FLAG_ON_GROUND) != 0, "repairs: other flags kept");
            check(Math.abs(e.x(1) - 1.0D) < 1e-3 && Math.abs(e.x(3) - 1.5D) < 1e-3, "repairs: previous good value used");
            check(Math.abs(e.x(4) - 2.0D) < 1e-3, "repairs: later frames unaffected");
        } catch (MhrpFormatException e) {
            check(false, "repairs: " + e.getMessage());
        }
    }

    private void headerOnly() {
        byte[] file = MhrpCodec.encode(header(500), frames(500));
        file[file.length - 10] ^= 0x40; // breaks the CRC, not the header
        try {
            MhrpCodec.decodeHeader(file, true);
            check(false, "header only: CRC not checked");
        } catch (MhrpFormatException e) {
            check(e.kind() == MhrpFormatException.Kind.BAD_CRC, "header only: expected BAD_CRC, got " + e.kind());
        }
        try {
            MhrpHeader h = MhrpCodec.decodeHeader(file, false);
            check(h.frameCount == 500, "header only without CRC");
        } catch (MhrpFormatException e) {
            check(false, "header only without CRC: " + e.getMessage());
        }
    }

    private void truncations() {
        byte[] file = MhrpCodec.encode(header(1500), frames(1500));
        int rejected = 0;
        for (int len = 0; len < file.length; len++) {
            if (expectRejected(java.util.Arrays.copyOf(file, len), "truncated to " + len)) {
                rejected++;
            }
        }
        this.log.accept("truncations: " + rejected + "/" + file.length + " rejected");
    }

    private void bitFlips() {
        byte[] file = MhrpCodec.encode(header(800), frames(800));
        int rejected = 0;
        int flips = 0;
        for (int i = 0; i < file.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                byte[] copy = file.clone();
                copy[i] ^= (byte) (1 << bit);
                flips++;
                if (expectRejected(copy, "bit " + bit + " of byte " + i)) {
                    rejected++;
                }
            }
        }
        this.log.accept("single-bit flips: " + rejected + "/" + flips + " rejected");
    }

    private void garbage() {
        for (int k = 0; k < 3000; k++) {
            byte[] junk = new byte[this.random.nextInt(4096)];
            this.random.nextBytes(junk);
            if (k % 3 == 0 && junk.length >= 6) {
                junk[0] = 'M';
                junk[1] = 'H';
                junk[2] = 'R';
                junk[3] = 'P';
                junk[4] = 0;
                junk[5] = 1;
            }
            if (k % 6 == 0 && junk.length >= 14) {
                refreshCrc(junk);
            }
            expectRejected(junk, "garbage #" + k);
        }
    }

    private void versionAndMagic() {
        byte[] file = MhrpCodec.encode(header(10), frames(10));
        byte[] newer = file.clone();
        newer[5] = 2;
        refreshCrc(newer);
        try {
            MhrpCodec.decode(newer);
            check(false, "version 2 accepted");
        } catch (MhrpFormatException e) {
            check(e.kind() == MhrpFormatException.Kind.UNSUPPORTED_VERSION && !e.isCorrupt(), "version 2: " + e.kind());
        }
        byte[] magic = file.clone();
        magic[0] = 'X';
        try {
            MhrpCodec.decode(magic);
            check(false, "bad magic accepted");
        } catch (MhrpFormatException e) {
            check(e.kind() == MhrpFormatException.Kind.BAD_MAGIC && e.isCorrupt(), "bad magic: " + e.kind());
        }
    }

    /** Structurally invalid files with a correct CRC: the decoder must not trust any length. */
    private void forgedStructure() {
        MhrpHeader h = header(3000);
        ReplayFrames f = frames(3000);
        byte[] good = MhrpCodec.encode(h, f);
        int headerLength = (int) u32(good, 6);
        int headerStart = 10;
        int frameCountOffset = frameCountOffset(good);
        int blocksOffset = headerStart + headerLength;

        forge(good, "header length 2^32-1", b -> putU32(b, 6, 0xFFFFFFFFL));
        forge(good, "header length past the end", b -> putU32(b, 6, b.length));
        forge(good, "frame count 2^32-1", b -> putU32(b, frameCountOffset, 0xFFFFFFFFL));
        forge(good, "frame count 2^20 (allowed max, too few bytes)", b -> putU32(b, frameCountOffset, MhrpCodec.MAX_FRAMES));
        forge(good, "frame count 2^20 + 1", b -> putU32(b, frameCountOffset, MhrpCodec.MAX_FRAMES + 1L));
        forge(good, "frame count off by one", b -> putU32(b, frameCountOffset, 3001));
        forge(good, "block count 2^32-1", b -> putU32(b, blocksOffset, 0xFFFFFFFFL));
        forge(good, "block count off by one", b -> putU32(b, blocksOffset, 4));
        forge(good, "raw length 2^32-1", b -> putU32(b, blocksOffset + 4, 0xFFFFFFFFL));
        forge(good, "raw length off by one", b -> putU32(b, blocksOffset + 4, u32(b, blocksOffset + 4) + 1));
        forge(good, "raw length one short", b -> putU32(b, blocksOffset + 4, u32(b, blocksOffset + 4) - 1));
        forge(good, "compressed length 2^32-1", b -> putU32(b, blocksOffset + 8, 0xFFFFFFFFL));
        forge(good, "compressed length one short", b -> putU32(b, blocksOffset + 8, u32(b, blocksOffset + 8) - 1));
        forge(good, "compressed data garbage", b -> {
            for (int i = blocksOffset + 12; i < blocksOffset + 40; i++) {
                b[i] = (byte) this.random.nextInt();
            }
        });
        forge(good, "string length past the cap", b -> {
            b[headerStart] = (byte) 0xFF;
            b[headerStart + 1] = (byte) 0xFF;
        });
        forge(good, "invalid UTF-8 in the replay id", b -> b[headerStart + 2] = (byte) 0xC3);
        forge(good, "frames per block 0", b -> putU32(b, headerStart + headerLength - 4, 0));
        forge(good, "frames per block 2^31", b -> putU32(b, headerStart + headerLength - 4, 0x80000000L));
        forge(good, "position scale 0", b -> putU32(b, headerStart + headerLength - 20, 0));
        forge(good, "NaN origin", b -> {
            long nan = Double.doubleToLongBits(Double.NaN);
            int originOffset = headerStart + headerLength - 20 - 6 * 8 - 3 * 8;
            putU32(b, originOffset, nan >>> 32);
            putU32(b, originOffset + 4, nan & 0xFFFFFFFFL);
        });
        // Trailing bytes after the last block (CRC recomputed over them).
        byte[] longer = java.util.Arrays.copyOf(good, good.length + 7);
        System.arraycopy(good, good.length - 4, longer, longer.length - 4, 4);
        refreshCrc(longer);
        expectRejected(longer, "trailing bytes");
        // A block that inflates to more than declared, and overlong varints inside a valid zlib stream.
        expectRejected(withBlock(h, new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x01}, 1), "overlong varlong");
        byte[] tooMuch = new byte[64];
        expectRejected(withBlock(h, tooMuch, 1), "block inflates to more columns than frames");
    }

    /** A valid header for {@code frames} frames followed by one block holding {@code raw} (CRC correct). */
    private static byte[] withBlock(MhrpHeader template, byte[] raw, int frames) {
        MhrpHeader h = template.copy();
        byte[] file = MhrpCodec.encode(h, oneFrame());
        int headerLength = (int) u32(file, 6);
        int blocksOffset = 10 + headerLength;
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] buffer = new byte[raw.length + 64];
        int compressedLength = deflater.deflate(buffer);
        deflater.end();
        byte[] out = new byte[blocksOffset + 4 + 8 + compressedLength + 4];
        System.arraycopy(file, 0, out, 0, blocksOffset);
        putU32(out, frameCountOffset(file), frames);
        putU32(out, blocksOffset, 1);
        putU32(out, blocksOffset + 4, Math.max(raw.length, 9L));
        putU32(out, blocksOffset + 8, compressedLength);
        System.arraycopy(buffer, 0, out, blocksOffset + 12, compressedLength);
        refreshCrc(out);
        return out;
    }

    private static ReplayFrames oneFrame() {
        return ReplayFrames.builder(1).add(0.0D, 64.0D, 0.0D, 0.0F, 0.0F, 0, 0.0F, 0.0F, 0).build();
    }

    /** Random byte changes with the CRC recomputed: decoding may succeed, but must never throw anything else. */
    private void mutationFuzz() {
        byte[] good = MhrpCodec.encode(header(2500), frames(2500));
        int rejected = 0;
        int accepted = 0;
        for (int k = 0; k < 4000; k++) {
            byte[] copy = good.clone();
            int changes = 1 + this.random.nextInt(4);
            for (int c = 0; c < changes; c++) {
                int i = 6 + this.random.nextInt(copy.length - 10);
                copy[i] = (byte) this.random.nextInt();
            }
            refreshCrc(copy);
            try {
                MhrpCodec.decode(copy);
                accepted++;
            } catch (MhrpFormatException e) {
                rejected++;
            } catch (Throwable t) {
                check(false, "mutation #" + k + " threw " + t);
            }
            this.checks++;
        }
        this.log.accept("CRC-valid mutations: " + rejected + " rejected, " + accepted + " decoded (changed values), none crashed");
    }

    private interface Mutation {
        void apply(byte[] file);
    }

    private void forge(byte[] good, String what, Mutation mutation) {
        byte[] copy = good.clone();
        mutation.apply(copy);
        refreshCrc(copy);
        expectRejected(copy, "forged: " + what);
    }

    /** True if decoding is rejected with a format error; anything else (accepted, other exception) is a failure. */
    private boolean expectRejected(byte[] file, String what) {
        long start = System.nanoTime();
        try {
            MhrpCodec.decode(file);
            check(false, what + ": accepted");
            return false;
        } catch (MhrpFormatException e) {
            long ms = (System.nanoTime() - start) / 1_000_000L;
            check(ms < 2000, what + ": took " + ms + " ms");
            return true;
        } catch (Throwable t) {
            check(false, what + ": threw " + t);
            return false;
        }
    }

    private static int frameCountOffset(byte[] file) {
        // Header: 4 strings, then time, saved_at, server time, client ticks (8 bytes each), tick rate (4), frame count.
        int offset = 10;
        for (int s = 0; s < 4; s++) {
            offset += 2 + (((file[offset] & 0xFF) << 8) | (file[offset + 1] & 0xFF));
        }
        return offset + 8 * 4 + 4;
    }

    private static long u32(byte[] b, int offset) {
        return ((b[offset] & 0xFFL) << 24) | ((b[offset + 1] & 0xFFL) << 16) | ((b[offset + 2] & 0xFFL) << 8) | (b[offset + 3] & 0xFFL);
    }

    private static void putU32(byte[] b, int offset, long value) {
        b[offset] = (byte) (value >>> 24);
        b[offset + 1] = (byte) (value >>> 16);
        b[offset + 2] = (byte) (value >>> 8);
        b[offset + 3] = (byte) value;
    }

    /** Recomputes the trailing CRC32 (to build files that are wrong in structure but pass the CRC). */
    public static void refreshCrc(byte[] file) {
        if (file.length < 4) {
            return;
        }
        CRC32 crc = new CRC32();
        crc.update(file, 0, file.length - 4);
        putU32(file, file.length - 4, crc.getValue());
    }
}
