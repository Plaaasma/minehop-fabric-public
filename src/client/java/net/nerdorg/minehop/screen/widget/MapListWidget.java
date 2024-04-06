package net.nerdorg.minehop.screen.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.nerdorg.minehop.data.DataManager;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MapListWidget extends EntryListWidget<MapListWidget.MapEntry> {

    public MapListWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
        super(client, width, height, top, itemHeight);
    }

    public void addEntry(DataManager.RecordData recordData) {
        this.addEntry(new MapEntry(recordData));
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }

    public static class MapEntry extends ElementListWidget.Entry<MapEntry> {
        private final DataManager.RecordData recordData;

        private final ButtonWidget mapButtonWidget;

        public MapEntry(DataManager.RecordData recordData) {
            this.recordData = recordData;

            this.mapButtonWidget = ButtonWidget.builder(Text.literal(recordData.map_name), button -> {})
                    .tooltip(Tooltip.of(Text.literal("Record holder: " + recordData.name + " Time: " + String.format("%.5f", recordData.time)).formatted(Formatting.RED)))
                    .build();

            this.mapButtonWidget.visible = true;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

            // Render the object's name and description here
            this.mapButtonWidget.setWidth(entryWidth);
            this.mapButtonWidget.setHeight(entryHeight);
            this.mapButtonWidget.setPosition(x, y);
//            this.mapButtonWidget.setTooltip(Tooltip.of(Text.literal("Record holder: " + recordData.name + " Time: " + String.format("%5f", recordData.time)).withColor(Formatting.RED.getColorValue())));
//            this.mapButtonWidget.setTooltipDelay(0);
            this.mapButtonWidget.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.mapButtonWidget.isMouseOver(mouseX, mouseY)) {
                MinecraftClient.getInstance().setScreen(null);
                MinecraftClient.getInstance().getNetworkHandler().sendCommand("map " + this.recordData.map_name);
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends Element> children() {
            return Collections.emptyList();
        }
    }
}
