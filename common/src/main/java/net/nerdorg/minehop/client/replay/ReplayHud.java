package net.nerdorg.minehop.client.replay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.render.GuiColors;
import net.nerdorg.minehop.replays.storage.ReplayFrames;

import java.util.Locale;

/**
 * The replay bar shown while watching a replay on this client: state (playing, paused, buffering, loading), speed,
 * whose run on which map, run time / total, a progress bar (pre- and post-run parts darker), the recorded keys of the
 * current frame (W A S D, jump, duck) and the jump stats. The regular timer and jump HUD show the replay's values too
 * (ReplayPlayback feeds them).
 */
public final class ReplayHud {
    private static final int PLATE = 0xA0000000;
    private static final int TRACK = 0xFF303030;
    private static final int RUN_TRACK = 0xFF4A4A4A;
    private static final int FILL = 0xFF55FF55;
    private static final int FILL_WR = 0xFFFFC040;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUBTEXT = 0xFFB0B0B0;
    private static final int KEY_OFF = 0x60FFFFFF;
    private static final int BAR_WIDTH = 220;

    private ReplayHud() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        ReplayPlayback.Watch watch = ReplayPlayback.current();
        Minecraft mc = Minecraft.getInstance();
        if (watch == null || mc.options.hideGui || (ConfigWrapper.config != null && ConfigWrapper.config.replay != null
                && !ConfigWrapper.config.replay.show_replay_hud)) {
            return;
        }
        Font font = mc.font;
        int width = graphics.guiWidth();
        int x = (width - BAR_WIDTH) / 2;
        // Below the run timer at the top: the bottom of the screen belongs to the speed, gauge and efficiency HUD.
        int timerPercent = ConfigWrapper.config == null || ConfigWrapper.config.jHud == null || ConfigWrapper.config.jHud.timerHud == null
                ? 5 : ConfigWrapper.config.jHud.timerHud.timer_y_offset;
        int y = Mth.clamp((int) (graphics.guiHeight() * Mth.clamp(timerPercent, 0, 40) / 100.0F) + 16, 6, Math.max(6, graphics.guiHeight() - 46));
        graphics.fill(x - 4, y - 4, x + BAR_WIDTH + 4, y + 38, PLATE);

        String who = (watch.worldRecord() ? "WR " : "PB ") + watch.player + " on " + watch.map;
        String state;
        ReplayFrames frames = watch.frames;
        if (frames == null) {
            float progress = watch.requestId >= 0 ? ClientReplayStreams.progress(watch.requestId) : 0.0F;
            state = watch.failure != null ? "Failed" : String.format(Locale.ROOT, "Loading %d%%", Math.round(Math.max(0.0F, progress) * 100.0F));
        } else if (watch.buffering) {
            state = "Buffering";
        } else if (watch.paused) {
            state = "|| Paused";
        } else {
            state = "> " + String.format(Locale.ROOT, "%.2fx", watch.speed);
        }
        graphics.text(font, state, x, y, GuiColors.text(TEXT));
        graphics.text(font, who, x + BAR_WIDTH - font.width(who), y, GuiColors.text(SUBTEXT));

        int barY = y + 11;
        graphics.fill(x, barY, x + BAR_WIDTH, barY + 4, TRACK);
        if (frames != null && frames.size() > 1) {
            double last = frames.size() - 1;
            int runStartX = x + (int) Math.round(BAR_WIDTH * frames.runStart() / last);
            int runEndX = x + (int) Math.round(BAR_WIDTH * Math.max(0, frames.runEnd() - 1) / last);
            graphics.fill(runStartX, barY, Math.max(runStartX + 1, runEndX), barY + 4, RUN_TRACK);
            int fillX = x + (int) Math.round(BAR_WIDTH * Mth.clamp(watch.position / last, 0.0D, 1.0D));
            graphics.fill(x, barY, fillX, barY + 4, watch.worldRecord() ? FILL_WR : FILL);
            graphics.fill(fillX - 1, barY - 2, fillX + 1, barY + 6, TEXT);
        }

        String time = formatTime(watch.runSeconds()) + " / " + formatTime(watch.time);
        graphics.text(font, time, x, y + 18, GuiColors.text(TEXT));
        if (frames != null) {
            int frame = (int) Mth.clamp(Math.floor(watch.position), 0, frames.size() - 1);
            String stats = String.format(Locale.ROOT, "Jumps %d  SSJ %.1f  Eff %.1f%%", frames.jumpCount(frame),
                    frames.lastJumpSpeed(frame) / 0.05D, frames.efficiency(frame));
            graphics.text(font, stats, x + BAR_WIDTH - font.width(stats), y + 18, GuiColors.text(SUBTEXT));
            drawKeys(graphics, font, x, y + 28, frames, frame);
        }
    }

    private static void drawKeys(GuiGraphicsExtractor graphics, Font font, int x, int y, ReplayFrames frames, int frame) {
        if (!frames.tickStream()) {
            graphics.text(font, "(no key data in this older replay)", x, y, GuiColors.text(KEY_OFF));
            return;
        }
        int flags = frames.flags(frame);
        int cursor = x;
        cursor = key(graphics, font, cursor, y, "W", (flags & ReplayFrames.FLAG_INPUT_FORWARD) != 0);
        cursor = key(graphics, font, cursor, y, "A", (flags & ReplayFrames.FLAG_INPUT_LEFT) != 0);
        cursor = key(graphics, font, cursor, y, "S", (flags & ReplayFrames.FLAG_INPUT_BACK) != 0);
        cursor = key(graphics, font, cursor, y, "D", (flags & ReplayFrames.FLAG_INPUT_RIGHT) != 0);
        cursor = key(graphics, font, cursor + 4, y, "JUMP", (flags & ReplayFrames.FLAG_INPUT_JUMP) != 0);
        cursor = key(graphics, font, cursor, y, "DUCK", (flags & ReplayFrames.FLAG_INPUT_SNEAK) != 0);
        key(graphics, font, cursor + 4, y, (flags & ReplayFrames.FLAG_ON_GROUND) != 0 ? "GROUND" : "AIR", (flags & ReplayFrames.FLAG_ON_GROUND) != 0);
    }

    private static int key(GuiGraphicsExtractor graphics, Font font, int x, int y, String label, boolean down) {
        int w = font.width(label) + 4;
        graphics.fill(x, y - 1, x + w, y + 9, down ? 0x80FFFFFF : 0x30FFFFFF);
        graphics.text(font, label, x + 2, y, GuiColors.text(down ? 0xFF000000 : KEY_OFF), false);
        return x + w + 2;
    }

    static String formatTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0D) {
            seconds = 0.0D;
        }
        int minutes = (int) (seconds / 60.0D);
        double rest = seconds - minutes * 60.0D;
        return minutes > 0 ? String.format(Locale.ROOT, "%d:%06.3f", minutes, rest) : String.format(Locale.ROOT, "%.3f", rest);
    }
}
