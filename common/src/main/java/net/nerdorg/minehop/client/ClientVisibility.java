package net.nerdorg.minehop.client;

import net.nerdorg.minehop.config.ConfigWrapper;

/**
 * /hide self|others|replay. The commands flip a toggle for the current connection on top of the configured value;
 * they used to flip the field in the loaded config object itself, so the change leaked into whatever saved the config
 * next (and, in singleplayer, into the server's copy). Toggles reset when the connection ends.
 */
public final class ClientVisibility {
    private static boolean selfToggled;
    private static boolean othersToggled;
    private static boolean replayToggled;

    private ClientVisibility() {
    }

    public static boolean hideSelf() {
        return (ConfigWrapper.config != null && ConfigWrapper.config.hideSelf) != selfToggled;
    }

    public static boolean hideOthers() {
        return (ConfigWrapper.config != null && ConfigWrapper.config.hideOthers) != othersToggled;
    }

    public static boolean hideReplay() {
        return (ConfigWrapper.config != null && ConfigWrapper.config.hideReplay) != replayToggled;
    }

    public static void toggleSelf() {
        selfToggled = !selfToggled;
    }

    public static void toggleOthers() {
        othersToggled = !othersToggled;
    }

    public static void toggleReplay() {
        replayToggled = !replayToggled;
    }

    public static void reset() {
        selfToggled = false;
        othersToggled = false;
        replayToggled = false;
    }
}
