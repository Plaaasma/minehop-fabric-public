// MADE BY SQUEEK

package net.nerdorg.minehop.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.util.StrafeStats;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public class SqueedometerHud {
    // ---- palette (ARGB) ----
    private static final int C_GREEN = 0xFF55FF55;
    private static final int C_RED = 0xFFFF5555;
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_CYAN = 0xFF55FFFF;
    private static final int C_TRACK = 0xFF202020;
    private static final int C_TICK = 0xFF505050;
    private static final int C_PLATE = 0xB0000000;
    private static final int C_GLOSS = 0x55FFFFFF;
    private static final int C_SUBTEXT = 0xFFB0B0B0;
    private static final int C_NOTCH = 0xFFFFFFFF;

    // ---- base bar geometry (multiplied by per-element scale) ----
    static final int BAR_LEN = 140;
    static final int EFF_THICK = 8;
    static final int GAUGE_THICK = 6;

    private MinecraftClient client;
    private TextRenderer textRenderer;

    private double lastFrameSpeed = 0.0;

    public static double possibleGain = 0;
    public static double actualGain = 0;

    private static double blocksPerSecond(double speedPerTick) {
        return speedPerTick / 0.05D;
    }

    // red (0) -> yellow (0.5) -> green (1)
    static int gradient(double t) {
        t = MathHelper.clamp(t, 0.0D, 1.0D);
        int r, g;
        if (t < 0.5D) {
            r = 255;
            g = (int) Math.round(510 * t);
        } else {
            r = (int) Math.round(510 * (1.0D - t));
            g = 255;
        }
        return 0xFF000000 | (r << 16) | (g << 8);
    }

    private static int gainColor(double delta) {
        if (delta > 0.02D) return C_GREEN;
        if (delta < -0.02D) return C_RED;
        return C_WHITE;
    }

    private int xPct(int pct) {
        return (int) ((pct / 100f) * this.client.getWindow().getScaledWidth());
    }

    private int yPct(int pct) {
        return (int) ((pct / 100f) * this.client.getWindow().getScaledHeight());
    }

    private void centeredText(DrawContext ctx, String text, int cx, int y, int color) {
        ctx.drawTextWithShadow(this.textRenderer, text, cx - this.textRenderer.getWidth(text) / 2, y, color);
    }

    private void scaledCenteredText(DrawContext ctx, String text, int cx, int y, float scale, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawTextWithShadow(this.textRenderer, text, -this.textRenderer.getWidth(text) / 2, 0, color);
        ctx.getMatrices().pop();
    }

    /**
     * Fill bar centered on (anchorX, anchorY). Horizontal: fills left->right. Vertical: fills
     * bottom->up. len = long axis, thick = short axis. tick80 marks the 80% "good" target.
     */
    static void fillBar(DrawContext ctx, int anchorX, int anchorY, int len, int thick, double frac,
                        int fillColor, boolean tick80, boolean vertical) {
        frac = MathHelper.clamp(frac, 0.0D, 1.0D);
        if (!vertical) {
            int x = anchorX - len / 2;
            int y = anchorY;
            ctx.fill(x - 2, y - 2, x + len + 2, y + thick + 2, C_PLATE);
            ctx.fill(x, y, x + len, y + thick, C_TRACK);
            int fillPx = (int) Math.round(len * frac);
            if (fillPx > 0) {
                ctx.fill(x, y, x + fillPx, y + thick, fillColor);
                ctx.fill(x, y, x + fillPx, y + 1, C_GLOSS);
            }
            if (tick80) {
                int tx = x + (int) Math.round(len * 0.8D);
                ctx.fill(tx, y - 1, tx + 1, y + thick + 1, C_NOTCH);
            }
        } else {
            int x = anchorX - thick / 2;
            int y = anchorY - len / 2;
            ctx.fill(x - 2, y - 2, x + thick + 2, y + len + 2, C_PLATE);
            ctx.fill(x, y, x + thick, y + len, C_TRACK);
            int fillPx = (int) Math.round(len * frac);
            if (fillPx > 0) {
                ctx.fill(x, y + len - fillPx, x + thick, y + len, fillColor);
                ctx.fill(x, y + len - fillPx, x + 1, y + len, C_GLOSS);
            }
            if (tick80) {
                int ty = y + len - (int) Math.round(len * 0.8D);
                ctx.fill(x - 1, ty, x + thick + 1, ty + 1, C_NOTCH);
            }
        }
    }

    /** Center-anchored slider gauge (0..200, 100=perfect=center). Marker color = closeness to perfect. */
    static void gaugeBar(DrawContext ctx, int anchorX, int anchorY, int len, int thick, double value0to200, boolean vertical) {
        double frac = MathHelper.clamp(value0to200 / 200.0D, 0.0D, 1.0D);
        double err = MathHelper.clamp(Math.abs(value0to200 - 100.0D) / 100.0D, 0.0D, 1.0D);
        int markerColor = err < 0.06D ? C_CYAN : gradient(1.0D - err);
        if (!vertical) {
            int x = anchorX - len / 2;
            int y = anchorY;
            int cx = x + len / 2;
            ctx.fill(x - 2, y - 2, x + len + 2, y + thick + 2, C_PLATE);
            ctx.fill(x, y, x + len, y + thick, C_TRACK);
            ctx.fill(x + len / 4, y + 1, x + len / 4 + 1, y + thick - 1, C_TICK);
            ctx.fill(x + 3 * len / 4, y + 1, x + 3 * len / 4 + 1, y + thick - 1, C_TICK);
            ctx.fill(cx - 1, y - 2, cx + 1, y + thick + 2, C_NOTCH);
            int m = x + (int) Math.round(frac * len);
            ctx.fill(m - 2, y - 1, m + 3, y + thick + 1, markerColor);
        } else {
            int x = anchorX - thick / 2;
            int y = anchorY - len / 2;
            int cy = y + len / 2;
            ctx.fill(x - 2, y - 2, x + thick + 2, y + len + 2, C_PLATE);
            ctx.fill(x, y, x + thick, y + len, C_TRACK);
            ctx.fill(x + 1, y + len / 4, x + thick - 1, y + len / 4 + 1, C_TICK);
            ctx.fill(x + 1, y + 3 * len / 4, x + thick - 1, y + 3 * len / 4 + 1, C_TICK);
            ctx.fill(x - 2, cy - 1, x + thick + 2, cy + 1, C_NOTCH);
            // value increases upward: marker y = bottom - frac*len
            int m = y + len - (int) Math.round(frac * len);
            ctx.fill(x - 1, m - 2, x + thick + 1, m + 3, markerColor);
        }
    }

    public void drawMain(DrawContext context, float tickDelta, MinehopConfig config) {
        this.client = MinecraftClient.getInstance();
        if (this.client == null || this.client.player == null) return;
        this.textRenderer = this.client.textRenderer;
        if (!config.jHud.speedHud.show_current_speed) return;

        Vec3d pos = this.client.player.getPos();
        double dx = pos.x - this.client.player.prevX;
        double dz = pos.z - this.client.player.prevZ;
        double speedPerTick = Math.sqrt(dx * dx + dz * dz);
        double bps = blocksPerSecond(speedPerTick);

        int speedColor = gainColor(bps - this.lastFrameSpeed);
        this.lastFrameSpeed = bps;

        float scale = (float) Math.max(0.1, config.jHud.speedHud.speed_scale);
        int cx = this.xPct(config.jHud.speedHud.speed_x_offset);
        int y = this.yPct(config.jHud.speedHud.speed_y_offset);
        this.scaledCenteredText(context, String.format(Locale.ROOT, "%.2f", bps), cx, y, scale, speedColor);
        int subY = y + (int) Math.round(this.textRenderer.fontHeight * scale) + 1;
        this.scaledCenteredText(context, "blocks/sec", cx, subY, Math.max(0.5f, scale * 0.55f), C_SUBTEXT);
    }

    public void drawJHUD(DrawContext context, MinehopConfig config) {
        this.client = MinecraftClient.getInstance();
        if (this.client == null || this.client.player == null) return;
        this.textRenderer = this.client.textRenderer;

        this.drawRunTimerHud(context, config);

        String name = this.client.player.getNameForScoreboard();
        StrafeStats stats = Minehop.strafeStatsMap.get(name);

        double gauge = stats != null ? stats.liveGauge : 0.0D;
        double eff;
        double sync;
        int strafes;
        if (stats != null && stats.measuredTicks >= 4) {
            // need a few samples before a running sync/eff is meaningful; otherwise the first
            // gaining tick reads 1/1 = 100% and flashes a bogus value.
            eff = stats.liveEfficiency;
            sync = stats.liveSync;
            strafes = stats.strafes;
        } else if (stats != null) {
            eff = stats.lastJumpEfficiency;
            sync = stats.lastJumpSync;
            strafes = stats.lastJumpStrafes;
        } else {
            eff = this.client.player.isSpectator() ? MinehopClient.last_efficiency : 0.0D;
            sync = 0.0D;
            strafes = 0;
        }
        if (Double.isNaN(eff) || Double.isInfinite(eff) || eff < 0) eff = 0;
        if (Double.isNaN(sync) || Double.isInfinite(sync) || sync < 0) sync = 0;

        // GAUGE (live strafe trainer) ----------------------------------------------------------
        if (config.jHud.gaugeHud.show_gauge) {
            boolean horiz = config.jHud.gaugeHud.horizontal_gauge;
            float gs = (float) Math.max(0.1, config.jHud.gaugeHud.gauge_scale);
            int len = Math.round(BAR_LEN * gs);
            int thick = Math.max(3, Math.round(GAUGE_THICK * gs));
            int gx = this.xPct(config.jHud.gaugeHud.gauge_x_offset);
            int gy = this.yPct(config.jHud.gaugeHud.gauge_y_offset);
            gaugeBar(context, gx, gy, len, thick, gauge, !horiz);
            int labelY = horiz ? gy - 11 : gy - len / 2 - 11;
            this.centeredText(context, "GAUGE", gx, labelY, C_SUBTEXT);
        }

        // EFFICIENCY bar + SYNC/STRAFES text ---------------------------------------------------
        if (config.jHud.efficiencyHud.show_efficiency) {
            boolean vert = config.jHud.efficiencyHud.vertical_efficiency;
            float es = (float) Math.max(0.1, config.jHud.efficiencyHud.efficiency_scale);
            int len = Math.round(BAR_LEN * es);
            int thick = Math.max(3, Math.round(EFF_THICK * es));
            int ex = this.xPct(config.jHud.efficiencyHud.efficiency_x_offset);
            int ey = this.yPct(config.jHud.efficiencyHud.efficiency_y_offset);
            int effColor = gradient(eff / 100.0D);
            fillBar(context, ex, ey, len, thick, eff / 100.0D, effColor, true, vert);
            int syncColor = sync >= 90 ? C_GREEN : (sync >= 70 ? Formatting.YELLOW.getColorValue() : C_RED);
            String effLabel = String.format(Locale.ROOT, "EFF %.2f%%", eff);
            String syncLabel = String.format(Locale.ROOT, "SYNC %.2f%%   STRAFES %d", sync, strafes);
            if (!vert) {
                this.centeredText(context, effLabel, ex, ey - 11, effColor);
                this.centeredText(context, syncLabel, ex, ey + thick + 3, syncColor);
            } else {
                this.centeredText(context, effLabel, ex, ey - len / 2 - 11, effColor);
                this.centeredText(context, syncLabel, ex, ey + len / 2 + 3, syncColor);
            }
        }

        // SSJ (last jump speed + chained jump count) -------------------------------------------
        if (config.jHud.ssjHud.show_ssj && MinehopClient.jump_count > 0) {
            int sx = this.xPct(config.jHud.ssjHud.ssj_x_offset);
            int sy = this.yPct(config.jHud.ssjHud.ssj_y_offset);
            String ssj = String.format(Locale.ROOT, "%.2f (%d)", blocksPerSecond(MinehopClient.last_jump_speed), MinehopClient.jump_count);
            this.scaledCenteredText(context, ssj, sx, sy, (float) Math.max(0.1, config.jHud.ssjHud.ssj_scale), C_WHITE);
        }

        // PRESPEED -----------------------------------------------------------------------------
        Vec3d pos = this.client.player.getPos();
        if (config.jHud.prespeedHud.show_prespeed) {
            double dx = pos.x - this.client.player.prevX;
            double dz = pos.z - this.client.player.prevZ;
            double speed = Math.sqrt(dx * dx + dz * dz);
            if (MinehopClient.wasOnGround && !this.client.player.isOnGround() && MinehopClient.jump_count == 0) {
                MinehopClient.start_jump_speed = speed;
            }
            String preText = String.format(Locale.ROOT, "%.2f", blocksPerSecond(MinehopClient.start_jump_speed));
            this.scaledCenteredText(context, preText,
                    this.xPct(config.jHud.prespeedHud.prespeed_x_offset),
                    this.yPct(config.jHud.prespeedHud.prespeed_y_offset),
                    (float) Math.max(0.1, config.jHud.prespeedHud.prespeed_scale), C_GREEN);
        }

        // jump-count / chain bookkeeping (client-side) -----------------------------------------
        if (this.client.player == null || !this.client.player.isSpectator()) {
            if (MinehopClient.jumping) {
                if (this.client.world.getTime() >= MinehopClient.last_jump_time + 1
                        || this.client.world.getTime() < MinehopClient.last_jump_time
                        || MinehopClient.last_jump_time == 0) {
                    if (this.client.player.isOnGround()) {
                        double dx = pos.x - this.client.player.prevX;
                        double dz = pos.z - this.client.player.prevZ;
                        double speed = Math.sqrt(dx * dx + dz * dz);
                        MinehopClient.old_jump_speed = MinehopClient.last_jump_speed;
                        MinehopClient.last_jump_speed = speed;
                        MinehopClient.jump_count += 1;
                        MinehopClient.old_jump_time = MinehopClient.last_jump_time;
                        MinehopClient.last_jump_time = this.client.world.getTime();
                    }
                }
            } else {
                MinehopClient.old_jump_speed = 0;
                MinehopClient.last_jump_speed = 0;
                MinehopClient.jump_count = 0;
                MinehopClient.old_jump_time = 0;
                MinehopClient.last_jump_time = 0;
            }
        }
        MinehopClient.wasOnGround = this.client.player.isOnGround();
    }

    private void drawRunTimerHud(DrawContext context, MinehopConfig config) {
        if (context == null || config == null || config.jHud == null || config.jHud.timerHud == null) {
            return;
        }
        if (!config.jHud.timerHud.show_timer || !MinehopClient.runTimerHudVisible) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (MinehopClient.runTimerHudUpdatedAtMs <= 0L || nowMs - MinehopClient.runTimerHudUpdatedAtMs > 1250L) {
            MinehopClient.runTimerHudVisible = false;
            return;
        }
        if (this.client == null || this.client.textRenderer == null) {
            return;
        }

        float timeValue = Math.max(0.0F, MinehopClient.runTimerHudTime);
        float pbValue = MinehopClient.runTimerHudPb;
        String pbText = pbValue > 0.0F ? String.format(Locale.ROOT, "%.5f", pbValue) : "No PB";
        String text = "Time: " + String.format(Locale.ROOT, "%.2f", timeValue) + " PB: " + pbText;

        int y = this.yPct(config.jHud.timerHud.timer_y_offset);
        int xCenter = this.xPct(config.jHud.timerHud.timer_x_offset);
        this.scaledCenteredText(context, text, xCenter, y, (float) Math.max(0.1, config.jHud.timerHud.timer_scale), C_WHITE);
    }

    public void drawSpectators(DrawContext context, float tickDelta) {
        this.client = MinecraftClient.getInstance();
        this.textRenderer = this.client.textRenderer;

        int top = (this.client.getWindow().getScaledHeight() / 2) + (this.textRenderer.fontHeight * 2);
        int left = 6;
        context.drawTextWithShadow(this.textRenderer, "Spectators \\/", left, top, Formatting.DARK_GRAY.getColorValue());
        for (int index = 0; index < MinehopClient.spectatorList.size(); index++) {
            top += this.textRenderer.fontHeight * 2;
            context.drawTextWithShadow(this.textRenderer, MinehopClient.spectatorList.get(index), left, top, Formatting.RED.getColorValue());
        }
    }
}
