package net.nerdorg.minehop.screen.widget;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.nerdorg.minehop.data.DataManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapListWidget extends AbstractSelectionList<MapListWidget.MapEntry> {
    private final int rowWidth;
    private final int scrollbarX;
    private static final Map<String, PendingRating> PENDING_RATINGS = new HashMap<>();

    public MapListWidget(
            Minecraft client,
            int width,
            int bottom,
            int top,
            int itemHeight,
            int rowWidth,
            int scrollbarX
    ) {
        // 1.20.1: EntryListWidget takes explicit top AND bottom coordinates (the list clips to
        // [top, bottom]); its "height" is only used for the header/footer shadows, which are off.
        super(client, width, bottom, top, bottom, itemHeight);
        // 1.20.1 equivalents of the 1.21.4 drawMenuListBackground / drawHeaderAndFooterSeparators
        // no-op overrides: SelectMapScreen draws the list panel background itself.
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
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
    protected int getScrollbarPosition() {
        return this.scrollbarX;
    }

    @Override
    public int getRowWidth() {
        return this.rowWidth;
    }

    @Override
    protected void narrateListElementPosition(NarrationElementOutput builder, MapEntry entry) {
        super.narrateListElementPosition(builder, entry);
    }

    @Override
    public void updateNarration(NarrationElementOutput builder) {
    }

    public int mapCount() {
        return this.getItemCount();
    }

    public List<Component> getTooltipAt(double mouseX, double mouseY) {
        for (MapEntry entry : this.children()) {
            if (entry != null && entry.isMouseOverCard(mouseX, mouseY)) {
                return entry.buildTooltip(mouseX, mouseY);
            }
        }
        return Collections.emptyList();
    }

    public static class MapEntry extends ContainerObjectSelectionList.Entry<MapEntry> {
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
                GuiGraphics context,
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

            Font textRenderer = Minecraft.getInstance().font;
            int cardHeight = Math.max(48, entryHeight - 2);
            int background = hovered ? 0xD02A3445 : 0xC0141A24;
            int border = hovered ? 0xFF78B8FF : 0xFF344156;

            context.fill(x, y, x + entryWidth, y + cardHeight, background);
            context.renderOutline(x, y, entryWidth, cardHeight, border);

            int padding = 7;
            int textLeft = x + padding;
            int textRight = x + entryWidth - padding;

            String safeName = this.mapName.isBlank() ? "Unnamed Map" : this.mapName;
            String trimmedName = textRenderer.plainSubstrByWidth(safeName, entryWidth - 170);
            context.drawString(textRenderer, trimmedName, textLeft, y + 5, 0xFFFFFF);

            String playersText = this.playerCount + " online";
            int playersWidth = textRenderer.width(playersText);
            context.drawString(textRenderer, playersText, textRight - playersWidth, y + 5, 0x99D5FF);

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
            String perfLine = textRenderer.plainSubstrByWidth(performanceText, entryWidth - 170 - rightReserved);
            context.drawString(textRenderer, perfLine, textLeft, y + 18, 0xD5DCE8);

            DifficultyVisual difficultyVisual = difficultyVisual(this.difficulty);
            int difficultyWidth = textRenderer.width(difficultyVisual.text);
            context.drawString(
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
            String infoLine = textRenderer.plainSubstrByWidth(infoText, entryWidth - (padding * 2) - rightReserved);
            context.drawString(textRenderer, infoLine, textLeft, y + 31, 0xA9B3C6);

            if (this.userMap) {
                PendingRating pendingRating = this.getPendingRating();
                int ratingStartX = textRight - RATING_AREA_WIDTH;
                int qualityY = y + QUALITY_ROW_Y_OFFSET;
                int difficultyY = y + DIFFICULTY_ROW_Y_OFFSET;
                int qualityHover = this.getRatingIndexAt(mouseX, mouseY, true);
                int difficultyHover = this.getRatingIndexAt(mouseX, mouseY, false);

                context.drawString(textRenderer, "Q", ratingStartX - 8, qualityY - 1, 0x80D4FF);
                context.drawString(textRenderer, "D", ratingStartX - 8, difficultyY - 1, 0x80D4FF);
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
            if (net.minecraft.client.gui.screens.Screen.hasShiftDown() && this.hasWorldRecord()) {
                this.watchWorldRecord();
                return true;
            }
            this.teleportToMap();
            return true;
        }

        private boolean hasWorldRecord() {
            return !this.minigame && this.recordTime > 0.0D && this.recordTime < 999999.0D;
        }

        /** Shift + click: watch the map's world record (played on this client by 1.1.7+ servers, else a ghost). */
        private void watchWorldRecord() {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getConnection() == null || this.mapName.isBlank()) {
                return;
            }
            client.setScreen(null);
            client.getConnection().sendUnsignedCommand("spec " + StringArgumentType.escapeIfRequired(this.mapName + "_replay"));
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        private boolean isMouseOverCard(double mouseX, double mouseY) {
            return mouseX >= this.lastX
                    && mouseX <= this.lastX + this.lastWidth
                    && mouseY >= this.lastY
                    && mouseY <= this.lastY + this.lastHeight;
        }

        private List<Component> buildTooltip(double mouseX, double mouseY) {
            List<Component> lines = new ArrayList<>();
            String safeName = this.mapName.isBlank() ? "Unnamed Map" : this.mapName;
            lines.add(Component.literal(safeName).withStyle(ChatFormatting.AQUA));
            lines.add(Component.literal("Left click: Teleport to this map").withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("Difficulty: " + difficultyVisual(this.difficulty).text).withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal("Players online: " + this.playerCount).withStyle(ChatFormatting.GRAY));

            if (this.userMap) {
                PendingRating pendingRating = this.getPendingRating();
                lines.add(Component.literal("Owner: " + this.ownerName).withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("Plays: " + this.playCount + ", Ratings: " + this.ratingCount).withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("Rate in menu using XP rows:").withStyle(ChatFormatting.GOLD));
                lines.add(Component.literal("Top row (Q): quality " + pendingRating.quality + "/5").withStyle(ChatFormatting.YELLOW));
                lines.add(Component.literal("Bottom row (D): difficulty " + pendingRating.difficulty + "/5").withStyle(ChatFormatting.YELLOW));
                if (this.getRatingIndexAt(mouseX, mouseY, true) > 0) {
                    lines.add(Component.literal("Click now to set quality").withStyle(ChatFormatting.GREEN));
                } else if (this.getRatingIndexAt(mouseX, mouseY, false) > 0) {
                    lines.add(Component.literal("Click now to set difficulty").withStyle(ChatFormatting.GREEN));
                } else {
                    lines.add(Component.literal("Use /map edit " + StringArgumentType.escapeIfRequired(safeName) + " to edit map properties").withStyle(ChatFormatting.DARK_GRAY));
                }
            } else if (!this.minigame) {
                lines.add(Component.literal("World record: " + this.formatRecord()).withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.literal("Average run: " + this.formatAverage()).withStyle(ChatFormatting.DARK_GRAY));
                if (this.hasWorldRecord()) {
                    lines.add(Component.literal("Shift + click: Watch the world record").withStyle(ChatFormatting.GRAY));
                }
            }
            return lines;
        }

        private void teleportToMap() {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getConnection() == null || this.mapName.isBlank()) {
                return;
            }
            String mapArgument = StringArgumentType.escapeIfRequired(this.mapName);
            client.setScreen(null);
            client.getConnection().sendUnsignedCommand("map " + mapArgument);
        }

        private void sendRatingCommand(PendingRating pendingRating) {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getConnection() == null || this.mapName.isBlank()) {
                return;
            }
            String mapArgument = StringArgumentType.escapeIfRequired(this.mapName);
            int clampedQuality = clampRating(pendingRating.quality);
            int clampedDifficulty = clampRating(pendingRating.difficulty);
            client.getConnection().sendUnsignedCommand("map rate " + mapArgument + " " + clampedQuality + " " + clampedDifficulty);
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

        private void drawXpRatingRow(GuiGraphics context, int startX, int y, int value, int hoverValue) {
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
                case 0 -> new DifficultyVisual("Beginner", ChatFormatting.AQUA.getColor());
                case 1 -> new DifficultyVisual("Easy", ChatFormatting.GREEN.getColor());
                case 2 -> new DifficultyVisual("Moderate", ChatFormatting.YELLOW.getColor());
                case 3 -> new DifficultyVisual("Challenging", ChatFormatting.RED.getColor());
                case 4 -> new DifficultyVisual("Ext Hard", ChatFormatting.DARK_RED.getColor());
                case 5 -> new DifficultyVisual("Impossible", ChatFormatting.LIGHT_PURPLE.getColor());
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
