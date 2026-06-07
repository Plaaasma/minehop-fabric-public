package net.nerdorg.minehop.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;

import java.util.Locale;

/**
 * Drag-to-move / corner-or-scroll-to-resize editor for the JHud elements, opened with the editor
 * keybind (default "]"). Edits ConfigWrapper.config.jHud live and saves on close. Previews use the
 * exact SqueedometerHud rendering so it's WYSIWYG.
 */
@Environment(EnvType.CLIENT)
public class HudEditorScreen extends Screen {
    private enum Kind { SPEED, GAUGE, EFFICIENCY, SSJ, PRESPEED, TIMER }

    private static final int HANDLE_DRAW = 4; // visible square size
    private static final int HANDLE_GRAB = 8;  // clickable zone (inside the corner)
    private static final int LABEL_GAP = 11;
    private static final double MIN_SCALE = 0.3D;
    private static final double MAX_SCALE = 4.0D;

    private static final int C_BORDER = 0xFF55FFFF;
    private static final int C_BORDER_HIDDEN = 0x66AAAAAA;
    private static final int C_HANDLE = 0xFFFFFFFF;
    private static final int C_SHADE = 0x66101018;

    private Kind dragging = null;
    private boolean resizing = false;
    private double grabDX, grabDY;     // mouse - anchor at press (move)
    private double startDist, startScale; // resize

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private MinehopConfig.JHud jhud() {
        return ConfigWrapper.config.jHud;
    }

    // ---- per-kind config accessors -------------------------------------------------------------
    private int xPct(Kind k) {
        MinehopConfig.JHud j = jhud();
        return switch (k) {
            case SPEED -> j.speedHud.speed_x_offset;
            case GAUGE -> j.gaugeHud.gauge_x_offset;
            case EFFICIENCY -> j.efficiencyHud.efficiency_x_offset;
            case SSJ -> j.ssjHud.ssj_x_offset;
            case PRESPEED -> j.prespeedHud.prespeed_x_offset;
            case TIMER -> j.timerHud.timer_x_offset;
        };
    }

    private int yPct(Kind k) {
        MinehopConfig.JHud j = jhud();
        return switch (k) {
            case SPEED -> j.speedHud.speed_y_offset;
            case GAUGE -> j.gaugeHud.gauge_y_offset;
            case EFFICIENCY -> j.efficiencyHud.efficiency_y_offset;
            case SSJ -> j.ssjHud.ssj_y_offset;
            case PRESPEED -> j.prespeedHud.prespeed_y_offset;
            case TIMER -> j.timerHud.timer_y_offset;
        };
    }

    private double scale(Kind k) {
        MinehopConfig.JHud j = jhud();
        return switch (k) {
            case SPEED -> j.speedHud.speed_scale;
            case GAUGE -> j.gaugeHud.gauge_scale;
            case EFFICIENCY -> j.efficiencyHud.efficiency_scale;
            case SSJ -> j.ssjHud.ssj_scale;
            case PRESPEED -> j.prespeedHud.prespeed_scale;
            case TIMER -> j.timerHud.timer_scale;
        };
    }

    private boolean show(Kind k) {
        MinehopConfig.JHud j = jhud();
        return switch (k) {
            case SPEED -> j.speedHud.show_current_speed;
            case GAUGE -> j.gaugeHud.show_gauge;
            case EFFICIENCY -> j.efficiencyHud.show_efficiency;
            case SSJ -> j.ssjHud.show_ssj;
            case PRESPEED -> j.prespeedHud.show_prespeed;
            case TIMER -> j.timerHud.show_timer;
        };
    }

