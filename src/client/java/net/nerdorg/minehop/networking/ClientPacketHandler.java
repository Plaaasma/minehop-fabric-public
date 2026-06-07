package net.nerdorg.minehop.networking;

import com.mojang.datafixers.util.Pair;
import com.mojang.util.UUIDTypeAdapter;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.anticheat.ProcessChecker;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.client.SurfStickPreviewState;
import net.nerdorg.minehop.client.BoundsStickPreviewState;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.client.CustomPlayerEntityRenderer;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.networking.payloads.*;
import net.nerdorg.minehop.screen.AntiCheatScreen;
import net.nerdorg.minehop.screen.MapCreationScreen;
import net.nerdorg.minehop.screen.SurfStickSettingsScreen;
import net.nerdorg.minehop.screen.SelectMapScreen;
import net.nerdorg.minehop.screen.ZoneStickSettingsScreen;
import org.joml.Vector3i;

import java.util.*;

public class ClientPacketHandler {
    private static boolean shouldApplyAuthoritativeListSync(MinecraftClient client) {
        if (client == null) {
            return false;
        }
        // In integrated singleplayer, client and server share static state. Applying list sync packets here can
        // overwrite authoritative server lists (e.g. checkpoints) from client-side payload projections.
        return client.getServer() == null;
    }

