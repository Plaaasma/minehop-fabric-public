package net.nerdorg.minehop.screen.widget;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.nerdorg.minehop.data.DataManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapListWidget extends EntryListWidget<MapListWidget.MapEntry> {
    private final int rowWidth;
    private final int scrollbarX;
    private static final Map<String, PendingRating> PENDING_RATINGS = new HashMap<>();

    public MapListWidget(
            MinecraftClient client,
            int width,
            int bottom,
            int top,
            int itemHeight,
            int rowWidth,
            int scrollbarX
    ) {
        super(client, width, bottom, top, itemHeight);
        this.rowWidth = Math.max(220, rowWidth);
        this.scrollbarX = scrollbarX;
    }

    public void addEntry(
            DataManager.RecordData recordData,
            double avgTime,
            boolean minigame,
            int playerCount,
            int difficulty,
            boolean userMap,
            String ownerName,
            String description,
            int playCount,
            int ratingCount,
            int ratingQualityTotal,
            int ratingDifficultyTotal
    ) {
        super.addEntry(new MapEntry(
                recordData,
                avgTime,
                minigame,
                playerCount,
                difficulty,
                userMap,
                ownerName,
                description,
                playCount,
                ratingCount,
                ratingQualityTotal,
                ratingDifficultyTotal
        ));
    }

    @Override
    protected int getScrollbarX() {
        return this.scrollbarX;
    }

    @Override
    public int getRowWidth() {
        return this.rowWidth;
    }

    @Override
    protected void appendNarrations(NarrationMessageBuilder builder, MapEntry entry) {
        super.appendNarrations(builder, entry);
    }

    @Override
    protected void drawMenuListBackground(DrawContext context) {
        // SelectMapScreen draws the list panel background itself.
    }

    @Override
    protected void drawHeaderAndFooterSeparators(DrawContext context) {
        // Suppress default separators to avoid dark overlays outside our custom panel.
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
    }

    public int mapCount() {
        return this.getEntryCount();
    }

    public List<Text> getTooltipAt(double mouseX, double mouseY) {
        for (MapEntry entry : this.children()) {
            if (entry != null && entry.isMouseOverCard(mouseX, mouseY)) {
                return entry.buildTooltip(mouseX, mouseY);
            }
        }
        return Collections.emptyList();
    }

    public static class MapEntry extends ElementListWidget.Entry<MapEntry> {
        private final String mapName;
        private final String recordHolder;
        private final double recordTime;
        private final double avgTime;
        private final boolean minigame;
        private final int playerCount;
        private final int difficulty;
        private final boolean userMap;
        private final String ownerName;
        private final String description;
        private final int playCount;
        private final int ratingCount;
        private final int ratingQualityTotal;
        private final int ratingDifficultyTotal;

        private static final int RATING_CELL_SIZE = 6;
        private static final int RATING_CELL_GAP = 3;
        private static final int RATING_CELL_COUNT = 5;
        private static final int RATING_AREA_WIDTH = RATING_CELL_COUNT * RATING_CELL_SIZE + (RATING_CELL_COUNT - 1) * RATING_CELL_GAP;
        private static final int QUALITY_ROW_Y_OFFSET = 30;
        private static final int DIFFICULTY_ROW_Y_OFFSET = 39;

        private int lastX;
        private int lastY;
        private int lastWidth;
        private int lastHeight;

        public MapEntry(
                DataManager.RecordData recordData,
                double avgTime,
                boolean minigame,
                int playerCount,
                int difficulty,
                boolean userMap,
                String ownerName,
                String description,
                int playCount,
                int ratingCount,
                int ratingQualityTotal,
                int ratingDifficultyTotal
        ) {
            this.mapName = recordData == null || recordData.map_name == null ? "" : recordData.map_name;
            this.recordHolder = recordData == null || recordData.name == null ? "" : recordData.name;
            this.recordTime = recordData == null ? 0.0D : recordData.time;
            this.avgTime = avgTime;
            this.minigame = minigame;
            this.playerCount = Math.max(0, playerCount);
            this.difficulty = difficulty;
            this.userMap = userMap;
            this.ownerName = ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName;
            this.description = description == null ? "" : description;
            this.playCount = Math.max(0, playCount);
            this.ratingCount = Math.max(0, ratingCount);
            this.ratingQualityTotal = Math.max(0, ratingQualityTotal);
            this.ratingDifficultyTotal = Math.max(0, ratingDifficultyTotal);
        }

        @Override
        public void render(
                DrawContext context,
                int index,
                int y,
                int x,
                int entryWidth,
                int entryHeight,
                int mouseX,
                int mouseY,
                boolean hovered,
                float tickDelta
        ) {
            this.lastX = x;
            this.lastY = y;
            this.lastWidth = entryWidth;
            this.lastHeight = entryHeight;

            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            int cardHeight = Math.max(48, entryHeight - 2);
            int background = hovered ? 0xD02A3445 : 0xC0141A24;
            int border = hovered ? 0xFF78B8FF : 0xFF344156;

            context.fill(x, y, x + entryWidth, y + cardHeight, background);
            context.drawBorder(x, y, entryWidth, cardHeight, border);

            int padding = 7;
            int textLeft = x + padding;
            int textRight = x + entryWidth - padding;

            String safeName = this.mapName.isBlank() ? "Unnamed Map" : this.mapName;
            String trimmedName = textRenderer.trimToWidth(safeName, entryWidth - 170);
            context.drawTextWithShadow(textRenderer, trimmedName, textLeft, y + 5, 0xFFFFFF);

            String playersText = this.playerCount + " online";
            int playersWidth = textRenderer.getWidth(playersText);
            context.drawTextWithShadow(textRenderer, playersText, textRight - playersWidth, y + 5, 0x99D5FF);

            String performanceText;
            if (this.userMap) {
                performanceText = "Community: " + this.formatCommunityRating()
                        + "   Diff: " + this.formatCommunityDifficulty()
                        + "   Plays: " + this.playCount;
            } else {
                String recordText = this.minigame
                        ? "Mode: Minigame"
                        : "WR: " + this.formatRecord();
                String averageText = this.minigame
                        ? "Fast action mode"
                        : "Avg: " + this.formatAverage();
                performanceText = recordText + "   " + averageText;
            }
            int rightReserved = this.userMap ? 92 : 0;
            String perfLine = textRenderer.trimToWidth(performanceText, entryWidth - 170 - rightReserved);
            context.drawTextWithShadow(textRenderer, perfLine, textLeft, y + 18, 0xD5DCE8);

            DifficultyVisual difficultyVisual = difficultyVisual(this.difficulty);
            int difficultyWidth = textRenderer.getWidth(difficultyVisual.text);
            context.drawTextWithShadow(
                    textRenderer,
                    difficultyVisual.text,
                    textRight - difficultyWidth,
                    y + 18,
                    difficultyVisual.color
            );

            String ownerPart = this.userMap ? "Owner: " + this.ownerName : "Server Map";
            String descriptionPart;
            if (this.description.isBlank()) {
                descriptionPart = this.userMap && this.ratingCount <= 0
                        ? "No description. Rate it: /map rate " + StringArgumentType.escapeIfRequired(this.mapName) + " <good 1-5> <difficulty 1-5>"
                        : "No description set.";
            } else {
                descriptionPart = this.description;
            }
            String infoText = ownerPart + " | " + descriptionPart;
            String infoLine = textRenderer.trimToWidth(infoText, entryWidth - (padding * 2) - rightReserved);
            context.drawTextWithShadow(textRenderer, infoLine, textLeft, y + 31, 0xA9B3C6);

            if (this.userMap) {
                PendingRating pendingRating = this.getPendingRating();
                int ratingStartX = textRight - RATING_AREA_WIDTH;
                int qualityY = y + QUALITY_ROW_Y_OFFSET;
                int difficultyY = y + DIFFICULTY_ROW_Y_OFFSET;
                int qualityHover = this.getRatingIndexAt(mouseX, mouseY, true);
                int difficultyHover = this.getRatingIndexAt(mouseX, mouseY, false);

                context.drawTextWithShadow(textRenderer, "Q", ratingStartX - 8, qualityY - 1, 0x80D4FF);
                context.drawTextWithShadow(textRenderer, "D", ratingStartX - 8, difficultyY - 1, 0x80D4FF);
                this.drawXpRatingRow(context, ratingStartX, qualityY, pendingRating.quality, qualityHover);
                this.drawXpRatingRow(context, ratingStartX, difficultyY, pendingRating.difficulty, difficultyHover);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            if (!this.isMouseOverCard(mouseX, mouseY)) {
                return false;
            }
            if (this.userMap) {
                PendingRating pendingRating = this.getPendingRating();
                int qualityIndex = this.getRatingIndexAt(mouseX, mouseY, true);
                if (qualityIndex > 0) {
                    pendingRating.quality = qualityIndex;
                    this.sendRatingCommand(pendingRating);
                    return true;
                }

                int difficultyIndex = this.getRatingIndexAt(mouseX, mouseY, false);
                if (difficultyIndex > 0) {
                    pendingRating.difficulty = difficultyIndex;
                    this.sendRatingCommand(pendingRating);
                    return true;
                }
            }
            this.teleportToMap();
            return true;
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends Element> children() {
            return Collections.emptyList();
        }

        private boolean isMouseOverCard(double mouseX, double mouseY) {
            return mouseX >= this.lastX
                    && mouseX <= this.lastX + this.lastWidth
                    && mouseY >= this.lastY
                    && mouseY <= this.lastY + this.lastHeight;
        }

        private List<Text> buildTooltip(double mouseX, double mouseY) {
            List<Text> lines = new ArrayList<>();
            String safeName = this.mapName.isBlank() ? "Unnamed Map" : this.mapName;
            lines.add(Text.literal(safeName).formatted(Formatting.AQUA));
            lines.add(Text.literal("Left click: Teleport to this map").formatted(Formatting.GRAY));
            lines.add(Text.literal("Difficulty: " + difficultyVisual(this.difficulty).text).formatted(Formatting.GRAY));
            lines.add(Text.literal("Players online: " + this.playerCount).formatted(Formatting.GRAY));

            if (this.userMap) {
                PendingRating pendingRating = this.getPendingRating();
                lines.add(Text.literal("Owner: " + this.ownerName).formatted(Formatting.GRAY));
                lines.add(Text.literal("Plays: " + this.playCount + ", Ratings: " + this.ratingCount).formatted(Formatting.GRAY));
                lines.add(Text.literal("Rate in menu using XP rows:").formatted(Formatting.GOLD));
                lines.add(Text.literal("Top row (Q): quality " + pendingRating.quality + "/5").formatted(Formatting.YELLOW));
                lines.add(Text.literal("Bottom row (D): difficulty " + pendingRating.difficulty + "/5").formatted(Formatting.YELLOW));
                if (this.getRatingIndexAt(mouseX, mouseY, true) > 0) {
                    lines.add(Text.literal("Click now to set quality").formatted(Formatting.GREEN));
                } else if (this.getRatingIndexAt(mouseX, mouseY, false) > 0) {
                    lines.add(Text.literal("Click now to set difficulty").formatted(Formatting.GREEN));
                } else {
                    lines.add(Text.literal("Use /map edit " + StringArgumentType.escapeIfRequired(safeName) + " to edit map properties").formatted(Formatting.DARK_GRAY));
                }
            } else if (!this.minigame) {
                lines.add(Text.literal("World record: " + this.formatRecord()).formatted(Formatting.DARK_GRAY));
                lines.add(Text.literal("Average run: " + this.formatAverage()).formatted(Formatting.DARK_GRAY));
            }
            return lines;
        }

        private void teleportToMap() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getNetworkHandler() == null || this.mapName.isBlank()) {
                return;
            }
            String mapArgument = StringArgumentType.escapeIfRequired(this.mapName);
            client.setScreen(null);
            client.getNetworkHandler().sendCommand("map " + mapArgument);
        }

        private void sendRatingCommand(PendingRating pendingRating) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getNetworkHandler() == null || this.mapName.isBlank()) {
                return;
            }
            String mapArgument = StringArgumentType.escapeIfRequired(this.mapName);
            int clampedQuality = clampRating(pendingRating.quality);
            int clampedDifficulty = clampRating(pendingRating.difficulty);
            client.getNetworkHandler().sendCommand("map rate " + mapArgument + " " + clampedQuality + " " + clampedDifficulty);
        }

        private PendingRating getPendingRating() {
            if (this.mapName.isBlank()) {
                return new PendingRating(3, 3);
            }
            PendingRating existing = PENDING_RATINGS.get(this.mapName);
            if (existing != null) {
                return existing;
            }
            int quality = this.ratingCount <= 0 ? 3 : (int) Math.round((double) this.ratingQualityTotal / (double) this.ratingCount);
            int difficulty = this.ratingCount <= 0 ? 3 : (int) Math.round((double) this.ratingDifficultyTotal / (double) this.ratingCount);
            PendingRating created = new PendingRating(clampRating(quality), clampRating(difficulty));
            PENDING_RATINGS.put(this.mapName, created);
            return created;
        }

        private int getRatingIndexAt(double mouseX, double mouseY, boolean qualityRow) {
            if (!this.userMap) {
                return 0;
            }
            int padding = 7;
            int textRight = this.lastX + this.lastWidth - padding;
            int ratingStartX = textRight - RATING_AREA_WIDTH;
            int ratingY = this.lastY + (qualityRow ? QUALITY_ROW_Y_OFFSET : DIFFICULTY_ROW_Y_OFFSET);
            if (mouseY < ratingY || mouseY > ratingY + RATING_CELL_SIZE) {
                return 0;
            }
            if (mouseX < ratingStartX || mouseX > ratingStartX + RATING_AREA_WIDTH) {
                return 0;
            }
            for (int i = 0; i < RATING_CELL_COUNT; i++) {
                int cellX = ratingStartX + i * (RATING_CELL_SIZE + RATING_CELL_GAP);
                if (mouseX >= cellX && mouseX <= cellX + RATING_CELL_SIZE) {
                    return i + 1;
                }
            }
            return 0;
        }

        private void drawXpRatingRow(DrawContext context, int startX, int y, int value, int hoverValue) {
            int clampedValue = clampRating(value);
            int effectiveHover = clampRatingAllowZero(hoverValue);
            for (int i = 0; i < RATING_CELL_COUNT; i++) {
                int x = startX + i * (RATING_CELL_SIZE + RATING_CELL_GAP);
                boolean isFilled = i < clampedValue;
                boolean isHoverFilled = effectiveHover > 0 && i < effectiveHover;
                int borderColor = isHoverFilled ? 0xFFB9FF8C : (isFilled ? 0xFF72E834 : 0xFF37502A);
                int fillColor = isHoverFilled ? 0xFF95F05D : (isFilled ? 0xFF58C72A : 0xFF1D3117);
                context.fill(x, y, x + RATING_CELL_SIZE, y + RATING_CELL_SIZE, borderColor);
                context.fill(x + 1, y + 1, x + RATING_CELL_SIZE - 1, y + RATING_CELL_SIZE - 1, fillColor);
            }
        }

        private static int clampRating(int value) {
            return Math.max(1, Math.min(5, value));
        }

        private static int clampRatingAllowZero(int value) {
            return Math.max(0, Math.min(5, value));
        }

        private String formatRecord() {
            boolean hasRecord = this.recordTime > 0.0D && this.recordTime < 999999.0D;
            if (!hasRecord) {
                return "--";
            }
            String holder = this.recordHolder == null || this.recordHolder.isBlank() ? "Unknown" : this.recordHolder;
            return holder + " (" + String.format(Locale.ROOT, "%.5f", this.recordTime) + "s)";
        }

        private String formatAverage() {
            if (this.avgTime <= 0.0D) {
                return "--";
            }
            return String.format(Locale.ROOT, "%.5fs", this.avgTime);
        }

        private String formatCommunityRating() {
            if (this.ratingCount <= 0) {
                return "--";
            }
            double average = (double) this.ratingQualityTotal / (double) this.ratingCount;
            return String.format(Locale.ROOT, "%.2f/5 (%d)", average, this.ratingCount);
        }

        private String formatCommunityDifficulty() {
            if (this.ratingCount <= 0) {
                return "--";
            }
            double average = (double) this.ratingDifficultyTotal / (double) this.ratingCount;
            return String.format(Locale.ROOT, "%.2f/5", average);
        }

        private static DifficultyVisual difficultyVisual(int difficulty) {
            return switch (difficulty) {
                case 0 -> new DifficultyVisual("Beginner", Formatting.AQUA.getColorValue());
                case 1 -> new DifficultyVisual("Easy", Formatting.GREEN.getColorValue());
                case 2 -> new DifficultyVisual("Moderate", Formatting.YELLOW.getColorValue());
                case 3 -> new DifficultyVisual("Challenging", Formatting.RED.getColorValue());
                case 4 -> new DifficultyVisual("Ext Hard", Formatting.DARK_RED.getColorValue());
                case 5 -> new DifficultyVisual("Impossible", Formatting.LIGHT_PURPLE.getColorValue());
                default -> new DifficultyVisual("Unknown", 0xFFAAAAAA);
            };
        }

        private record DifficultyVisual(String text, int color) {
        }
    }

    private static final class PendingRating {
        private int quality;
        private int difficulty;

        private PendingRating(int quality, int difficulty) {
            this.quality = quality;
            this.difficulty = difficulty;
        }
    }
}
