package net.nerdorg.minehop.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.ReplayEntity;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import net.nerdorg.minehop.entity.custom.Zone;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.replays.ReplayManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class UserPlotManager {
    public static final Identifier PLOTS_DIMENSION_ID = Identifier.of(Minehop.MOD_ID, "plots");
    public static final RegistryKey<World> PLOTS_WORLD_KEY = RegistryKey.of(RegistryKeys.WORLD, PLOTS_DIMENSION_ID);

    private static final int PLOT_SIZE = 512;
    private static final int PLOT_PADDING = 64;
    private static final int PLOT_STRIDE = PLOT_SIZE + PLOT_PADDING;
    private static final int GRID_COLUMNS = 128;
    private static final int PLOT_GROUND_Y = 64;
    private static final int PLOT_DELETE_CLEAR_HEIGHT = 96;
    private static final String DEFAULT_GROUND_BLOCK_ID = "minecraft:grass_block";
    private static final int PLOT_FILL_BLOCKS_PER_TICK = 4096;
    private static final long PLOT_FILL_TIME_BUDGET_NANOS = 2_250_000L;
    private static final int SPAWN_PATCH_RADIUS = 8;
    private static final Set<Block> ILLEGAL_PLOT_GROUND_BLOCKS = Set.of(
            Blocks.BARRIER,
            Blocks.BEDROCK,
            Blocks.STRUCTURE_VOID,
            Blocks.STRUCTURE_BLOCK,
            Blocks.JIGSAW,
            Blocks.COMMAND_BLOCK,
            Blocks.CHAIN_COMMAND_BLOCK,
            Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.LIGHT,
            Blocks.TNT,
            Blocks.FIRE,
            Blocks.SOUL_FIRE,
            Blocks.LAVA,
            Blocks.WATER,
            Blocks.END_PORTAL,
            Blocks.END_GATEWAY,
            Blocks.NETHER_PORTAL
    );
    private static final Set<Item> ILLEGAL_PLOT_ITEMS = Set.of(
            Items.LAVA_BUCKET,
            Items.WATER_BUCKET,
            Items.POWDER_SNOW_BUCKET,
            Items.FLINT_AND_STEEL,
            Items.FIRE_CHARGE,
            Items.TNT_MINECART,
            Items.END_CRYSTAL
    );

    private static final Set<String> RESERVED_MAP_NAMES = Set.of("spawn");
    private static final Set<String> FORCED_CREATIVE_PLAYERS = new HashSet<>();
    private static final Set<String> PLOT_GAMEMODE_OVERRIDES = new HashSet<>();
    private static final ArrayDeque<PlotFillTask> PENDING_FILL_TASKS = new ArrayDeque<>();
    private static final Map<String, PlotFillTask> FILL_TASKS_BY_MAP = new HashMap<>();

    private UserPlotManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UserPlotManager::tickServer);

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return true;
            }
            if (world instanceof ServerWorld serverWorld && canBuildAt(serverPlayer, serverWorld, pos)) {
                return true;
            }
            if (isPlotsWorld(world) && !serverPlayer.hasPermissionLevel(4)) {
                Logger.logActionBar(serverPlayer, "You can only build inside your own plot.");
                return false;
            }
            return true;
        });

        UseBlockCallback.EVENT.register(UserPlotManager::onUseBlock);
        UseItemCallback.EVENT.register(UserPlotManager::onUseItem);
        UseEntityCallback.EVENT.register(UserPlotManager::onUseEntity);
    }

    private static void tickServer(MinecraftServer server) {
        tickCreativeAccess(server);
        processFillQueue(server);
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, net.minecraft.util.Hand hand, BlockHitResult hitResult) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
            return ActionResult.PASS;
        }
        if (isSpawnEggUsageBlocked(serverPlayer, serverWorld, hand)) {
            return ActionResult.FAIL;
        }
        if (isPlotsWorld(serverWorld) && !serverPlayer.hasPermissionLevel(4)) {
            ItemStack heldStack = serverPlayer.getStackInHand(hand);
            if (isIllegalPlotItem(heldStack)) {
                Logger.logActionBar(serverPlayer, "That item is disabled in plot worlds.");
                return ActionResult.FAIL;
            }
            BlockPos clickedPos = hitResult.getBlockPos();
            BlockPos placementPos = clickedPos.offset(hitResult.getSide());
            boolean isPlacement = isPlacementAttempt(heldStack);
            boolean allowed = canBuildAt(serverPlayer, serverWorld, clickedPos)
                    && (!isPlacement || canBuildAt(serverPlayer, serverWorld, placementPos));
            if (!allowed) {
                Logger.logActionBar(serverPlayer, "You can only build inside your own plot.");
                return ActionResult.FAIL;
            }
        }
        return ActionResult.PASS;
    }

    private static ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
            return ActionResult.PASS;
        }
        if (isSpawnEggUsageBlocked(serverPlayer, serverWorld, hand)) {
            return ActionResult.FAIL;
        }
        if (isPlotsWorld(serverWorld) && !serverPlayer.hasPermissionLevel(4)
                && isIllegalPlotItem(serverPlayer.getStackInHand(hand))) {
            Logger.logActionBar(serverPlayer, "That item is disabled in plot worlds.");
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
            return ActionResult.PASS;
        }
        if (isSpawnEggUsageBlocked(serverPlayer, serverWorld, hand)) {
            return ActionResult.FAIL;
        }
        if (isPlotsWorld(serverWorld) && !serverPlayer.hasPermissionLevel(4)
                && !canEditEntity(serverPlayer, entity)) {
            Logger.logActionBar(serverPlayer, "You can only interact with entities inside your own plot.");
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    private static boolean isSpawnEggUsageBlocked(ServerPlayerEntity player, ServerWorld world, Hand hand) {
        if (player == null || world == null || hand == null) {
            return false;
        }
        if (player.hasPermissionLevel(4)) {
            return false;
        }
        if (!isPlotsWorld(world)) {
            return false;
        }
        ItemStack heldStack = player.getStackInHand(hand);
        if (heldStack == null || heldStack.isEmpty()) {
            return false;
        }
        if (!(heldStack.getItem() instanceof SpawnEggItem)) {
            return false;
        }
        Logger.logActionBar(player, "Spawn eggs are disabled in plot worlds.");
        return true;
    }

    private static boolean isPlacementAttempt(ItemStack heldStack) {
        if (heldStack == null || heldStack.isEmpty()) {
            return false;
        }
        return heldStack.getItem() instanceof BlockItem || heldStack.getItem() instanceof BucketItem;
    }

    private static boolean isIllegalPlotItem(ItemStack heldStack) {
        if (heldStack == null || heldStack.isEmpty()) {
            return false;
        }
        Item item = heldStack.getItem();
        if (item instanceof SpawnEggItem || item instanceof BucketItem || ILLEGAL_PLOT_ITEMS.contains(item)) {
            return true;
        }
        if (item instanceof BlockItem blockItem) {
            return !isAllowedPlotPlacedBlock(blockItem.getBlock());
        }
        return false;
    }

    private static void tickCreativeAccess(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player == null) {
                continue;
            }
            String uuidString = player.getUuidAsString();
            if (player.hasPermissionLevel(4) || player.isSpectator()) {
                FORCED_CREATIVE_PLAYERS.remove(uuidString);
                continue;
            }

            boolean shouldHaveCreative = isInsideOwnedPlot(player, player.getPos());
            boolean forcedByPlotSystem = FORCED_CREATIVE_PLAYERS.contains(uuidString);
            boolean hasPlotGamemodeOverride = PLOT_GAMEMODE_OVERRIDES.contains(uuidString);

            if (shouldHaveCreative && !hasPlotGamemodeOverride) {
                if (!player.isCreative()) {
                    if (player.changeGameMode(GameMode.CREATIVE)) {
                        FORCED_CREATIVE_PLAYERS.add(uuidString);
                    }
                } else {
                    FORCED_CREATIVE_PLAYERS.add(uuidString);
                }
            } else if (forcedByPlotSystem) {
                consumeForcedCreativeState(player);
            } else if (!shouldHaveCreative && hasPlotGamemodeOverride) {
                PLOT_GAMEMODE_OVERRIDES.remove(uuidString);
            } else if (player.isCreative() && player.getWorld() instanceof ServerWorld serverWorld && isPlotsWorld(serverWorld)) {
                player.changeGameMode(GameMode.SURVIVAL);
                player.getInventory().clear();
            }
        }
    }

    public static boolean consumeForcedCreativeState(ServerPlayerEntity player) {
        if (player == null || player.hasPermissionLevel(4) || player.isSpectator()) {
            return false;
        }

        String uuidString = player.getUuidAsString();
        boolean wasForced = FORCED_CREATIVE_PLAYERS.remove(uuidString);
        if (!wasForced) {
            return false;
        }

        if (player.isCreative()) {
            player.changeGameMode(GameMode.SURVIVAL);
        }
        player.getInventory().clear();
        return true;
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        FORCED_CREATIVE_PLAYERS.remove(player.getUuidAsString());
        PLOT_GAMEMODE_OVERRIDES.remove(player.getUuidAsString());
    }

    public static int setOwnedPlotGameMode(ServerPlayerEntity player, GameMode gameMode) {
        if (player == null || gameMode == null) {
            return 0;
        }
        if (player.hasPermissionLevel(4)) {
            player.changeGameMode(gameMode);
            Logger.logSuccess(player, "Set plot gamemode to " + formatGameMode(gameMode) + ".");
            return 1;
        }

        DataManager.MapData mapData = getOwnedPlotAt(player, player.getPos());
        if (mapData == null) {
            Logger.logFailure(player, "Stand inside your plot to change plot gamemode.");
            return 0;
        }

        String uuidString = player.getUuidAsString();
        if (gameMode == GameMode.CREATIVE) {
            PLOT_GAMEMODE_OVERRIDES.remove(uuidString);
            if (player.changeGameMode(GameMode.CREATIVE)) {
                FORCED_CREATIVE_PLAYERS.add(uuidString);
            }
            Logger.logSuccess(player, "Set plot gamemode to creative.");
            return 1;
        }

        if (gameMode != GameMode.ADVENTURE && gameMode != GameMode.SURVIVAL) {
            Logger.logFailure(player, "Plot gamemode can only be creative, adventure, or survival.");
            return 0;
        }

        FORCED_CREATIVE_PLAYERS.remove(uuidString);
        PLOT_GAMEMODE_OVERRIDES.add(uuidString);
        player.changeGameMode(gameMode);
        if (!player.isCreative()) {
            player.getInventory().clear();
        }
        Logger.logSuccess(player, "Set plot gamemode to " + formatGameMode(gameMode) + ".");
        return 1;
    }

    public static boolean canOpenMapManager(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissionLevel(4)) {
            return true;
        }
        return getOwnedPlotAt(player, player.getPos()) != null;
    }

    public static boolean canManageMap(ServerPlayerEntity player, DataManager.MapData mapData) {
        if (player == null || mapData == null) {
            return false;
        }
        if (player.hasPermissionLevel(4)) {
            return true;
        }
        if (!isOwnedBy(player, mapData)) {
            return false;
        }
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        if (!isMapInWorld(mapData, serverWorld)) {
            return false;
        }
        return isInsidePlotBounds(mapData, player.getPos())
                && isInsidePlotBuildBounds(mapData, player.getBlockPos());
    }

    public static boolean canManageMapByName(ServerPlayerEntity player, String mapName) {
        DataManager.MapData mapData = DataManager.getMap(mapName);
        return canManageMap(player, mapData);
    }

    public static boolean canBuildAt(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (player == null || world == null || pos == null) {
            return false;
        }
        if (player.hasPermissionLevel(4)) {
            return true;
        }
        if (!isPlotsWorld(world)) {
            return false;
        }
        DataManager.MapData plot = getOwnedPlotAt(player, pos.toCenterPos());
        return plot != null && isInsidePlotBuildBounds(plot, pos);
    }

    public static boolean canEditEntity(ServerPlayerEntity player, Entity entity) {
        if (player == null || entity == null || !(entity.getWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        if (player.hasPermissionLevel(4)) {
            return true;
        }
        if (!isPlotsWorld(serverWorld)) {
            return false;
        }
        DataManager.MapData plot = getOwnedPlotAt(player, entity.getPos());
        return plot != null && isInsidePlotBuildBounds(plot, entity.getBlockPos());
    }

    public static boolean isInsideOwnedPlot(ServerPlayerEntity player, Vec3d position) {
        return getOwnedPlotAt(player, position) != null;
    }

    public static DataManager.MapData getOwnedPlotAt(ServerPlayerEntity player, Vec3d position) {
        if (player == null || position == null || !(player.getWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }
        if (!isPlotsWorld(serverWorld)) {
            return null;
        }
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData == null || !mapData.userMap) {
                continue;
            }
            if (!isOwnedBy(player, mapData)) {
                continue;
            }
            if (!isMapInWorld(mapData, serverWorld)) {
                continue;
            }
            if (isInsidePlotBounds(mapData, position)) {
                return mapData;
            }
        }
        return null;
    }

    public static DataManager.MapData getPlotAt(ServerWorld world, Vec3d position) {
        if (world == null || position == null || !isPlotsWorld(world)) {
            return null;
        }
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData == null || !mapData.userMap) {
                continue;
            }
            if (!isMapInWorld(mapData, world)) {
                continue;
            }
            if (isInsidePlotBounds(mapData, position)) {
                return mapData;
            }
        }
        return null;
    }

    public static DataManager.MapData getAnyOwnedPlot(ServerPlayerEntity player) {
        if (player == null) {
            return null;
        }
        String uuid = player.getUuidAsString();
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData == null || !mapData.userMap) {
                continue;
            }
            if (uuid.equals(mapData.ownerUuid)) {
                return mapData;
            }
        }
        return null;
    }

    public static int createPlot(ServerPlayerEntity player, String rawMapName, String rawGroundBlockId, String rawDescription) {
        if (player == null) {
            return 0;
        }
        if (getAnyOwnedPlot(player) != null) {
            Logger.logFailure(player, "You already own a plot map. Use /plot home to return to it.");
            return 0;
        }

        String mapName = sanitizeMapName(rawMapName);
        if (mapName == null) {
            Logger.logFailure(player, "Plot map name must be 1-128 chars and cannot contain '~'.");
            return 0;
        }
        if (isReservedMapName(mapName)) {
            Logger.logFailure(player, "That map name is reserved and cannot be used for a plot.");
            return 0;
        }
        if (DataManager.getMap(mapName) != null) {
            Logger.logFailure(player, "A map with that name already exists.");
            return 0;
        }

        ServerWorld plotsWorld = getPlotsWorld(player.getServer());
        if (plotsWorld == null) {
            Logger.logFailure(player, "Plots dimension was not found. Make sure data/minehop/dimension/plots.json exists.");
            return 0;
        }

        int freeSlot = findFirstFreePlotSlot();
        int gridX = freeSlot % GRID_COLUMNS;
        int gridZ = freeSlot / GRID_COLUMNS;

        int minX = gridX * PLOT_STRIDE;
        int minZ = gridZ * PLOT_STRIDE;
        int maxX = minX + PLOT_SIZE;
        int maxZ = minZ + PLOT_SIZE;

        GroundBlockValidation groundValidation = validateGroundBlockId(rawGroundBlockId, true);
        if (!groundValidation.valid) {
            Logger.logFailure(player, groundValidation.errorMessage);
            return 0;
        }
        String groundBlockId = groundValidation.blockId;
        String description = DescriptionCensor.sanitizeAndMaybeCensor(rawDescription);

        double spawnX = minX + (PLOT_SIZE / 2.0D);
        double spawnY = PLOT_GROUND_Y + 2.0D;
        double spawnZ = minZ + (PLOT_SIZE / 2.0D);

        DataManager.MapData mapData = new DataManager.MapData(
                mapName,
                spawnX,
                spawnY,
                spawnZ,
                0.0D,
                0.0D,
                plotsWorld.getRegistryKey().toString(),
                false,
                false,
                false,
                false,
                1,
                0,
                true,
                player.getUuidAsString(),
                player.getNameForScoreboard(),
                description,
                minX,
                PLOT_GROUND_Y,
                minZ,
                maxX,
                PLOT_GROUND_Y + PLOT_DELETE_CLEAR_HEIGHT,
                maxZ,
                groundBlockId
        );
        mapData.copyMovementFrom(ConfigWrapper.config, false);

        Minehop.mapList.add(mapData);
        enqueuePlotGroundFill(plotsWorld, mapData, groundBlockId, player, true);
        DataManager.saveData(plotsWorld, DataManager.mapListLocation, Minehop.mapList);
        syncMaps(player.getServer());

        player.teleportTo(ZoneUtil.makeTeleportTarget(
                plotsWorld,
                new Vec3d(spawnX, spawnY, spawnZ),
                0.0F,
                0.0F
        ));
        if (!player.hasPermissionLevel(4) && !player.isCreative()) {
            if (player.changeGameMode(GameMode.CREATIVE)) {
                FORCED_CREATIVE_PLAYERS.add(player.getUuidAsString());
            }
        }
        Logger.logSuccess(player, "Created plot '" + mapName + "' (" + PLOT_SIZE + "x" + PLOT_SIZE + ") in the plots dimension.");
        Logger.log(player, net.minecraft.text.Text.literal("Ground material: " + groundBlockId));
        if (!description.isBlank()) {
            Logger.log(player, net.minecraft.text.Text.literal("Description: " + description));
        }
        return 1;
    }

    public static int teleportHome(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        DataManager.MapData mapData = getAnyOwnedPlot(player);
        if (mapData == null) {
            Logger.logFailure(player, "You do not have a plot yet. Use /plot create <name>.");
            return 0;
        }
        ServerWorld world = resolveWorld(player.getServer(), mapData.worldKey);
        if (world == null) {
            world = getPlotsWorld(player.getServer());
        }
        if (world == null) {
            Logger.logFailure(player, "Could not find your plot dimension.");
            return 0;
        }

        double targetX = mapData.x;
        double targetY = mapData.y;
        double targetZ = mapData.z;
        double minSafeY = (double) (mapData.plotMinY > 0 ? mapData.plotMinY : PLOT_GROUND_Y) + 1.0D;
        if (!Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ)
                || !isInsidePlotBounds(mapData, new Vec3d(targetX, targetY, targetZ))) {
            targetX = (mapData.plotMinX + mapData.plotMaxX) * 0.5D;
            targetY = minSafeY + 1.0D;
            targetZ = (mapData.plotMinZ + mapData.plotMaxZ) * 0.5D;
        }
        if (targetY < minSafeY) {
            targetY = minSafeY + 1.0D;
        }

        player.teleportTo(ZoneUtil.makeTeleportTarget(
                world,
                new Vec3d(targetX, targetY, targetZ),
                (float) mapData.yrot,
                (float) mapData.xrot
        ));
        Logger.logSuccess(player, "Teleported to your plot: " + mapData.name);
        return 1;
    }

    public static int setDescription(ServerPlayerEntity player, String rawDescription) {
        if (player == null) {
            return 0;
        }
        DataManager.MapData mapData = getAnyOwnedPlot(player);
        if (mapData == null) {
            Logger.logFailure(player, "You do not have a plot map.");
            return 0;
        }
        String newDescription = DescriptionCensor.sanitizeAndMaybeCensor(rawDescription);
        mapData.description = newDescription;
        DataManager.saveData(player.getServerWorld(), DataManager.mapListLocation, Minehop.mapList);
        syncMaps(player.getServer());
        Logger.logSuccess(player, "Updated plot description.");
        if (!newDescription.isBlank()) {
            Logger.log(player, net.minecraft.text.Text.literal(newDescription));
        }
        return 1;
    }

    public static int setGround(ServerPlayerEntity player, String rawGroundBlockId) {
        if (player == null) {
            return 0;
        }
        DataManager.MapData mapData = getAnyOwnedPlot(player);
        if (mapData == null) {
            Logger.logFailure(player, "You do not have a plot map.");
            return 0;
        }

        ServerWorld world = resolveWorld(player.getServer(), mapData.worldKey);
        if (world == null) {
            world = getPlotsWorld(player.getServer());
        }
        if (world == null) {
            Logger.logFailure(player, "Could not find your plot world.");
            return 0;
        }

        GroundBlockValidation groundValidation = validateGroundBlockId(rawGroundBlockId, false);
        if (!groundValidation.valid) {
            Logger.logFailure(player, groundValidation.errorMessage);
            return 0;
        }
        String groundBlockId = groundValidation.blockId;
        mapData.plotGroundBlockId = groundBlockId;
        enqueuePlotGroundFill(world, mapData, groundBlockId, player, true);
        DataManager.saveData(world, DataManager.mapListLocation, Minehop.mapList);
        syncMaps(player.getServer());
        Logger.logSuccess(player, "Queued ground update to " + groundBlockId + ".");
        return 1;
    }

    public static int deleteOwnedPlot(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        DataManager.MapData mapData = getAnyOwnedPlot(player);
        if (mapData == null || mapData.name == null || mapData.name.isBlank()) {
            Logger.logFailure(player, "You do not have a plot map to delete.");
            return 0;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            Logger.logFailure(player, "Could not access server.");
            return 0;
        }

        String mapName = mapData.name;
        ServerWorld plotWorld = resolveWorld(server, mapData.worldKey);
        if (plotWorld == null) {
            plotWorld = getPlotsWorld(server);
        }

        cancelPlotFillTask(mapName);
        if (plotWorld != null) {
            enqueuePlotClearTask(plotWorld, mapData);
        }

        int removedEntities = removePlotEntities(server, mapData, plotWorld);
        boolean removedMap = Minehop.mapList.removeIf(data -> data != null && mapName.equals(data.name));
        DataManager.removeRatingsForMap(mapName);
        Minehop.timerManager.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue().containsKey(mapName));
        Minehop.playerMapLocation.entrySet().removeIf(entry -> {
            Zone zone = entry.getValue();
            return zone == null || mapName.equals(zone.getPairedMap());
        });

        Minehop.personalRecordList.removeIf(record -> record != null && mapName.equals(record.map_name));
        Minehop.recordList.removeIf(record -> record != null && mapName.equals(record.map_name));
        ReplayManager.deleteReplaysForMap(mapName);

        ServerWorld saveWorld = plotWorld != null ? plotWorld : player.getServerWorld();
        DataManager.saveData(saveWorld, DataManager.mapListLocation, Minehop.mapList);
        DataManager.saveData(saveWorld, DataManager.mapRatingsLocation, Minehop.mapRatingList);
        DataManager.saveData(saveWorld, DataManager.pbListLocation, Minehop.personalRecordList);
        DataManager.saveData(saveWorld, DataManager.recordsListLocation, Minehop.recordList);
        ReplayManager.saveRecordReplays(saveWorld, Minehop.replayList);
        syncAllMapData(server);

        FORCED_CREATIVE_PLAYERS.remove(player.getUuidAsString());
        if (!player.hasPermissionLevel(4) && !player.isSpectator()) {
            player.changeGameMode(GameMode.SURVIVAL);
        }

        if (plotWorld != null && player.getServerWorld() == plotWorld) {
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                BlockPos spawnPos = overworld.getSpawnPos();
                Vec3d spawnCenter = Vec3d.ofBottomCenter(spawnPos).add(0.0D, 1.0D, 0.0D);
                player.teleportTo(ZoneUtil.makeTeleportTarget(overworld, spawnCenter, player.getYaw(), player.getPitch()));
            }
        }

        if (removedMap) {
            Logger.logSuccess(player, "Deleted plot '" + mapName + "' and removed " + removedEntities + " linked entities.");
            return 1;
        }

        Logger.logFailure(player, "Could not delete your plot map.");
        return 0;
    }

    public static String sanitizeGroundBlockId(String rawGroundBlockId) {
        GroundBlockValidation validation = validateGroundBlockId(rawGroundBlockId, true);
        return validation.valid ? validation.blockId : DEFAULT_GROUND_BLOCK_ID;
    }

    public static ServerWorld getPlotsWorld(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.getWorld(PLOTS_WORLD_KEY);
    }

    public static boolean isPlotsWorld(World world) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return false;
        }
        return serverWorld.getRegistryKey().equals(PLOTS_WORLD_KEY);
    }

    private static boolean isOwnedBy(ServerPlayerEntity player, DataManager.MapData mapData) {
        if (player == null || mapData == null) {
            return false;
        }
        return mapData.userMap && player.getUuidAsString().equals(mapData.ownerUuid);
    }

    private static String formatGameMode(GameMode gameMode) {
        return gameMode == null ? "unknown" : gameMode.getName();
    }

    private static boolean isMapInWorld(DataManager.MapData mapData, ServerWorld world) {
        if (mapData == null || world == null) {
            return false;
        }
        String expected = world.getRegistryKey().toString();
        return expected.equals(mapData.worldKey);
    }

    private static boolean isInsidePlotBounds(DataManager.MapData mapData, Vec3d pos) {
        if (mapData == null || pos == null) {
            return false;
        }
        if (!mapData.userMap) {
            return false;
        }
        if (mapData.plotMaxX <= mapData.plotMinX || mapData.plotMaxZ <= mapData.plotMinZ) {
            return false;
        }
        return pos.x >= mapData.plotMinX
                && pos.x < mapData.plotMaxX
                && pos.z >= mapData.plotMinZ
                && pos.z < mapData.plotMaxZ;
    }

    private static boolean isInsidePlotBuildBounds(DataManager.MapData mapData, BlockPos pos) {
        if (mapData == null || pos == null) {
            return false;
        }
        int minY = mapData.plotMinY > 0 ? mapData.plotMinY : PLOT_GROUND_Y;
        int maxYExclusive = Math.max(mapData.plotMaxY, minY + PLOT_DELETE_CLEAR_HEIGHT);
        return pos.getY() >= minY && pos.getY() < maxYExclusive;
    }

    private static int findFirstFreePlotSlot() {
        Set<Long> occupied = new HashSet<>();
        int maxSlotIndex = -1;
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData == null || !mapData.userMap) {
                continue;
            }
            if (mapData.plotMaxX <= mapData.plotMinX || mapData.plotMaxZ <= mapData.plotMinZ) {
                continue;
            }
            int gx = Math.floorDiv(mapData.plotMinX, PLOT_STRIDE);
            int gz = Math.floorDiv(mapData.plotMinZ, PLOT_STRIDE);
            if (gx < 0 || gz < 0 || gx >= GRID_COLUMNS) {
                continue;
            }
            occupied.add(encodeGridSlot(gx, gz));
            int slotIndex = gz * GRID_COLUMNS + gx;
            if (slotIndex > maxSlotIndex) {
                maxSlotIndex = slotIndex;
            }
        }

        int startSearchIndex = maxSlotIndex + 1;
        int maxSlots = GRID_COLUMNS * GRID_COLUMNS;
        for (int index = startSearchIndex; index < maxSlots; index++) {
            int gx = index % GRID_COLUMNS;
            int gz = index / GRID_COLUMNS;
            long key = encodeGridSlot(gx, gz);
            if (!occupied.contains(key)) {
                return index;
            }
        }

        for (int index = 0; index < startSearchIndex && index < maxSlots; index++) {
            int gx = index % GRID_COLUMNS;
            int gz = index / GRID_COLUMNS;
            long key = encodeGridSlot(gx, gz);
            if (!occupied.contains(key)) {
                return index;
            }
        }
        return Math.max(0, occupied.size());
    }

    private static long encodeGridSlot(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static void cancelPlotFillTask(String mapName) {
        if (mapName == null || mapName.isBlank()) {
            return;
        }
        PlotFillTask task = FILL_TASKS_BY_MAP.remove(mapName);
        if (task == null) {
            return;
        }
        task.canceled = true;
        PENDING_FILL_TASKS.remove(task);
    }

    private static int removePlotEntities(MinecraftServer server, DataManager.MapData mapData, ServerWorld plotWorld) {
        if (server == null || mapData == null || mapData.name == null) {
            return 0;
        }

        String mapName = mapData.name;
        int removedCount = 0;

        for (ServerWorld world : server.getWorlds()) {
            List<Entity> toRemove = new ArrayList<>();
            Iterator<Entity> iterator = world.iterateEntities().iterator();
            while (iterator.hasNext()) {
                Entity entity = iterator.next();
                if (entity == null || entity.isRemoved() || !entity.isAlive()) {
                    continue;
                }

                if (entity instanceof Zone zone) {
                    if (mapName.equals(zone.getPairedMap())) {
                        toRemove.add(entity);
                    } else if (world == plotWorld && intersectsPlotBounds(mapData, entity.getBoundingBox())) {
                        toRemove.add(entity);
                    }
                    continue;
                }

                if (entity instanceof ReplayEntity replayEntity) {
                    if (mapName.equals(replayEntity.getMapName())) {
                        toRemove.add(entity);
                    }
                    continue;
                }

                if (world == plotWorld && entity instanceof SurfRampEntity && intersectsPlotBounds(mapData, entity.getBoundingBox())) {
                    toRemove.add(entity);
                }
            }

            for (Entity entity : toRemove) {
                entity.kill(world);
                removedCount++;
            }
        }

        return removedCount;
    }

    public static int countSurfRampsInPlot(ServerWorld world, DataManager.MapData mapData) {
        if (world == null || mapData == null) {
            return 0;
        }
        if (!mapData.userMap || !isMapInWorld(mapData, world)) {
            return 0;
        }
        List<SurfRampEntity> ramps = SurfRampEntity.collectAllActiveRamps(world);
        if (ramps.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (SurfRampEntity ramp : ramps) {
            if (ramp == null || ramp.isRemoved() || !ramp.isAlive()) {
                continue;
            }
            if (intersectsPlotBounds(mapData, ramp.getBoundingBox())) {
                count++;
            }
        }
        return count;
    }

    private static boolean intersectsPlotBounds(DataManager.MapData mapData, Box box) {
        if (mapData == null || box == null) {
            return false;
        }
        return box.maxX > mapData.plotMinX
                && box.minX < mapData.plotMaxX
                && box.maxZ > mapData.plotMinZ
                && box.minZ < mapData.plotMaxZ;
    }

    private static void enqueuePlotClearTask(ServerWorld world, DataManager.MapData mapData) {
        if (world == null || mapData == null || mapData.name == null) {
            return;
        }
        if (mapData.plotMaxX <= mapData.plotMinX || mapData.plotMaxZ <= mapData.plotMinZ) {
            return;
        }
        int groundY = mapData.plotMinY > 0 ? mapData.plotMinY : PLOT_GROUND_Y;
        int clearTopExclusive = groundY + PLOT_DELETE_CLEAR_HEIGHT;
        String taskKey = "__clear__:" + mapData.name + ":" + System.nanoTime();
        PlotFillTask task = new PlotFillTask(
                taskKey,
                "",
                world,
                mapData.plotMinX,
                mapData.plotMaxX,
                mapData.plotMinZ,
                mapData.plotMaxZ,
                groundY,
                clearTopExclusive,
                Blocks.AIR.getDefaultState()
        );
        PENDING_FILL_TASKS.add(task);
        FILL_TASKS_BY_MAP.put(taskKey, task);
    }

    private static void enqueuePlotGroundFill(
            ServerWorld world,
            DataManager.MapData mapData,
            String groundBlockId,
            ServerPlayerEntity ownerPlayer,
            boolean notifyQueued
    ) {
        if (world == null || mapData == null || mapData.name == null || mapData.name.isBlank()) {
            return;
        }
        if (mapData.plotMaxX <= mapData.plotMinX || mapData.plotMaxZ <= mapData.plotMinZ) {
            return;
        }

        String mapName = mapData.name;
        String ownerUuid = mapData.ownerUuid == null ? "" : mapData.ownerUuid;
        BlockState groundState = resolveGroundState(groundBlockId);
        int y = mapData.plotMinY > 0 ? mapData.plotMinY : PLOT_GROUND_Y;

        PlotFillTask previousTask = FILL_TASKS_BY_MAP.remove(mapName);
        if (previousTask != null) {
            previousTask.canceled = true;
            PENDING_FILL_TASKS.remove(previousTask);
        }

        placeSpawnPatch(world, mapData, y, groundState);

        PlotFillTask task = new PlotFillTask(
                mapName,
                ownerUuid,
                world,
                mapData.plotMinX,
                mapData.plotMaxX,
                mapData.plotMinZ,
                mapData.plotMaxZ,
                y,
                groundState
        );
        PENDING_FILL_TASKS.add(task);
        FILL_TASKS_BY_MAP.put(mapName, task);

        if (notifyQueued && ownerPlayer != null) {
            Logger.logSuccess(ownerPlayer, "Generating plot ground in background...");
        }
    }

    private static void processFillQueue(MinecraftServer server) {
        if (server == null || PENDING_FILL_TASKS.isEmpty()) {
            return;
        }

        int remainingBudget = PLOT_FILL_BLOCKS_PER_TICK;
        long deadline = System.nanoTime() + PLOT_FILL_TIME_BUDGET_NANOS;
        int roundRobinTasks = PENDING_FILL_TASKS.size();

        while (roundRobinTasks-- > 0 && remainingBudget > 0 && System.nanoTime() < deadline) {
            PlotFillTask task = PENDING_FILL_TASKS.pollFirst();
            if (task == null) {
                break;
            }
            if (task.canceled || task.world == null || task.world.getServer() != server) {
                FILL_TASKS_BY_MAP.remove(task.mapName, task);
                continue;
            }

            int placedThisTick = task.process(remainingBudget, deadline);
            remainingBudget = Math.max(0, remainingBudget - Math.max(0, placedThisTick));

            if (task.isComplete()) {
                FILL_TASKS_BY_MAP.remove(task.mapName, task);
                if (!task.ownerUuid.isBlank()) {
                    ServerPlayerEntity owner = server.getPlayerManager().getPlayer(task.ownerUuid);
                    if (owner != null) {
                        Logger.logActionBar(owner, "Plot ground generation complete.");
                    }
                }
            } else {
                PENDING_FILL_TASKS.addLast(task);
            }
        }
    }

    private static BlockState resolveGroundState(String groundBlockId) {
        GroundBlockValidation validation = validateGroundBlockId(groundBlockId, true);
        Block block = Blocks.GRASS_BLOCK;
        if (validation.valid) {
            Identifier blockId = Identifier.tryParse(validation.blockId);
            if (blockId != null && Registries.BLOCK.containsId(blockId)) {
                block = Registries.BLOCK.get(blockId);
            }
        }
        return block.getDefaultState();
    }

    private static GroundBlockValidation validateGroundBlockId(String rawGroundBlockId, boolean allowBlankDefault) {
        if (rawGroundBlockId == null || rawGroundBlockId.isBlank()) {
            if (allowBlankDefault) {
                return GroundBlockValidation.valid(DEFAULT_GROUND_BLOCK_ID);
            }
            return GroundBlockValidation.invalid("Ground block cannot be blank.");
        }

        String trimmed = rawGroundBlockId.trim().toLowerCase(Locale.ROOT);
        if (trimmed.contains("{") || trimmed.contains("}") || trimmed.contains("[") || trimmed.contains("]")) {
            return GroundBlockValidation.invalid("Ground block must be a plain block id only (NBT or block-state syntax is not allowed).");
        }

        Identifier id = Identifier.tryParse(trimmed);
        if (id == null || !Registries.BLOCK.containsId(id)) {
            return GroundBlockValidation.invalid("Invalid ground block id: " + rawGroundBlockId + ".");
        }

        Block block = Registries.BLOCK.get(id);
        if (!isAllowedGroundBlock(block)) {
            return GroundBlockValidation.invalid("That ground block is not allowed for plots. Use a normal placeable solid block.");
        }

        return GroundBlockValidation.valid(id.toString());
    }

    private static boolean isAllowedGroundBlock(Block block) {
        if (block == null) {
            return false;
        }
        BlockState state = block.getDefaultState();
        if (state.isAir()) {
            return false;
        }
        if (ILLEGAL_PLOT_GROUND_BLOCKS.contains(block)) {
            return false;
        }
        if (block instanceof BlockWithEntity || state.hasBlockEntity()) {
            return false;
        }
        return true;
    }

    private static boolean isAllowedPlotPlacedBlock(Block block) {
        if (block == ModBlocks.BOOSTER_BLOCK) {
            return true;
        }
        return isAllowedGroundBlock(block);
    }

    private static void placeSpawnPatch(ServerWorld world, DataManager.MapData mapData, int y, BlockState groundState) {
        if (world == null || mapData == null || groundState == null) {
            return;
        }
        int centerX = (int) Math.floor(mapData.x);
        int centerZ = (int) Math.floor(mapData.z);
        int minX = Math.max(mapData.plotMinX, centerX - SPAWN_PATCH_RADIUS);
        int maxX = Math.min(mapData.plotMaxX - 1, centerX + SPAWN_PATCH_RADIUS);
        int minZ = Math.max(mapData.plotMinZ, centerZ - SPAWN_PATCH_RADIUS);
        int maxZ = Math.min(mapData.plotMaxZ - 1, centerZ + SPAWN_PATCH_RADIUS);

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                mutable.set(x, y, z);
                world.setBlockState(mutable, groundState, 3);
            }
        }
    }

    private static ServerWorld resolveWorld(MinecraftServer server, String worldKeyString) {
        if (server == null || worldKeyString == null || worldKeyString.isBlank()) {
            return null;
        }
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().toString().equals(worldKeyString)) {
                return world;
            }
        }
        return null;
    }

    private static void syncMaps(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity worldPlayer : server.getPlayerManager().getPlayerList()) {
            PacketHandler.sendMaps(worldPlayer);
        }
    }

    private static void syncAllMapData(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity worldPlayer : server.getPlayerManager().getPlayerList()) {
            PacketHandler.sendMaps(worldPlayer);
            PacketHandler.sendRecords(worldPlayer);
            PacketHandler.sendPersonalRecords(worldPlayer);
        }
    }

    private static String sanitizeMapName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty() || trimmed.length() > 128) {
            return null;
        }
        if (trimmed.contains("~") || trimmed.contains("\n") || trimmed.contains("\r")) {
            return null;
        }
        String censored = DescriptionCensor.censorProfanity(trimmed).trim();
        if (censored.isEmpty()) {
            return null;
        }
        return censored;
    }

    public static boolean isReservedMapName(String mapName) {
        if (mapName == null) {
            return false;
        }
        return RESERVED_MAP_NAMES.contains(mapName.trim().toLowerCase(Locale.ROOT));
    }

    private static final class PlotFillTask {
        private final String mapName;
        private final String ownerUuid;
        private final ServerWorld world;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int minY;
        private final int maxYExclusive;
        private final BlockState groundState;
        private final long totalBlocks;

        private int cursorX;
        private int cursorZ;
        private int cursorY;
        private long placed;
        private boolean canceled;

        private PlotFillTask(
                String mapName,
                String ownerUuid,
                ServerWorld world,
                int minX,
                int maxX,
                int minZ,
                int maxZ,
                int y,
                BlockState groundState
        ) {
            this(mapName, ownerUuid, world, minX, maxX, minZ, maxZ, y, y + 1, groundState);
        }

        private PlotFillTask(
                String mapName,
                String ownerUuid,
                ServerWorld world,
                int minX,
                int maxX,
                int minZ,
                int maxZ,
                int minY,
                int maxYExclusive,
                BlockState groundState
        ) {
            this.mapName = mapName == null ? "" : mapName;
            this.ownerUuid = ownerUuid == null ? "" : ownerUuid;
            this.world = world;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.minY = minY;
            this.maxYExclusive = Math.max(minY + 1, maxYExclusive);
            this.groundState = groundState;
            long xSpan = Math.max(0, (long) maxX - (long) minX);
            long zSpan = Math.max(0, (long) maxZ - (long) minZ);
            long ySpan = Math.max(0, (long) this.maxYExclusive - (long) minY);
            this.totalBlocks = xSpan * zSpan * ySpan;
            this.cursorX = minX;
            this.cursorZ = minZ;
            this.cursorY = minY;
            this.placed = 0L;
            this.canceled = false;
        }

        private int process(int blockBudget, long deadlineNanos) {
            if (this.canceled || blockBudget <= 0 || this.isComplete()) {
                return 0;
            }
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            int placedNow = 0;
            while (placedNow < blockBudget && this.cursorY < this.maxYExclusive) {
                mutable.set(this.cursorX, this.cursorY, this.cursorZ);
                this.world.setBlockState(mutable, this.groundState, 3);
                this.placed++;
                placedNow++;

                this.cursorZ++;
                if (this.cursorZ >= this.maxZ) {
                    this.cursorZ = this.minZ;
                    this.cursorX++;
                    if (this.cursorX >= this.maxX) {
                        this.cursorX = this.minX;
                        this.cursorY++;
                    }
                }

                if ((placedNow & 63) == 0 && System.nanoTime() >= deadlineNanos) {
                    break;
                }
            }
            return placedNow;
        }

        private boolean isComplete() {
            return this.canceled || this.cursorY >= this.maxYExclusive || this.placed >= this.totalBlocks;
        }
    }

    private static final class GroundBlockValidation {
        private final boolean valid;
        private final String blockId;
        private final String errorMessage;

        private GroundBlockValidation(boolean valid, String blockId, String errorMessage) {
            this.valid = valid;
            this.blockId = blockId;
            this.errorMessage = errorMessage;
        }

        private static GroundBlockValidation valid(String blockId) {
            return new GroundBlockValidation(true, blockId, "");
        }

        private static GroundBlockValidation invalid(String errorMessage) {
            return new GroundBlockValidation(false, DEFAULT_GROUND_BLOCK_ID, errorMessage);
        }
    }
}
