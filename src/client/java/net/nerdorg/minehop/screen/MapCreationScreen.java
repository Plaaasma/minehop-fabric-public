package net.nerdorg.minehop.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.ClientPacketHandler;
import net.nerdorg.minehop.networking.payloads.MapCreatorActionPayload;

@Environment(EnvType.CLIENT)
public class MapCreationScreen extends Screen {
    private static final String[] DIFFICULTY_NAMES = new String[]{
            "Beginner",
            "Easy",
            "Moderate",
            "Challenging",
            "Extremely Hard",
            "Impossible"
    };

    private final String initialMapName;
    private final int initialDifficulty;
    private final boolean initialArena;
    private final boolean initialHns;
    private final boolean initialSurf;
    private final boolean initialKz;
    private final MovementSettingsScreen.Values initialMovementSettings;
    private final int initialCheckpointIndex;
    private final boolean initialResetToStart;

    private TextFieldWidget mapNameField;
    private TextFieldWidget checkpointIndexField;
    private ButtonWidget difficultyButton;
    private ButtonWidget arenaButton;
    private ButtonWidget hnsButton;
    private ButtonWidget surfButton;
    private ButtonWidget kzButton;
    private ButtonWidget movementButton;
    private ButtonWidget resetTargetButton;

    private int selectedDifficulty;
    private boolean selectedArena;
    private boolean selectedHns;
    private boolean selectedSurf;
    private boolean selectedKz;
    private MovementSettingsScreen.Values selectedMovementSettings;
    private boolean selectedResetToStart;

