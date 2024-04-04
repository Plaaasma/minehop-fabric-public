package net.nerdorg.minehop.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.screen.widget.MapListWidget;

@Environment(EnvType.CLIENT)
public class SelectMapScreen extends Screen {
    private MapListWidget listWidget;

    public SelectMapScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        listWidget = new MapListWidget(this.client, this.width, this.height, 32, this.height - 32);
        for (DataManager.RecordData recordData : MinehopClient.clientRecords) {
            listWidget.addEntry(recordData);
        }
        this.addSelectableChild(listWidget);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        listWidget.render(context, mouseX, mouseY, delta);

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawCenteredTextWithShadow(textRenderer, "Map Selection", this.width / 2, 16, Formatting.WHITE.getColorValue());

        super.render(context, mouseX, mouseY, delta);
    }
}
