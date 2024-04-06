package net.nerdorg.minehop.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.screen.widget.MapListWidget;

@Environment(EnvType.CLIENT)
public class SelectMapScreen extends Screen {
    private MapListWidget listWidget;
    private TextFieldWidget  textFieldWidget;
    private String lastFieldText = "";


    public SelectMapScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.textFieldWidget = new TextFieldWidget(this.client.textRenderer, 128, 14, Text.literal("Map Filter"));
        this.textFieldWidget.setX((this.width / 2) - (this.textFieldWidget.getWidth() / 2));
        this.textFieldWidget.setY(16);
        this.addSelectableChild(this.textFieldWidget);

        this.listWidget = new MapListWidget(this.client, this.width, this.height - 32, 32, 20);
        for (DataManager.RecordData recordData : MinehopClient.clientRecords) {
            if (!recordData.map_name.equals("spawn")) {
                this.listWidget.addEntry(recordData);
            }
        }
        this.addSelectableChild(this.listWidget);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        this.textFieldWidget.render(context, mouseX, mouseY, delta);
        String fieldText = this.textFieldWidget.getText();

        if (!fieldText.equals(this.lastFieldText)) {
            MapListWidget newListWidget = new MapListWidget(this.client, this.width, this.height - 32, 32, 20);
            if (!fieldText.equals("")) {
                for (DataManager.RecordData recordData : MinehopClient.clientRecords) {
                    if (!recordData.map_name.equals("spawn") && recordData.map_name.contains(fieldText)) {
                        newListWidget.addEntry(recordData);
                    }
                }
            } else {
                for (DataManager.RecordData recordData : MinehopClient.clientRecords) {
                    if (!recordData.map_name.equals("spawn")) {
                        newListWidget.addEntry(recordData);
                    }
                }
            }
            this.remove(this.listWidget);
            this.listWidget = newListWidget;
            this.addSelectableChild(this.listWidget);
            this.lastFieldText = fieldText;
        }

        this.listWidget.render(context, mouseX, mouseY, delta);

        TextRenderer textRenderer = this.client.textRenderer;
        context.drawCenteredTextWithShadow(textRenderer, "Map Selection", this.width / 2, 4, Formatting.WHITE.getColorValue());

        super.render(context, mouseX, mouseY, delta);
    }
}