    private void setXY(Kind k, int x, int y) {
        MinehopConfig.JHud j = jhud();
        x = MathHelper.clamp(x, 0, 100);
        y = MathHelper.clamp(y, 0, 100);
        switch (k) {
            case SPEED -> { j.speedHud.speed_x_offset = x; j.speedHud.speed_y_offset = y; }
            case GAUGE -> { j.gaugeHud.gauge_x_offset = x; j.gaugeHud.gauge_y_offset = y; }
            case EFFICIENCY -> { j.efficiencyHud.efficiency_x_offset = x; j.efficiencyHud.efficiency_y_offset = y; }
            case SSJ -> { j.ssjHud.ssj_x_offset = x; j.ssjHud.ssj_y_offset = y; }
            case PRESPEED -> { j.prespeedHud.prespeed_x_offset = x; j.prespeedHud.prespeed_y_offset = y; }
            case TIMER -> { j.timerHud.timer_x_offset = x; j.timerHud.timer_y_offset = y; }
        }
    }

    private void setScale(Kind k, double s) {
        MinehopConfig.JHud j = jhud();
        s = MathHelper.clamp(s, MIN_SCALE, MAX_SCALE);
        s = Math.round(s * 100.0D) / 100.0D;
        switch (k) {
            case SPEED -> j.speedHud.speed_scale = s;
            case GAUGE -> j.gaugeHud.gauge_scale = s;
            case EFFICIENCY -> j.efficiencyHud.efficiency_scale = s;
            case SSJ -> j.ssjHud.ssj_scale = s;
            case PRESPEED -> j.prespeedHud.prespeed_scale = s;
            case TIMER -> j.timerHud.timer_scale = s;
        }
    }

    private void toggleShow(Kind k) {
        MinehopConfig.JHud j = jhud();
        switch (k) {
            case SPEED -> j.speedHud.show_current_speed = !j.speedHud.show_current_speed;
            case GAUGE -> j.gaugeHud.show_gauge = !j.gaugeHud.show_gauge;
            case EFFICIENCY -> j.efficiencyHud.show_efficiency = !j.efficiencyHud.show_efficiency;
            case SSJ -> j.ssjHud.show_ssj = !j.ssjHud.show_ssj;
            case PRESPEED -> j.prespeedHud.show_prespeed = !j.prespeedHud.show_prespeed;
            case TIMER -> j.timerHud.show_timer = !j.timerHud.show_timer;
        }
    }

    private int anchorX(Kind k) { return (int) ((xPct(k) / 100f) * this.width); }
    private int anchorY(Kind k) { return (int) ((yPct(k) / 100f) * this.height); }

    private String sample(Kind k) {
        return switch (k) {
            case SPEED -> "247.00";
            case SSJ -> "247.00 (12)";
            case PRESPEED -> "120.00";
            case TIMER -> "Time: 12.34 PB: 10.00000";
            default -> "";
        };
    }

    private static final String EFF_SAMPLE = "EFF 62.00%";
    private static final String SYNC_SAMPLE = "SYNC 88.00%   STRAFES 12";

    // ---- geometry (mirrors SqueedometerHud) ----------------------------------------------------
    private int[] rect(Kind k) {
        TextRenderer tr = this.textRenderer;
        int ax = anchorX(k), ay = anchorY(k);
        double s = scale(k);
        switch (k) {
            case SPEED -> {
                double subS = Math.max(0.5D, s * 0.55D);
                int w = (int) Math.ceil(Math.max(tr.getWidth(sample(Kind.SPEED)) * s, tr.getWidth("blocks/sec") * subS));
                int h = (int) Math.ceil(tr.fontHeight * s + 1 + tr.fontHeight * subS);
                return new int[]{ax - w / 2, ay, w, h};
            }
            case SSJ, PRESPEED, TIMER -> {
                int w = (int) Math.ceil(tr.getWidth(sample(k)) * s);
                int h = (int) Math.ceil(tr.fontHeight * s);
                return new int[]{ax - w / 2, ay, Math.max(w, 6), h};
            }
            case GAUGE -> {
                boolean horiz = jhud().gaugeHud.horizontal_gauge;
                int len = (int) Math.round(SqueedometerHud.BAR_LEN * s);
                int thick = Math.max(3, (int) Math.round(SqueedometerHud.GAUGE_THICK * s));
                int labelW = tr.getWidth("GAUGE");
                if (horiz) {
                    int w = Math.max(len, labelW);
                    return new int[]{ax - w / 2, ay - LABEL_GAP, w, thick + LABEL_GAP};
                } else {
                    int w = Math.max(thick, labelW);
                    return new int[]{ax - w / 2, ay - len / 2 - LABEL_GAP, w, len + LABEL_GAP};
                }
            }
            case EFFICIENCY -> {
                boolean vert = jhud().efficiencyHud.vertical_efficiency;
                int len = (int) Math.round(SqueedometerHud.BAR_LEN * s);
                int thick = Math.max(3, (int) Math.round(SqueedometerHud.EFF_THICK * s));
                int syncW = tr.getWidth(SYNC_SAMPLE);
                if (!vert) {
                    int w = Math.max(len, syncW);
                    int h = thick + LABEL_GAP + 3 + tr.fontHeight;
                    return new int[]{ax - w / 2, ay - LABEL_GAP, w, h};
                } else {
                    int w = Math.max(thick, syncW);
                    int h = len + LABEL_GAP + 3 + tr.fontHeight;
                    return new int[]{ax - w / 2, ay - len / 2 - LABEL_GAP, w, h};
                }
            }
        }
        return new int[]{ax, ay, 1, 1};
    }

