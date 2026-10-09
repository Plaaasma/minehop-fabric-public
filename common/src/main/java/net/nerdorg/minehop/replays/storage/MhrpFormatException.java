package net.nerdorg.minehop.replays.storage;

/** An MHRP file that can't be read. {@link #isCorrupt()} says whether the file itself is bad (and may be quarantined). */
public final class MhrpFormatException extends Exception {
    private static final long serialVersionUID = 1L;

    public enum Kind {
        /** Shorter than the smallest valid file, or a length field points past the end. */
        TRUNCATED(true),
        /** Not an MHRP file. */
        BAD_MAGIC(true),
        /** Written by a newer format version: not corrupt, just not readable by this version. */
        UNSUPPORTED_VERSION(false),
        /** The CRC32 over the file doesn't match. */
        BAD_CRC(true),
        /** Structurally invalid (bad lengths, values out of range, bad compressed data, trailing bytes). */
        CORRUPT(true),
        /** Larger than any file this version writes; not read (no allocation is made for it). */
        TOO_LARGE(false);

        private final boolean corrupt;

        Kind(boolean corrupt) {
            this.corrupt = corrupt;
        }
    }

    private final Kind kind;

    public MhrpFormatException(Kind kind, String message) {
        super(kind + ": " + message);
        this.kind = kind;
    }

    public MhrpFormatException(Kind kind, String message, Throwable cause) {
        super(kind + ": " + message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    /** True if the file's content is bad (quarantine it); false if it is merely unreadable by this version. */
    public boolean isCorrupt() {
        return this.kind.corrupt;
    }
}
