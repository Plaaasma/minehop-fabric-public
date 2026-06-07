package net.nerdorg.minehop.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.nerdorg.minehop.networking.ClientPacketHandler;
import net.nerdorg.minehop.render.ModRenderLayer;
import net.nerdorg.minehop.util.SurfRampVisualStyle;
import org.joml.Matrix4f;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public class SurfStickSettingsScreen extends Screen {
    private static final String DEFAULT_TEXTURE_ID = "minecraft:smooth_stone";
    private static final int PREVIEW_LIGHT = 0x00F000F0;
    private static final double PREVIEW_UV_SPLIT_EPSILON = 1.0E-7D;
    private static final int BASE_PANEL_WIDTH = 320;
    private static final int BASE_PANEL_HEIGHT = 404;

    private final float initialWidth;
    private final float initialDrop;
    private final String initialTextureBlockId;
    private final int initialWireframeColor;
    private final int initialWireframeFillColor;
    private final int initialWireframeFillAlpha;

    private TextFieldWidget widthField;
    private TextFieldWidget dropField;
    private TextFieldWidget textureField;
    private TextFieldWidget wireColorField;
    private TextFieldWidget fillColorField;
    private TextFieldWidget fillAlphaField;
    private ButtonWidget sidesButton;
    private ButtonWidget curveFaceButton;
    private ButtonWidget modeButton;
    private ButtonWidget fillToggleButton;
    private ButtonWidget deleteButton;

    private String selectedRenderMode;
    private boolean selectedOneSided;
    private boolean selectedOutsideCurve;
    private boolean wireframeFillEnabled;
    private final boolean editingExisting;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int panelScrollOffset;
    private int panelMaxScroll;

    public SurfStickSettingsScreen(
            float width,
            float drop,
            String textureBlockId,
            boolean oneSided,
            boolean outsideCurve,
            String renderMode,
            int wireframeColor,
            boolean wireframeFill,
            int wireframeFillColor,
            int wireframeFillAlpha,
            boolean editingExisting
    ) {
        super(Text.translatable("screen.minehop.surf_stick_settings.title"));
        this.initialWidth = width;
        this.initialDrop = drop;
        this.initialTextureBlockId = textureBlockId == null || textureBlockId.isBlank()
                ? DEFAULT_TEXTURE_ID
                : textureBlockId;
        this.selectedOneSided = oneSided;
        this.selectedOutsideCurve = outsideCurve;
        this.selectedRenderMode = SurfRampVisualStyle.sanitizeMode(renderMode);
        this.initialWireframeColor = SurfRampVisualStyle.sanitizeColor(
                wireframeColor,
                SurfRampVisualStyle.DEFAULT_WIREFRAME_COLOR
        );
        this.wireframeFillEnabled = wireframeFill;
        this.initialWireframeFillColor = SurfRampVisualStyle.sanitizeColor(
                wireframeFillColor,
                SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_COLOR
        );
        this.initialWireframeFillAlpha = SurfRampVisualStyle.sanitizeAlpha(
                wireframeFillAlpha,
                SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA
        );
        this.editingExisting = editingExisting;
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
        this.updatePanelBounds();
        int panelWidth = this.panelWidth;
        int panelHeight = this.panelHeight;
        int panelX = this.panelX;
        int panelY = this.panelY;

        int fieldX = panelX + 124;
        int fieldWidth = Math.max(110, panelWidth - 142);

        int widthRow = panelY + 30;
        int dropRow = panelY + 56;
        int textureRow = panelY + 82;
        int sidesRow = panelY + 108;
        int curveRow = panelY + 134;
        int modeRow = panelY + 160;
        int wireColorRow = panelY + 186;
        int fillRow = panelY + 212;
        int fillAlphaRow = panelY + 238;

        this.widthField = new TextFieldWidget(this.textRenderer, fieldX, widthRow, fieldWidth, 18, Text.translatable("screen.minehop.surf_stick_settings.width"));
        this.widthField.setText(String.format(Locale.ROOT, "%.2f", this.initialWidth));
        this.widthField.setMaxLength(16);
        this.addDrawableChild(this.widthField);

        this.dropField = new TextFieldWidget(this.textRenderer, fieldX, dropRow, fieldWidth, 18, Text.translatable("screen.minehop.surf_stick_settings.drop"));
        this.dropField.setText(String.format(Locale.ROOT, "%.2f", this.initialDrop));
        this.dropField.setMaxLength(16);
        this.addDrawableChild(this.dropField);

        this.textureField = new TextFieldWidget(this.textRenderer, fieldX, textureRow, fieldWidth, 18, Text.translatable("screen.minehop.surf_stick_settings.texture"));
        this.textureField.setText(this.initialTextureBlockId);
        this.textureField.setMaxLength(128);
        this.addDrawableChild(this.textureField);

        this.sidesButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.toggleSides())
                        .dimensions(fieldX, sidesRow, fieldWidth, 20)
                        .build()
        );

        this.curveFaceButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.toggleCurveFace())
                        .dimensions(fieldX, curveRow, fieldWidth, 20)
                        .build()
        );

        this.modeButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.cycleRenderMode())
                        .dimensions(fieldX, modeRow, fieldWidth, 20)
                        .build()
        );

        this.wireColorField = new TextFieldWidget(
                this.textRenderer,
                fieldX,
                wireColorRow,
                fieldWidth,
                18,
                Text.translatable("screen.minehop.surf_stick_settings.wire_color")
        );
        this.wireColorField.setText(this.formatColor(this.initialWireframeColor));
        this.wireColorField.setMaxLength(9);
        this.addDrawableChild(this.wireColorField);

        this.fillToggleButton = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.toggleFill())
                        .dimensions(fieldX, fillRow, 82, 20)
                        .build()
        );

        this.fillColorField = new TextFieldWidget(
                this.textRenderer,
                fieldX + 88,
                fillRow + 1,
                Math.max(24, fieldWidth - 88),
                18,
                Text.translatable("screen.minehop.surf_stick_settings.fill_color")
        );
        this.fillColorField.setText(this.formatColor(this.initialWireframeFillColor));
        this.fillColorField.setMaxLength(9);
        this.addDrawableChild(this.fillColorField);

        this.fillAlphaField = new TextFieldWidget(
                this.textRenderer,
                fieldX,
                fillAlphaRow,
                fieldWidth,
                18,
                Text.translatable("screen.minehop.surf_stick_settings.fill_alpha")
        );
        this.fillAlphaField.setText(Integer.toString(this.initialWireframeFillAlpha));
        this.fillAlphaField.setMaxLength(3);
        this.addDrawableChild(this.fillAlphaField);

        int buttonY = panelY + panelHeight - 24;
        if (this.editingExisting) {
            int actionX = panelX + 16;
            int actionGap = 6;
            int actionWidth = Math.max(54, (panelWidth - 32 - actionGap * 2) / 3);
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> this.applyAndClose())
                    .dimensions(actionX, buttonY, actionWidth, 20)
                    .build());
            this.deleteButton = this.addDrawableChild(
                    ButtonWidget.builder(
                                    Text.translatable("screen.minehop.surf_stick_settings.delete"),
                                    button -> this.deleteAndClose()
                            )
                            .dimensions(actionX + actionWidth + actionGap, buttonY, actionWidth, 20)
                            .build()
            );
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> this.cancelAndClose())
                    .dimensions(actionX + (actionWidth + actionGap) * 2, buttonY, actionWidth, 20)
                    .build());
        } else {
            int actionGap = 10;
            int actionWidth = Math.max(80, (panelWidth - 40 - actionGap) / 2);
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> this.applyAndClose())
                    .dimensions(panelX + 20, buttonY, actionWidth, 20)
                    .build());
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> this.cancelAndClose())
                    .dimensions(panelX + 20 + actionWidth + actionGap, buttonY, actionWidth, 20)
                    .build());
        }

        this.updateSidesButtonText();
        this.updateCurveFaceButtonText();
        this.updateSideControls();
        this.updateModeButtonText();
        this.updateFillButtonText();
        this.updateWireControls();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        String widthText = this.widthField == null ? String.format(Locale.ROOT, "%.2f", this.initialWidth) : this.widthField.getText();
        String dropText = this.dropField == null ? String.format(Locale.ROOT, "%.2f", this.initialDrop) : this.dropField.getText();
        String textureText = this.textureField == null ? this.initialTextureBlockId : this.textureField.getText();
        String wireColorText = this.wireColorField == null ? this.formatColor(this.initialWireframeColor) : this.wireColorField.getText();
        String fillColorText = this.fillColorField == null ? this.formatColor(this.initialWireframeFillColor) : this.fillColorField.getText();
        String fillAlphaText = this.fillAlphaField == null ? Integer.toString(this.initialWireframeFillAlpha) : this.fillAlphaField.getText();
        super.resize(client, width, height);
        if (this.widthField != null) {
            this.widthField.setText(widthText);
        }
        if (this.dropField != null) {
            this.dropField.setText(dropText);
        }
        if (this.textureField != null) {
            this.textureField.setText(textureText);
        }
        if (this.wireColorField != null) {
            this.wireColorField.setText(wireColorText);
        }
        if (this.fillColorField != null) {
            this.fillColorField.setText(fillColorText);
        }
        if (this.fillAlphaField != null) {
            this.fillAlphaField.setText(fillAlphaText);
        }
        this.updateSidesButtonText();
        this.updateCurveFaceButtonText();
        this.updateSideControls();
        this.updateModeButtonText();
        this.updateFillButtonText();
        this.updateWireControls();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelWidth = this.panelWidth;
        int panelHeight = this.panelHeight;
        int panelX = this.panelX;
        int panelY = this.panelY;
        int labelX = panelX + 18;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0101010);
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF666666);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, panelY + 10, 0xFFFFFF);

        int normalLabelColor = 0xE0E0E0;
        int disabledLabelColor = 0x808080;
        boolean wireMode = SurfRampVisualStyle.MODE_WIREFRAME.equals(this.selectedRenderMode);

        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.surf_stick_settings.width"), labelX, panelY + 35, normalLabelColor);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.surf_stick_settings.drop"), labelX, panelY + 61, normalLabelColor);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.surf_stick_settings.texture"), labelX, panelY + 87, normalLabelColor);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.surf_stick_settings.sides"), labelX, panelY + 113, normalLabelColor);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.surf_stick_settings.curve_face"),
                labelX,
                panelY + 139,
                this.selectedOneSided ? normalLabelColor : disabledLabelColor
        );
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.surf_stick_settings.render_mode"), labelX, panelY + 165, normalLabelColor);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.surf_stick_settings.wire_color"),
                labelX,
                panelY + 191,
                wireMode ? normalLabelColor : disabledLabelColor
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.surf_stick_settings.fill"),
                labelX,
                panelY + 217,
                wireMode ? normalLabelColor : disabledLabelColor
        );
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("screen.minehop.surf_stick_settings.fill_alpha"),
                labelX,
                panelY + 243,
                wireMode && this.wireframeFillEnabled ? normalLabelColor : disabledLabelColor
        );
        context.drawTextWithShadow(this.textRenderer, Text.translatable("screen.minehop.surf_stick_settings.preview"), labelX, panelY + 264, 0xFFFFFF);

        int previewY = panelY + 276;
        int previewBottom = panelY + panelHeight - 30;
        int previewHeight = Math.max(52, previewBottom - previewY);
        this.drawRampPreview(context, panelX + 18, previewY, panelWidth - 36, previewHeight);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (this.panelMaxScroll <= 0 || Math.abs(verticalAmount) < 1.0E-6D) {
            return false;
        }

        int step = 26;
        int nextOffset = MathHelper.clamp(
                this.panelScrollOffset - (int) Math.signum(verticalAmount) * step,
                0,
                this.panelMaxScroll
        );
        if (nextOffset == this.panelScrollOffset) {
            return false;
        }

        this.panelScrollOffset = nextOffset;
        if (this.client != null) {
            this.resize(this.client, this.width, this.height);
        }
        return true;
    }

    private void updatePanelBounds() {
        int maxPanelWidth = Math.max(200, this.width - 8);
        int availableHeight = Math.max(120, this.height - 8);
        int desiredWidth = Math.min(460, Math.max(BASE_PANEL_WIDTH, this.width - 24));

        this.panelWidth = Math.min(desiredWidth, maxPanelWidth);
        this.panelHeight = BASE_PANEL_HEIGHT;
        this.panelMaxScroll = Math.max(0, this.panelHeight - availableHeight);
        this.panelScrollOffset = MathHelper.clamp(this.panelScrollOffset, 0, this.panelMaxScroll);

        this.panelX = (this.width - this.panelWidth) / 2;
        if (this.panelMaxScroll > 0) {
            this.panelY = 4 - this.panelScrollOffset;
        } else {
            this.panelY = (this.height - this.panelHeight) / 2;
        }
    }

    private void toggleSides() {
        this.selectedOneSided = !this.selectedOneSided;
        this.updateSidesButtonText();
        this.updateSideControls();
    }

    private void toggleCurveFace() {
        this.selectedOutsideCurve = !this.selectedOutsideCurve;
        this.updateCurveFaceButtonText();
    }

    private void updateSidesButtonText() {
        if (this.sidesButton == null) {
            return;
        }
        Text modeLabel = this.selectedOneSided
                ? Text.translatable("screen.minehop.surf_stick_settings.sides.one")
                : Text.translatable("screen.minehop.surf_stick_settings.sides.two");
        this.sidesButton.setMessage(modeLabel);
    }

    private void updateCurveFaceButtonText() {
        if (this.curveFaceButton == null) {
            return;
        }
        Text faceLabel = this.selectedOutsideCurve
                ? Text.translatable("screen.minehop.surf_stick_settings.curve_face.outside")
                : Text.translatable("screen.minehop.surf_stick_settings.curve_face.inside");
        this.curveFaceButton.setMessage(faceLabel);
    }

    private void updateSideControls() {
        if (this.curveFaceButton != null) {
            this.curveFaceButton.active = this.selectedOneSided;
        }
        this.updateCurveFaceButtonText();
    }

    private void cycleRenderMode() {
        this.selectedRenderMode = SurfRampVisualStyle.MODE_WIREFRAME.equals(this.selectedRenderMode)
                ? SurfRampVisualStyle.MODE_BLOCK
                : SurfRampVisualStyle.MODE_WIREFRAME;
        this.updateModeButtonText();
        this.updateWireControls();
    }

    private void toggleFill() {
        this.wireframeFillEnabled = !this.wireframeFillEnabled;
        this.updateFillButtonText();
        this.updateWireControls();
    }

    private void updateModeButtonText() {
        if (this.modeButton == null) {
            return;
        }
        Text modeLabel = SurfRampVisualStyle.MODE_WIREFRAME.equals(this.selectedRenderMode)
                ? Text.translatable("screen.minehop.surf_stick_settings.render_mode.wireframe")
                : Text.translatable("screen.minehop.surf_stick_settings.render_mode.block");
        this.modeButton.setMessage(modeLabel);
    }

    private void updateFillButtonText() {
        if (this.fillToggleButton == null) {
            return;
        }
        Text fillLabel = this.wireframeFillEnabled
                ? Text.translatable("screen.minehop.surf_stick_settings.fill.on")
                : Text.translatable("screen.minehop.surf_stick_settings.fill.off");
        this.fillToggleButton.setMessage(fillLabel);
    }

    private void updateWireControls() {
        boolean wireMode = SurfRampVisualStyle.MODE_WIREFRAME.equals(this.selectedRenderMode);
        if (this.wireColorField != null) {
            this.wireColorField.setEditable(wireMode);
            this.wireColorField.active = wireMode;
        }
        if (this.fillToggleButton != null) {
            this.fillToggleButton.active = wireMode;
        }
        if (this.fillColorField != null) {
            boolean fillColorActive = wireMode && this.wireframeFillEnabled;
            this.fillColorField.setEditable(fillColorActive);
            this.fillColorField.active = fillColorActive;
        }
        if (this.fillAlphaField != null) {
            boolean fillAlphaActive = wireMode && this.wireframeFillEnabled;
            this.fillAlphaField.setEditable(fillAlphaActive);
            this.fillAlphaField.active = fillAlphaActive;
        }
    }

    private void drawRampPreview(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, 0xCC050505);
        context.drawBorder(x, y, width, height, 0xFF4A4A4A);

        double rampWidth = MathHelper.clamp(this.parseDouble(this.widthField, this.initialWidth), 0.15D, 64.0D);
        double rampDrop = MathHelper.clamp(this.parseDouble(this.dropField, this.initialDrop), 0.1D, 64.0D);
        float visualLength = 6.0F;
        float visualWidth = (float) MathHelper.clamp(0.30D + rampWidth * 0.34D, 0.30D, 2.60D);
        float visualDrop = (float) MathHelper.clamp(0.14D + rampDrop * 0.30D, 0.14D, 2.40D);

        Sprite sprite = this.resolvePreviewSprite();
        int wireColor = this.parseHexColor(this.wireColorField, this.initialWireframeColor);
        int fillColor = this.parseHexColor(this.fillColorField, this.initialWireframeFillColor);
        int fillAlpha = this.parseFillAlpha(this.fillAlphaField, this.initialWireframeFillAlpha);
        boolean wireMode = SurfRampVisualStyle.MODE_WIREFRAME.equals(this.selectedRenderMode);
        boolean oneSided = this.selectedOneSided;
        int sideSign = this.selectedOutsideCurve ? -1 : 1;

        MatrixStack matrices = context.getMatrices();
        context.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        matrices.push();
        matrices.translate(x + width * 0.50F, y + height * 0.62F, 160.0F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-26.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-46.0F));
        float scale = Math.min(width, height) * 0.13F;
        matrices.scale(scale, -scale, scale);

        context.draw();
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        context.draw(vertexConsumers -> {
            if (wireMode) {
                if (this.wireframeFillEnabled) {
                    VertexConsumer fillConsumer = vertexConsumers.getBuffer(ModRenderLayer.getTranslucentColorQuads());
                    this.renderStraightRampPreviewFill(matrices, fillConsumer, visualLength, visualWidth, visualDrop, oneSided, sideSign, fillColor, fillAlpha);
                }
                VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
                this.renderStraightRampPreviewWire(matrices, lineConsumer, visualLength, visualWidth, visualDrop, oneSided, sideSign, wireColor, 255);
            } else {
                VertexConsumer texturedConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
                this.renderStraightRampPreviewMesh(matrices, texturedConsumer, sprite, visualLength, visualWidth, visualDrop, oneSided, sideSign);
            }
        });
        context.draw();

        RenderSystem.disableDepthTest();
        matrices.pop();
        context.disableScissor();
    }

    private void applyAndClose() {
        double width = MathHelper.clamp(this.parseDouble(this.widthField, this.initialWidth), 0.15D, 64.0D);
        double drop = MathHelper.clamp(this.parseDouble(this.dropField, this.initialDrop), 0.1D, 64.0D);
        String textureId = this.resolveTextureId();
        int wireColor = this.parseHexColor(this.wireColorField, this.initialWireframeColor);
        int fillColor = this.parseHexColor(this.fillColorField, this.initialWireframeFillColor);
        int fillAlpha = this.parseFillAlpha(this.fillAlphaField, this.initialWireframeFillAlpha);
        ClientPacketHandler.sendSurfStickSettings(
                width,
                drop,
                textureId,
                this.selectedOneSided,
                this.selectedOutsideCurve,
                this.selectedRenderMode,
                wireColor,
                this.wireframeFillEnabled,
                fillColor,
                fillAlpha
        );
        this.close();
    }

    private void cancelAndClose() {
        ClientPacketHandler.sendSurfStickCancel();
        this.close();
    }

    private void deleteAndClose() {
        ClientPacketHandler.sendSurfStickDelete();
        this.close();
    }

    private double parseDouble(TextFieldWidget field, double fallback) {
        if (field == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String resolveTextureId() {
        String raw = this.textureField == null ? this.initialTextureBlockId : this.textureField.getText().trim();
        Identifier parsed = Identifier.tryParse(raw);
        if (parsed == null || !Registries.BLOCK.containsId(parsed)) {
            return DEFAULT_TEXTURE_ID;
        }
        Block block = Registries.BLOCK.get(parsed);
        if (block == Blocks.AIR) {
            return DEFAULT_TEXTURE_ID;
        }
        return parsed.toString();
    }

    private Sprite resolvePreviewSprite() {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockState state = this.resolveTextureBlockState();
        BakedModel model = client.getBlockRenderManager().getModel(state);
        Sprite sprite = model.getParticleSprite();
        if (sprite != null) {
            return sprite;
        }
        return client.getBlockRenderManager().getModel(Blocks.SMOOTH_STONE.getDefaultState()).getParticleSprite();
    }

    private BlockState resolveTextureBlockState() {
        Identifier parsed = Identifier.tryParse(this.resolveTextureId());
        if (parsed == null || !Registries.BLOCK.containsId(parsed)) {
            return Blocks.SMOOTH_STONE.getDefaultState();
        }
        Block block = Registries.BLOCK.get(parsed);
        if (block == Blocks.AIR) {
            return Blocks.SMOOTH_STONE.getDefaultState();
        }
        return block.getDefaultState();
    }

    private String formatColor(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private int parseHexColor(TextFieldWidget field, int fallback) {
        if (field == null) {
            return fallback;
        }
        return parseHexColor(field.getText(), fallback);
    }

    private int parseHexColor(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        } else if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }
        if (value.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int parseFillAlpha(TextFieldWidget field, int fallback) {
        if (field == null) {
            return SurfRampVisualStyle.sanitizeAlpha(fallback, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA);
        }
        String raw = field.getText();
        if (raw == null) {
            return SurfRampVisualStyle.sanitizeAlpha(fallback, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA);
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return SurfRampVisualStyle.sanitizeAlpha(parsed, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA);
        } catch (NumberFormatException ignored) {
            return SurfRampVisualStyle.sanitizeAlpha(fallback, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA);
        }
    }

    private void renderStraightRampPreviewMesh(
            MatrixStack matrices,
            VertexConsumer consumer,
            Sprite sprite,
            float length,
            float width,
            float drop,
            boolean oneSided,
            int sideSign
    ) {
        if (oneSided) {
            PreviewRampGeometryOneSided geometry = new PreviewRampGeometryOneSided(length, width, drop, sideSign);
            this.drawTiledTexturedQuad(matrices, consumer, geometry.startTop, geometry.endTop, geometry.endOuter, geometry.startOuter, sprite, 255, 0.0D, length, 0.0F, 1.0F);
            this.drawTiledTexturedQuad(matrices, consumer, geometry.startInner, geometry.startTop, geometry.endTop, geometry.endInner, sprite, 212, 0.0D, length, 0.0F, 1.0F);
            this.drawTiledTexturedQuad(matrices, consumer, geometry.startOuter, geometry.endOuter, geometry.endInner, geometry.startInner, sprite, 176, 0.0D, length, 0.0F, 1.0F);
            this.drawDoubleSidedTexturedTriangle(matrices, consumer, geometry.startTop, geometry.startOuter, geometry.startInner, sprite, 228);
            this.drawDoubleSidedTexturedTriangle(matrices, consumer, geometry.endTop, geometry.endInner, geometry.endOuter, sprite, 228);
            return;
        }

        PreviewRampGeometryTwoSided geometry = new PreviewRampGeometryTwoSided(length, width, drop);
        this.drawTiledTexturedQuad(matrices, consumer, geometry.startTop, geometry.endTop, geometry.endLeft, geometry.startLeft, sprite, 255, 0.0D, length, 0.0F, 1.0F);
        this.drawTiledTexturedQuad(matrices, consumer, geometry.startRight, geometry.endRight, geometry.endTop, geometry.startTop, sprite, 238, 0.0D, length, 0.0F, 1.0F);
        this.drawTiledTexturedQuad(matrices, consumer, geometry.startLeft, geometry.endLeft, geometry.endRight, geometry.startRight, sprite, 176, 0.0D, length, 0.0F, 1.0F);
        this.drawDoubleSidedTexturedTriangle(matrices, consumer, geometry.startTop, geometry.startRight, geometry.startLeft, sprite, 228);
        this.drawDoubleSidedTexturedTriangle(matrices, consumer, geometry.endTop, geometry.endLeft, geometry.endRight, sprite, 228);
    }

    private void renderStraightRampPreviewFill(
            MatrixStack matrices,
            VertexConsumer consumer,
            float length,
            float width,
            float drop,
            boolean oneSided,
            int sideSign,
            int rgb,
            int alpha
    ) {
        int r = SurfRampVisualStyle.red(rgb);
        int g = SurfRampVisualStyle.green(rgb);
        int b = SurfRampVisualStyle.blue(rgb);

        if (oneSided) {
            PreviewRampGeometryOneSided geometry = new PreviewRampGeometryOneSided(length, width, drop, sideSign);
            this.drawColoredQuadDoubleSided(matrices, consumer, geometry.startTop, geometry.endTop, geometry.endOuter, geometry.startOuter, r, g, b, alpha);
            this.drawColoredQuadDoubleSided(matrices, consumer, geometry.startInner, geometry.startTop, geometry.endTop, geometry.endInner, r, g, b, alpha);
            this.drawColoredQuadDoubleSided(matrices, consumer, geometry.startOuter, geometry.endOuter, geometry.endInner, geometry.startInner, r, g, b, alpha);
            this.drawColoredTriangleDoubleSided(matrices, consumer, geometry.startTop, geometry.startOuter, geometry.startInner, r, g, b, alpha);
            this.drawColoredTriangleDoubleSided(matrices, consumer, geometry.endTop, geometry.endInner, geometry.endOuter, r, g, b, alpha);
            return;
        }

        PreviewRampGeometryTwoSided geometry = new PreviewRampGeometryTwoSided(length, width, drop);
        this.drawColoredQuadDoubleSided(matrices, consumer, geometry.startTop, geometry.endTop, geometry.endLeft, geometry.startLeft, r, g, b, alpha);
        this.drawColoredQuadDoubleSided(matrices, consumer, geometry.startRight, geometry.endRight, geometry.endTop, geometry.startTop, r, g, b, alpha);
        this.drawColoredQuadDoubleSided(matrices, consumer, geometry.startLeft, geometry.endLeft, geometry.endRight, geometry.startRight, r, g, b, alpha);
        this.drawColoredTriangleDoubleSided(matrices, consumer, geometry.startTop, geometry.startRight, geometry.startLeft, r, g, b, alpha);
        this.drawColoredTriangleDoubleSided(matrices, consumer, geometry.endTop, geometry.endLeft, geometry.endRight, r, g, b, alpha);
    }

    private void renderStraightRampPreviewWire(
            MatrixStack matrices,
            VertexConsumer consumer,
            float length,
            float width,
            float drop,
            boolean oneSided,
            int sideSign,
            int rgb,
            int alpha
    ) {
        int r = SurfRampVisualStyle.red(rgb);
        int g = SurfRampVisualStyle.green(rgb);
        int b = SurfRampVisualStyle.blue(rgb);

        if (oneSided) {
            PreviewRampGeometryOneSided geometry = new PreviewRampGeometryOneSided(length, width, drop, sideSign);
            this.drawColoredLine(matrices, consumer, geometry.startTop, geometry.endTop, r, g, b, alpha);
            this.drawColoredLine(matrices, consumer, geometry.startOuter, geometry.endOuter, r, g, b, alpha);
            this.drawColoredLine(matrices, consumer, geometry.startInner, geometry.endInner, r, g, b, alpha);

            int ribs = 6;
            for (int i = 0; i <= ribs; i++) {
                double t = (double) i / (double) ribs;
                PreviewVertex top = this.lerp(geometry.startTop, geometry.endTop, t);
                PreviewVertex outer = this.lerp(geometry.startOuter, geometry.endOuter, t);
                PreviewVertex inner = this.lerp(geometry.startInner, geometry.endInner, t);
                this.drawColoredLine(matrices, consumer, top, outer, r, g, b, alpha);
                this.drawColoredLine(matrices, consumer, top, inner, r, g, b, alpha);
                this.drawColoredLine(matrices, consumer, outer, inner, r, g, b, alpha);
            }
            return;
        }

        PreviewRampGeometryTwoSided geometry = new PreviewRampGeometryTwoSided(length, width, drop);
        this.drawColoredLine(matrices, consumer, geometry.startTop, geometry.endTop, r, g, b, alpha);
        this.drawColoredLine(matrices, consumer, geometry.startLeft, geometry.endLeft, r, g, b, alpha);
        this.drawColoredLine(matrices, consumer, geometry.startRight, geometry.endRight, r, g, b, alpha);

        int ribs = 6;
        for (int i = 0; i <= ribs; i++) {
            double t = (double) i / (double) ribs;
            PreviewVertex top = this.lerp(geometry.startTop, geometry.endTop, t);
            PreviewVertex left = this.lerp(geometry.startLeft, geometry.endLeft, t);
            PreviewVertex right = this.lerp(geometry.startRight, geometry.endRight, t);
            this.drawColoredLine(matrices, consumer, top, left, r, g, b, alpha);
            this.drawColoredLine(matrices, consumer, top, right, r, g, b, alpha);
            this.drawColoredLine(matrices, consumer, left, right, r, g, b, alpha);
        }
    }

    private void drawTiledTexturedQuad(
            MatrixStack matrices,
            VertexConsumer consumer,
            PreviewVertex a,
            PreviewVertex b,
            PreviewVertex c,
            PreviewVertex d,
            Sprite sprite,
            int shade,
            double u0,
            double u1,
            float v0,
            float v1
    ) {
        if (u1 <= u0 + PREVIEW_UV_SPLIT_EPSILON) {
            this.drawTexturedQuadSegment(matrices, consumer, a, b, c, d, sprite, shade, u0, u1, v0, v1);
            return;
        }

        PreviewVertex currentA = a;
        PreviewVertex currentB = b;
        PreviewVertex currentC = c;
        PreviewVertex currentD = d;
        double currentU0 = u0;
        double currentU1 = u1;

        while (currentU1 > currentU0 + PREVIEW_UV_SPLIT_EPSILON) {
            double nextBoundary = Math.floor(currentU0) + 1.0D;
            if (currentU1 <= nextBoundary + PREVIEW_UV_SPLIT_EPSILON) {
                this.drawTexturedQuadSegment(
                        matrices,
                        consumer,
                        currentA,
                        currentB,
                        currentC,
                        currentD,
                        sprite,
                        shade,
                        currentU0,
                        currentU1,
                        v0,
                        v1
                );
                break;
            }

            double splitT = (nextBoundary - currentU0) / (currentU1 - currentU0);
            PreviewVertex splitTop = this.lerp(currentA, currentB, splitT);
            PreviewVertex splitBottom = this.lerp(currentD, currentC, splitT);
            this.drawTexturedQuadSegment(
                    matrices,
                    consumer,
                    currentA,
                    splitTop,
                    splitBottom,
                    currentD,
                    sprite,
                    shade,
                    currentU0,
                    nextBoundary,
                    v0,
                    v1
            );

            currentA = splitTop;
            currentD = splitBottom;
            currentU0 = nextBoundary;
        }
    }

    private void drawTexturedQuadSegment(
            MatrixStack matrices,
            VertexConsumer consumer,
            PreviewVertex a,
            PreviewVertex b,
            PreviewVertex c,
            PreviewVertex d,
            Sprite sprite,
            int shade,
            double u0,
            double u1,
            float v0,
            float v1
    ) {
        double tileBase = Math.floor(u0);
        float localU0 = MathHelper.clamp((float) (u0 - tileBase), 0.0F, 1.0F);
        float localU1 = MathHelper.clamp((float) (u1 - tileBase), 0.0F, 1.0F);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float[] normal = this.computeNormal(a, b, d);
        this.putTexturedVertex(consumer, matrix, a, localU0, v0, sprite, shade, normal);
        this.putTexturedVertex(consumer, matrix, b, localU1, v0, sprite, shade, normal);
        this.putTexturedVertex(consumer, matrix, c, localU1, v1, sprite, shade, normal);
        this.putTexturedVertex(consumer, matrix, d, localU0, v1, sprite, shade, normal);
    }

    private void drawDoubleSidedTexturedTriangle(
            MatrixStack matrices,
            VertexConsumer consumer,
            PreviewVertex a,
            PreviewVertex b,
            PreviewVertex c,
            Sprite sprite,
            int shade
    ) {
        this.drawTexturedTriangle(matrices, consumer, a, b, c, sprite, shade);
        this.drawTexturedTriangle(matrices, consumer, a, c, b, sprite, shade);
    }

    private void drawTexturedTriangle(
            MatrixStack matrices,
            VertexConsumer consumer,
            PreviewVertex a,
            PreviewVertex b,
            PreviewVertex c,
            Sprite sprite,
            int shade
    ) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float[] normal = this.computeNormal(a, b, c);
        this.putTexturedVertex(consumer, matrix, a, 0.0F, 0.0F, sprite, shade, normal);
        this.putTexturedVertex(consumer, matrix, b, 1.0F, 0.0F, sprite, shade, normal);
        this.putTexturedVertex(consumer, matrix, c, 0.5F, 1.0F, sprite, shade, normal);
        this.putTexturedVertex(consumer, matrix, c, 0.5F, 1.0F, sprite, shade, normal);
    }

    private void putTexturedVertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            PreviewVertex pos,
            float u,
            float v,
            Sprite sprite,
            int shade,
            float[] normal
    ) {
        float wrappedU = MathHelper.clamp(u, 0.0F, 1.0F);
        float wrappedV = MathHelper.clamp(v, 0.0F, 1.0F);
        float atlasU = MathHelper.lerp(wrappedU, sprite.getMinU(), sprite.getMaxU());
        float atlasV = MathHelper.lerp(wrappedV, sprite.getMinV(), sprite.getMaxV());
        consumer.vertex(matrix, pos.x, pos.y, pos.z)
                .color(shade, shade, shade, 255)
                .texture(atlasU, atlasV)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(PREVIEW_LIGHT)
                .normal(normal[0], normal[1], normal[2]);
    }

    private void drawColoredQuadDoubleSided(
            MatrixStack matrices,
            VertexConsumer consumer,
            PreviewVertex a,
            PreviewVertex b,
            PreviewVertex c,
            PreviewVertex d,
            int r,
            int g,
            int bColor,
            int alpha
    ) {
        this.drawColoredQuad(matrices, consumer, a, b, c, d, r, g, bColor, alpha);
        this.drawColoredQuad(matrices, consumer, a, d, c, b, r, g, bColor, alpha);
    }

    private void drawColoredQuad(
            MatrixStack matrices,
            VertexConsumer consumer,
            PreviewVertex a,
            PreviewVertex b,
            PreviewVertex c,
            PreviewVertex d,
            int r,
            int g,
            int bColor,
            int alpha
    ) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        this.putColorVertex(consumer, matrix, a, r, g, bColor, alpha);
        this.putColorVertex(consumer, matrix, b, r, g, bColor, alpha);
        this.putColorVertex(consumer, matrix, c, r, g, bColor, alpha);
        this.putColorVertex(consumer, matrix, d, r, g, bColor, alpha);
    }

    private void drawColoredTriangleDoubleSided(
            MatrixStack matrices,
            VertexConsumer consumer,
            PreviewVertex a,
            PreviewVertex b,
            PreviewVertex c,
            int r,
            int g,
            int bColor,
            int alpha
    ) {
        this.drawColoredTriangle(matrices, consumer, a, b, c, r, g, bColor, alpha);
        this.drawColoredTriangle(matrices, consumer, a, c, b, r, g, bColor, alpha);
    }

    private void drawColoredTriangle(
            MatrixStack matrices,
            VertexConsumer consumer,
            PreviewVertex a,
            PreviewVertex b,
            PreviewVertex c,
            int r,
            int g,
            int bColor,
            int alpha
    ) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        this.putColorVertex(consumer, matrix, a, r, g, bColor, alpha);
        this.putColorVertex(consumer, matrix, b, r, g, bColor, alpha);
        this.putColorVertex(consumer, matrix, c, r, g, bColor, alpha);
        this.putColorVertex(consumer, matrix, c, r, g, bColor, alpha);
    }

    private void putColorVertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            PreviewVertex pos,
            int r,
            int g,
            int b,
            int alpha
    ) {
        consumer.vertex(matrix, pos.x, pos.y, pos.z).color(r, g, b, alpha);
    }

    private void drawColoredLine(
            MatrixStack matrices,
            VertexConsumer consumer,
            PreviewVertex a,
            PreviewVertex b,
            int r,
            int g,
            int bColor,
            int alpha
    ) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        consumer.vertex(matrix, a.x, a.y, a.z).color(r, g, bColor, alpha).normal(0.0F, 1.0F, 0.0F);
        consumer.vertex(matrix, b.x, b.y, b.z).color(r, g, bColor, alpha).normal(0.0F, 1.0F, 0.0F);
    }

    private float[] computeNormal(PreviewVertex a, PreviewVertex b, PreviewVertex c) {
        float ux = b.x - a.x;
        float uy = b.y - a.y;
        float uz = b.z - a.z;
        float vx = c.x - a.x;
        float vy = c.y - a.y;
        float vz = c.z - a.z;

        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float length = MathHelper.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-6F) {
            return new float[]{0.0F, 1.0F, 0.0F};
        }
        return new float[]{nx / length, ny / length, nz / length};
    }

    private PreviewVertex lerp(PreviewVertex from, PreviewVertex to, double t) {
        return new PreviewVertex(
                (float) MathHelper.lerp((float) t, from.x, to.x),
                (float) MathHelper.lerp((float) t, from.y, to.y),
                (float) MathHelper.lerp((float) t, from.z, to.z)
        );
    }

    private static final class PreviewRampGeometryOneSided {
        private final PreviewVertex startTop;
        private final PreviewVertex endTop;
        private final PreviewVertex startOuter;
        private final PreviewVertex endOuter;
        private final PreviewVertex startInner;
        private final PreviewVertex endInner;

        private PreviewRampGeometryOneSided(float length, float width, float drop, int sideSign) {
            float halfLength = length * 0.5F;

            float sx = -halfLength;
            float ex = halfLength;
            float topY = drop;
            float baseY = 0.0F;
            float innerZ = 0.0F;
            float outerZ = sideSign >= 0 ? width : -width;

            this.startTop = new PreviewVertex(sx, topY, innerZ);
            this.endTop = new PreviewVertex(ex, topY, innerZ);
            this.startOuter = new PreviewVertex(sx, baseY, outerZ);
            this.endOuter = new PreviewVertex(ex, baseY, outerZ);
            this.startInner = new PreviewVertex(sx, baseY, innerZ);
            this.endInner = new PreviewVertex(ex, baseY, innerZ);
        }
    }

    private static final class PreviewRampGeometryTwoSided {
        private final PreviewVertex startTop;
        private final PreviewVertex endTop;
        private final PreviewVertex startLeft;
        private final PreviewVertex endLeft;
        private final PreviewVertex startRight;
        private final PreviewVertex endRight;

        private PreviewRampGeometryTwoSided(float length, float width, float drop) {
            float halfLength = length * 0.5F;

            float sx = -halfLength;
            float ex = halfLength;
            float topY = drop;
            float baseY = 0.0F;
            float leftZ = width;
            float rightZ = -width;

            this.startTop = new PreviewVertex(sx, topY, 0.0F);
            this.endTop = new PreviewVertex(ex, topY, 0.0F);
            this.startLeft = new PreviewVertex(sx, baseY, leftZ);
            this.endLeft = new PreviewVertex(ex, baseY, leftZ);
            this.startRight = new PreviewVertex(sx, baseY, rightZ);
            this.endRight = new PreviewVertex(ex, baseY, rightZ);
        }
    }

    private record PreviewVertex(float x, float y, float z) {
    }
}