    public MapCreationScreen(
            String mapName,
            int difficulty,
            boolean arena,
            boolean hns,
            boolean surf,
            boolean kz,
            boolean movementOverride,
            double movementSvFriction,
            double movementSvAccelerate,
            double movementSvAiraccelerate,
            double movementSvMaxairspeed,
            double movementSvJumpImpulse,
            double movementSpeedMul,
            double movementSvGravity,
            double movementSvStopspeed,
            double movementSpeedCoefficient,
            double movementSpeedCap,
            boolean movementAutoStepUp,
            boolean movementCssCrouchJump,
            boolean movementDisableSprint,
            boolean movementFallDamage,
            int checkpointIndex
    ) {
        super(Text.translatable("screen.minehop.map_creator.title"));
        this.initialMapName = mapName == null ? "" : mapName;
        this.initialDifficulty = MathHelper.clamp(difficulty, 0, 5);
        this.initialArena = arena;
        this.initialHns = hns;
        this.initialSurf = surf;
        this.initialKz = kz;
        this.initialMovementSettings = new MovementSettingsScreen.Values(
                movementOverride,
                movementSvFriction,
                movementSvAccelerate,
                movementSvAiraccelerate,
                movementSvMaxairspeed,
                movementSvJumpImpulse,
                movementSpeedMul,
                movementSvGravity,
                movementSvStopspeed,
                movementSpeedCoefficient,
                movementSpeedCap,
                movementAutoStepUp,
                movementCssCrouchJump,
                movementDisableSprint,
                movementFallDamage
        );
        this.initialCheckpointIndex = Math.max(0, checkpointIndex);
        this.initialResetToStart = this.initialCheckpointIndex <= 0;
        this.selectedDifficulty = this.initialDifficulty;
        this.selectedArena = this.initialArena;
        this.selectedHns = this.initialHns;
        this.selectedSurf = this.initialSurf;
        this.selectedKz = this.initialKz;
        this.selectedMovementSettings = this.initialMovementSettings;
        this.selectedResetToStart = this.initialResetToStart;
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
        int panelWidth = 380;
        int panelHeight = 402;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        int fieldX = panelX + 142;
        int fieldWidth = panelWidth - 160;

        this.mapNameField = new TextFieldWidget(
                this.textRenderer,
                fieldX,
                panelY + 30,
                fieldWidth,
                18,
                Text.translatable("screen.minehop.map_creator.map_name")
        );
        this.mapNameField.setText(this.initialMapName);
        this.mapNameField.setMaxLength(128);
        this.addDrawableChild(this.mapNameField);

        this.difficultyButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.cycleDifficulty())
                        .dimensions(fieldX, panelY + 56, fieldWidth, 20)
                        .build()
        );
        this.arenaButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.toggleArena())
                        .dimensions(fieldX, panelY + 82, fieldWidth, 20)
                        .build()
        );
        this.hnsButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.toggleHns())
                        .dimensions(fieldX, panelY + 108, fieldWidth, 20)
                        .build()
        );
        this.surfButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.toggleSurf())
                        .dimensions(fieldX, panelY + 134, fieldWidth, 20)
                        .build()
        );
        this.kzButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.toggleKz())
                        .dimensions(fieldX, panelY + 160, fieldWidth, 20)
                        .build()
        );
        this.movementButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.openMovementSettings())
                        .dimensions(fieldX, panelY + 186, fieldWidth, 20)
                        .build()
        );

        this.resetTargetButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.toggleResetTarget())
                        .dimensions(fieldX, panelY + 212, fieldWidth, 20)
                        .build()
        );

        this.checkpointIndexField = new TextFieldWidget(
                this.textRenderer,
                fieldX,
                panelY + 238,
                fieldWidth,
                18,
                Text.translatable("screen.minehop.map_creator.checkpoint_index")
        );
        this.checkpointIndexField.setText(Integer.toString(this.initialCheckpointIndex));
        this.checkpointIndexField.setMaxLength(6);
        this.addDrawableChild(this.checkpointIndexField);

        int actionY = panelY + 272;
        int buttonWidth = 166;
        int leftX = panelX + 18;
        int rightX = panelX + panelWidth - buttonWidth - 18;

        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.minehop.map_creator.create_update"), button ->
                                this.sendAction(MapCreatorActionPayload.ACTION_CREATE_OR_UPDATE))
                        .dimensions(leftX, actionY, buttonWidth, 20)
                        .build()
        );
        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.minehop.map_creator.set_spawn"), button ->
                                this.sendAction(MapCreatorActionPayload.ACTION_SET_SPAWN))
                        .dimensions(rightX, actionY, buttonWidth, 20)
                        .build()
        );
        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.minehop.map_creator.add_checkpoint"), button ->
                                this.sendAction(MapCreatorActionPayload.ACTION_ADD_CHECKPOINT))
                        .dimensions(leftX, actionY + 24, buttonWidth, 20)
                        .build()
        );
        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.minehop.map_creator.add_start_zone"), button ->
                                this.sendAction(MapCreatorActionPayload.ACTION_ADD_START_ZONE))
                        .dimensions(rightX, actionY + 24, buttonWidth, 20)
                        .build()
        );
        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.minehop.map_creator.add_end_zone"), button ->
                                this.sendAction(MapCreatorActionPayload.ACTION_ADD_END_ZONE))
                        .dimensions(leftX, actionY + 48, buttonWidth, 20)
                        .build()
        );
        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.minehop.map_creator.add_reset_zone"), button ->
                                this.sendAction(MapCreatorActionPayload.ACTION_ADD_RESET_ZONE))
                        .dimensions(rightX, actionY + 48, buttonWidth, 20)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("gui.done"), button -> this.close())
                        .dimensions(panelX + 130, panelY + panelHeight - 24, 120, 20)
                        .build()
        );

        this.updateDifficultyButtonText();
        this.updateArenaButtonText();
        this.updateHnsButtonText();
        this.updateSurfButtonText();
        this.updateKzButtonText();
        this.updateMovementButtonText();
        this.updateResetTargetButtonText();
        this.updateCheckpointFieldState();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = 380;
        int panelHeight = 402;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int labelX = panelX + 18;
        int disabledColor = 0x808080;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0101010);
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF666666);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, panelY + 10, 0xFFFFFF);

        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.map_creator.map_name"), labelX, panelY + 35, 0xE0E0E0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.map_creator.difficulty"), labelX, panelY + 61, 0xE0E0E0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.map_creator.arena"), labelX, panelY + 87, 0xE0E0E0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.map_creator.hns"), labelX, panelY + 113, 0xE0E0E0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.map_creator.surf"), labelX, panelY + 139, 0xE0E0E0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.map_creator.kz"), labelX, panelY + 165, 0xE0E0E0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.map_creator.movement"), labelX, panelY + 191, 0xE0E0E0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.map_creator.reset_target"), labelX, panelY + 217, 0xE0E0E0);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.map_creator.checkpoint_index"),
                labelX,
                panelY + 243,
                this.selectedResetToStart ? disabledColor : 0xE0E0E0
        );
        context.drawTextWithShadow(this.textRenderer, Text.literal(this.getCheckpointCountHint()), labelX, panelY + 261, 0xB0B0B0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.map_creator.help.bounds"), labelX, panelY + 350, 0xC8C8C8);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.map_creator.help.reopen"), labelX, panelY + 362, 0xC8C8C8);

        super.render(context, mouseX, mouseY, delta);
    }

    private void cycleDifficulty() {
        this.selectedDifficulty = (this.selectedDifficulty + 1) % DIFFICULTY_NAMES.length;
        this.updateDifficultyButtonText();
    }

    private void toggleArena() {
        this.selectedArena = !this.selectedArena;
        this.updateArenaButtonText();
    }

    private void toggleHns() {
        this.selectedHns = !this.selectedHns;
        this.updateHnsButtonText();
    }

    private void toggleSurf() {
        this.selectedSurf = !this.selectedSurf;
        this.updateSurfButtonText();
    }

    private void toggleKz() {
        this.selectedKz = !this.selectedKz;
        this.updateKzButtonText();
    }

    private void openMovementSettings() {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new MovementSettingsScreen(this, this.selectedMovementSettings, values -> {
            this.selectedMovementSettings = values;
            this.updateMovementButtonText();
        }));
    }

    private void toggleResetTarget() {
        this.selectedResetToStart = !this.selectedResetToStart;
        this.updateResetTargetButtonText();
        this.updateCheckpointFieldState();
    }

    private void updateDifficultyButtonText() {
        if (this.difficultyButton == null) {
            return;
        }
        this.difficultyButton.setMessage(
                Text.literal(DIFFICULTY_NAMES[MathHelper.clamp(this.selectedDifficulty, 0, DIFFICULTY_NAMES.length - 1)])
        );
    }

    private void updateArenaButtonText() {
        if (this.arenaButton == null) {
            return;
        }
        this.arenaButton.setMessage(
                this.selectedArena
                        ? Text.translatable("screen.minehop.map_creator.toggle.on")
                        : Text.translatable("screen.minehop.map_creator.toggle.off")
        );
    }

    private void updateHnsButtonText() {
        if (this.hnsButton == null) {
            return;
        }
        this.hnsButton.setMessage(
                this.selectedHns
                        ? Text.translatable("screen.minehop.map_creator.toggle.on")
                        : Text.translatable("screen.minehop.map_creator.toggle.off")
        );
    }

    private void updateSurfButtonText() {
        if (this.surfButton == null) {
            return;
        }
        this.surfButton.setMessage(
                this.selectedSurf
                        ? Text.translatable("screen.minehop.map_creator.toggle.on")
                        : Text.translatable("screen.minehop.map_creator.toggle.off")
        );
    }

    private void updateKzButtonText() {
        if (this.kzButton == null) {
            return;
        }
        this.kzButton.setMessage(
                this.selectedKz
                        ? Text.translatable("screen.minehop.map_creator.toggle.on")
                        : Text.translatable("screen.minehop.map_creator.toggle.off")
        );
    }

    private void updateMovementButtonText() {
        if (this.movementButton == null) {
            return;
        }
        boolean override = this.selectedMovementSettings != null && this.selectedMovementSettings.overrideEnabled();
        this.movementButton.setMessage(
                override
                        ? Text.translatable("screen.minehop.map_creator.movement.custom")
                        : Text.translatable("screen.minehop.map_creator.movement.global")
        );
    }

    private void updateResetTargetButtonText() {
        if (this.resetTargetButton == null) {
            return;
        }
        this.resetTargetButton.setMessage(
                this.selectedResetToStart
                        ? Text.translatable("screen.minehop.map_creator.reset_target.start")
                        : Text.translatable("screen.minehop.map_creator.reset_target.checkpoint")
        );
    }

    private void updateCheckpointFieldState() {
        if (this.checkpointIndexField == null) {
            return;
        }
        boolean active = !this.selectedResetToStart;
        this.checkpointIndexField.active = active;
        this.checkpointIndexField.setEditable(active);
        if (active) {
            String raw = this.checkpointIndexField.getText();
            if (raw == null || raw.isBlank() || "0".equals(raw.trim())) {
                this.checkpointIndexField.setText("1");
            }
        } else if (this.checkpointIndexField.getText() == null || this.checkpointIndexField.getText().isBlank()) {
            this.checkpointIndexField.setText("0");
        }
    }

    private String getCheckpointCountHint() {
        int count = this.resolveCheckpointCount();
        return Text.translatable("screen.minehop.map_creator.checkpoint_count", count).getString();
    }

    private int resolveCheckpointCount() {
        if (this.mapNameField == null) {
            return 0;
        }
        String mapName = this.mapNameField.getText();
        if (mapName == null || mapName.isBlank()) {
            return 0;
        }
        DataManager.MapData mapData = null;
        for (DataManager.MapData existing : Minehop.mapList) {
            if (existing != null && existing.name != null && existing.name.equalsIgnoreCase(mapName.trim())) {
                mapData = existing;
                break;
            }
        }
        if (mapData == null || mapData.checkpointPositions == null) {
            return 0;
        }
        return Math.max(0, mapData.checkpointPositions.size());
    }

    private void sendAction(String action) {
        int checkpointIndex = this.selectedResetToStart ? 0 : this.parseCheckpointIndex();
        ClientPacketHandler.sendMapCreatorAction(
                action,
                this.mapNameField == null ? "" : this.mapNameField.getText(),
                this.selectedDifficulty,
                this.selectedArena,
                this.selectedHns,
                this.selectedSurf,
                this.selectedKz,
                this.selectedMovementSettings.overrideEnabled(),
                this.selectedMovementSettings.svFriction(),
                this.selectedMovementSettings.svAccelerate(),
                this.selectedMovementSettings.svAiraccelerate(),
                this.selectedMovementSettings.svMaxairspeed(),
                this.selectedMovementSettings.svJumpImpulse(),
                this.selectedMovementSettings.speedMul(),
                this.selectedMovementSettings.svGravity(),
                this.selectedMovementSettings.svStopspeed(),
                this.selectedMovementSettings.speedCoefficient(),
                this.selectedMovementSettings.speedCap(),
                this.selectedMovementSettings.autoStepUp(),
                this.selectedMovementSettings.cssCrouchJump(),
                this.selectedMovementSettings.disableSprint(),
                this.selectedMovementSettings.fallDamage(),
                checkpointIndex
        );
    }

    private int parseCheckpointIndex() {
        if (this.selectedResetToStart) {
            return 0;
        }
        if (this.checkpointIndexField == null) {
            return Math.max(1, this.initialCheckpointIndex);
        }
        String text = this.checkpointIndexField.getText();
        if (text == null || text.isBlank()) {
            return Math.max(1, this.initialCheckpointIndex);
        }
        try {
            return Math.max(1, Integer.parseInt(text.trim()));
        } catch (NumberFormatException ignored) {
            return Math.max(1, this.initialCheckpointIndex);
        }
    }
}
