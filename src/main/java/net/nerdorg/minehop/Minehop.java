package net.nerdorg.minehop;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.nerdorg.minehop.anticheat.AntiCheatManager;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.block.entity.ModBlockEntities;
import net.nerdorg.minehop.commands.*;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.MobManager;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.*;
import net.nerdorg.minehop.hns.HNSManager;
import net.nerdorg.minehop.item.ModItems;
import net.nerdorg.minehop.motd.MotdManager;
import net.nerdorg.minehop.networking.*;
import net.nerdorg.minehop.networking.payloads.*;
import net.nerdorg.minehop.replays.ReplayEvents;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.UserPlotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class Minehop implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("minehop");
    public static final String MOD_ID = "minehop";
    public static final int MOD_VERSION = 11300;
    public static final String MOD_VERSION_STRING = "1.1.3";

	public static boolean override_config = false;
	public static double o_sv_friction = 0;
	public static double o_sv_accelerate = 0;
	public static double o_sv_airaccelerate = 0;
	public static double o_sv_maxairspeed = 0;
	public static double o_sv_jump_impulse = 0;
	public static double o_speed_mul = 0;
	public static double o_sv_gravity = 0;
	public static double o_sv_stopspeed = 75;
	public static double o_speed_cap = 0;
	public static double o_speed_coefficient = 0;
	public static boolean o_auto_step_up = true;
	public static boolean o_css_crouch_jump = true;
	public static boolean o_disable_sprint = false;
	public static boolean o_hns = false;
	public static boolean o_kz = false;
	public static boolean o_enabled = true;
	public static boolean o_fall_damage = true;

	public static boolean receivedConfig = false;
	public static volatile int clientRenderFps = -1;
	public static volatile long clientRenderFrameNanos = -1L;
	public static final boolean surfPerfLoggingEnabled = Boolean.getBoolean("minehop.surfPerfLog");
	public static final boolean surfHullSolverEnabled = !"false".equalsIgnoreCase(System.getProperty("minehop.surfHullSolver", "true"));

	public static List<DataManager.MapData> mapList = new ArrayList<>();
	public static List<DataManager.MapRatingData> mapRatingList = new ArrayList<>();
	public static List<DataManager.RecordData> personalRecordList = new ArrayList<>();
	public static List<DataManager.RecordData> recordList = new ArrayList<>();
	public static List<ReplayManager.Replay> replayList = new ArrayList<>();

	public static List<String> groundedList = new ArrayList<>();
	public static final Set<Integer> surfCollisionBypassEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
	public static final Set<UUID> surfDebugPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
	public static HashMap<String, HashMap<String, Long>> timerManager = new HashMap<>();
	// Server-stamped finish time (nanoTime) at the tick the runner enters the end zone, so the run
	// time is measured server-side at the real zone crossing and isn't inflated by packet/processing
	// latency (e.g. a plot fill/clear stall) like `now - timerStart` was — which wrongly rejected
	// legit runs as "Invalid time".
	public static HashMap<String, HashMap<String, Long>> finishTimeManager = new HashMap<>();
	public static HashMap<String, Double> efficiencyMap = new HashMap<>();
	public static HashMap<String, List<Double>> efficiencyListMap = new HashMap<>();
	public static HashMap<String, ReplayManager.SSJEntry> lastEfficiencyMap = new HashMap<>();
	public static HashMap<String, Double> efficiencyUpdateMap = new HashMap<>();
	public static HashMap<String, Double> speedCapMap = new HashMap<>();
	public static HashMap<String, List<Double>> gaugeListMap = new HashMap<>();
	public static java.util.Map<String, net.nerdorg.minehop.util.StrafeStats> strafeStatsMap = new ConcurrentHashMap<>();
	public static HashMap<String, Zone> playerMapLocation = new HashMap<>();

	public static List<PlayerEntity> currentCheaters = new ArrayList<>();

	@Override
	public void onInitialize() {
		AutoConfig.register(MinehopConfig.class, JanksonConfigSerializer::new);
		ConfigWrapper.loadConfig();

		PacketHandler.register();

		ServerPlayConnectionEvents.INIT.register(((serverPlayNetworkHandler, minecraftServer) -> {
			PacketHandler.registerReceivers();
			HandshakeHandler.register();
		}));

		ConfigWrapper.register();
		DataManager.register();
		JoinLeaveManager.register();
		MobManager.register();
		AntiCheatManager.register();
		net.nerdorg.minehop.util.MovementTestHarness.register();

		HNSManager.register();

		CommandRegister.register();

		ReplayManager.register();
		ReplayEvents.register();

		ModItems.initialize();
		ModItems.registerModItems();
		ModBlockEntities.registerBlockEntities();

		MotdManager.register();
		UserPlotManager.register();

		FabricDefaultAttributeRegistry.register(ModEntities.GAMEMODE_ENTITY, GamemodeEntity.createResetEntityAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.RESET_ENTITY, ResetEntity.createResetEntityAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.START_ENTITY, StartEntity.createResetEntityAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.END_ENTITY, EndEntity.createResetEntityAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.REPLAY_ENTITY, ReplayEntity.createResetEntityAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.SURF_RAMP_ENTITY, SurfRampEntity.createSurfRampAttributes());
	}
}
