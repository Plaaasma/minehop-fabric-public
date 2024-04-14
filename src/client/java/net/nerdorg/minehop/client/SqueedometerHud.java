// MADE BY SQUEEK

package net.nerdorg.minehop.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;

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


    public void drawMain(DrawContext context, float tickDelta) {
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
            // Calculate text position
            int height = this.textRenderer.fontHeight;
            int paddingX = 2;
            int paddingY = 2;
            int marginX = 4;
            int marginY = 4;
            int left = 0 + marginX;
            int top = 0 + marginY;
            int realHeight = height + paddingY * 2 - 1;

            top += client.getWindow().getScaledHeight() - marginY * 2 - realHeight;

            left += paddingX;
            top += paddingY;

            // Render the text
            context.drawTextWithShadow(this.textRenderer, currentSpeedText, left, top, color);
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

    public void drawJHUD(DrawContext context, MinehopConfig config) {
        this.client = MinecraftClient.getInstance();

        if (client != null) {
            Vec3d playerPosVec = client.player.getPos();
            if (MinehopClient.jump_count > 0) {
                this.client = MinecraftClient.getInstance();
                this.textRenderer = client.textRenderer;

                double effPercent = MinehopClient.last_efficiency;
                if (effPercent >= Double.POSITIVE_INFINITY || effPercent <= Double.NEGATIVE_INFINITY) {
                    effPercent = 0;
                } else if (effPercent < 0) {
                    effPercent = 0;
                }

                int effColor = getEffColor(effPercent);

                String effText = SpeedCalculator.effText(effPercent);

                int eff_top = (int) ((client.getWindow().getScaledHeight() / 2) + (this.textRenderer.fontHeight * 4));

                int top = (int) ((client.getWindow().getScaledHeight() / 2) + (this.textRenderer.fontHeight * 2));

                String ssjText = SpeedCalculator.ssjText(MinehopClient.last_jump_speed, MinehopClient.jump_count);

                int eff_left = (int) ((client.getWindow().getScaledWidth() / 2) - (this.textRenderer.getWidth(effText) / 2));

                int left = (int) ((client.getWindow().getScaledWidth() / 2) - (this.textRenderer.getWidth(ssjText) / 2));


                // Render the text
                if (config.show_ssj) {
                    context.drawTextWithShadow(this.textRenderer, ssjText, left, top, color);
                }
                if (config.show_efficiency) {
                    context.drawTextWithShadow(this.textRenderer, effText, eff_left, eff_top, effColor);
                }
            }
            if (config.show_prespeed) {
                String preText = SpeedCalculator.speedText(MinehopClient.start_jump_speed);

                int pre_top = (int) ((client.getWindow().getScaledHeight()) - (this.textRenderer.fontHeight * 4));
                int pre_left = 6;
                double travelledX = playerPosVec.x - client.player.prevX;
                double travelledZ = playerPosVec.z - client.player.prevZ;
                double speed = (double) MathHelper.sqrt((float) (travelledX * travelledX + travelledZ * travelledZ));
                if (MinehopClient.wasOnGround && !client.player.isOnGround() && MinehopClient.jump_count == 0) {
                    MinehopClient.start_jump_speed = speed;
                }
                context.drawTextWithShadow(this.textRenderer, preText, pre_left, pre_top, Formatting.GREEN.getColorValue());
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
