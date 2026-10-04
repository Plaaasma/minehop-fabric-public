package net.nerdorg.minehop.screen;

import com.google.gson.Gson;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.nerdorg.minehop.networking.payloads.AntiCheatActionPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class AntiCheatScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int BG_COLOR = 0xE60D1118;
    private static final int PANEL_COLOR = 0xF2151B27;
    private static final int CARD_COLOR = 0xCC1B2334;
    private static final int CARD_HOVER_COLOR = 0xCC273349;
    private static final int CARD_SELECTED_COLOR = 0xCC3A4D6E;
    private static final int BORDER_COLOR = 0xFF3B4D6B;
    private static final int ACCENT_COLOR = 0xFF6FB7FF;
    private static final int TEXT_DIM = 0xFFB4C3D9;
    private static final int TEXT_BRIGHT = 0xFFFFFFFF;
    private static final int TEXT_FLAGS_HIGH = 0xFFFF6464;
    private static final int TEXT_FLAGS_MED = 0xFFFFC062;
    private static final int TEXT_FLAGS_OK = 0xFF74E891;
    private static final int RIGHT_PANEL_WIDTH = 380;
    private static final int LEFT_PANEL_MIN_WIDTH = 240;
    private static final int CARD_HEIGHT = 36;

    private Snapshot snapshot = new Snapshot();
    private EditBox searchField;
    private Button refreshButton;
    private Button toggleEnabledButton;
    private Button clearFlagsButton;
    private Button exemptButton;
    private Button kickButton;
    private int leftScroll = 0;
    private int rightScroll = 0;
    private String selectedUuid = "";

    public AntiCheatScreen(String json) {
        super(Component.literal("Minehop AntiCheat"));
        applySnapshotJson(json);
    }

    public void applySnapshotJson(String json) {
        try {
            Snapshot parsed = GSON.fromJson(json, Snapshot.class);
            this.snapshot = parsed == null ? new Snapshot() : parsed;
            if (this.snapshot.players == null) {
                this.snapshot.players = new ArrayList<>();
            }
            if (this.minecraft != null) {
                this.refreshButtons();
            }
        } catch (Throwable ignored) {
            this.snapshot = new Snapshot();
        }
    }

    @Override
    protected void renderBlurredBackground() {
    }

    @Override
    public void clearFocus() {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        int searchWidth = Math.min(LEFT_PANEL_MIN_WIDTH + 60, this.width / 2 - 40);
        this.searchField = this.addRenderableWidget(new EditBox(this.font, 24, 56, searchWidth, 18, Component.literal("Filter players by name or UUID")));
        this.searchField.setMaxLength(64);

        int rightX = this.width - RIGHT_PANEL_WIDTH - 18;
        int buttonY = this.height - 40;
        int buttonGap = 6;
        int buttonWidth = (RIGHT_PANEL_WIDTH - buttonGap * 3) / 4;
        int currentX = rightX;

        this.clearFlagsButton = this.addRenderableWidget(Button.builder(Component.literal("Clear flags"), b -> this.sendAction(AntiCheatActionPayload.ACTION_CLEAR_FLAGS))
                .bounds(currentX, buttonY, buttonWidth, 20).build());
        currentX += buttonWidth + buttonGap;

        this.exemptButton = this.addRenderableWidget(Button.builder(Component.literal("Toggle exempt"), b -> this.toggleExempt())
                .bounds(currentX, buttonY, buttonWidth, 20).build());
        currentX += buttonWidth + buttonGap;

        this.kickButton = this.addRenderableWidget(Button.builder(Component.literal("Kick").withStyle(ChatFormatting.RED), b -> this.sendAction(AntiCheatActionPayload.ACTION_KICK))
                .bounds(currentX, buttonY, buttonWidth, 20).build());
        currentX += buttonWidth + buttonGap;

        this.refreshButton = this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> this.sendAction(AntiCheatActionPayload.ACTION_REFRESH))
                .bounds(currentX, buttonY, buttonWidth, 20).build());

        this.toggleEnabledButton = this.addRenderableWidget(Button.builder(Component.literal("AC: " + (this.snapshot.enabled ? "ON" : "OFF")),
                        b -> {
                        })
                .bounds(this.width - 100, 16, 84, 20).build());
        this.toggleEnabledButton.active = false;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(this.width - 80, this.height - 18, 72, 14).build());

        this.refreshButtons();
    }

    private void refreshButtons() {
        boolean hasTarget = this.selectedPlayer() != null;
        if (this.clearFlagsButton != null) this.clearFlagsButton.active = hasTarget;
        if (this.exemptButton != null) {
            this.exemptButton.active = hasTarget;
            PlayerData p = this.selectedPlayer();
            this.exemptButton.setMessage(Component.literal(p != null && p.exempt ? "Remove exempt" : "Add exempt"));
        }
        if (this.kickButton != null) {
            PlayerData p = this.selectedPlayer();
            this.kickButton.active = hasTarget && p != null && p.online;
        }
        if (this.toggleEnabledButton != null) {
            this.toggleEnabledButton.setMessage(Component.literal("AC: " + (this.snapshot.enabled ? "ON" : "OFF"))
                    .withStyle(this.snapshot.enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, BG_COLOR);

        int titleY = 14;
        context.drawString(this.font, Component.literal("Minehop AntiCheat Console").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), 24, titleY, TEXT_BRIGHT);
        context.drawString(this.font, Component.literal("Server tick: " + this.snapshot.serverTick + "   Tracked players: " + this.snapshot.players.size()).withStyle(ChatFormatting.GRAY), 24, titleY + 14, TEXT_DIM);

        super.render(context, mouseX, mouseY, delta);

        int leftX = 16;
        int leftY = 84;
        int leftPanelWidth = this.width - RIGHT_PANEL_WIDTH - 50;
        int leftPanelHeight = this.height - leftY - 24;
        this.drawListPanel(context, leftX, leftY, leftPanelWidth, leftPanelHeight, mouseX, mouseY);

        int rightX = this.width - RIGHT_PANEL_WIDTH - 18;
        int rightY = 56;
        int rightPanelHeight = this.height - rightY - 56;
        this.drawDetailPanel(context, rightX, rightY, RIGHT_PANEL_WIDTH, rightPanelHeight, mouseX, mouseY);
    }

    private void drawListPanel(GuiGraphics context, int x, int y, int w, int h, int mouseX, int mouseY) {
        context.fill(x, y, x + w, y + h, PANEL_COLOR);
        context.renderOutline(x, y, w, h, BORDER_COLOR);
        context.drawString(this.font, Component.literal("Tracked Players").withStyle(ChatFormatting.GOLD), x + 8, y - 14, TEXT_BRIGHT);

        List<PlayerData> filtered = this.filteredPlayers();
        int maxScroll = Math.max(0, filtered.size() * CARD_HEIGHT - h + 8);
        this.leftScroll = Mth.clamp(this.leftScroll, 0, maxScroll);

        context.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        int cardX = x + 4;
        int cardWidth = w - 10;
        int startY = y + 4 - this.leftScroll;
        int index = 0;
        for (PlayerData player : filtered) {
            int cardY = startY + index * CARD_HEIGHT;
            this.drawPlayerCard(context, player, cardX, cardY, cardWidth, mouseX, mouseY);
            index++;
        }
        context.disableScissor();

        if (filtered.isEmpty()) {
            context.drawCenteredString(this.font, Component.literal("(no matches)").withStyle(ChatFormatting.DARK_GRAY), x + w / 2, y + h / 2 - 4, TEXT_DIM);
        }

        if (maxScroll > 0) {
            int barX = x + w - 6;
            int barHeight = Math.max(20, (int) ((double) h * h / (h + maxScroll)));
            int barY = y + 4 + (int) ((double) this.leftScroll / maxScroll * (h - barHeight - 8));
            context.fill(barX, barY, barX + 4, barY + barHeight, ACCENT_COLOR);
        }
    }

    private void drawPlayerCard(GuiGraphics context, PlayerData player, int x, int y, int w, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + CARD_HEIGHT - 4;
        boolean selected = player.uuid.equals(this.selectedUuid);
        int bg = selected ? CARD_SELECTED_COLOR : (hovered ? CARD_HOVER_COLOR : CARD_COLOR);
        context.fill(x, y, x + w, y + CARD_HEIGHT - 4, bg);
        context.renderOutline(x, y, w, CARD_HEIGHT - 4, selected ? ACCENT_COLOR : BORDER_COLOR);

        int nameColor = player.online ? TEXT_BRIGHT : TEXT_DIM;
        String name = player.name == null || player.name.isBlank() ? player.uuid.substring(0, Math.min(8, player.uuid.length())) : player.name;
        context.drawString(this.font, Component.literal(name), x + 8, y + 5, nameColor);

        String statusText = (player.online ? "● online" : "○ offline") + (player.exempt ? "  ✓ exempt" : "");
        context.drawString(this.font, Component.literal(statusText).withStyle(player.online ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), x + 8, y + 18, TEXT_DIM);

        int flagColor = player.totalFlags >= 50 ? TEXT_FLAGS_HIGH : (player.totalFlags >= 5 ? TEXT_FLAGS_MED : TEXT_FLAGS_OK);
        String flagText = "flags " + player.totalFlags;
        int flagWidth = this.font.width(flagText);
        context.drawString(this.font, Component.literal(flagText), x + w - flagWidth - 8, y + 5, flagColor);
        if (player.lagbacks > 0) {
            String lbText = "lagback x" + player.lagbacks;
            int lbWidth = this.font.width(lbText);
            context.drawString(this.font, Component.literal(lbText).withStyle(ChatFormatting.RED), x + w - lbWidth - 8, y + 18, TEXT_FLAGS_HIGH);
        }
    }

    private void drawDetailPanel(GuiGraphics context, int x, int y, int w, int h, int mouseX, int mouseY) {
        context.fill(x, y, x + w, y + h, PANEL_COLOR);
        context.renderOutline(x, y, w, h, BORDER_COLOR);
        context.drawString(this.font, Component.literal("Flag Detail").withStyle(ChatFormatting.GOLD), x + 8, y - 14, TEXT_BRIGHT);

        PlayerData selected = this.selectedPlayer();
        if (selected == null) {
            context.drawCenteredString(this.font, Component.literal("Select a player on the left").withStyle(ChatFormatting.DARK_GRAY), x + w / 2, y + h / 2 - 8, TEXT_DIM);
            return;
        }

        int headerY = y + 8;
        context.drawString(this.font, Component.literal(selected.name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), x + 10, headerY, TEXT_BRIGHT);
        context.drawString(this.font, Component.literal(selected.uuid).withStyle(ChatFormatting.DARK_GRAY), x + 10, headerY + 11, TEXT_DIM);
        String summary = String.format(Locale.ROOT, "Total: %d   Recent: %d   Lagbacks: %d   %s",
                selected.totalFlags, selected.recentFlagCount, selected.lagbacks, selected.exempt ? "EXEMPT" : "active");
        context.drawString(this.font, Component.literal(summary).withStyle(selected.exempt ? ChatFormatting.GRAY : ChatFormatting.YELLOW), x + 10, headerY + 24, TEXT_DIM);

        int listY = headerY + 42;
        int listHeight = h - (listY - y) - 8;
        context.fill(x + 8, listY, x + w - 8, listY + listHeight, 0x99090E16);
        context.renderOutline(x + 8, listY, w - 16, listHeight, 0xFF263041);

        List<FlagData> flags = selected.flags == null ? new ArrayList<>() : selected.flags;
        int rowHeight = 22;
        int maxScroll = Math.max(0, flags.size() * rowHeight - listHeight + 4);
        this.rightScroll = Mth.clamp(this.rightScroll, 0, maxScroll);

        context.enableScissor(x + 9, listY + 1, x + w - 9, listY + listHeight - 1);
        int rowX = x + 12;
        int rowWidth = w - 24;
        int rowStartY = listY + 3 - this.rightScroll;
        for (int i = flags.size() - 1; i >= 0; i--) {
            FlagData flag = flags.get(i);
            int rowY = rowStartY + (flags.size() - 1 - i) * rowHeight;
            String timeStr = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(flag.timestamp));
            String line1 = "[" + timeStr + "] " + flag.check + " vl=" + String.format(Locale.ROOT, "%.1f", flag.severity);
            String line2 = flag.details;
            context.drawString(this.font, Component.literal(line1).withStyle(severityFormatting(flag.severity)), rowX, rowY, TEXT_BRIGHT);
            String trimmed = this.font.plainSubstrByWidth(line2, rowWidth - 4);
            context.drawString(this.font, Component.literal(trimmed).withStyle(ChatFormatting.GRAY), rowX, rowY + 10, TEXT_DIM);
        }
        context.disableScissor();

        if (flags.isEmpty()) {
            context.drawCenteredString(this.font, Component.literal("No recent flags").withStyle(ChatFormatting.DARK_GRAY), x + w / 2, listY + listHeight / 2 - 4, TEXT_DIM);
        }

        if (maxScroll > 0) {
            int barX = x + w - 12;
            int barHeight = Math.max(20, (int) ((double) listHeight * listHeight / (listHeight + maxScroll)));
            int barY = listY + 3 + (int) ((double) this.rightScroll / maxScroll * (listHeight - barHeight - 4));
            context.fill(barX, barY, barX + 4, barY + barHeight, ACCENT_COLOR);
        }
    }

    private static ChatFormatting severityFormatting(double severity) {
        if (severity >= 4.0D) return ChatFormatting.RED;
        if (severity >= 2.0D) return ChatFormatting.GOLD;
        return ChatFormatting.YELLOW;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            int leftX = 16;
            int leftY = 84;
            int leftPanelWidth = this.width - RIGHT_PANEL_WIDTH - 50;
            int leftPanelHeight = this.height - leftY - 24;
            if (mouseX >= leftX && mouseX <= leftX + leftPanelWidth && mouseY >= leftY && mouseY <= leftY + leftPanelHeight) {
                List<PlayerData> filtered = this.filteredPlayers();
                int relativeY = (int) (mouseY - leftY - 4 + this.leftScroll);
                int index = relativeY / CARD_HEIGHT;
                if (index >= 0 && index < filtered.size()) {
                    PlayerData p = filtered.get(index);
                    this.selectedUuid = p.uuid;
                    this.refreshButtons();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int rightX = this.width - RIGHT_PANEL_WIDTH - 18;
        if (mouseX >= rightX) {
            this.rightScroll -= (int) (verticalAmount * 18);
            this.rightScroll = Math.max(0, this.rightScroll);
            return true;
        }
        this.leftScroll -= (int) (verticalAmount * 18);
        this.leftScroll = Math.max(0, this.leftScroll);
        return true;
    }

    private PlayerData selectedPlayer() {
        if (this.selectedUuid == null || this.selectedUuid.isEmpty()) {
            return null;
        }
        for (PlayerData p : this.snapshot.players) {
            if (this.selectedUuid.equals(p.uuid)) {
                return p;
            }
        }
        return null;
    }

    private List<PlayerData> filteredPlayers() {
        String filter = this.searchField == null ? "" : this.searchField.getValue().toLowerCase(Locale.ROOT).trim();
        if (filter.isEmpty()) {
            return this.snapshot.players;
        }
        List<PlayerData> out = new ArrayList<>();
        for (PlayerData p : this.snapshot.players) {
            if (p == null) continue;
            String name = p.name == null ? "" : p.name.toLowerCase(Locale.ROOT);
            String uuid = p.uuid == null ? "" : p.uuid.toLowerCase(Locale.ROOT);
            if (name.contains(filter) || uuid.contains(filter)) {
                out.add(p);
            }
        }
        return out;
    }

    private void toggleExempt() {
        PlayerData selected = this.selectedPlayer();
        if (selected == null) return;
        this.sendAction(selected.exempt ? AntiCheatActionPayload.ACTION_EXEMPT_REMOVE : AntiCheatActionPayload.ACTION_EXEMPT_ADD);
    }

    private void sendAction(String action) {
        PlayerData selected = this.selectedPlayer();
        String uuid = selected == null ? "" : selected.uuid;
        if (uuid == null) uuid = "";
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        ClientPlayNetworking.send(new AntiCheatActionPayload(action, uuid));
    }

    public static final class Snapshot {
        public boolean enabled = true;
        public long serverTick;
        public List<PlayerData> players = new ArrayList<>();
    }

    public static final class PlayerData {
        public String uuid = "";
        public String name = "";
        public boolean online;
        public boolean exempt;
        public int totalFlags;
        public int recentFlagCount;
        public int lagbacks;
        public List<FlagData> flags = new ArrayList<>();
    }

    public static final class FlagData {
        public String check = "";
        public long timestamp;
        public double severity;
        public String details = "";
        public double x;
        public double y;
        public double z;
    }
}
