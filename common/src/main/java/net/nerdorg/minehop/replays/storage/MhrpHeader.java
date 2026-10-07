package net.nerdorg.minehop.replays.storage;

/**
 * The header of an MHRP replay file (see {@link MhrpCodec} for the byte layout). Everything about the run except its
 * frames; written once with the file and never changed (a run's later status, e.g. invalidated, lives in the store's
 * run log and status files, not here).
 */
public final class MhrpHeader {
    /** Header flag: the run was converted from the version 1 JSON store (minehop_replays.json). */
    public static final int FLAG_MIGRATED = 1;
    /** Header flag: some frames held non-finite or out-of-range values and were replaced (see ReplayFrames.FLAG_REPAIRED). */
    public static final int FLAG_REPAIRED_FRAMES = 1 << 1;

    public String replayId = "";
    public String mapName = "";
    /** "" for runs recorded before UUIDs were stored. */
    public String playerUuid = "";
    public String playerName = "";
    /** The run's time in seconds, as on the leaderboard. */
    public double time;
    /** When the run was saved, epoch milliseconds. */
    public long savedAt;
    /** The server-measured run time in seconds (start-zone launch to end-zone entry), or NaN if unknown. */
    public double serverTime = Double.NaN;
    /** Client ticks the run took (anticheat stream count), or -1 if unknown. */
    public long clientTicks = -1L;
    /** Frames recorded per second. */
    public float tickRate = 20.0F;
    /** Frames in the file, including {@link #preFrames} and {@link #postFrames}. */
    public int frameCount;
    /** Leading frames recorded before the run's timer started (0 until phase 3 records them). */
    public int preFrames;
    /** Trailing frames recorded after the finish (0 until phase 3 records them). */
    public int postFrames;
    public int flags;
    /** Anticheat flags raised during the run, "" if clean. */
    public String acFlags = "";
    /** Positions are stored relative to this point (the first frame's position). */
    public double originX;
    public double originY;
    public double originZ;
    /** Bounding box of the recorded positions: min x/y/z, max x/y/z. All 0 for a run without frames. */
    public double minX;
    public double minY;
    public double minZ;
    public double maxX;
    public double maxY;
    public double maxZ;
    /** Quantisation: position units per block, angle units per full turn, speed units per block/tick, efficiency units per percent. */
    public int positionScale = MhrpCodec.POSITION_SCALE;
    public int angleScale = MhrpCodec.ANGLE_SCALE;
    public int speedScale = MhrpCodec.SPEED_SCALE;
    public int efficiencyScale = MhrpCodec.EFFICIENCY_SCALE;
    /** Frames per column block (each block decodes on its own). */
    public int framesPerBlock = MhrpCodec.FRAMES_PER_BLOCK;

    public MhrpHeader copy() {
        MhrpHeader copy = new MhrpHeader();
        copy.replayId = this.replayId;
        copy.mapName = this.mapName;
        copy.playerUuid = this.playerUuid;
        copy.playerName = this.playerName;
        copy.time = this.time;
        copy.savedAt = this.savedAt;
        copy.serverTime = this.serverTime;
        copy.clientTicks = this.clientTicks;
        copy.tickRate = this.tickRate;
        copy.frameCount = this.frameCount;
        copy.preFrames = this.preFrames;
        copy.postFrames = this.postFrames;
        copy.flags = this.flags;
        copy.acFlags = this.acFlags;
        copy.originX = this.originX;
        copy.originY = this.originY;
        copy.originZ = this.originZ;
        copy.minX = this.minX;
        copy.minY = this.minY;
        copy.minZ = this.minZ;
        copy.maxX = this.maxX;
        copy.maxY = this.maxY;
        copy.maxZ = this.maxZ;
        copy.positionScale = this.positionScale;
        copy.angleScale = this.angleScale;
        copy.speedScale = this.speedScale;
        copy.efficiencyScale = this.efficiencyScale;
        copy.framesPerBlock = this.framesPerBlock;
        return copy;
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ}, or null for a run without frames. */
    public double[] bounds() {
        if (this.frameCount <= 0) {
            return null;
        }
        return new double[]{this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ};
    }
}
