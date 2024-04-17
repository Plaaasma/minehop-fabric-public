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
        super(client, width, height, top, height + 32, itemHeight);
    }

    public void addEntry(DataManager.RecordData recordData, double avgTime, boolean minigame, int player_count, int difficulty) {
        this.addEntry(new MapEntry(recordData, avgTime, minigame, player_count, difficulty));
    }

    @Override
    protected void appendNarrations(NarrationMessageBuilder builder, MapEntry entry) {
        super.appendNarrations(builder, entry);
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {

    }

    public static class MapEntry extends ElementListWidget.Entry<MapEntry> {
        private final DataManager.RecordData recordData;
        private final double avgTime;

        private final ButtonWidget mapButtonWidget;

        public MapEntry(DataManager.RecordData recordData, double avgTime, boolean minigame, int player_count, int difficulty) {
            this.recordData = recordData;
            this.avgTime = avgTime;

            String difficultyText;
            Formatting colorFormatting;
            if (difficulty == 0) {
                difficultyText = "Beginner";
                colorFormatting = Formatting.AQUA;
            }
            else if (difficulty == 1) {
                difficultyText = "Easy";
                colorFormatting = Formatting.GREEN;
            }
            else if (difficulty == 2) {
                difficultyText = "Moderate";
                colorFormatting = Formatting.YELLOW;
            }
            else if (difficulty == 3) {
                difficultyText = "Challenging";
                colorFormatting = Formatting.RED;
            }
            else if (difficulty == 4) {
                difficultyText = "Extremely Hard";
                colorFormatting = Formatting.DARK_RED;
            }
            else if (difficulty == 5) {
                difficultyText = "Impossible";
                colorFormatting = Formatting.LIGHT_PURPLE;
            }
            else {
                difficultyText = "Impossible";
                colorFormatting = Formatting.LIGHT_PURPLE;
            }

            if (!minigame) {
                this.mapButtonWidget = ButtonWidget.builder(Text.literal(recordData.map_name), button -> {
                        })
                        .tooltip(Tooltip.of(Text.literal("Players: " + player_count + "\nRecord holder: " + recordData.name + "\nRecord Time: " + String.format("%.5f", recordData.time) + "\nAverage Time: " + String.format("%.5f", avgTime) + "\nDifficulty: " + difficultyText).formatted(colorFormatting)))
                        .size(128, 20)
                        .build();
            }
            else {
                this.mapButtonWidget = ButtonWidget.builder(Text.literal(recordData.map_name), button -> {
                        })
                        .tooltip(Tooltip.of(Text.literal("Players: " + player_count).formatted(Formatting.GOLD)))
                        .size(128, 20)
                        .build();
            }

            this.mapButtonWidget.visible = true;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

            // Render the object's name and description here
            this.mapButtonWidget.setWidth(entryWidth);
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
