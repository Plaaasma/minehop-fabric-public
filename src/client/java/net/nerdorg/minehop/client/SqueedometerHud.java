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


    public void drawMain(DrawContext context, float tickDelta) {
        this.client = MinecraftClient.getInstance();
        this.textRenderer = client.textRenderer;

        // Calculating Speed
        Vec3d playerPosVec = client.player.getPos();
        double travelledX = playerPosVec.x - client.player.prevX;
        double travelledZ = playerPosVec.z - client.player.prevZ;
        double currentSpeed = (double)MathHelper.sqrt((float)(travelledX * travelledX + travelledZ * travelledZ));

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
            return Formatting.GRAY.getColorValue();
        }
        else if (percent < 95) {
            return Formatting.GREEN.getColorValue();
        }
        else {
            return Formatting.AQUA.getColorValue();
        }
    }

    public void drawSSJ(DrawContext context, float tickDelta) {
        MinehopConfig config;
        if (Minehop.override_config) {
            config = new MinehopConfig();
            config.sv_friction = Minehop.o_sv_friction;
            config.sv_accelerate = Minehop.o_sv_accelerate;
            config.sv_airaccelerate = Minehop.o_sv_airaccelerate;
            config.sv_maxairspeed = Minehop.o_sv_maxairspeed;
            config.speed_mul = Minehop.o_speed_mul;
            config.sv_gravity = Minehop.o_sv_gravity;
        }
        else {
            config = ConfigWrapper.config;
        }
        Vec3d playerPosVec = client.player.getPos();
        if (MinehopClient.jump_count > 0) {
            this.client = MinecraftClient.getInstance();
            this.textRenderer = client.textRenderer;

            int eff_top = (int) ((client.getWindow().getScaledHeight() / 2) + (this.textRenderer.fontHeight * 4));

            double speed_difference = MinehopClient.last_jump_speed - MinehopClient.old_jump_speed;
            double max_possible_gain = (config.sv_maxairspeed / (MinehopClient.last_jump_speed * 1.5));
            double effPercent = (speed_difference / max_possible_gain) * 100;
            if (effPercent >= Double.POSITIVE_INFINITY || effPercent <= Double.NEGATIVE_INFINITY) {
                effPercent = 0;
            }
//            else if (effPercent > 100) {
//                effPercent = 100;
//            }
            else if (effPercent < 0) {
                effPercent = 0;
            }

            int effColor = getEffColor(effPercent);

            String effText = SpeedCalculator.effText(effPercent);

            int eff_left = (int) ((client.getWindow().getScaledWidth() / 2) - (this.textRenderer.getWidth(effText) / 2));

            int top = (int) ((client.getWindow().getScaledHeight() / 2) + (this.textRenderer.fontHeight * 2));

            String ssjText = SpeedCalculator.ssjText(MinehopClient.last_jump_speed, MinehopClient.jump_count);

            int left = (int) ((client.getWindow().getScaledWidth() / 2) - (this.textRenderer.getWidth(ssjText) / 2));

            // Render the text
            context.drawTextWithShadow(this.textRenderer, ssjText, left, top, color);
            context.drawTextWithShadow(this.textRenderer, effText, eff_left, eff_top, effColor);
        }
        if (MinehopClient.jumping) {
            if (client.world.getTime() >= MinehopClient.last_jump_time + 1 || client.world.getTime() < MinehopClient.last_jump_time || MinehopClient.last_jump_time == 0) {
                if (client.player.isOnGround()) {
                    double travelledX = playerPosVec.x - client.player.prevX;
                    double travelledZ = playerPosVec.z - client.player.prevZ;
                    double speed = (double) MathHelper.sqrt((float) (travelledX * travelledX + travelledZ * travelledZ));

                    MinehopClient.old_jump_speed = MinehopClient.last_jump_speed;
                    MinehopClient.last_jump_speed = speed;
                    MinehopClient.jump_count += 1;
                    MinehopClient.last_jump_time = client.world.getTime();
                }
            }
        }
        else {
            MinehopClient.old_jump_speed = 0;
            MinehopClient.last_jump_speed = 0;
            MinehopClient.jump_count = 0;
        }
    }
}
