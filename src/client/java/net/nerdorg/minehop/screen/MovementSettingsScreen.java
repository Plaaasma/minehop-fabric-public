package net.nerdorg.minehop.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class MovementSettingsScreen extends Screen {
    private final Screen parent;
    private final Consumer<Values> onApply;
    private Values values;

    private TextFieldWidget svFrictionField;
    private TextFieldWidget svAccelerateField;
    private TextFieldWidget svAiraccelerateField;
    private TextFieldWidget svMaxairspeedField;
    private TextFieldWidget svJumpImpulseField;
    private TextFieldWidget speedMulField;
    private TextFieldWidget svGravityField;
    private TextFieldWidget svStopspeedField;
    private TextFieldWidget speedCoefficientField;
    private ButtonWidget overrideButton;
    private ButtonWidget autoStepButton;
    private ButtonWidget cssCrouchButton;
    private ButtonWidget disableSprintButton;
    private ButtonWidget fallDamageButton;

    public MovementSettingsScreen(Screen parent, Values values, Consumer<Values> onApply) {
        super(Text.translatable("screen.minehop.map_creator.movement.title"));
        this.parent = parent;
        this.values = values == null ? Values.defaults() : values;
        this.onApply = onApply;
    }

    @Override
    protected void applyBlur() {
    }

    @Override
    public void blur() {
    }

    @Override
    protected void init() {
        super.init();
        int panelWidth = 460;
        int panelHeight = 384;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int labelX = panelX + 18;
        int fieldX = panelX + 190;
        int fieldWidth = panelWidth - 208;
        int y = panelY + 32;

        this.overrideButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            this.values = this.values.withOverride(!this.values.overrideEnabled());
            this.updateButtonText();
        }).dimensions(fieldX, y, fieldWidth, 20).build());
        y += 24;

        this.svFrictionField = this.addField(fieldX, y, fieldWidth, this.values.svFriction());
        y += 22;
        this.svAccelerateField = this.addField(fieldX, y, fieldWidth, this.values.svAccelerate());
        y += 22;
        this.svAiraccelerateField = this.addField(fieldX, y, fieldWidth, this.values.svAiraccelerate());
        y += 22;
        this.svMaxairspeedField = this.addField(fieldX, y, fieldWidth, this.values.svMaxairspeed());
        y += 22;
        this.svJumpImpulseField = this.addField(fieldX, y, fieldWidth, this.values.svJumpImpulse());
        y += 22;
        this.speedMulField = this.addField(fieldX, y, fieldWidth, this.values.speedMul());
        y += 22;
        this.svGravityField = this.addField(fieldX, y, fieldWidth, this.values.svGravity());
        y += 22;
        this.svStopspeedField = this.addField(fieldX, y, fieldWidth, this.values.svStopspeed());
        y += 22;
        this.speedCoefficientField = this.addField(fieldX, y, fieldWidth, this.values.speedCoefficient());
        y += 24;

        this.autoStepButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            this.values = this.values.withAutoStepUp(!this.values.autoStepUp());
            this.updateButtonText();
        }).dimensions(fieldX, y, fieldWidth, 20).build());
        y += 24;
        this.cssCrouchButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            this.values = this.values.withCssCrouchJump(!this.values.cssCrouchJump());
            this.updateButtonText();
        }).dimensions(fieldX, y, fieldWidth, 20).build());
        y += 24;
        this.disableSprintButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            this.values = this.values.withDisableSprint(!this.values.disableSprint());
            this.updateButtonText();
        }).dimensions(fieldX, y, fieldWidth, 20).build());
        y += 24;
        this.fallDamageButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            this.values = this.values.withFallDamage(!this.values.fallDamage());
            this.updateButtonText();
        }).dimensions(fieldX, y, fieldWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> this.applyAndClose())
                .dimensions(panelX + panelWidth - 142, panelY + panelHeight - 26, 60, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> this.close())
                .dimensions(panelX + panelWidth - 76, panelY + panelHeight - 26, 60, 20)
                .build());

        this.updateButtonText();
    }

    private TextFieldWidget addField(int x, int y, int width, double value) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, width, 18, Text.empty());
        field.setMaxLength(16);
        field.setText(Double.toString(value));
        return this.addDrawableChild(field);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = 460;
        int panelHeight = 384;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int labelX = panelX + 18;
        int y = panelY + 37;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0101010);
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF666666);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, panelY + 10, 0xFFFFFF);

        this.drawLabel(context, "Use Custom Movement", labelX, y);
        y += 24;
        this.drawLabel(context, "sv_friction", labelX, y);
        y += 22;
        this.drawLabel(context, "sv_accelerate", labelX, y);
        y += 22;
        this.drawLabel(context, "sv_airaccelerate", labelX, y);
        y += 22;
        this.drawLabel(context, "sv_maxairspeed", labelX, y);
        y += 22;
        this.drawLabel(context, "sv_jump_impulse", labelX, y);
        y += 22;
        this.drawLabel(context, "speed_mul", labelX, y);
        y += 22;
        this.drawLabel(context, "sv_gravity", labelX, y);
        y += 22;
        this.drawLabel(context, "sv_stopspeed", labelX, y);
        y += 22;
        this.drawLabel(context, "speed_coefficient", labelX, y);
        y += 24;
        this.drawLabel(context, "auto_step_up", labelX, y);
        y += 24;
        this.drawLabel(context, "css_crouch_jump", labelX, y);
        y += 24;
        this.drawLabel(context, "disable_sprint", labelX, y);
        y += 24;
        this.drawLabel(context, "fall_damage", labelX, y);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawLabel(DrawContext context, String label, int x, int y) {
        context.drawTextWithShadow(this.textRenderer, Text.literal(label), x, y, 0xE0E0E0);
    }

    private void updateButtonText() {
        if (this.overrideButton != null) {
            this.overrideButton.setMessage(this.values.overrideEnabled()
                    ? Text.translatable("screen.minehop.map_creator.toggle.on")
                    : Text.translatable("screen.minehop.map_creator.toggle.off"));
        }
        if (this.autoStepButton != null) {
            this.autoStepButton.setMessage(this.values.autoStepUp()
                    ? Text.translatable("screen.minehop.map_creator.toggle.on")
                    : Text.translatable("screen.minehop.map_creator.toggle.off"));
        }
        if (this.cssCrouchButton != null) {
            this.cssCrouchButton.setMessage(this.values.cssCrouchJump()
                    ? Text.translatable("screen.minehop.map_creator.toggle.on")
                    : Text.translatable("screen.minehop.map_creator.toggle.off"));
        }
        if (this.disableSprintButton != null) {
            this.disableSprintButton.setMessage(this.values.disableSprint()
                    ? Text.translatable("screen.minehop.map_creator.toggle.on")
                    : Text.translatable("screen.minehop.map_creator.toggle.off"));
        }
        if (this.fallDamageButton != null) {
            this.fallDamageButton.setMessage(this.values.fallDamage()
                    ? Text.translatable("screen.minehop.map_creator.toggle.on")
                    : Text.translatable("screen.minehop.map_creator.toggle.off"));
        }
    }

    private void applyAndClose() {
        Values updated = new Values(
                this.values.overrideEnabled(),
                this.parse(this.svFrictionField, this.values.svFriction()),
                this.parse(this.svAccelerateField, this.values.svAccelerate()),
                this.parse(this.svAiraccelerateField, this.values.svAiraccelerate()),
                this.parse(this.svMaxairspeedField, this.values.svMaxairspeed()),
                this.parse(this.svJumpImpulseField, this.values.svJumpImpulse()),
                this.parse(this.speedMulField, this.values.speedMul()),
                this.parse(this.svGravityField, this.values.svGravity()),
                this.parse(this.svStopspeedField, this.values.svStopspeed()),
                this.parse(this.speedCoefficientField, this.values.speedCoefficient()),
                this.values.autoStepUp(),
                this.values.cssCrouchJump(),
                this.values.disableSprint(),
                this.values.fallDamage()
        );
        if (this.onApply != null) {
            this.onApply.accept(updated);
        }
        this.close();
    }

    private double parse(TextFieldWidget field, double fallback) {
        if (field == null || field.getText() == null || field.getText().isBlank()) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(field.getText().trim());
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    public record Values(
            boolean overrideEnabled,
            double svFriction,
            double svAccelerate,
            double svAiraccelerate,
            double svMaxairspeed,
            double svJumpImpulse,
            double speedMul,
            double svGravity,
            double svStopspeed,
            double speedCoefficient,
            boolean autoStepUp,
            boolean cssCrouchJump,
            boolean disableSprint,
            boolean fallDamage
    ) {
        public static Values defaults() {
            return new Values(false, 4.0D, 10.0D, 100.0D, 30.0D, 300.0D, 3.25D, 800.0D, 75.0D, 1.0D, false, true, false, false);
        }

        public Values withOverride(boolean value) {
            return new Values(value, svFriction, svAccelerate, svAiraccelerate, svMaxairspeed, svJumpImpulse, speedMul, svGravity, svStopspeed, speedCoefficient, autoStepUp, cssCrouchJump, disableSprint, fallDamage);
        }

        public Values withAutoStepUp(boolean value) {
            return new Values(overrideEnabled, svFriction, svAccelerate, svAiraccelerate, svMaxairspeed, svJumpImpulse, speedMul, svGravity, svStopspeed, speedCoefficient, value, cssCrouchJump, disableSprint, fallDamage);
        }

        public Values withCssCrouchJump(boolean value) {
            return new Values(overrideEnabled, svFriction, svAccelerate, svAiraccelerate, svMaxairspeed, svJumpImpulse, speedMul, svGravity, svStopspeed, speedCoefficient, autoStepUp, value, disableSprint, fallDamage);
        }

        public Values withDisableSprint(boolean value) {
            return new Values(overrideEnabled, svFriction, svAccelerate, svAiraccelerate, svMaxairspeed, svJumpImpulse, speedMul, svGravity, svStopspeed, speedCoefficient, autoStepUp, cssCrouchJump, value, fallDamage);
        }

        public Values withFallDamage(boolean value) {
            return new Values(overrideEnabled, svFriction, svAccelerate, svAiraccelerate, svMaxairspeed, svJumpImpulse, speedMul, svGravity, svStopspeed, speedCoefficient, autoStepUp, cssCrouchJump, disableSprint, value);
        }
    }
}
