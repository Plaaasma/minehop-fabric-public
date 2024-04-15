package net.nerdorg.minehop.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.screen.widget.MapListWidget;

@Environment(EnvType.CLIENT)
public class SelectMapScreen extends Screen {
    private MapListWidget bhopListWidget;
    private MapListWidget arenaListWidget;
    private MapListWidget hnsListWidget;
    private TextFieldWidget textFieldWidget;
    private ButtonWidget bhopButtonWidget;
    private ButtonWidget arenaButtonWidget;
    private ButtonWidget hnsButtonWidget;

    private String lastFieldText = "";

    private boolean bhop_tab = true;
    private boolean arena_tab = false;

    public SelectMapScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.textFieldWidget = new TextFieldWidget(this.client.textRenderer, (this.width / 2) - (128 / 2), 16, 128, 14, Text.literal("Map Filter"));
        this.addSelectableChild(this.textFieldWidget);

        this.bhopButtonWidget = ButtonWidget.builder(Text.of("Bhop"), (press) -> {
                    bhop_tab = true;
                    arena_tab = false;
                    this.hnsButtonWidget.active = true;
                    this.hnsListWidget.active = false;
                    this.bhopButtonWidget.active = false;
                    this.bhopListWidget.active = true;
                    this.arenaButtonWidget.active = true;
                    this.arenaListWidget.active = false;
                })
                .size(48, 12)
                .build();

        this.bhopButtonWidget.setX((this.width / 2) - (this.bhopButtonWidget.getWidth() / 2) - 56);
        this.bhopButtonWidget.setY(3);
        this.bhopButtonWidget.active = false;
        this.addSelectableChild(this.bhopButtonWidget);

        this.arenaButtonWidget = ButtonWidget.builder(Text.of("Arena"), (press) -> {
                    bhop_tab = false;
                    arena_tab = true;
                    this.hnsButtonWidget.active = true;
                    this.hnsListWidget.active = false;
                    this.bhopButtonWidget.active = true;
                    this.bhopListWidget.active = false;
                    this.arenaButtonWidget.active = false;
                    this.arenaListWidget.active = true;
                })
                .size(48, 12)
                .build();

        this.arenaButtonWidget.setX((this.width / 2) - (this.arenaButtonWidget.getWidth() / 2) + 56);
        this.arenaButtonWidget.setY(3);
        this.arenaButtonWidget.active = true;
        this.addSelectableChild(this.arenaButtonWidget);

        this.hnsButtonWidget = ButtonWidget.builder(Text.of("HNS"), (press) -> {
                    bhop_tab = false;
                    arena_tab = false;
                    this.hnsButtonWidget.active = false;
                    this.hnsListWidget.active = true;
                    this.bhopButtonWidget.active = true;
                    this.bhopListWidget.active = false;
                    this.arenaButtonWidget.active = true;
                    this.arenaListWidget.active = false;
                })
                .size(48, 12)
                .build();

        this.hnsButtonWidget.setX((this.width / 2) - (this.hnsButtonWidget.getWidth() / 2));
        this.hnsButtonWidget.setY(3);
        this.hnsButtonWidget.active = true;
        this.addSelectableChild(this.hnsButtonWidget);

