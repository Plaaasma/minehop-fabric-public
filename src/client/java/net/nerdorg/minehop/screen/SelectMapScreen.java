package net.nerdorg.minehop.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.screen.widget.MapListWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Environment(EnvType.CLIENT)
public class SelectMapScreen extends Screen {
    private static final int TAB_COUNT = 6;
    private static final int TAB_GAP = 6;
    private static final int LIST_ITEM_HEIGHT = 54;

    private MapListWidget bhopListWidget;
    private MapListWidget surfListWidget;
    private MapListWidget kzListWidget;
    private MapListWidget arenaListWidget;
    private MapListWidget hnsListWidget;
    private MapListWidget userListWidget;

    private TextFieldWidget textFieldWidget;
    private TextFieldWidget authorFieldWidget;
    private ButtonWidget userSortButtonWidget;
    private ButtonWidget bhopButtonWidget;
    private ButtonWidget surfButtonWidget;
    private ButtonWidget kzButtonWidget;
    private ButtonWidget arenaButtonWidget;
    private ButtonWidget hnsButtonWidget;
    private ButtonWidget userButtonWidget;
    private ButtonWidget closeButtonWidget;

    private String lastFieldText = "";
    private String lastAuthorText = "";
    private MapTab activeTab = MapTab.BHOP;
    private UserSortMode userSortMode = UserSortMode.HIGHEST_RATED;
    private UserSortMode lastUserSortMode = UserSortMode.HIGHEST_RATED;
    private int lastMapListSize = -1;
    private int lastMapListHash = 0;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listTop;
    private int listBottom;
    private int panelScrollOffset;
    private int panelMaxScroll;
    private int tabWidth;

