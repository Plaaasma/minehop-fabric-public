package net.nerdorg.minehop;

import net.nerdorg.minehop.platform.ClientServices;
import net.minecraft.client.Minecraft;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.client.SqueedometerHud;
import net.nerdorg.minehop.client.BoundsStickPreviewRenderer;
import net.nerdorg.minehop.client.BoundsStickPreviewState;
import net.nerdorg.minehop.client.ReplayPathRenderer;
import net.nerdorg.minehop.client.ReplayPathState;
import net.nerdorg.minehop.client.SurfStickPreviewRenderer;
import net.nerdorg.minehop.client.SurfStickPreviewState;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.discord.DiscordIntegration;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.client.*;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.event.JoinEvent;
import net.nerdorg.minehop.event.KeyInputHandler;
import net.nerdorg.minehop.networking.ClientPacketHandler;
import net.nerdorg.minehop.networking.payloads.HandshakeIDPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Common client initialization and client-side run state. Each loader's client entrypoint calls
 * {@link #onInitializeClient()} once on the physical client (Fabric: {@code net.nerdorg.minehop.fabric.MinehopFabricClient}).
 */
public class MinehopClient {
	public static SqueedometerHud squeedometerHud;

	public static int jump_count = 0;
	public static boolean jumping = false;
	public static double last_jump_speed = 0;
	public static double start_jump_speed = 0;
	public static double old_jump_speed = 0;
	public static long last_jump_time = 0;
	public static long old_jump_time = 0;
	public static double prevVelY = 0.0D;
	public static double last_efficiency;
	public static double gauge;
	public static boolean wasOnGround = false;
	public static boolean runTimerHudVisible = false;
	public static float runTimerHudTime = 0.0F;
	public static float runTimerHudPb = 0.0F;
	public static long runTimerHudUpdatedAtMs = 0L;
	public static int resetCarryTicks = 0;
	public static double resetCarryX = 0.0D;
	public static double resetCarryY = 0.0D;
	public static double resetCarryZ = 0.0D;
	private static boolean startZoneArmed = false;
	private static boolean wasInsideStartZone = false;
	private static boolean wasInsideEndZone = false;
	private static String activeRunMapName = "";
	private static Vec3 lastFinishSamplePos = null;
	private static long lastFinishSampleNanos = 0L;
	private static long lastFinishSampleStartNanos = 0L;

	//public static boolean hideSelf = false;
	//public static boolean hideReplay = false;
	//public static boolean hideOthers = false;

	public static long startTime = 0;
	public static float lastSendTime = 0;

	public static List<String> spectatorList = new ArrayList<>();

	private static final String OFFICIAL_SERVER_IP = "play.minehop.net";
	private static final String OFFICIAL_SERVER_NAME = "§c§l§nOfficial Minehop Server";
	// Builds for other Minecraft versions list the official server too, with a warning: they can't join it.
	private static final String OFFICIAL_SERVER_NAME_OTHER_VERSION = "§c§l§nOfficial Minehop§r §e(requires " + Minehop.OFFICIAL_SERVER_MC_VERSION + ")";

	public void onInitializeClient() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			ServerList serverList = new ServerList(minecraft);
			serverList.load();
			String officialName = isOfficialServerVersion() ? OFFICIAL_SERVER_NAME : OFFICIAL_SERVER_NAME_OTHER_VERSION;
			ServerData official = findServer(serverList, OFFICIAL_SERVER_IP);
			if (official == null) {
				serverList.add(new ServerData(officialName, OFFICIAL_SERVER_IP, ServerData.Type.OTHER), false);
				serverList.swap(0, serverList.size() - 1);
				serverList.save();
			} else if (!official.name.equals(officialName)
					&& (official.name.equals(OFFICIAL_SERVER_NAME) || official.name.equals(OFFICIAL_SERVER_NAME_OTHER_VERSION))) {
				// Launchers can share servers.dat between Minecraft versions: label our entry for the running one.
				official.name = officialName;
				serverList.save();
			}
		});

		ClientServices.NETWORK.onConnectionInit((handler, client) -> {
			ClientPacketHandler.registerReceivers();
		});
		ClientServices.NETWORK.onConnectionDisconnect((handler, client) -> {
			SurfStickPreviewState.clear();
			BoundsStickPreviewState.clear();
			ReplayPathState.clear();
			runTimerHudVisible = false;
			runTimerHudTime = 0.0F;
			runTimerHudPb = 0.0F;
			runTimerHudUpdatedAtMs = 0L;
			startTime = 0L;
			lastSendTime = 0.0F;
			wasInsideStartZone = false;
			wasInsideEndZone = false;
			startZoneArmed = false;
			activeRunMapName = "";
			lastFinishSamplePos = null;
			lastFinishSampleNanos = 0L;
			lastFinishSampleStartNanos = 0L;
			resetCarryTicks = 0;
			resetCarryX = 0.0D;
			resetCarryY = 0.0D;
			resetCarryZ = 0.0D;
		});

		ConfigWrapper.loadConfig();
		squeedometerHud = new SqueedometerHud();

		KeyInputHandler.register();
		JoinEvent.register();
		BoundsStickPreviewRenderer.register();
		ReplayPathRenderer.register();
		SurfStickPreviewRenderer.register();
		ClientServices.CLIENT.registerEntityRenderer(ModEntities.GAMEMODE_ENTITY, GamemodeRenderer::new);
		ClientServices.CLIENT.registerModelLayer(ModModelLayers.GAMEMODE_ENTITY, GamemodeModel::getTexturedModelData);
		ClientServices.CLIENT.registerEntityRenderer(ModEntities.RESET_ENTITY, ResetRenderer::new);
		ClientServices.CLIENT.registerModelLayer(ModModelLayers.RESET_ENTITY, ResetModel::getTexturedModelData);
		ClientServices.CLIENT.registerEntityRenderer(ModEntities.START_ENTITY, StartRenderer::new);
		ClientServices.CLIENT.registerModelLayer(ModModelLayers.START_ENTITY, StartModel::getTexturedModelData);
		ClientServices.CLIENT.registerEntityRenderer(ModEntities.END_ENTITY, EndRenderer::new);
		ClientServices.CLIENT.registerModelLayer(ModModelLayers.END_ENTITY, EndModel::getTexturedModelData);
		ClientServices.CLIENT.registerEntityRenderer(ModEntities.REPLAY_ENTITY, ReplayRenderer::new);
		ClientServices.CLIENT.registerModelLayer(ModModelLayers.REPLAY_ENTITY, ReplayModel::getTexturedModelData);
		ClientServices.CLIENT.registerEntityRenderer(ModEntities.SURF_RAMP_ENTITY, SurfRampRenderer::new);
		ClientServices.CLIENT.registerModelLayer(ModModelLayers.SURF_RAMP_ENTITY, SurfRampModel::getTexturedModelData);

		ClientServices.CLIENT.onClientTickEnd(client -> {
			if (!client.isLocalServer()) {
				Minehop.override_config = true;
			}
			if (client.player != null) {
				if (resetCarryTicks > 0) {
					client.player.setDeltaMovement(resetCarryX, resetCarryY, resetCarryZ);
					client.player.setOnGround(false);
					resetCarryTicks--;
					if (resetCarryTicks <= 0) {
						resetCarryX = 0.0D;
						resetCarryY = 0.0D;
						resetCarryZ = 0.0D;
					}
				}
				if (client.options.keyJump.isDown()) {
					jumping = true;
				}
				else {
					jumping = false;
				}

				// Jump/SSJ counter via upward-velocity spike (takeoff). With auto-bhop the player
				// lands AND jumps in the same tick, so onGround is never true at a tick boundary —
				// counting on isOnGround() missed jumps. Detect the jump impulse directly instead.
				if (!client.player.isSpectator()) {
					Vec3 jv = client.player.getDeltaMovement();
					double jumpImpulseBpt = (Minehop.override_config && Minehop.receivedConfig
							? Minehop.o_sv_jump_impulse
							: ConfigWrapper.config.movement.sv_jump_impulse) / 800.0D;
					double takeoffThreshold = Math.max(0.05D, jumpImpulseBpt * 0.6D);
					if (jumping) {
						if (prevVelY <= 0.05D && jv.y >= takeoffThreshold) {
							old_jump_speed = last_jump_speed;
							last_jump_speed = Math.sqrt(jv.x * jv.x + jv.z * jv.z);
							jump_count += 1;
							old_jump_time = last_jump_time;
							last_jump_time = client.level != null ? client.level.getGameTime() : 0L;
						}
					} else {
						old_jump_speed = 0;
						last_jump_speed = 0;
						jump_count = 0;
						old_jump_time = 0;
						last_jump_time = 0;
					}
					prevVelY = jv.y;
				}

				if (startTime != 0L && !client.player.isSpectator()) {
					float elapsedTime = (float) (((double) (System.nanoTime() - startTime)) / 1_000_000_000.0D);
					if (Float.isFinite(elapsedTime) && elapsedTime >= 0.0F) {
						ClientPacketHandler.sendCurrentTime(elapsedTime);
					}
				}

				updateRunTimerStartZones(client);
			}
		});

		final long[] lastRenderFrameNanos = {-1L};
		ClientServices.CLIENT.onWorldRenderEnd(context -> {
			long now = System.nanoTime();
			long previous = lastRenderFrameNanos[0];
			if (previous > 0L) {
				long frameNanos = now - previous;
				if (frameNanos > 0L) {
					double instantFps = 1_000_000_000.0D / (double) frameNanos;
					int fps = (int) Math.round(instantFps);
					Minehop.clientRenderFps = Mth.clamp(fps, 1, 4000);
					Minehop.clientRenderFrameNanos = frameNanos;
				}
			}
			lastRenderFrameNanos[0] = now;
			updateRunTimerFinishZones(Minecraft.getInstance(), now);
		});

		ClientServices.CLIENT.setBlockRenderType(ModBlocks.BOOSTER_BLOCK, RenderType.translucent());
		net.nerdorg.minehop.client.ClientPerfProbe.register();
	}

	private static void updateRunTimerStartZones(Minecraft client) {
		if (client == null || client.player == null || client.level == null) {
			return;
		}
		if (client.player.isCreative() || client.player.isSpectator()) {
			clearClientRunState();
			return;
		}

		Vec3 playerPos = client.player.position();
		boolean grounded = client.player.onGround();

		boolean insideStartZone = false;
		String startMapName = null;

		List<Entity> nearbyZones = client.level.getEntities(
				client.player,
				client.player.getBoundingBox().inflate(256.0D),
				entity -> entity instanceof StartEntity
		);
		for (Entity entity : nearbyZones) {
			if (entity instanceof StartEntity startEntity) {
				if (isInsideZoneBounds(playerPos, startEntity.getCorner1(), startEntity.getCorner2())) {
					insideStartZone = true;
					if (startMapName == null || startMapName.isBlank()) {
						startMapName = startEntity.getPairedMap();
					}
				}
			}
		}

		// Arm a fresh start by slowing below walk on the ground in the start zone (circling never
		// goes below walk, so it can't re-arm/reset). While armed + grounded the timer is held at 0
		// (ground prestrafe is free); the run STARTS the moment they go airborne. Mirrors StartEntity.
		Vec3 vel = client.player.getDeltaMovement();
		double horizontalSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
		boolean belowWalk = horizontalSpeed < net.nerdorg.minehop.entity.custom.StartEntity.WALK_SPEED_BPT;
		if (insideStartZone && grounded && belowWalk) {
			startZoneArmed = true;
		}
		if (startZoneArmed) {
			if (insideStartZone && grounded) {
				startTime = System.nanoTime();
				lastSendTime = 0.0F;
				activeRunMapName = startMapName == null ? "" : startMapName;
				lastFinishSamplePos = null;
				lastFinishSampleNanos = 0L;
				lastFinishSampleStartNanos = startTime;
			} else {
				// became airborne (or left the zone) -> run starts; freeze startTime, disarm
				startZoneArmed = false;
			}
		}

		wasInsideStartZone = insideStartZone;
	}

	private static void updateRunTimerFinishZones(Minecraft client, long nowNanos) {
		if (client == null || client.player == null || client.level == null) {
			return;
		}
		if (client.player.isCreative() || client.player.isSpectator() || startTime == 0L) {
			resetFinishSampling();
			wasInsideEndZone = false;
			return;
		}
		if (lastFinishSampleStartNanos != startTime) {
			resetFinishSampling();
			lastFinishSampleStartNanos = startTime;
		}

		Vec3 samplePos = getInterpolatedPlayerPosition(client);
		if (samplePos == null) {
			return;
		}

		FinishZoneHit finishHit = findFinishZoneHit(client, lastFinishSamplePos, samplePos);
		boolean insideEndZone = finishHit != null && finishHit.insideAtSample;
		if (finishHit != null && (!wasInsideEndZone || finishHit.fraction > 0.0D)) {
			long finishNanos = nowNanos;
			if (lastFinishSampleNanos > 0L && nowNanos > lastFinishSampleNanos) {
				double fraction = Mth.clamp(finishHit.fraction, 0.0D, 1.0D);
				finishNanos = lastFinishSampleNanos + Math.round((nowNanos - lastFinishSampleNanos) * fraction);
			}
			double elapsedTime = ((double) (finishNanos - startTime)) / 1_000_000_000.0D;
			if (Double.isFinite(elapsedTime) && elapsedTime >= 0.0D && finishHit.mapName != null && !finishHit.mapName.isBlank()) {
				ClientPacketHandler.sendEndMapEvent(finishHit.mapName, elapsedTime, finishHit.position);
			}
			clearClientRunState();
			return;
		}

		lastFinishSamplePos = samplePos;
		lastFinishSampleNanos = nowNanos;
		wasInsideEndZone = insideEndZone;
	}

	private static FinishZoneHit findFinishZoneHit(Minecraft client, Vec3 previousPos, Vec3 samplePos) {
		FinishZoneHit bestHit = null;
		List<Entity> nearbyZones = client.level.getEntities(
				client.player,
				client.player.getBoundingBox().inflate(256.0D),
				entity -> entity instanceof EndEntity
		);
		for (Entity entity : nearbyZones) {
			if (!(entity instanceof EndEntity endEntity)) {
				continue;
			}
			String mapName = endEntity.getPairedMap();
			if (activeRunMapName != null && !activeRunMapName.isBlank() && !activeRunMapName.equals(mapName)) {
				continue;
			}
			AABB box = getZoneBoundsBox(endEntity.getCorner1(), endEntity.getCorner2());
			if (box == null) {
				continue;
			}
			double fraction = previousPos == null ? (box.contains(samplePos) ? 1.0D : Double.NaN) : segmentEntryFraction(box, previousPos, samplePos);
			if (!Double.isFinite(fraction)) {
				continue;
			}
			if (bestHit == null || fraction < bestHit.fraction) {
				Vec3 hitPosition = previousPos == null ? samplePos : previousPos.lerp(samplePos, Mth.clamp(fraction, 0.0D, 1.0D));
				bestHit = new FinishZoneHit(mapName, fraction, hitPosition, box.contains(samplePos));
			}
		}
		return bestHit;
	}

	private static Vec3 getInterpolatedPlayerPosition(Minecraft client) {
		if (client == null || client.player == null) {
			return null;
		}
		float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		return new Vec3(
				Mth.lerp((double) tickDelta, client.player.xo, client.player.getX()),
				Mth.lerp((double) tickDelta, client.player.yo, client.player.getY()),
				Mth.lerp((double) tickDelta, client.player.zo, client.player.getZ())
		);
	}

	private static void clearClientRunState() {
		startTime = 0L;
		lastSendTime = 0.0F;
		runTimerHudVisible = false;
		startZoneArmed = false;
		wasInsideStartZone = false;
		wasInsideEndZone = false;
		activeRunMapName = "";
		resetFinishSampling();
	}

	private static void resetFinishSampling() {
		lastFinishSamplePos = null;
		lastFinishSampleNanos = 0L;
		lastFinishSampleStartNanos = startTime;
	}

	private static boolean isInsideZoneBounds(Vec3 pos, BlockPos corner1, BlockPos corner2) {
		if (pos == null || corner1 == null || corner2 == null) {
			return false;
		}
		double minX = Math.min(corner1.getX(), corner2.getX());
		double minY = Math.min(corner1.getY(), corner2.getY());
		double minZ = Math.min(corner1.getZ(), corner2.getZ());
		double maxX = Math.max(corner1.getX(), corner2.getX());
		double maxY = Math.max(corner1.getY(), corner2.getY());
		double maxZ = Math.max(corner1.getZ(), corner2.getZ());

		if (maxX <= minX) {
			maxX = minX + 1.0D;
		}
		if (maxY <= minY) {
			maxY = minY + 1.0D;
		}
		if (maxZ <= minZ) {
			maxZ = minZ + 1.0D;
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ).contains(pos);
	}

	private static AABB getZoneBoundsBox(BlockPos corner1, BlockPos corner2) {
		if (corner1 == null || corner2 == null) {
			return null;
		}
		double minX = Math.min(corner1.getX(), corner2.getX());
		double minY = Math.min(corner1.getY(), corner2.getY());
		double minZ = Math.min(corner1.getZ(), corner2.getZ());
		double maxX = Math.max(corner1.getX(), corner2.getX());
		double maxY = Math.max(corner1.getY(), corner2.getY());
		double maxZ = Math.max(corner1.getZ(), corner2.getZ());
		if (maxX <= minX) {
			maxX = minX + 1.0D;
		}
		if (maxY <= minY) {
			maxY = minY + 1.0D;
		}
		if (maxZ <= minZ) {
			maxZ = minZ + 1.0D;
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static double segmentEntryFraction(AABB box, Vec3 start, Vec3 end) {
		if (box == null || start == null || end == null) {
			return Double.NaN;
		}
		if (box.contains(start)) {
			return 0.0D;
		}

		double tMin = 0.0D;
		double tMax = 1.0D;
		double dx = end.x - start.x;
		double dy = end.y - start.y;
		double dz = end.z - start.z;

		double[] startValues = {start.x, start.y, start.z};
		double[] deltas = {dx, dy, dz};
		double[] mins = {box.minX, box.minY, box.minZ};
		double[] maxs = {box.maxX, box.maxY, box.maxZ};

		for (int i = 0; i < 3; i++) {
			double delta = deltas[i];
			if (Math.abs(delta) < 1.0E-12D) {
				if (startValues[i] < mins[i] || startValues[i] >= maxs[i]) {
					return Double.NaN;
				}
				continue;
			}
			double invDelta = 1.0D / delta;
			double t1 = (mins[i] - startValues[i]) * invDelta;
			double t2 = (maxs[i] - startValues[i]) * invDelta;
			if (t1 > t2) {
				double swap = t1;
				t1 = t2;
				t2 = swap;
			}
			tMin = Math.max(tMin, t1);
			tMax = Math.min(tMax, t2);
			if (tMin > tMax) {
				return Double.NaN;
			}
		}
		if (tMin < 0.0D || tMin > 1.0D) {
			return Double.NaN;
		}
		return tMin;
	}

	private record FinishZoneHit(String mapName, double fraction, Vec3 position, boolean insideAtSample) {
	}

	private static boolean isOfficialServerVersion() {
		return SharedConstants.getCurrentVersion().getName().equals(Minehop.OFFICIAL_SERVER_MC_VERSION);
	}

	private static ServerData findServer(ServerList serverList, String ip) {
		for (int i = 0; i < serverList.size(); i++) {
			ServerData info = serverList.get(i);
			if (info.ip.equals(ip)) {
				return info;
			}
		}
		return null;
	}
}
