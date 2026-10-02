package net.nerdorg.minehop.screen;

import net.nerdorg.minehop.render.GuiColors;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class MovementSettingsScreen extends Screen {
    private final Screen parent;
    private final Consumer<Values> onApply;
    private Values values;

    private EditBox svFrictionField;
    private EditBox svAccelerateField;
    private EditBox svAiraccelerateField;
    private EditBox svMaxairspeedField;
    private EditBox svJumpImpulseField;
    private EditBox speedMulField;
    private EditBox svGravityField;
    private EditBox svStopspeedField;
    private EditBox speedCoefficientField;
    private EditBox speedCapField;
    private Button overrideButton;
    private Button autoStepButton;
    private Button cssCrouchButton;
    private Button disableSprintButton;
    private Button fallDamageButton;

    public MovementSettingsScreen(Screen parent, Values values, Consumer<Values> onApply) {
        super(Component.translatable("screen.minehop.map_creator.movement.title"));
        this.parent = parent;
        this.values = values == null ? Values.defaults() : values;
        this.onApply = onApply;
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor context) {
    }

    @Override
    public void clearFocus() {
    }

    @Override
    protected void init() {
        super.init();
        int panelWidth = 460;
        int panelHeight = 406;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int labelX = panelX + 18;
        int fieldX = panelX + 190;
        int fieldWidth = panelWidth - 208;
        int y = panelY + 32;

        this.overrideButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.values = this.values.withOverride(!this.values.overrideEnabled());
            this.updateButtonText();
        }).bounds(fieldX, y, fieldWidth, 20).build());
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
        y += 22;
        this.speedCapField = this.addField(fieldX, y, fieldWidth, this.values.speedCap());
        y += 24;

        this.autoStepButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.values = this.values.withAutoStepUp(!this.values.autoStepUp());
            this.updateButtonText();
        }).bounds(fieldX, y, fieldWidth, 20).build());
        y += 24;
        this.cssCrouchButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.values = this.values.withCssCrouchJump(!this.values.cssCrouchJump());
            this.updateButtonText();
        }).bounds(fieldX, y, fieldWidth, 20).build());
        y += 24;
        this.disableSprintButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.values = this.values.withDisableSprint(!this.values.disableSprint());
            this.updateButtonText();
        }).bounds(fieldX, y, fieldWidth, 20).build());
        y += 24;
        this.fallDamageButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            this.values = this.values.withFallDamage(!this.values.fallDamage());
            this.updateButtonText();
        }).bounds(fieldX, y, fieldWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.applyAndClose())
                .bounds(panelX + panelWidth - 142, panelY + panelHeight - 26, 60, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(panelX + panelWidth - 76, panelY + panelHeight - 26, 60, 20)
                .build());

        this.updateButtonText();
    }

    private EditBox addField(int x, int y, int width, double value) {
        EditBox field = new EditBox(this.font, x, y, width, 18, Component.empty());
        field.setMaxLength(16);
        field.setValue(Double.toString(value));
        return this.addRenderableWidget(field);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.extractBackground(context, mouseX, mouseY, delta);

        int panelWidth = 460;
        int panelHeight = 406;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int labelX = panelX + 18;
        int y = panelY + 37;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0101010);
        context.outline(panelX, panelY, panelWidth, panelHeight, 0xFF666666);
        context.centeredText(this.font, this.title, this.width / 2, panelY + 10, GuiColors.text(0xFFFFFF));

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
        y += 22;
        this.drawLabel(context, "speed_cap", labelX, y);
        y += 24;
        this.drawLabel(context, "auto_step_up", labelX, y);
        y += 24;
        this.drawLabel(context, "css_crouch_jump", labelX, y);
        y += 24;
        this.drawLabel(context, "disable_sprint", labelX, y);
        y += 24;
        this.drawLabel(context, "fall_damage", labelX, y);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawLabel(GuiGraphicsExtractor context, String label, int x, int y) {
        context.text(this.font, Component.literal(label), x, y, GuiColors.text(0xE0E0E0));
    }

    private void updateButtonText() {
        if (this.overrideButton != null) {
            this.overrideButton.setMessage(this.values.overrideEnabled()
                    ? Component.translatable("screen.minehop.map_creator.toggle.on")
                    : Component.translatable("screen.minehop.map_creator.toggle.off"));
        }
        if (this.autoStepButton != null) {
            this.autoStepButton.setMessage(this.values.autoStepUp()
                    ? Component.translatable("screen.minehop.map_creator.toggle.on")
                    : Component.translatable("screen.minehop.map_creator.toggle.off"));
        }
        if (this.cssCrouchButton != null) {
            this.cssCrouchButton.setMessage(this.values.cssCrouchJump()
                    ? Component.translatable("screen.minehop.map_creator.toggle.on")
                    : Component.translatable("screen.minehop.map_creator.toggle.off"));
        }
        if (this.disableSprintButton != null) {
            this.disableSprintButton.setMessage(this.values.disableSprint()
                    ? Component.translatable("screen.minehop.map_creator.toggle.on")
                    : Component.translatable("screen.minehop.map_creator.toggle.off"));
        }
        if (this.fallDamageButton != null) {
            this.fallDamageButton.setMessage(this.values.fallDamage()
                    ? Component.translatable("screen.minehop.map_creator.toggle.on")
                    : Component.translatable("screen.minehop.map_creator.toggle.off"));
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
                this.parse(this.speedCapField, this.values.speedCap()),
                this.values.autoStepUp(),
                this.values.cssCrouchJump(),
                this.values.disableSprint(),
                this.values.fallDamage()
        );
        if (this.onApply != null) {
            this.onApply.accept(updated);
        }
        this.onClose();
    }

    private double parse(EditBox field, double fallback) {
        if (field == null || field.getValue() == null || field.getValue().isBlank()) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(field.getValue().trim());
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
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
            double speedCap,
            boolean autoStepUp,
            boolean cssCrouchJump,
            boolean disableSprint,
            boolean fallDamage
    ) {
        public static Values defaults() {
            return new Values(false, 4.0D, 10.0D, 100.0D, 30.0D, 300.0D, 3.25D, 800.0D, 75.0D, 1.0D, 0.0D, false, true, false, false);
        }

        public Values withOverride(boolean value) {
            return new Values(value, svFriction, svAccelerate, svAiraccelerate, svMaxairspeed, svJumpImpulse, speedMul, svGravity, svStopspeed, speedCoefficient, speedCap, autoStepUp, cssCrouchJump, disableSprint, fallDamage);
        }

        public Values withAutoStepUp(boolean value) {
            return new Values(overrideEnabled, svFriction, svAccelerate, svAiraccelerate, svMaxairspeed, svJumpImpulse, speedMul, svGravity, svStopspeed, speedCoefficient, speedCap, value, cssCrouchJump, disableSprint, fallDamage);
        }

        public Values withCssCrouchJump(boolean value) {
            return new Values(overrideEnabled, svFriction, svAccelerate, svAiraccelerate, svMaxairspeed, svJumpImpulse, speedMul, svGravity, svStopspeed, speedCoefficient, speedCap, autoStepUp, value, disableSprint, fallDamage);
        }

        public Values withDisableSprint(boolean value) {
            return new Values(overrideEnabled, svFriction, svAccelerate, svAiraccelerate, svMaxairspeed, svJumpImpulse, speedMul, svGravity, svStopspeed, speedCoefficient, speedCap, autoStepUp, cssCrouchJump, value, fallDamage);
        }

        public Values withFallDamage(boolean value) {
            return new Values(overrideEnabled, svFriction, svAccelerate, svAiraccelerate, svMaxairspeed, svJumpImpulse, speedMul, svGravity, svStopspeed, speedCoefficient, speedCap, autoStepUp, cssCrouchJump, disableSprint, value);
        }
    }
}