    public SelectMapScreen(Text title) {
        super(title);
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
        this.computeLayout();

        int contentX = this.panelX + 10;
        int contentWidth = this.panelWidth - 20;

        this.textFieldWidget = this.addDrawableChild(
                new TextFieldWidget(
                        this.client.textRenderer,
                        contentX,
                        this.panelY + 34,
                        contentWidth,
                        18,
                        Text.literal("Filter maps by name, owner, or description")
                )
        );
        this.textFieldWidget.setMaxLength(128);
        this.textFieldWidget.setText(this.lastFieldText);

        int tabY = this.panelY + 56;
        this.tabWidth = Math.max(24, (contentWidth - (TAB_GAP * (TAB_COUNT - 1))) / TAB_COUNT);
        int tabX = contentX;

        this.bhopButtonWidget = this.addDrawableChild(
                ButtonWidget.builder(Text.of("Bhop"), button -> this.setActiveTab(MapTab.BHOP))
                        .dimensions(tabX, tabY, this.tabWidth, 18)
                        .build()
        );
        tabX += this.tabWidth + TAB_GAP;

        this.surfButtonWidget = this.addDrawableChild(
                ButtonWidget.builder(Text.of("Surf"), button -> this.setActiveTab(MapTab.SURF))
                        .dimensions(tabX, tabY, this.tabWidth, 18)
                        .build()
        );
        tabX += this.tabWidth + TAB_GAP;

        this.kzButtonWidget = this.addDrawableChild(
                ButtonWidget.builder(Text.of("KZ"), button -> this.setActiveTab(MapTab.KZ))
                        .dimensions(tabX, tabY, this.tabWidth, 18)
                        .build()
        );
        tabX += this.tabWidth + TAB_GAP;

        this.arenaButtonWidget = this.addDrawableChild(
                ButtonWidget.builder(Text.of("Arena"), button -> this.setActiveTab(MapTab.ARENA))
                        .dimensions(tabX, tabY, this.tabWidth, 18)
                        .build()
        );
        tabX += this.tabWidth + TAB_GAP;

        this.hnsButtonWidget = this.addDrawableChild(
                ButtonWidget.builder(Text.of("HNS"), button -> this.setActiveTab(MapTab.HNS))
                        .dimensions(tabX, tabY, this.tabWidth, 18)
                        .build()
        );
        tabX += this.tabWidth + TAB_GAP;

        this.userButtonWidget = this.addDrawableChild(
                ButtonWidget.builder(Text.of("User"), button -> this.setActiveTab(MapTab.USER))
                        .dimensions(tabX, tabY, this.tabWidth, 18)
                        .build()
        );

        int authorY = this.panelY + 88;
        int sortWidth = Math.min(130, Math.max(92, contentWidth / 3));
        int authorWidth = Math.max(80, contentWidth - sortWidth - 6);
        this.authorFieldWidget = this.addDrawableChild(
                new TextFieldWidget(
                        this.client.textRenderer,
                        contentX,
                        authorY,
                        authorWidth,
                        18,
                        Text.literal("Filter user maps by author")
                )
        );
        this.authorFieldWidget.setMaxLength(64);
        this.authorFieldWidget.setText(this.lastAuthorText);

        this.userSortButtonWidget = this.addDrawableChild(
                ButtonWidget.builder(Text.empty(), button -> this.cycleUserSort())
                        .dimensions(contentX + authorWidth + 6, authorY, sortWidth, 18)
                        .build()
        );
        this.updateUserSortButtonText();

        this.closeButtonWidget = this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("gui.done"), button -> this.close())
                        .dimensions(this.panelX + this.panelWidth - 90, this.panelY + this.panelHeight - 22, 80, 20)
                        .build()
        );

        this.rebuildLists(this.textFieldWidget.getText(), this.authorFieldWidget.getText());
        this.updateTabButtonLabels();
        this.updateMapListSignature();
        this.setActiveTab(this.activeTab);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        String currentFilter = this.textFieldWidget == null ? this.lastFieldText : this.textFieldWidget.getText();
        String currentAuthor = this.authorFieldWidget == null ? this.lastAuthorText : this.authorFieldWidget.getText();
        super.resize(client, width, height);
        if (this.textFieldWidget != null) {
            this.textFieldWidget.setText(currentFilter);
        }
        if (this.authorFieldWidget != null) {
            this.authorFieldWidget.setText(currentAuthor);
        }
        this.lastFieldText = currentFilter == null ? "" : currentFilter;
        this.lastAuthorText = currentAuthor == null ? "" : currentAuthor;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.panelMaxScroll > 0 && this.isMouseInsidePanel(mouseX, mouseY) && Math.abs(verticalAmount) >= 1.0E-6D) {
            int adaptiveStep = MathHelper.clamp((int) Math.round(this.panelMaxScroll / 8.0D), 2, 10);
            int scrollDelta = (int) Math.round(verticalAmount * adaptiveStep);
            if (scrollDelta == 0) {
                scrollDelta = verticalAmount > 0.0D ? 1 : -1;
            }
            int nextOffset = MathHelper.clamp(this.panelScrollOffset - scrollDelta, 0, this.panelMaxScroll);
            if (nextOffset != this.panelScrollOffset) {
                this.panelScrollOffset = nextOffset;
                if (this.client != null) {
                    this.resize(this.client, this.width, this.height);
                }
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        this.drawPanel(context);

        String filterText = this.textFieldWidget == null ? "" : this.textFieldWidget.getText();
        String authorText = this.authorFieldWidget == null ? "" : this.authorFieldWidget.getText();
        boolean mapChanged = this.hasMapListChanged();
        boolean filterChanged = !Objects.equals(filterText, this.lastFieldText);
        boolean authorChanged = !Objects.equals(authorText, this.lastAuthorText);
        boolean sortChanged = this.userSortMode != this.lastUserSortMode;

        if (mapChanged || filterChanged || authorChanged || sortChanged) {
            this.rebuildLists(filterText, authorText);
            this.updateTabButtonLabels();
            this.attachActiveList();
            this.lastFieldText = filterText;
            this.lastAuthorText = authorText;
            this.lastUserSortMode = this.userSortMode;
            this.updateMapListSignature();
        }

        this.drawHeaderText(context);
        super.render(context, mouseX, mouseY, delta);

        MapListWidget activeList = this.getActiveList();
        if (activeList != null) {
            List<Text> tooltip = activeList.getTooltipAt(mouseX, mouseY);
            if (tooltip != null && !tooltip.isEmpty()) {
                context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
            }
        }
    }

    private void drawPanel(DrawContext context) {
        context.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, 0xD010141D);
        context.drawBorder(this.panelX, this.panelY, this.panelWidth, this.panelHeight, 0xFF4A5D78);
        context.fill(this.panelX + 1, this.panelY + 1, this.panelX + this.panelWidth - 1, this.panelY + 26, 0xA0162436);

        int listX = this.panelX + 7;
        int listWidth = this.panelWidth - 14;
        int listHeight = this.listBottom - this.listTop;
        context.fill(listX, this.listTop, listX + listWidth, this.listTop + listHeight, 0x8E0C1018);
        context.drawBorder(listX, this.listTop, listWidth, listHeight, 0xFF30425E);
    }

    private void drawHeaderText(DrawContext context) {
        context.drawTextWithShadow(this.textRenderer, Text.literal("Map Browser"), this.panelX + 10, this.panelY + 9, 0xFFFFFF);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Click a map card to teleport"),
                this.panelX + 118,
                this.panelY + 9,
                0xB7C6DA
        );

        context.drawTextWithShadow(this.textRenderer, Text.literal("Map Filter"), this.panelX + 10, this.panelY + 24, 0xC5D4E8);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Author"), this.panelX + 10, this.panelY + 78, 0xC5D4E8);

        MapListWidget activeList = this.getActiveList();
        int activeCount = activeList == null ? 0 : activeList.mapCount();
        int totalCount = this.getTotalMapCount();
        String summary = "Showing " + activeCount + " of " + totalCount + " maps";
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(summary),
                this.panelX + 10,
                this.panelY + this.panelHeight - 17,
                0x96A8C2
        );

        if (this.activeTab == MapTab.USER) {
            int hintLeftBound = this.panelX + 170;
            int hintRight = this.panelX + this.panelWidth - 10;
            int hintMaxWidth = Math.max(60, hintRight - hintLeftBound);
            String hintText = this.textRenderer.trimToWidth(
                    "Rate user maps by clicking the XP rows on each map card",
                    hintMaxWidth
            );
            int hintWidth = this.textRenderer.getWidth(hintText);
            int hintX = Math.max(hintLeftBound, hintRight - hintWidth);
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(hintText),
                    hintX,
                    this.panelY + 78,
                    0x89B8FF
            );
        }
    }

    private void setActiveTab(MapTab tab) {
        this.activeTab = tab == null ? MapTab.BHOP : tab;

        if (this.bhopButtonWidget != null) {
            this.bhopButtonWidget.active = this.activeTab != MapTab.BHOP;
        }
        if (this.surfButtonWidget != null) {
            this.surfButtonWidget.active = this.activeTab != MapTab.SURF;
        }
        if (this.kzButtonWidget != null) {
            this.kzButtonWidget.active = this.activeTab != MapTab.KZ;
        }
        if (this.arenaButtonWidget != null) {
            this.arenaButtonWidget.active = this.activeTab != MapTab.ARENA;
        }
        if (this.hnsButtonWidget != null) {
            this.hnsButtonWidget.active = this.activeTab != MapTab.HNS;
        }
        if (this.userButtonWidget != null) {
            this.userButtonWidget.active = this.activeTab != MapTab.USER;
        }

        this.updateUserControlsState();
        this.attachActiveList();
    }

    private void updateUserControlsState() {
        boolean userTab = this.activeTab == MapTab.USER;
        if (this.authorFieldWidget != null) {
            this.authorFieldWidget.setEditable(userTab);
            this.authorFieldWidget.active = userTab;
        }
        if (this.userSortButtonWidget != null) {
            this.userSortButtonWidget.active = userTab;
        }
    }

    private void cycleUserSort() {
        this.userSortMode = this.userSortMode.next();
        this.updateUserSortButtonText();
    }

    private void updateUserSortButtonText() {
        if (this.userSortButtonWidget == null) {
            return;
        }
        this.userSortButtonWidget.setMessage(Text.literal(this.userSortMode.label));
    }

    private void attachActiveList() {
        if (this.bhopListWidget != null) {
            this.remove(this.bhopListWidget);
        }
        if (this.surfListWidget != null) {
            this.remove(this.surfListWidget);
        }
        if (this.kzListWidget != null) {
            this.remove(this.kzListWidget);
        }
        if (this.arenaListWidget != null) {
            this.remove(this.arenaListWidget);
        }
        if (this.hnsListWidget != null) {
            this.remove(this.hnsListWidget);
        }
        if (this.userListWidget != null) {
            this.remove(this.userListWidget);
        }

        MapListWidget activeList = this.getActiveList();
        if (activeList != null) {
            this.addDrawableChild(activeList);
        }
    }

    private MapListWidget getActiveList() {
        return switch (this.activeTab) {
            case BHOP -> this.bhopListWidget;
            case SURF -> this.surfListWidget;
            case KZ -> this.kzListWidget;
            case ARENA -> this.arenaListWidget;
            case HNS -> this.hnsListWidget;
            case USER -> this.userListWidget;
        };
    }

    private void rebuildLists(String rawFilter, String rawAuthorFilter) {
        String filter = rawFilter == null ? "" : rawFilter.trim().toLowerCase(Locale.ROOT);
        String authorFilter = rawAuthorFilter == null ? "" : rawAuthorFilter.trim().toLowerCase(Locale.ROOT);
        int rowWidth = this.panelWidth - 22;
        int scrollbarX = this.panelX + this.panelWidth - 13;

        MapListWidget oldBhopList = this.bhopListWidget;
        MapListWidget oldSurfList = this.surfListWidget;
        MapListWidget oldKzList = this.kzListWidget;
        MapListWidget oldArenaList = this.arenaListWidget;
        MapListWidget oldHnsList = this.hnsListWidget;
        MapListWidget oldUserList = this.userListWidget;

        this.detachListWidget(oldBhopList);
        this.detachListWidget(oldSurfList);
        this.detachListWidget(oldKzList);
        this.detachListWidget(oldArenaList);
        this.detachListWidget(oldHnsList);
        this.detachListWidget(oldUserList);

        this.bhopListWidget = new MapListWidget(this.client, this.width, this.listBottom, this.listTop, LIST_ITEM_HEIGHT, rowWidth, scrollbarX);
        this.surfListWidget = new MapListWidget(this.client, this.width, this.listBottom, this.listTop, LIST_ITEM_HEIGHT, rowWidth, scrollbarX);
        this.kzListWidget = new MapListWidget(this.client, this.width, this.listBottom, this.listTop, LIST_ITEM_HEIGHT, rowWidth, scrollbarX);
        this.arenaListWidget = new MapListWidget(this.client, this.width, this.listBottom, this.listTop, LIST_ITEM_HEIGHT, rowWidth, scrollbarX);
        this.hnsListWidget = new MapListWidget(this.client, this.width, this.listBottom, this.listTop, LIST_ITEM_HEIGHT, rowWidth, scrollbarX);
        this.userListWidget = new MapListWidget(this.client, this.width, this.listBottom, this.listTop, LIST_ITEM_HEIGHT, rowWidth, scrollbarX);

        List<DataManager.MapData> userMaps = new ArrayList<>();

        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData == null || "spawn".equals(mapData.name)) {
                continue;
            }
            if (!this.matchesFilter(mapData, filter)) {
                continue;
            }

            double avgTime = this.getAverageTimeForMap(mapData.name);
            DataManager.RecordData recordData = DataManager.getRecord(mapData.name);
            if (recordData == null) {
                recordData = new DataManager.RecordData("No Record", mapData.name, 0.0D);
            }

            if (mapData.userMap) {
                if (this.matchesAuthorFilter(mapData, authorFilter)) {
                    userMaps.add(mapData);
                }
                this.addUserMapToGamemodeTab(mapData, recordData, avgTime);
                continue;
            }

            if (mapData.arena) {
                this.arenaListWidget.addEntry(
                        new DataManager.RecordData("Arena", mapData.name, 0.0D),
                        avgTime,
                        true,
                        mapData.player_count,
                        mapData.difficulty,
                        false,
                        mapData.ownerName,
                        mapData.description,
                        mapData.play_count,
                        mapData.rating_count,
                        mapData.rating_quality_total,
                        mapData.rating_difficulty_total
                );
                continue;
            }

            if (mapData.hns) {
                this.hnsListWidget.addEntry(
                        new DataManager.RecordData("HNS", mapData.name, 0.0D),
                        avgTime,
                        true,
                        mapData.player_count,
                        mapData.difficulty,
                        false,
                        mapData.ownerName,
                        mapData.description,
                        mapData.play_count,
                        mapData.rating_count,
                        mapData.rating_quality_total,
                        mapData.rating_difficulty_total
                );
                continue;
            }

            if (mapData.kz) {
                this.kzListWidget.addEntry(
                        recordData,
                        avgTime,
                        false,
                        mapData.player_count,
                        mapData.difficulty,
                        false,
                        mapData.ownerName,
                        mapData.description,
                        mapData.play_count,
                        mapData.rating_count,
                        mapData.rating_quality_total,
                        mapData.rating_difficulty_total
                );
                continue;
            }

            if (mapData.surf) {
                this.surfListWidget.addEntry(
                        recordData,
                        avgTime,
                        false,
                        mapData.player_count,
                        mapData.difficulty,
                        false,
                        mapData.ownerName,
                        mapData.description,
                        mapData.play_count,
                        mapData.rating_count,
                        mapData.rating_quality_total,
                        mapData.rating_difficulty_total
                );
            } else {
                this.bhopListWidget.addEntry(
                        recordData,
                        avgTime,
                        false,
                        mapData.player_count,
                        mapData.difficulty,
                        false,
                        mapData.ownerName,
                        mapData.description,
                        mapData.play_count,
                        mapData.rating_count,
                        mapData.rating_quality_total,
                        mapData.rating_difficulty_total
                );
            }
        }

        this.sortUserMaps(userMaps);
        for (DataManager.MapData userMap : userMaps) {
            if (userMap == null) {
                continue;
            }
            DataManager.RecordData recordData = DataManager.getRecord(userMap.name);
            if (recordData == null) {
                recordData = new DataManager.RecordData("No Record", userMap.name, 0.0D);
            }
            this.userListWidget.addEntry(
                    recordData,
                    this.getAverageTimeForMap(userMap.name),
                    false,
                    userMap.player_count,
                    userMap.difficulty,
                    true,
                    userMap.ownerName,
                    userMap.description,
                    userMap.play_count,
                    userMap.rating_count,
                    userMap.rating_quality_total,
                    userMap.rating_difficulty_total
            );
        }
    }

    private void detachListWidget(MapListWidget listWidget) {
        if (listWidget != null) {
            this.remove(listWidget);
        }
    }

    private void addUserMapToGamemodeTab(DataManager.MapData mapData, DataManager.RecordData recordData, double avgTime) {
        if (mapData == null) {
            return;
        }
        if (mapData.arena) {
            this.arenaListWidget.addEntry(
                    new DataManager.RecordData("Arena", mapData.name, 0.0D),
                    avgTime,
                    true,
                    mapData.player_count,
                    mapData.difficulty,
                    true,
                    mapData.ownerName,
                    mapData.description,
                    mapData.play_count,
                    mapData.rating_count,
                    mapData.rating_quality_total,
                    mapData.rating_difficulty_total
            );
            return;
        }
        if (mapData.hns) {
            this.hnsListWidget.addEntry(
                    new DataManager.RecordData("HNS", mapData.name, 0.0D),
                    avgTime,
                    true,
                    mapData.player_count,
                    mapData.difficulty,
                    true,
                    mapData.ownerName,
                    mapData.description,
                    mapData.play_count,
                    mapData.rating_count,
                    mapData.rating_quality_total,
                    mapData.rating_difficulty_total
            );
            return;
        }
        if (mapData.kz) {
            this.kzListWidget.addEntry(
                    recordData,
                    avgTime,
                    false,
                    mapData.player_count,
                    mapData.difficulty,
                    true,
                    mapData.ownerName,
                    mapData.description,
                    mapData.play_count,
                    mapData.rating_count,
                    mapData.rating_quality_total,
                    mapData.rating_difficulty_total
            );
            return;
        }
        if (mapData.surf) {
            this.surfListWidget.addEntry(
                    recordData,
                    avgTime,
                    false,
                    mapData.player_count,
                    mapData.difficulty,
                    true,
                    mapData.ownerName,
                    mapData.description,
                    mapData.play_count,
                    mapData.rating_count,
                    mapData.rating_quality_total,
                    mapData.rating_difficulty_total
            );
            return;
        }
        this.bhopListWidget.addEntry(
                recordData,
                avgTime,
                false,
                mapData.player_count,
                mapData.difficulty,
                true,
                mapData.ownerName,
                mapData.description,
                mapData.play_count,
                mapData.rating_count,
                mapData.rating_quality_total,
                mapData.rating_difficulty_total
        );
    }

    private void sortUserMaps(List<DataManager.MapData> userMaps) {
        if (userMaps == null || userMaps.size() <= 1) {
            return;
        }

        Comparator<DataManager.MapData> byNameAsc = Comparator
                .comparing((DataManager.MapData map) -> map == null || map.name == null ? "" : map.name, String.CASE_INSENSITIVE_ORDER);
        Comparator<DataManager.MapData> byOwnerAsc = Comparator
                .comparing((DataManager.MapData map) -> map == null || map.ownerName == null ? "" : map.ownerName, String.CASE_INSENSITIVE_ORDER);
        Comparator<DataManager.MapData> byPlaysDesc = Comparator
                .comparingInt((DataManager.MapData map) -> map == null ? 0 : map.play_count)
                .reversed();
        Comparator<DataManager.MapData> byRatingCountDesc = Comparator
                .comparingInt((DataManager.MapData map) -> map == null ? 0 : map.rating_count)
                .reversed();
        Comparator<DataManager.MapData> byQualityDesc = Comparator
                .comparingDouble(this::getAverageQualityRating)
                .reversed();
        Comparator<DataManager.MapData> byDifficultyDesc = Comparator
                .comparingDouble(this::getAverageDifficultyRating)
                .reversed();

        Comparator<DataManager.MapData> comparator = switch (this.userSortMode) {
            case MOST_PLAYS -> byPlaysDesc
                    .thenComparing(byQualityDesc)
                    .thenComparing(byRatingCountDesc)
                    .thenComparing(byNameAsc);
            case AUTHOR_AZ -> byOwnerAsc
                    .thenComparing(byQualityDesc)
                    .thenComparing(byRatingCountDesc)
                    .thenComparing(byNameAsc);
            case DIFFICULTY_RATED -> byDifficultyDesc
                    .thenComparing(byRatingCountDesc)
                    .thenComparing(byQualityDesc)
                    .thenComparing(byNameAsc);
            case NAME_AZ -> byNameAsc;
            case HIGHEST_RATED -> byQualityDesc
                    .thenComparing(byRatingCountDesc)
                    .thenComparing(byPlaysDesc)
                    .thenComparing(byNameAsc);
        };
        userMaps.sort(comparator);
    }

    private double getAverageQualityRating(DataManager.MapData mapData) {
        if (mapData == null || mapData.rating_count <= 0) {
            return 0.0D;
        }
        return (double) mapData.rating_quality_total / (double) mapData.rating_count;
    }

    private double getAverageDifficultyRating(DataManager.MapData mapData) {
        if (mapData == null || mapData.rating_count <= 0) {
            return 0.0D;
        }
        return (double) mapData.rating_difficulty_total / (double) mapData.rating_count;
    }

    private void updateTabButtonLabels() {
        if (this.bhopButtonWidget != null) {
            this.bhopButtonWidget.setMessage(this.tabLabel("Bhop", "B", this.getCount(this.bhopListWidget)));
        }
        if (this.surfButtonWidget != null) {
            this.surfButtonWidget.setMessage(this.tabLabel("Surf", "S", this.getCount(this.surfListWidget)));
        }
        if (this.kzButtonWidget != null) {
            this.kzButtonWidget.setMessage(this.tabLabel("KZ", "KZ", this.getCount(this.kzListWidget)));
        }
        if (this.arenaButtonWidget != null) {
            this.arenaButtonWidget.setMessage(this.tabLabel("Arena", "A", this.getCount(this.arenaListWidget)));
        }
        if (this.hnsButtonWidget != null) {
            this.hnsButtonWidget.setMessage(this.tabLabel("HNS", "H", this.getCount(this.hnsListWidget)));
        }
        if (this.userButtonWidget != null) {
            this.userButtonWidget.setMessage(this.tabLabel("User", "U", this.getCount(this.userListWidget)));
        }
    }

    private Text tabLabel(String fullName, String shortName, int count) {
        int maxTextWidth = Math.max(0, this.tabWidth - 8);
        String full = fullName + " (" + count + ")";
        if (this.textRenderer == null || this.textRenderer.getWidth(full) <= maxTextWidth) {
            return Text.literal(full);
        }
        String compact = shortName + " (" + count + ")";
        if (this.textRenderer.getWidth(compact) <= maxTextWidth) {
            return Text.literal(compact);
        }
        return Text.literal(shortName);
    }

    private boolean matchesFilter(DataManager.MapData mapData, String filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        String searchable = (
                safeLower(mapData.name) + " " +
                        safeLower(mapData.ownerName) + " " +
                        safeLower(mapData.description)
        );
        return searchable.contains(filter);
    }

    private boolean matchesAuthorFilter(DataManager.MapData mapData, String authorFilter) {
        if (authorFilter == null || authorFilter.isEmpty()) {
            return true;
        }
        return safeLower(mapData.ownerName).contains(authorFilter);
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private double getAverageTimeForMap(String mapName) {
        double total = 0.0D;
        double count = 0.0D;
        for (DataManager.RecordData personalRecordData : Minehop.personalRecordList) {
            if (personalRecordData != null && mapName.equals(personalRecordData.map_name)) {
                total += personalRecordData.time;
                count += 1.0D;
            }
        }
        if (count <= 0.0D) {
            return 0.0D;
        }
        return total / count;
    }

    private int getTotalMapCount() {
        int total = 0;
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData != null && mapData.name != null && !"spawn".equals(mapData.name)) {
                total++;
            }
        }
        return total;
    }

    private int getCount(MapListWidget listWidget) {
        return listWidget == null ? 0 : listWidget.mapCount();
    }

    private boolean hasMapListChanged() {
        int currentSize = Minehop.mapList == null ? 0 : Minehop.mapList.size();
        int currentHash = this.computeMapListHash();
        return currentSize != this.lastMapListSize || currentHash != this.lastMapListHash;
    }

    private void updateMapListSignature() {
        this.lastMapListSize = Minehop.mapList == null ? 0 : Minehop.mapList.size();
        this.lastMapListHash = this.computeMapListHash();
    }

    private int computeMapListHash() {
        if (Minehop.mapList == null || Minehop.mapList.isEmpty()) {
            return 0;
        }
        int hash = 1;
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData == null) {
                hash = 31 * hash;
                continue;
            }
            hash = 31 * hash + Objects.hash(
                    mapData.name,
                    mapData.player_count,
                    mapData.arena,
                    mapData.hns,
                    mapData.surf,
                    mapData.kz,
                    mapData.movement_override,
                    mapData.movement_sv_friction,
                    mapData.movement_sv_accelerate,
                    mapData.movement_sv_airaccelerate,
                    mapData.movement_sv_maxairspeed,
                    mapData.movement_sv_jump_impulse,
                    mapData.movement_speed_mul,
                    mapData.movement_sv_gravity,
                    mapData.movement_sv_stopspeed,
                    mapData.movement_speed_coefficient,
                    mapData.movement_auto_step_up,
                    mapData.movement_css_crouch_jump,
                    mapData.movement_fall_damage,
                    mapData.userMap,
                    mapData.ownerName,
                    mapData.description,
                    mapData.difficulty,
                    mapData.play_count,
                    mapData.rating_count,
                    mapData.rating_quality_total,
                    mapData.rating_difficulty_total
            );
        }
        return hash;
    }

    private void computeLayout() {
        int maxPanelWidth = Math.max(180, this.width - 8);
        int availableHeight = Math.max(120, this.height - 8);

        int desiredWidth = this.width - 24;
        int desiredHeight = this.height - 24;

        this.panelWidth = Math.min(960, Math.max(320, desiredWidth));
        this.panelHeight = Math.min(620, Math.max(340, desiredHeight));

        this.panelWidth = Math.min(this.panelWidth, maxPanelWidth);
        this.panelMaxScroll = Math.max(0, this.panelHeight - availableHeight);
        this.panelScrollOffset = MathHelper.clamp(this.panelScrollOffset, 0, this.panelMaxScroll);

        this.panelX = (this.width - this.panelWidth) / 2;
        if (this.panelMaxScroll > 0) {
            this.panelY = 4 - this.panelScrollOffset;
        } else {
            this.panelY = (this.height - this.panelHeight) / 2;
        }

        this.listTop = this.panelY + 112;
        this.listBottom = this.panelY + this.panelHeight - 28;
        if (this.listBottom - this.listTop < 70) {
            this.listBottom = this.listTop + 70;
        }

        if (this.listBottom > this.height - 4) {
            this.listBottom = this.height - 4;
        }
        this.listTop = MathHelper.clamp(this.listTop, 0, Math.max(0, this.listBottom - 70));
    }

    private boolean isMouseInsidePanel(double mouseX, double mouseY) {
        return mouseX >= this.panelX
                && mouseX <= this.panelX + this.panelWidth
                && mouseY >= this.panelY
                && mouseY <= this.panelY + this.panelHeight;
    }

    private enum MapTab {
        BHOP,
        SURF,
        KZ,
        ARENA,
        HNS,
        USER
    }

    private enum UserSortMode {
        HIGHEST_RATED("Sort: Highest Rated"),
        MOST_PLAYS("Sort: Most Plays"),
        AUTHOR_AZ("Sort: Author A-Z"),
        DIFFICULTY_RATED("Sort: Rated Difficulty"),
        NAME_AZ("Sort: Name A-Z");

        private final String label;

        UserSortMode(String label) {
            this.label = label;
        }

        private UserSortMode next() {
            UserSortMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }
}
