package net.nerdorg.minehop.neoforge.platform;

/**
 * PHASE 3: remove once every NeoForge service is implemented.
 */
final class Todo {
    private Todo() {
    }

    static UnsupportedOperationException notImplemented(String what) {
        return new UnsupportedOperationException("Minehop on NeoForge: " + what + " is not implemented yet (multiloader phase 3, see docs/MULTILOADER.md)");
    }
}