    private void scaledText(DrawContext ctx, String text, int cx, int y, double s, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y, 0);
        ctx.getMatrices().scale((float) s, (float) s, 1f);
        ctx.drawTextWithShadow(this.textRenderer, text, -this.textRenderer.getWidth(text) / 2, 0, color);
        ctx.getMatrices().pop();
    }

    private void drawElement(DrawContext ctx, Kind k) {
        int ax = anchorX(k), ay = anchorY(k);
        double s = scale(k);
        int alpha = show(k) ? 0xFFFFFFFF : 0x80FFFFFF; // dim if hidden
        switch (k) {
            case SPEED -> {
                scaledText(ctx, sample(Kind.SPEED), ax, ay, s, 0xFF55FF55 & alpha);
                double subS = Math.max(0.5D, s * 0.55D);
                scaledText(ctx, "blocks/sec", ax, ay + (int) Math.round(this.textRenderer.fontHeight * s) + 1, subS, 0xFFB0B0B0 & alpha);
            }
            case SSJ -> scaledText(ctx, sample(k), ax, ay, s, 0xFFFFFFFF & alpha);
            case PRESPEED -> scaledText(ctx, sample(k), ax, ay, s, 0xFF55FF55 & alpha);
            case TIMER -> scaledText(ctx, sample(k), ax, ay, s, 0xFFFFFFFF & alpha);
            case GAUGE -> {
                boolean horiz = jhud().gaugeHud.horizontal_gauge;
                int len = (int) Math.round(SqueedometerHud.BAR_LEN * s);
                int thick = Math.max(3, (int) Math.round(SqueedometerHud.GAUGE_THICK * s));
                SqueedometerHud.gaugeBar(ctx, ax, ay, len, thick, 72.0D, !horiz);
                int ly = horiz ? ay - LABEL_GAP : ay - len / 2 - LABEL_GAP;
                ctx.drawTextWithShadow(this.textRenderer, "GAUGE", ax - this.textRenderer.getWidth("GAUGE") / 2, ly, 0xFFB0B0B0 & alpha);
            }
            case EFFICIENCY -> {
                boolean vert = jhud().efficiencyHud.vertical_efficiency;
                int len = (int) Math.round(SqueedometerHud.BAR_LEN * s);
                int thick = Math.max(3, (int) Math.round(SqueedometerHud.EFF_THICK * s));
                int effColor = SqueedometerHud.gradient(0.62D);
                SqueedometerHud.fillBar(ctx, ax, ay, len, thick, 0.62D, effColor, true, vert);
                String effL = EFF_SAMPLE;
                String syncL = SYNC_SAMPLE;
                if (!vert) {
                    ctx.drawTextWithShadow(this.textRenderer, effL, ax - this.textRenderer.getWidth(effL) / 2, ay - LABEL_GAP, effColor);
                    ctx.drawTextWithShadow(this.textRenderer, syncL, ax - this.textRenderer.getWidth(syncL) / 2, ay + thick + 3, 0xFF55FF55);
                } else {
                    ctx.drawTextWithShadow(this.textRenderer, effL, ax - this.textRenderer.getWidth(effL) / 2, ay - len / 2 - LABEL_GAP, effColor);
                    ctx.drawTextWithShadow(this.textRenderer, syncL, ax - this.textRenderer.getWidth(syncL) / 2, ay + len / 2 + 3, 0xFF55FF55);
                }
            }
        }
    }

    private boolean inRect(int[] r, double mx, double my) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    private boolean inHandle(int[] r, double mx, double my) {
        // grab zone sits INSIDE the bottom-right corner so it's covered by the element rect
        return mx >= r[0] + r[2] - HANDLE_GRAB && mx <= r[0] + r[2]
                && my >= r[1] + r[3] - HANDLE_GRAB && my <= r[1] + r[3];
    }

    private Kind hit(double mx, double my) {
        // topmost-ish: iterate reverse so later kinds win overlaps
        Kind[] order = Kind.values();
        for (int i = order.length - 1; i >= 0; i--) {
            if (inRect(rect(order[i]), mx, my)) {
                return order[i];
            }
        }
        return null;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, C_SHADE);

        for (Kind k : Kind.values()) {
            drawElement(ctx, k);
            int[] r = rect(k);
            boolean hidden = !show(k);
            int border = hidden ? C_BORDER_HIDDEN : (hit(mouseX, mouseY) == k || dragging == k ? C_BORDER : 0x88FFFFFF);
            // border
            ctx.drawBorder(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, border);
            // resize handle: small square inside the bottom-right corner
            ctx.fill(r[0] + r[2] - HANDLE_DRAW, r[1] + r[3] - HANDLE_DRAW, r[0] + r[2], r[1] + r[3], C_HANDLE);
            // name tag
            String tag = k.name().toLowerCase(Locale.ROOT) + (hidden ? " (off)" : "");
            ctx.drawTextWithShadow(this.textRenderer, tag, r[0], r[1] - 10, hidden ? 0xFF888888 : 0xFFFFFFFF);
        }

        // help bar
        int y = 6;
        ctx.drawTextWithShadow(this.textRenderer, "HUD Editor — drag to move, drag corner / scroll to resize", this.width / 2 - 150, y, 0xFFFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, "right-click to toggle an element on/off   •   ESC to save & close", this.width / 2 - 150, y + 11, 0xFFB0B0B0);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // keep the world visible behind the editor; render() draws its own light shade
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Kind k = hit(mx, my);
        if (k == null) {
            return super.mouseClicked(mx, my, button);
        }
        if (button == 1) { // right-click toggles visibility
            toggleShow(k);
            return true;
        }
        if (button == 0) {
            int[] r = rect(k);
            this.dragging = k;
            if (inHandle(r, mx, my)) {
                this.resizing = true;
                this.startScale = scale(k);
                this.startDist = Math.max(2.0D, Math.hypot(mx - anchorX(k), my - anchorY(k)));
            } else {
                this.resizing = false;
                this.grabDX = mx - anchorX(k);
                this.grabDY = my - anchorY(k);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.dragging != null) {
            if (this.resizing) {
                double curDist = Math.hypot(mx - anchorX(this.dragging), my - anchorY(this.dragging));
                setScale(this.dragging, this.startScale * (curDist / this.startDist));
            } else {
                int nx = (int) Math.round(((mx - this.grabDX) / this.width) * 100.0D);
                int ny = (int) Math.round(((my - this.grabDY) / this.height) * 100.0D);
                setXY(this.dragging, nx, ny);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.dragging = null;
        this.resizing = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        Kind k = hit(mx, my);
        if (k != null && vAmount != 0) {
            setScale(k, scale(k) + vAmount * 0.1D);
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public void close() {
        ConfigWrapper.saveConfig(ConfigWrapper.config);
        super.close();
    }
}