        this.bhopListWidget = new MapListWidget(this.client, this.width, this.height - 32, 32, 20);
        this.arenaListWidget = new MapListWidget(this.client, this.width, this.height - 32, 32, 20);
        this.hnsListWidget = new MapListWidget(this.client, this.width, this.height - 32, 32, 20);
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (!mapData.name.equals("spawn")) {
                double avgTime = 0;
                double recordCount = 0;
                for (DataManager.RecordData personalRecordData : Minehop.personalRecordList) {
                    if (personalRecordData.map_name.equals(mapData.name)) {
                        recordCount += 1;
                        avgTime += personalRecordData.time;
                    }
                }
                recordCount = recordCount == 0 ? 1 : recordCount;
                avgTime = avgTime / recordCount;

                if (mapData.arena) {
                    DataManager.RecordData recordData = new DataManager.RecordData(mapData.name, mapData.name, 1000000);;
                    this.arenaListWidget.addEntry(recordData, avgTime, true);
                }
                else if (mapData.hns) {
                    DataManager.RecordData recordData = new DataManager.RecordData(mapData.name, mapData.name, 1000000);;
                    this.hnsListWidget.addEntry(recordData, avgTime, true);
                }
                else {
                    DataManager.RecordData recordData = DataManager.getRecord(mapData.name);
                    if (recordData == null) {
                        recordData = new DataManager.RecordData(mapData.name, mapData.name, 1000000);
                    }
                    this.bhopListWidget.addEntry(recordData, avgTime, false);
                }
            }
        }

        this.bhopListWidget.active = false;
        this.arenaListWidget.active = false;
        this.hnsListWidget.active = false;

        this.addSelectableChild(this.bhopListWidget);
        this.addSelectableChild(this.arenaListWidget);
        this.addSelectableChild(this.hnsListWidget);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        this.textFieldWidget.render(context, mouseX, mouseY, delta);
        String fieldText = this.textFieldWidget.getText();

        if (!fieldText.equals(this.lastFieldText)) {
            MapListWidget newBhopListWidget = new MapListWidget(this.client, this.width, this.height - 32, 32, 20);
            MapListWidget newArenaListWidget = new MapListWidget(this.client, this.width, this.height - 32, 32, 20);
            MapListWidget newHNSListWidget = new MapListWidget(this.client, this.width, this.height - 32, 32, 20);
            if (!fieldText.equals("")) {
                for (DataManager.MapData mapData : Minehop.mapList) {
                    if (!mapData.name.equals("spawn") && mapData.name.contains(fieldText)) {
                        double avgTime = 0;
                        double recordCount = 0;
                        for (DataManager.RecordData personalRecordData : Minehop.personalRecordList) {
                            if (personalRecordData.map_name.equals(mapData.name)) {
                                recordCount += 1;
                                avgTime += personalRecordData.time;
                            }
                        }
                        recordCount = recordCount == 0 ? 1 : recordCount;
                        avgTime = avgTime / recordCount;

                        if (mapData.arena) {
                            DataManager.RecordData recordData = new DataManager.RecordData(mapData.name, mapData.name, 1000000);;
                            newArenaListWidget.addEntry(recordData, avgTime, true);
                        } else if (mapData.hns) {
                            DataManager.RecordData recordData = new DataManager.RecordData(mapData.name, mapData.name, 1000000);;
                            newHNSListWidget.addEntry(recordData, avgTime, true);
                        } else {
                            DataManager.RecordData recordData = DataManager.getRecord(mapData.name);
                            if (recordData == null) {
                                recordData = new DataManager.RecordData(mapData.name, mapData.name, 1000000);;
                            }
                            newBhopListWidget.addEntry(recordData, avgTime, false);
                        }
                    }
                }
            } else {
                for (DataManager.MapData mapData : Minehop.mapList) {
                    if (!mapData.name.equals("spawn")) {
                        double avgTime = 0;
                        double recordCount = 0;
                        for (DataManager.RecordData personalRecordData : Minehop.personalRecordList) {
                            if (personalRecordData.map_name.equals(mapData.name)) {
                                recordCount += 1;
                                avgTime += personalRecordData.time;
                            }
                        }
                        recordCount = recordCount == 0 ? 1 : recordCount;
                        avgTime = avgTime / recordCount;

                        if (mapData.arena) {
                            DataManager.RecordData recordData = new DataManager.RecordData(mapData.name, mapData.name, 1000000);;
                            newArenaListWidget.addEntry(recordData, avgTime, true);
                        } else if (mapData.hns) {
                            DataManager.RecordData recordData = new DataManager.RecordData(mapData.name, mapData.name, 1000000);;
                            newHNSListWidget.addEntry(recordData, avgTime, true);
                        } else {
                            DataManager.RecordData recordData = DataManager.getRecord(mapData.name);
                            if (recordData == null) {
                                recordData = new DataManager.RecordData(mapData.name, mapData.name, 1000000);;
                            }
                            newBhopListWidget.addEntry(recordData, avgTime, false);
                        }
                    }
                }
            }

            this.remove(this.bhopListWidget);
            this.bhopListWidget = newBhopListWidget;
            this.addSelectableChild(this.bhopListWidget);

            this.remove(this.arenaListWidget);
            this.arenaListWidget = newArenaListWidget;
            this.addSelectableChild(this.arenaListWidget);

            this.remove(this.hnsListWidget);
            this.hnsListWidget = newHNSListWidget;
            this.addSelectableChild(this.hnsListWidget);

            this.lastFieldText = fieldText;
        }

        if (this.bhop_tab && this.bhopListWidget != null) {
            this.bhopListWidget.render(context, mouseX, mouseY, delta);
        }
        else if (this.arena_tab && this.arenaListWidget != null) {
            this.arenaListWidget.render(context, mouseX, mouseY, delta);
        }
        else {
            if (this.hnsListWidget != null) {
                this.hnsListWidget.render(context, mouseX, mouseY, delta);
            }
        }

        if (this.bhopButtonWidget != null) {
            this.bhopButtonWidget.render(context, mouseX, mouseY, delta);
        }

        if (this.hnsButtonWidget != null) {
            this.hnsButtonWidget.render(context, mouseX, mouseY, delta);
        }

        if (this.arenaButtonWidget != null) {
            this.arenaButtonWidget.render(context, mouseX, mouseY, delta);
        }

//        TextRenderer textRenderer = this.client.textRenderer;
//        context.drawCenteredTextWithShadow(textRenderer, "Map Selection", this.width / 2, 4, Formatting.WHITE.getColorValue());

        super.render(context, mouseX, mouseY, delta);
    }
}
