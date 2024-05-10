// MADE BY SQUEEK

package net.nerdorg.minehop.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import oshi.util.tuples.Pair;

import java.util.List;

@Environment(EnvType.CLIENT)
public class SqueedometerHud {
    // Vars
    private MinecraftClient client;
    private TextRenderer textRenderer;

    private int color = Formatting.WHITE.getColorValue();
    private int vertColor = Formatting.WHITE.getColorValue();
    private double lastFrameSpeed = 0.0;
    private double lastFrameVertSpeed = 0.0;
    private float tickCounter = 0.0f;

    public static double possibleGain = 0;
    public static double actualGain = 0;


    public void drawMain(DrawContext context, float tickDelta, MinehopConfig config) {
        this.client = MinecraftClient.getInstance();
        if (client != null) {
            this.textRenderer = client.textRenderer;

            // Calculating Speed
            Vec3d playerPosVec = client.player.getPos();
            double travelledX = playerPosVec.x - client.player.prevX;
            double travelledZ = playerPosVec.z - client.player.prevZ;
            double currentSpeed = (double) MathHelper.sqrt((float) (travelledX * travelledX + travelledZ * travelledZ));

            String currentSpeedText = "";

            currentSpeedText = SpeedCalculator.speedText(currentSpeed);

            // Render the text
            context.drawTextWithShadow(this.textRenderer, currentSpeedText, (int) ((config.jHud.speedHud.speed_x_offset / 100f) * client.getWindow().getScaledWidth()), (int) ((config.jHud.speedHud.speed_y_offset / 100f) * client.getWindow().getScaledHeight()), color);
        }
    }

    private int getEffColor(double percent) {
        if (percent < 50) {
            return Formatting.DARK_RED.getColorValue();
        }
        else if (percent < 70) {
            return Formatting.RED.getColorValue();
        }
        else if (percent < 80) {
            return Formatting.YELLOW.getColorValue();
        }
        else if (percent < 90) {
            return Formatting.GREEN.getColorValue();
        }
        else if (percent < 95) {
            return Formatting.AQUA.getColorValue();
        }
        else {
            return Formatting.WHITE.getColorValue();
        }
    }

    private int getGaugeColor(double gauge) {
        int absGauge = MathHelper.abs((int) gauge);
        if (absGauge > 90) {
            return Formatting.DARK_RED.getColorValue();
        }
        else if (absGauge > 70) {
            return Formatting.RED.getColorValue();
        }
        else if (absGauge > 50) {
            return Formatting.YELLOW.getColorValue();
        }
        else if (absGauge > 30) {
            return Formatting.DARK_GREEN.getColorValue();
        }
        else if (absGauge > 10) {
            return Formatting.GREEN.getColorValue();
        }
        else {
            return Formatting.AQUA.getColorValue();
        }
    }