    public static void sortMapList(List<DataManager.MapData> mapList) {
        Collections.sort(mapList, new Comparator<DataManager.MapData>() {
            @Override
            public int compare(DataManager.MapData m1, DataManager.MapData m2) {
                return m1.name.compareToIgnoreCase(m2.name);
            }
        });
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, (payload, ctx) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            ctx.client().execute(() -> {
                // Assign the read values to your variables or fields here
                Minehop.o_sv_friction = payload.sv_friction();
                Minehop.o_sv_accelerate = payload.sv_accelerate();
                Minehop.o_sv_airaccelerate = payload.sv_airaccelerate();
                Minehop.o_sv_maxairspeed = payload.sv_maxairspeed();
                Minehop.o_sv_jump_impulse = payload.sv_jump_impulse();
                Minehop.o_speed_mul = payload.speed_mul();
                Minehop.o_sv_gravity = payload.sv_gravity();
                Minehop.o_speed_coefficient = payload.speedCoef();
                Minehop.o_speed_cap = payload.speedCap();
                Minehop.o_auto_step_up = payload.autoStepUp();
                Minehop.o_css_crouch_jump = payload.cssCrouchJump();
                Minehop.o_hns = payload.isHNS();
                Minehop.o_enabled = payload.isEnabled();
                Minehop.o_fall_damage = payload.fallDamage();
                Minehop.o_sv_stopspeed = payload.sv_stopspeed();

                Minehop.receivedConfig = true;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SurfStickPreviewPayload.ID, (payload, ctx) -> {
            ctx.client().execute(() -> {
                if (payload.clear()) {
                    SurfStickPreviewState.clear();
                } else {
                    SurfStickPreviewState.update(
                            payload.points(),
                            payload.width(),
                            payload.drop(),
                            payload.oneSided(),
                            payload.outsideCurve()
                    );
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(BoundsStickSelectionPayload.ID, (payload, ctx) -> {
            ctx.client().execute(() -> {
                BlockPos first = payload.hasFirst() ? payload.first() : null;
                BlockPos second = payload.hasSecond() ? payload.second() : null;
                BoundsStickPreviewState.update(first, second);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenAntiCheatScreenPayload.ID, (payload, ctx) -> {
            ctx.client().execute(() -> {
                MinecraftClient client = ctx.client();
                if (client == null || client.player == null) {
                    return;
                }
                if (client.currentScreen instanceof AntiCheatScreen existing) {
                    existing.applySnapshotJson(payload.json());
                } else {
                    client.setScreen(new AntiCheatScreen(payload.json()));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenSurfStickSettingsPayload.ID, (payload, ctx) -> {
            ctx.client().execute(() -> {
                MinecraftClient client = ctx.client();
                if (client.player != null) {
                    client.setScreen(
                            new SurfStickSettingsScreen(
                                    payload.width(),
                                    payload.drop(),
                                    payload.textureBlockId(),
                                    payload.oneSided(),
                                    payload.outsideCurve(),
                                    payload.renderMode(),
                                    payload.wireframeColor(),
                                    payload.wireframeFill(),
                                    payload.wireframeFillColor(),
                                    payload.wireframeFillAlpha(),
                                    payload.editingExisting()
                            )
                    );
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenZoneStickSettingsPayload.ID, (payload, ctx) -> {
            ctx.client().execute(() -> {
                MinecraftClient client = ctx.client();
                if (client.player != null) {
                    client.setScreen(
                            new ZoneStickSettingsScreen(
                                    payload.zoneType(),
                                    payload.mapName(),
                                    payload.checkpointIndex(),
                                    payload.checkpointEditable(),
                                    payload.preserveSpeed(),
                                    payload.preserveSpeedEditable()
                            )
                    );
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ZoneSyncIDPayload.ID, (payload, ctx) -> {
            BlockPos pos1 = new BlockPos((int) payload.pos1().x, (int) payload.pos1().y, (int) payload.pos1().z);
            BlockPos pos2 = new BlockPos((int) payload.pos2().x, (int) payload.pos2().y, (int) payload.pos2().z);

            MinecraftClient client = ctx.client();
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            client.execute(() -> {
                // Assign the read values to your variables or fields here
                Entity entity = client.world.getEntityById(payload.entityId());
                if (entity instanceof ResetEntity resetEntity) {
                    resetEntity.setCorner1(pos1);
                    resetEntity.setCorner2(pos2);
                    resetEntity.setPairedMap(payload.name());
                    resetEntity.setCheckIndex(payload.check_index());
                }
                else if (entity instanceof StartEntity startEntity) {
                    startEntity.setCorner1(pos1);
                    startEntity.setCorner2(pos2);
                    startEntity.setPairedMap(payload.name());
                }
                else if (entity instanceof EndEntity endEntity) {
                    endEntity.setCorner1(pos1);
                    endEntity.setCorner2(pos2);
                    endEntity.setPairedMap(payload.name());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SelfVTogglePayload.ID, (payload, ctx) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            ctx.client().execute(() -> {
              //  MinehopClient.hideSelf = !MinehopClient.hideSelf;
                ConfigWrapper.config.hideSelf = !ConfigWrapper.config.hideSelf;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OtherVTogglePayload.ID, (payload, ctx) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            ctx.client().execute(() -> {
             //   MinehopClient.hideOthers = !MinehopClient.hideOthers;
                ConfigWrapper.config.hideOthers = !ConfigWrapper.config.hideOthers;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OtherVTogglePayload.ID, (payload, ctx) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            ctx.client().execute(() -> {
                //MinehopClient.hideReplay = !MinehopClient.hideReplay;
                ConfigWrapper.config.hideReplay = !ConfigWrapper.config.hideReplay;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SendSpectatorsPayload.ID, (payload, ctx) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            String buff = payload.spectatorBuff();
            ctx.client().execute(() -> {
                List<String> newSpectatorList = new ArrayList<>();
                String splitBuff[] = buff.split("~");
                int stringCount = Integer.parseInt(splitBuff[0]);


                for (int i = 1; i < stringCount; i++) {
                    String spectatorName = splitBuff[i]; // This reads a string from the buffer
                    newSpectatorList.add(spectatorName);
                }

                MinehopClient.spectatorList = newSpectatorList;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SendEfficiencyPayload.ID, (payload, ctx) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            double efficiency = payload.efficiency();

            MinecraftClient client = ctx.client();
            client.execute(() -> {
                if (efficiency != 0) {
                    MinehopClient.last_efficiency = efficiency;
                }
                else {
                    if (Minehop.efficiencyListMap.containsKey(client.player.getNameForScoreboard())) {
                        List<Double> efficiencyList = Minehop.efficiencyListMap.get(client.player.getNameForScoreboard());
                        if (efficiencyList != null && efficiencyList.size() > 1) {
                            double averageEfficiency = efficiencyList.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
                            MinehopClient.last_efficiency = averageEfficiency;
                            Minehop.efficiencyListMap.put(client.player.getNameForScoreboard(), new ArrayList<>());
                        }
                    }
                }
                sendSpecEfficiency();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(RunTimerHudPayload.ID, (payload, ctx) -> {
            MinecraftClient client = ctx.client();
            client.execute(() -> {
                if (!payload.visible()) {
                    MinehopClient.runTimerHudVisible = false;
                    return;
                }
                MinehopClient.runTimerHudVisible = true;
                MinehopClient.runTimerHudTime = payload.time();
                MinehopClient.runTimerHudPb = payload.personalBest();
                MinehopClient.runTimerHudUpdatedAtMs = System.currentTimeMillis();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ResetVelocityCarryPayload.ID, (payload, ctx) -> {
            MinecraftClient client = ctx.client();
            client.execute(() -> {
                MinehopClient.resetCarryX = payload.x();
                MinehopClient.resetCarryY = payload.y();
                MinehopClient.resetCarryZ = payload.z();
                MinehopClient.resetCarryTicks = Math.max(1, payload.ticks());
                if (client.player != null) {
                    client.player.setVelocity(MinehopClient.resetCarryX, MinehopClient.resetCarryY, MinehopClient.resetCarryZ);
                    client.player.setOnGround(false);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CSpecEfficiencyPayload.ID, (payload, ctx) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            double last_jump_speed = payload.last_jump_speed();
            int jump_count = payload.jump_count();
            double last_efficiency = payload.last_efficiency();

            ctx.client().execute(() -> {
                MinehopClient.last_jump_speed = last_jump_speed;
                MinehopClient.jump_count = jump_count;
                MinehopClient.last_efficiency = last_efficiency;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenMapScreenPayload.ID, (payload, ctx) -> {
            String title = payload.title();
            MinecraftClient client = ctx.client();
            client.execute(() -> {
                client.setScreen(new SelectMapScreen(Text.literal(title)));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenMapCreatorScreenPayload.ID, (payload, ctx) -> {
            MinecraftClient client = ctx.client();
            client.execute(() -> {
                if (client.player != null) {
                    client.setScreen(
                            new MapCreationScreen(
                                    payload.mapName(),
                                    payload.difficulty(),
                                    payload.arena(),
                                    payload.hns(),
                                    payload.surf(),
                                    payload.kz(),
                                    payload.movementOverride(),
                                    payload.movementSvFriction(),
                                    payload.movementSvAccelerate(),
                                    payload.movementSvAiraccelerate(),
                                    payload.movementSvMaxairspeed(),
                                    payload.movementSvJumpImpulse(),
                                    payload.movementSpeedMul(),
                                    payload.movementSvGravity(),
                                    payload.movementSvStopspeed(),
                                    payload.movementSpeedCoefficient(),
                                    payload.movementAutoStepUp(),
                                    payload.movementCssCrouchJump(),
                                    payload.movementFallDamage(),
                                    payload.checkpointIndex()
                            )
                    );
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SendRecordPayload.ID, (payload, ctx) -> {
            String buff = payload.buff();

            ctx.client().execute(() -> {
                if (!shouldApplyAuthoritativeListSync(ctx.client())) {
                    return;
                }
                if ("#RESET".equals(buff)) {
                    Minehop.recordList = new ArrayList<>();
                    return;
                }

                List<DataManager.RecordData> list = Minehop.recordList;
                String[] lines = buff.split("\\n");

                for (String line : lines) {
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("~", 3);
                    if (parts.length < 3) continue;

                    String mapName = parts[0];
                    String name    = parts[1];

                    double time;
                    try {
                        time = Double.parseDouble(parts[2]);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    for (Iterator<DataManager.RecordData> it = list.iterator(); it.hasNext();) {
                        if (Objects.equals(it.next().map_name, mapName)) {
                            it.remove();
                            break;
                        }
                    }

                    if (time > 0) {
                        list.add(new DataManager.RecordData(name, mapName, time));
                    }
                }

                Minehop.recordList = list;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SendMapPayload.ID, (payload, ctx) -> {
            String raw = payload.buff();
            ctx.client().execute(() -> {
                if (!shouldApplyAuthoritativeListSync(ctx.client())) {
                    return;
                }
                if ("#RESET".equals(raw)) {
                    Minehop.mapList = new ArrayList<>();
                    return;
                }

                String[] buff = raw.split("~", -1);
                if (buff.length < 12) {
                    return;
                }

                List<DataManager.MapData> newMapList = new ArrayList<>(Minehop.mapList);
                String name = buff[0];
                newMapList.removeIf(existing -> existing != null && Objects.equals(existing.name, name));

                double x = parseDoubleSafe(buff, 1, 0.0D);
                double y = parseDoubleSafe(buff, 2, 0.0D);
                double z = parseDoubleSafe(buff, 3, 0.0D);
                double xrot = parseDoubleSafe(buff, 4, 0.0D);
                double yrot = parseDoubleSafe(buff, 5, 0.0D);
                String worldKey = getSafe(buff, 6, "");
                boolean arena = parseBooleanSafe(buff, 7, false);
                boolean hns = parseBooleanSafe(buff, 8, false);
                boolean surf = parseBooleanSafe(buff, 9, false);
                int difficulty = parseIntSafe(buff, 10, 1);
                int player_count = parseIntSafe(buff, 11, 0);

                boolean userMap = parseBooleanSafe(buff, 12, false);
                String ownerUuid = getSafe(buff, 13, "");
                String ownerName = getSafe(buff, 14, "");
                String description = getSafe(buff, 15, "");
                int plotMinX = parseIntSafe(buff, 16, 0);
                int plotMinY = parseIntSafe(buff, 17, 0);
                int plotMinZ = parseIntSafe(buff, 18, 0);
                int plotMaxX = parseIntSafe(buff, 19, 0);
                int plotMaxY = parseIntSafe(buff, 20, 0);
                int plotMaxZ = parseIntSafe(buff, 21, 0);
                String plotGroundBlockId = getSafe(buff, 22, "");
                int playCount = parseIntSafe(buff, 23, 0);
                int ratingCount = parseIntSafe(buff, 24, 0);
                int ratingQualityTotal = parseIntSafe(buff, 25, 0);
                int ratingDifficultyTotal = parseIntSafe(buff, 26, 0);
                boolean kz = parseBooleanSafe(buff, 27, false);
                boolean movementOverride = parseBooleanSafe(buff, 28, false);
                double movementSvFriction = parseDoubleSafe(buff, 29, 4.0D);
                double movementSvAccelerate = parseDoubleSafe(buff, 30, 10.0D);
                double movementSvAiraccelerate = parseDoubleSafe(buff, 31, 100.0D);
                double movementSvMaxairspeed = parseDoubleSafe(buff, 32, 30.0D);
                double movementSvJumpImpulse = parseDoubleSafe(buff, 33, 300.0D);
                double movementSpeedMul = parseDoubleSafe(buff, 34, 3.25D);
                double movementSvGravity = parseDoubleSafe(buff, 35, 800.0D);
                double movementSvStopspeed = parseDoubleSafe(buff, 36, 75.0D);
                double movementSpeedCoefficient = parseDoubleSafe(buff, 37, 1.0D);
                boolean movementAutoStepUp = parseBooleanSafe(buff, 38, true);
                boolean movementCssCrouchJump = parseBooleanSafe(buff, 39, true);
                boolean movementFallDamage = parseBooleanSafe(buff, 40, false);

                DataManager.MapData mapData = new DataManager.MapData(
                        name,
                        x,
                        y,
                        z,
                        xrot,
                        yrot,
                        worldKey,
                        arena,
                        hns,
                        surf,
                        kz,
                        difficulty,
                        player_count,
                        userMap,
                        ownerUuid,
                        ownerName,
                        description,
                        plotMinX,
                        plotMinY,
                        plotMinZ,
                        plotMaxX,
                        plotMaxY,
                        plotMaxZ,
                        plotGroundBlockId,
                        playCount,
                        ratingCount,
                        ratingQualityTotal,
                        ratingDifficultyTotal
                );
                mapData.applyMovementSettings(
                        movementOverride,
                        movementSvFriction,
                        movementSvAccelerate,
                        movementSvAiraccelerate,
                        movementSvMaxairspeed,
                        movementSvJumpImpulse,
                        movementSpeedMul,
                        movementSvGravity,
                        movementSvStopspeed,
                        movementSpeedCoefficient,
                        movementAutoStepUp,
                        movementCssCrouchJump,
                        movementFallDamage
                );
                newMapList.add(mapData);

                sortMapList(newMapList);
                Minehop.mapList = newMapList;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SendPersonalRecordPayload.ID, (payload, ctx) -> {
            String buff = payload.buff();

            ctx.client().execute(() -> {
                if (!shouldApplyAuthoritativeListSync(ctx.client())) {
                    return;
                }
                if ("#RESET".equals(buff)) {
                    Minehop.personalRecordList = new ArrayList<>();
                    return;
                }

                List<DataManager.RecordData> list = Minehop.personalRecordList;
                String[] lines = buff.split("\\n");

                for (String line : lines) {
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("~", 3);
                    if (parts.length < 3) continue;

                    String mapName = parts[0];
                    String name    = parts[1];
                    double time;
                    try {
                        time = Double.parseDouble(parts[2]);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    for (Iterator<DataManager.RecordData> it = list.iterator(); it.hasNext();) {
                        if (Objects.equals(it.next().map_name, mapName)) {
                            it.remove();
                            break;
                        }
                    }

                    if (time > 0) {
                        list.add(new DataManager.RecordData(name, mapName, time));
                    }
                }

                Minehop.personalRecordList = list;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(UpdatePowerPayload.ID, (payload, ctx) -> {
            double power_x = payload.x_power();
            double power_y = payload.y_power();
            double power_z = payload.z_power();

            BlockPos boosterPos = new BlockPos(payload.posX(), payload.posY(), payload.posZ());
            MinecraftClient client = ctx.client();
            // Ensure you are on the main thread when modifying the game or accessing client side only classes
            client.execute(() -> {
                // Assign the read values to your variables or fields here
                new Thread(() -> {
                    BlockEntity blockEntity = client.player.getWorld().getBlockEntity(boosterPos);
                    if (blockEntity instanceof BoostBlockEntity boostBlockEntity) {
                        boostBlockEntity.setXPower(power_x);
                        boostBlockEntity.setYPower(power_y);
                        boostBlockEntity.setZPower(power_z);
                    }
                }).start();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(AntiCheatPayload.ID, (payload, ctx) -> {
            MinecraftClient client = ctx.client();
            client.execute(() -> {
                new Thread(() -> {
                    sendAntiCheatCheck(null);
                }).start();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SetCheaterPayload.ID, (payload, ctx) -> {
            MinecraftClient client = ctx.client();
            ClientWorld world = ctx.client().world;
            client.execute(() -> {
                new Thread(() -> {

                    String UUID = payload.uuid();
                    boolean isCheater = payload.isCheater();

                    PlayerEntity cheater = world.getPlayerByUuid(java.util.UUID.fromString(UUID));

                    if (isCheater) {
                        if (client.player.getUuidAsString().equals(UUID)) {
                                client.getNetworkHandler().sendCommand("map restart");
                        }
                        CustomPlayerEntityRenderer.setPlayerModel(CustomPlayerEntityRenderer.PlayerModel.Cheater, UUID);
                        Minehop.currentCheaters.add(world.getPlayerByUuid(java.util.UUID.fromString(UUID)));
                    }
                    else {
                        CustomPlayerEntityRenderer.setPlayerModel(CustomPlayerEntityRenderer.PlayerModel.Player, UUID);
                        while (Minehop.currentCheaters.contains(world.getPlayerByUuid(java.util.UUID.fromString(UUID)))) {
                            Minehop.currentCheaters.remove(world.getPlayerByUuid(java.util.UUID.fromString(UUID)));
                        }
                    }

                }).start();
            });
        });
    }

    public static void sendHandshake() {
        ClientPlayNetworking.send(new HandshakeIDPayload(Minehop.MOD_VERSION));
    }

    public static void sendSpecEfficiency() {
        ClientPlayNetworking.send(new SSpecEfficiencyPayload(MinehopClient.last_jump_speed, MinehopClient.jump_count, MinehopClient.last_efficiency));
    }

    public static void sendAntiCheatCheck(String checkResults) {
        if (checkResults == null) {checkResults="";}

        ClientPlayNetworking.send(new AntiCheatPayload(checkResults));
    }

    public static void sendEndMapEvent(String map_name, float time) {
        ClientPlayNetworking.send(new MapFinishPayload(map_name, time));
    }

    public static void sendCurrentTime(float time) {
        if (time > MinehopClient.lastSendTime + 0.01) {
            ClientPlayNetworking.send(new SendTimePayload(time));
            MinehopClient.lastSendTime = time;
        }
    }

    public static void sendSurfStickSettings(
            double width,
            double drop,
            String textureBlockId,
            boolean oneSided,
            boolean outsideCurve,
            String renderMode,
            int wireframeColor,
            boolean wireframeFill,
            int wireframeFillColor,
            int wireframeFillAlpha
    ) {
        ClientPlayNetworking.send(
                new SurfStickSettingsPayload(
                        (float) width,
                        (float) drop,
                        textureBlockId,
                        oneSided,
                        outsideCurve,
                        renderMode,
                        wireframeColor,
                        wireframeFill,
                        wireframeFillColor,
                        wireframeFillAlpha
                )
        );
    }

    public static void sendSurfStickCancel() {
        ClientPlayNetworking.send(new SurfStickCancelPayload(true));
    }

    public static void sendSurfStickDelete() {
        ClientPlayNetworking.send(new SurfStickDeletePayload(true));
    }

    public static void sendMapCreatorAction(
            String action,
            String mapName,
            int difficulty,
            boolean arena,
            boolean hns,
            boolean surf,
            boolean kz,
            boolean movementOverride,
            double movementSvFriction,
            double movementSvAccelerate,
            double movementSvAiraccelerate,
            double movementSvMaxairspeed,
            double movementSvJumpImpulse,
            double movementSpeedMul,
            double movementSvGravity,
            double movementSvStopspeed,
            double movementSpeedCoefficient,
            boolean movementAutoStepUp,
            boolean movementCssCrouchJump,
            boolean movementFallDamage,
            int checkpointIndex
    ) {
        ClientPlayNetworking.send(
                new MapCreatorActionPayload(
                        action,
                        mapName,
                        difficulty,
                        arena,
                        hns,
                        surf,
                        kz,
                        movementOverride,
                        movementSvFriction,
                        movementSvAccelerate,
                        movementSvAiraccelerate,
                        movementSvMaxairspeed,
                        movementSvJumpImpulse,
                        movementSpeedMul,
                        movementSvGravity,
                        movementSvStopspeed,
                        movementSpeedCoefficient,
                        movementAutoStepUp,
                        movementCssCrouchJump,
                        movementFallDamage,
                        checkpointIndex
                )
        );
    }

    public static void sendZoneStickSettings(
            String mapName,
            int checkpointIndex,
            boolean applyBounds,
            boolean preserveSpeed
    ) {
        ClientPlayNetworking.send(new ZoneStickSettingsPayload(mapName, checkpointIndex, applyBounds, preserveSpeed));
    }

    public static void sendZoneStickCancel() {
        ClientPlayNetworking.send(new ZoneStickCancelPayload(true));
    }

    public static void sendZoneStickDelete() {
        ClientPlayNetworking.send(new ZoneStickDeletePayload(true));
    }

    private static int parseIntSafe(String[] buff, int index, int fallback) {
        String value = getSafe(buff, index, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDoubleSafe(String[] buff, int index, double fallback) {
        String value = getSafe(buff, index, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBooleanSafe(String[] buff, int index, boolean fallback) {
        String value = getSafe(buff, index, null);
        if (value == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    private static String getSafe(String[] buff, int index, String fallback) {
        if (buff == null || index < 0 || index >= buff.length) {
            return fallback;
        }
        return buff[index];
    }
}
