package net.nerdorg.minehop.forge.platform;

/**
 * PHASE 3: remove once every Forge service is implemented.
 */
final class Todo {
    private Todo() {
    }

    static UnsupportedOperationException notImplemented(String what) {
        return new UnsupportedOperationException("Minehop on Forge: " + what + " is not implemented yet (multiloader phase 3, see docs/MULTILOADER.md)");
    }
}
