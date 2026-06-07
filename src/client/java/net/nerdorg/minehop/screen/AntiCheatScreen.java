package net.nerdorg.minehop.screen;

import com.google.gson.Gson;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
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
    private TextFieldWidget searchField;
    private ButtonWidget refreshButton;
    private ButtonWidget toggleEnabledButton;
    private ButtonWidget clearFlagsButton;
    private ButtonWidget exemptButton;
    private ButtonWidget kickButton;
    private int leftScroll = 0;
    private int rightScroll = 0;
    private String selectedUuid = "";

    public AntiCheatScreen(String json) {
        super(Text.literal("Minehop AntiCheat"));
        applySnapshotJson(json);
    }

    public void applySnapshotJson(String json) {
        try {
            Snapshot parsed = GSON.fromJson(json, Snapshot.class);
            this.snapshot = parsed == null ? new Snapshot() : parsed;
            if (this.snapshot.players == null) {
                this.snapshot.players = new ArrayList<>();
            }
            if (this.client != null) {
                this.refreshButtons();
            }
        } catch (Throwable ignored) {
            this.snapshot = new Snapshot();
        }
    }

    @Override
    protected void applyBlur() {
    }

    @Override
    public void blur() {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        int searchWidth = Math.min(LEFT_PANEL_MIN_WIDTH + 60, this.width / 2 - 40);
        this.searchField = this.addDrawableChild(new TextFieldWidget(this.textRenderer, 24, 56, searchWidth, 18, Text.literal("Filter players by name or UUID")));
        this.searchField.setMaxLength(64);

        int rightX = this.width - RIGHT_PANEL_WIDTH - 18;
        int buttonY = this.height - 40;
        int buttonGap = 6;
        int buttonWidth = (RIGHT_PANEL_WIDTH - buttonGap * 3) / 4;
        int currentX = rightX;

        this.clearFlagsButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear flags"), b -> this.sendAction(AntiCheatActionPayload.ACTION_CLEAR_FLAGS))
                .dimensions(currentX, buttonY, buttonWidth, 20).build());
        currentX += buttonWidth + buttonGap;

        this.exemptButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Toggle exempt"), b -> this.toggleExempt())
                .dimensions(currentX, buttonY, buttonWidth, 20).build());
        currentX += buttonWidth + buttonGap;

        this.kickButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Kick").formatted(Formatting.RED), b -> this.sendAction(AntiCheatActionPayload.ACTION_KICK))
                .dimensions(currentX, buttonY, buttonWidth, 20).build());
        currentX += buttonWidth + buttonGap;

        this.refreshButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), b -> this.sendAction(AntiCheatActionPayload.ACTION_REFRESH))
                .dimensions(currentX, buttonY, buttonWidth, 20).build());

        this.toggleEnabledButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("AC: " + (this.snapshot.enabled ? "ON" : "OFF")),
                        b -> {
                        })
                .dimensions(this.width - 100, 16, 84, 20).build());
        this.toggleEnabledButton.active = false;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> this.close())
                .dimensions(this.width - 80, this.height - 18, 72, 14).build());

        this.refreshButtons();
    }

    private void refreshButtons() {
        boolean hasTarget = this.selectedPlayer() != null;
        if (this.clearFlagsButton != null) this.clearFlagsButton.active = hasTarget;
        if (this.exemptButton != null) {
            this.exemptButton.active = hasTarget;
            PlayerData p = this.selectedPlayer();
            this.exemptButton.setMessage(Text.literal(p != null && p.exempt ? "Remove exempt" : "Add exempt"));
        }
        if (this.kickButton != null) {
            PlayerData p = this.selectedPlayer();
            this.kickButton.active = hasTarget && p != null && p.online;
        }
        if (this.toggleEnabledButton != null) {
            this.toggleEnabledButton.setMessage(Text.literal("AC: " + (this.snapshot.enabled ? "ON" : "OFF"))
                    .formatted(this.snapshot.enabled ? Formatting.GREEN : Formatting.RED));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, 0, this.width, this.height, BG_COLOR);

        int titleY = 14;
        context.drawTextWithShadow(this.textRenderer, Text.literal("Minehop AntiCheat Console").formatted(Formatting.AQUA, Formatting.BOLD), 24, titleY, TEXT_BRIGHT);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Server tick: " + this.snapshot.serverTick + "   Tracked players: " + this.snapshot.players.size()).formatted(Formatting.GRAY), 24, titleY + 14, TEXT_DIM);

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

    private void drawListPanel(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
        context.fill(x, y, x + w, y + h, PANEL_COLOR);
        context.drawBorder(x, y, w, h, BORDER_COLOR);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Tracked Players").formatted(Formatting.GOLD), x + 8, y - 14, TEXT_BRIGHT);

        List<PlayerData> filtered = this.filteredPlayers();
        int maxScroll = Math.max(0, filtered.size() * CARD_HEIGHT - h + 8);
        this.leftScroll = MathHelper.clamp(this.leftScroll, 0, maxScroll);

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
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("(no matches)").formatted(Formatting.DARK_GRAY), x + w / 2, y + h / 2 - 4, TEXT_DIM);
        }

        if (maxScroll > 0) {
            int barX = x + w - 6;
            int barHeight = Math.max(20, (int) ((double) h * h / (h + maxScroll)));
            int barY = y + 4 + (int) ((double) this.leftScroll / maxScroll * (h - barHeight - 8));
            context.fill(barX, barY, barX + 4, barY + barHeight, ACCENT_COLOR);
        }
    }

    private void drawPlayerCard(DrawContext context, PlayerData player, int x, int y, int w, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + CARD_HEIGHT - 4;
        boolean selected = player.uuid.equals(this.selectedUuid);
        int bg = selected ? CARD_SELECTED_COLOR : (hovered ? CARD_HOVER_COLOR : CARD_COLOR);
        context.fill(x, y, x + w, y + CARD_HEIGHT - 4, bg);
        context.drawBorder(x, y, w, CARD_HEIGHT - 4, selected ? ACCENT_COLOR : BORDER_COLOR);

        int nameColor = player.online ? TEXT_BRIGHT : TEXT_DIM;
        String name = player.name == null || player.name.isBlank() ? player.uuid.substring(0, Math.min(8, player.uuid.length())) : player.name;
        context.drawTextWithShadow(this.textRenderer, Text.literal(name), x + 8, y + 5, nameColor);

        String statusText = (player.online ? "● online" : "○ offline") + (player.exempt ? "  ✓ exempt" : "");
        context.drawTextWithShadow(this.textRenderer, Text.literal(statusText).formatted(player.online ? Formatting.GREEN : Formatting.DARK_GRAY), x + 8, y + 18, TEXT_DIM);

        int flagColor = player.totalFlags >= 50 ? TEXT_FLAGS_HIGH : (player.totalFlags >= 5 ? TEXT_FLAGS_MED : TEXT_FLAGS_OK);
        String flagText = "flags " + player.totalFlags;
        int flagWidth = this.textRenderer.getWidth(flagText);
        context.drawTextWithShadow(this.textRenderer, Text.literal(flagText), x + w - flagWidth - 8, y + 5, flagColor);
        if (player.lagbacks > 0) {
            String lbText = "lagback x" + player.lagbacks;
            int lbWidth = this.textRenderer.getWidth(lbText);
            context.drawTextWithShadow(this.textRenderer, Text.literal(lbText).formatted(Formatting.RED), x + w - lbWidth - 8, y + 18, TEXT_FLAGS_HIGH);
        }
    }

    private void drawDetailPanel(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
        context.fill(x, y, x + w, y + h, PANEL_COLOR);
        context.drawBorder(x, y, w, h, BORDER_COLOR);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Flag Detail").formatted(Formatting.GOLD), x + 8, y - 14, TEXT_BRIGHT);

        PlayerData selected = this.selectedPlayer();
        if (selected == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Select a player on the left").formatted(Formatting.DARK_GRAY), x + w / 2, y + h / 2 - 8, TEXT_DIM);
            return;
        }

        int headerY = y + 8;
        context.drawTextWithShadow(this.textRenderer, Text.literal(selected.name).formatted(Formatting.AQUA, Formatting.BOLD), x + 10, headerY, TEXT_BRIGHT);
        context.drawTextWithShadow(this.textRenderer, Text.literal(selected.uuid).formatted(Formatting.DARK_GRAY), x + 10, headerY + 11, TEXT_DIM);
        String summary = String.format(Locale.ROOT, "Total: %d   Recent: %d   Lagbacks: %d   %s",
                selected.totalFlags, selected.recentFlagCount, selected.lagbacks, selected.exempt ? "EXEMPT" : "active");
        context.drawTextWithShadow(this.textRenderer, Text.literal(summary).formatted(selected.exempt ? Formatting.GRAY : Formatting.YELLOW), x + 10, headerY + 24, TEXT_DIM);

        int listY = headerY + 42;
        int listHeight = h - (listY - y) - 8;
        context.fill(x + 8, listY, x + w - 8, listY + listHeight, 0x99090E16);
        context.drawBorder(x + 8, listY, w - 16, listHeight, 0xFF263041);

        List<FlagData> flags = selected.flags == null ? new ArrayList<>() : selected.flags;
        int rowHeight = 22;
        int maxScroll = Math.max(0, flags.size() * rowHeight - listHeight + 4);
        this.rightScroll = MathHelper.clamp(this.rightScroll, 0, maxScroll);

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
            context.drawTextWithShadow(this.textRenderer, Text.literal(line1).formatted(severityFormatting(flag.severity)), rowX, rowY, TEXT_BRIGHT);
            String trimmed = this.textRenderer.trimToWidth(line2, rowWidth - 4);
            context.drawTextWithShadow(this.textRenderer, Text.literal(trimmed).formatted(Formatting.GRAY), rowX, rowY + 10, TEXT_DIM);
        }
        context.disableScissor();

        if (flags.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No recent flags").formatted(Formatting.DARK_GRAY), x + w / 2, listY + listHeight / 2 - 4, TEXT_DIM);
        }

        if (maxScroll > 0) {
            int barX = x + w - 12;
            int barHeight = Math.max(20, (int) ((double) listHeight * listHeight / (listHeight + maxScroll)));
            int barY = listY + 3 + (int) ((double) this.rightScroll / maxScroll * (listHeight - barHeight - 4));
            context.fill(barX, barY, barX + 4, barY + barHeight, ACCENT_COLOR);
        }
    }

    private static Formatting severityFormatting(double severity) {
        if (severity >= 4.0D) return Formatting.RED;
        if (severity >= 2.0D) return Formatting.GOLD;
        return Formatting.YELLOW;
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
        String filter = this.searchField == null ? "" : this.searchField.getText().toLowerCase(Locale.ROOT).trim();
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
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