    public void drawJHUD(DrawContext context, MinehopConfig config) {
        this.client = MinecraftClient.getInstance();

        if (client != null) {
            Vec3d playerPosVec = client.player.getPos();
            if (MinehopClient.jump_count > 0) {
                this.client = MinecraftClient.getInstance();
                this.textRenderer = client.textRenderer;

                double effPercent;
                if (client.player.isSpectator()) {
                    effPercent = MinehopClient.last_efficiency;
                }
                else {
                    effPercent = Minehop.efficiencyUpdateMap.containsKey(client.player.getNameForScoreboard()) ? Minehop.efficiencyUpdateMap.get(client.player.getNameForScoreboard()) : 0;
                }
                if (effPercent >= Double.POSITIVE_INFINITY || effPercent <= Double.NEGATIVE_INFINITY) {
                    effPercent = 0;
                } else if (effPercent < 0) {
                    effPercent = 0;
                }

                int effColor = getEffColor(effPercent);

                String effText = SpeedCalculator.effText(effPercent);

                int eff_top = (int) (((float) config.jHud.efficiencyHud.efficiency_y_offset / 100f) * client.getWindow().getScaledHeight());

                int eff_left = (int) ((((float) config.jHud.efficiencyHud.efficiency_x_offset / 100f) * client.getWindow().getScaledWidth()) - (this.textRenderer.getWidth(effText) / 2));

                String ssjText = SpeedCalculator.ssjText(MinehopClient.last_jump_speed, MinehopClient.jump_count);

                int ssj_top = (int) (((float) config.jHud.ssjHud.ssj_y_offset / 100f) * client.getWindow().getScaledHeight());

                int ssj_left = (int) ((((float) config.jHud.ssjHud.ssj_x_offset / 100f) * client.getWindow().getScaledWidth()) - (this.textRenderer.getWidth(ssjText) / 2));

                if (Minehop.gaugeListMap.containsKey(client.player.getNameForScoreboard())) {
                    if (client.world.getTime() % 4 == 0) {
                        List<Double> gaugeList = Minehop.gaugeListMap.get(client.player.getNameForScoreboard());

                        if (gaugeList.size() > 4) {
                            gaugeList = gaugeList.subList(gaugeList.size() - 4, gaugeList.size());
                        }

                        MinehopClient.gauge = gaugeList.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);

                        Minehop.gaugeListMap.put(client.player.getNameForScoreboard(), gaugeList);
                    }
                }

                Pair<String, Integer> gaugeData = SpeedCalculator.gaugeText(MinehopClient.gauge, config.jHud.gaugeHud.horizontal_gauge);
                String gauge_text = gaugeData.getA();
                int offsetToO = gaugeData.getB();

                int gauge_top = (int) ((((float) config.jHud.gaugeHud.gauge_y_offset / 100f) * client.getWindow().getScaledHeight()) - (this.textRenderer.fontHeight * (config.jHud.gaugeHud.horizontal_gauge ? 1 : offsetToO)));

                int gauge_horizontal_offset = offsetToO >= 0 ? ((this.textRenderer.getWidth(gauge_text) / 2) + (this.textRenderer.getWidth(gauge_text.substring(0, offsetToO)) / 2)) : ((this.textRenderer.getWidth(gauge_text) / 2) - (this.textRenderer.getWidth(gauge_text.substring(gauge_text.length() - 1 + offsetToO, gauge_text.length() - 1)) / 2));

                int gauge_offset_left = config.jHud.gaugeHud.horizontal_gauge ? gauge_horizontal_offset : this.textRenderer.getWidth("/\\") / 2;

                int gauge_left = (int) ((((float) config.jHud.gaugeHud.gauge_x_offset / 100f) * client.getWindow().getScaledWidth()) - gauge_offset_left);

                int gaugeColor = getGaugeColor(MinehopClient.gauge);

                // Render the text
                if (config.jHud.ssjHud.show_ssj) {
                    context.drawTextWithShadow(this.textRenderer, ssjText, ssj_left, ssj_top, color);
                }
                if (config.jHud.efficiencyHud.show_efficiency) {
                    context.drawTextWithShadow(this.textRenderer, effText, eff_left, eff_top, effColor);
                }
                if (config.jHud.gaugeHud.show_gauge) {
                    if (config.jHud.gaugeHud.horizontal_gauge) {
                        context.drawTextWithShadow(this.textRenderer, gauge_text, gauge_left, gauge_top, gaugeColor);
                    }
                    else {
                        context.drawTextWrapped(this.textRenderer, StringVisitable.plain(gauge_text), gauge_left, gauge_top, this.textRenderer.getWidth("/\\"), gaugeColor);
                    }
                }
            }
            if (config.jHud.prespeedHud.show_prespeed) {
                String preText = SpeedCalculator.speedText(MinehopClient.start_jump_speed);

                double travelledX = playerPosVec.x - client.player.prevX;
                double travelledZ = playerPosVec.z - client.player.prevZ;
                double speed = (double) MathHelper.sqrt((float) (travelledX * travelledX + travelledZ * travelledZ));
                if (MinehopClient.wasOnGround && !client.player.isOnGround() && MinehopClient.jump_count == 0) {
                    MinehopClient.start_jump_speed = speed;
                }
                context.drawTextWithShadow(this.textRenderer, preText, (int) ((config.jHud.prespeedHud.prespeed_x_offset / 100f) * client.getWindow().getScaledWidth()), (int) ((config.jHud.prespeedHud.prespeed_y_offset / 100f) * client.getWindow().getScaledHeight()), Formatting.GREEN.getColorValue());
            }

            if (client.player == null || !client.player.isSpectator()) {
                if (MinehopClient.jumping) {
                    if (client.world.getTime() >= MinehopClient.last_jump_time + 1 || client.world.getTime() < MinehopClient.last_jump_time || MinehopClient.last_jump_time == 0) {
                        if (client.player.isOnGround()) {
                            double travelledX = playerPosVec.x - client.player.prevX;
                            double travelledZ = playerPosVec.z - client.player.prevZ;
                            double speed = (double) MathHelper.sqrt((float) (travelledX * travelledX + travelledZ * travelledZ));
                            MinehopClient.old_jump_speed = MinehopClient.last_jump_speed;
                            MinehopClient.last_jump_speed = speed;
                            MinehopClient.jump_count += 1;
                            MinehopClient.old_jump_time = MinehopClient.last_jump_time;
                            MinehopClient.last_jump_time = client.world.getTime();
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
            MinehopClient.wasOnGround = client.player.isOnGround();
        }
    }

    public void drawSpectators(DrawContext context, float tickDelta) {
        this.client = MinecraftClient.getInstance();
        this.textRenderer = client.textRenderer;

        int top = (int) ((client.getWindow().getScaledHeight() / 2) + (this.textRenderer.fontHeight * 2));
        int left = 6;
        context.drawTextWithShadow(this.textRenderer, "Spectators \\/", left, top, Formatting.DARK_GRAY.getColorValue());
        for (int index = 0; index < MinehopClient.spectatorList.size(); index++) {
            top += this.textRenderer.fontHeight * 2;
            context.drawTextWithShadow(this.textRenderer, MinehopClient.spectatorList.get(index), left, top, Formatting.RED.getColorValue());
        }
    }
}
