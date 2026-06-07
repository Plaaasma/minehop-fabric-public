package net.nerdorg.minehop.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.ClientPacketHandler;

@Environment(EnvType.CLIENT)
public class ZoneStickSettingsScreen extends Screen {
    private final String zoneType;
    private final String initialMapName;
    private final int initialCheckpointIndex;
    private final boolean checkpointEditable;
    private final boolean preserveSpeedEditable;
    private final boolean initialPreserveSpeed;
    private final boolean initialResetToStart;

    private TextFieldWidget mapNameField;
    private TextFieldWidget checkpointIndexField;
    private ButtonWidget applyBoundsButton;
    private ButtonWidget preserveSpeedButton;
    private ButtonWidget resetTargetButton;
    private boolean applyBounds;
    private boolean preserveSpeed;
    private boolean resetToStart;

    public ZoneStickSettingsScreen(
            String zoneType,
            String mapName,
            int checkpointIndex,
            boolean checkpointEditable,
            boolean preserveSpeed,
            boolean preserveSpeedEditable
    ) {
        super(Text.translatable("screen.minehop.zone_stick_settings.title"));
        this.zoneType = (zoneType == null || zoneType.isBlank()) ? "zone" : zoneType;
        this.initialMapName = mapName == null ? "" : mapName;
        this.initialCheckpointIndex = Math.max(0, checkpointIndex);
        this.checkpointEditable = checkpointEditable;
        this.preserveSpeedEditable = preserveSpeedEditable;
        this.initialPreserveSpeed = preserveSpeed;
        this.initialResetToStart = this.initialCheckpointIndex <= 0;
        this.applyBounds = false;
        this.preserveSpeed = preserveSpeed;
        this.resetToStart = this.initialResetToStart;
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
        int panelWidth = 360;
        int panelHeight = 338;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int fieldX = panelX + 196;
        int fieldWidth = panelWidth - 212;

        this.mapNameField = new TextFieldWidget(
                this.textRenderer,
                fieldX,
                panelY + 52,
                fieldWidth,
                18,
                Text.translatable("screen.minehop.zone_stick_settings.map_name")
        );
        this.mapNameField.setText(this.initialMapName);
        this.mapNameField.setMaxLength(128);
        this.addDrawableChild(this.mapNameField);

        this.resetTargetButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.toggleResetTarget())
                        .dimensions(fieldX, panelY + 78, fieldWidth, 20)
                        .build()
        );
        this.resetTargetButton.active = this.checkpointEditable;

        this.checkpointIndexField = new TextFieldWidget(
                this.textRenderer,
                fieldX,
                panelY + 104,
                fieldWidth,
                18,
                Text.translatable("screen.minehop.zone_stick_settings.checkpoint_index")
        );
        this.checkpointIndexField.setText(Integer.toString(this.initialCheckpointIndex));
        this.checkpointIndexField.setMaxLength(6);
        this.addDrawableChild(this.checkpointIndexField);

        this.applyBoundsButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.toggleApplyBounds())
                        .dimensions(panelX + 16, panelY + 158, panelWidth - 32, 20)
                        .build()
        );

        this.preserveSpeedButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.togglePreserveSpeed())
                        .dimensions(panelX + 16, panelY + 194, panelWidth - 32, 20)
                        .build()
        );
        this.preserveSpeedButton.active = this.preserveSpeedEditable;

        int actionButtonWidth = 102;
        int actionButtonGap = 9;
        int actionButtonsStartX = panelX + (panelWidth - (actionButtonWidth * 3 + actionButtonGap * 2)) / 2;
        int actionButtonsY = panelY + panelHeight - 24;

        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("gui.done"), button -> this.applyAndClose())
                        .dimensions(actionButtonsStartX, actionButtonsY, actionButtonWidth, 20)
                        .build()
        );
        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.minehop.zone_stick_settings.delete"), button -> this.deleteAndClose())
                        .dimensions(actionButtonsStartX + actionButtonWidth + actionButtonGap, actionButtonsY, actionButtonWidth, 20)
                        .build()
        );
        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("gui.cancel"), button -> this.cancelAndClose())
                        .dimensions(actionButtonsStartX + (actionButtonWidth + actionButtonGap) * 2, actionButtonsY, actionButtonWidth, 20)
                        .build()
        );

        this.updateApplyBoundsButton();
        this.updatePreserveSpeedButton();
        this.updateResetTargetButton();
        this.updateCheckpointFieldState();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = 360;
        int panelHeight = 338;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int valueX = panelX + 196;
        int labelX = panelX + 16;
        int disabledColor = 0x808080;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0101010);
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF666666);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, panelY + 10, 0xFFFFFF);

        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.zone_stick_settings.zone_type"),
                labelX,
                panelY + 31,
                0xE0E0E0
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(this.zoneType.toUpperCase()),
                valueX,
                panelY + 31,
                0xFFFFFF
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.zone_stick_settings.map_name"),
                labelX,
                panelY + 56,
                0xE0E0E0
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.zone_stick_settings.reset_target"),
                labelX,
                panelY + 82,
                this.checkpointEditable ? 0xE0E0E0 : disabledColor
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.zone_stick_settings.checkpoint_index"),
                labelX,
                panelY + 108,
                this.checkpointEditable && !this.resetToStart ? 0xE0E0E0 : disabledColor
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(this.getCheckpointCountHint()),
                labelX,
                panelY + 126,
                0xB0B0B0
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.zone_stick_settings.apply_bounds"),
                labelX,
                panelY + 147,
                0xE0E0E0
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.zone_stick_settings.preserve_speed"),
                labelX,
                panelY + 183,
                this.preserveSpeedEditable ? 0xE0E0E0 : disabledColor
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.zone_stick_settings.help"),
                labelX,
                panelY + 228,
                0xC8C8C8
        );

        super.render(context, mouseX, mouseY, delta);
    }

    private void toggleApplyBounds() {
        this.applyBounds = !this.applyBounds;
        this.updateApplyBoundsButton();
    }

    private void updateApplyBoundsButton() {
        if (this.applyBoundsButton == null) {
            return;
        }
        this.applyBoundsButton.setMessage(
                this.applyBounds
                        ? Text.translatable("screen.minehop.zone_stick_settings.toggle.on")
                        : Text.translatable("screen.minehop.zone_stick_settings.toggle.off")
        );
    }

    private void toggleResetTarget() {
        if (!this.checkpointEditable) {
            return;
        }
        this.resetToStart = !this.resetToStart;
        this.updateResetTargetButton();
        this.updateCheckpointFieldState();
    }

    private void updateResetTargetButton() {
        if (this.resetTargetButton == null) {
            return;
        }
        if (!this.checkpointEditable) {
            this.resetTargetButton.setMessage(Text.literal("-"));
            return;
        }
        this.resetTargetButton.setMessage(
                this.resetToStart
                        ? Text.translatable("screen.minehop.zone_stick_settings.reset_target.start")
                        : Text.translatable("screen.minehop.zone_stick_settings.reset_target.checkpoint")
        );
    }

    private void updateCheckpointFieldState() {
        if (this.checkpointIndexField == null) {
            return;
        }
        boolean active = this.checkpointEditable && !this.resetToStart;
        this.checkpointIndexField.active = active;
        this.checkpointIndexField.setEditable(active);
        if (active) {
            String raw = this.checkpointIndexField.getText();
            if (raw == null || raw.isBlank() || "0".equals(raw.trim())) {
                this.checkpointIndexField.setText("1");
            }
        }
    }

    private String getCheckpointCountHint() {
        int count = this.resolveCheckpointCount();
        return Text.translatable("screen.minehop.zone_stick_settings.checkpoint_count", count).getString();
    }

    private int resolveCheckpointCount() {
        if (this.mapNameField == null) {
            return 0;
        }
        String mapName = this.mapNameField.getText();
        if (mapName == null || mapName.isBlank()) {
            return 0;
        }
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData == null || mapData.name == null) {
                continue;
            }
            if (!mapData.name.equalsIgnoreCase(mapName.trim())) {
                continue;
            }
            if (mapData.checkpointPositions == null) {
                return 0;
            }
            return Math.max(0, mapData.checkpointPositions.size());
        }
        return 0;
    }

    private void togglePreserveSpeed() {
        if (!this.preserveSpeedEditable) {
            return;
        }
        this.preserveSpeed = !this.preserveSpeed;
        this.updatePreserveSpeedButton();
    }

    private void updatePreserveSpeedButton() {
        if (this.preserveSpeedButton == null) {
            return;
        }
        if (!this.preserveSpeedEditable) {
            this.preserveSpeedButton.setMessage(Text.literal("-"));
            return;
        }
        this.preserveSpeedButton.setMessage(
                this.preserveSpeed
                        ? Text.translatable("screen.minehop.zone_stick_settings.toggle.on")
                        : Text.translatable("screen.minehop.zone_stick_settings.toggle.off")
        );
    }

    private void applyAndClose() {
        ClientPacketHandler.sendZoneStickSettings(
                this.mapNameField == null ? "" : this.mapNameField.getText(),
                this.parseCheckpointIndex(),
                this.applyBounds,
                this.preserveSpeedEditable ? this.preserveSpeed : this.initialPreserveSpeed
        );
        this.close();
    }

    private void deleteAndClose() {
        ClientPacketHandler.sendZoneStickDelete();
        this.close();
    }

    private void cancelAndClose() {
        ClientPacketHandler.sendZoneStickCancel();
        this.close();
    }

    private int parseCheckpointIndex() {
        if (!this.checkpointEditable || this.resetToStart) {
            return 0;
        }
        if (this.checkpointIndexField == null) {
            return Math.max(1, this.initialCheckpointIndex);
        }
        String raw = this.checkpointIndexField.getText();
        if (raw == null || raw.isBlank()) {
            return Math.max(1, this.initialCheckpointIndex);
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return Math.max(1, this.initialCheckpointIndex);
        }
    }
}
