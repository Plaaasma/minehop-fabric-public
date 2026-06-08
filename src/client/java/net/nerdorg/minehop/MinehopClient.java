package net.nerdorg.minehop;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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

public class MinehopClient implements ClientModInitializer {
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
	private static Vec3d lastFinishSamplePos = null;
	private static long lastFinishSampleNanos = 0L;
	private static long lastFinishSampleStartNanos = 0L;

	//public static boolean hideSelf = false;
	//public static boolean hideReplay = false;
	//public static boolean hideOthers = false;

	public static long startTime = 0;
	public static float lastSendTime = 0;

	public static List<String> spectatorList = new ArrayList<>();

    @Override
	public void onInitializeClient() {
		MinecraftClient minecraft = MinecraftClient.getInstance();
		minecraft.execute(() -> {
			ServerList serverList = new ServerList(minecraft);
			serverList.loadFile();
			if (!isServerInList(serverList, "play.minehop.net")) {
				serverList.add(new ServerInfo("§c§l§nOfficial Minehop Server", "play.minehop.net", ServerInfo.ServerType.OTHER), false);
				serverList.swapEntries(0, serverList.size() - 1);
				serverList.saveFile();
			}
		});

		ClientPlayConnectionEvents.INIT.register((handler, client) -> {
			ClientPacketHandler.registerReceivers();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
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
		EntityRendererRegistry.register(ModEntities.GAMEMODE_ENTITY, GamemodeRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.GAMEMODE_ENTITY, GamemodeModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.RESET_ENTITY, ResetRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.RESET_ENTITY, ResetModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.START_ENTITY, StartRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.START_ENTITY, StartModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.END_ENTITY, EndRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.END_ENTITY, EndModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.REPLAY_ENTITY, ReplayRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.REPLAY_ENTITY, ReplayModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.SURF_RAMP_ENTITY, SurfRampRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.SURF_RAMP_ENTITY, SurfRampModel::getTexturedModelData);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!client.isInSingleplayer()) {
				Minehop.override_config = true;
			}
			if (client.player != null) {
				if (resetCarryTicks > 0) {
					client.player.setVelocity(resetCarryX, resetCarryY, resetCarryZ);
					client.player.setOnGround(false);
					resetCarryTicks--;
					if (resetCarryTicks <= 0) {
						resetCarryX = 0.0D;
						resetCarryY = 0.0D;
						resetCarryZ = 0.0D;
					}
				}
				if (client.options.jumpKey.isPressed()) {
					jumping = true;
				}
				else {
					jumping = false;
				}

				// Jump/SSJ counter via upward-velocity spike (takeoff). With auto-bhop the player
				// lands AND jumps in the same tick, so onGround is never true at a tick boundary —
				// counting on isOnGround() missed jumps. Detect the jump impulse directly instead.
				if (!client.player.isSpectator()) {
					Vec3d jv = client.player.getVelocity();
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
							last_jump_time = client.world != null ? client.world.getTime() : 0L;
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
		WorldRenderEvents.END.register(context -> {
			long now = System.nanoTime();
			long previous = lastRenderFrameNanos[0];
			if (previous > 0L) {
				long frameNanos = now - previous;
				if (frameNanos > 0L) {
					double instantFps = 1_000_000_000.0D / (double) frameNanos;
					int fps = (int) Math.round(instantFps);
					Minehop.clientRenderFps = MathHelper.clamp(fps, 1, 4000);
					Minehop.clientRenderFrameNanos = frameNanos;
				}
			}
			lastRenderFrameNanos[0] = now;
			updateRunTimerFinishZones(MinecraftClient.getInstance(), now);
		});

		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.BOOSTER_BLOCK, RenderLayer.getTranslucent());
	}

	private static void updateRunTimerStartZones(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) {
			return;
		}
		if (client.player.isCreative() || client.player.isSpectator()) {
			clearClientRunState();
			return;
		}

		Vec3d playerPos = client.player.getPos();
		boolean grounded = client.player.isOnGround();

		boolean insideStartZone = false;
		String startMapName = null;

		List<Entity> nearbyZones = client.world.getOtherEntities(
				client.player,
				client.player.getBoundingBox().expand(256.0D),
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
		Vec3d vel = client.player.getVelocity();
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

	private static void updateRunTimerFinishZones(MinecraftClient client, long nowNanos) {
		if (client == null || client.player == null || client.world == null) {
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

		Vec3d samplePos = getInterpolatedPlayerPosition(client);
		if (samplePos == null) {
			return;
		}

		FinishZoneHit finishHit = findFinishZoneHit(client, lastFinishSamplePos, samplePos);
		boolean insideEndZone = finishHit != null && finishHit.insideAtSample;
		if (finishHit != null && (!wasInsideEndZone || finishHit.fraction > 0.0D)) {
			long finishNanos = nowNanos;
			if (lastFinishSampleNanos > 0L && nowNanos > lastFinishSampleNanos) {
				double fraction = MathHelper.clamp(finishHit.fraction, 0.0D, 1.0D);
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

	private static FinishZoneHit findFinishZoneHit(MinecraftClient client, Vec3d previousPos, Vec3d samplePos) {
		FinishZoneHit bestHit = null;
		List<Entity> nearbyZones = client.world.getOtherEntities(
				client.player,
				client.player.getBoundingBox().expand(256.0D),
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
			Box box = getZoneBoundsBox(endEntity.getCorner1(), endEntity.getCorner2());
			if (box == null) {
				continue;
			}
			double fraction = previousPos == null ? (box.contains(samplePos) ? 1.0D : Double.NaN) : segmentEntryFraction(box, previousPos, samplePos);
			if (!Double.isFinite(fraction)) {
				continue;
			}
			if (bestHit == null || fraction < bestHit.fraction) {
				Vec3d hitPosition = previousPos == null ? samplePos : previousPos.lerp(samplePos, MathHelper.clamp(fraction, 0.0D, 1.0D));
				bestHit = new FinishZoneHit(mapName, fraction, hitPosition, box.contains(samplePos));
			}
		}
		return bestHit;
	}

	private static Vec3d getInterpolatedPlayerPosition(MinecraftClient client) {
		if (client == null || client.player == null) {
			return null;
		}
		float tickDelta = client.getRenderTickCounter().getTickDelta(true);
		return new Vec3d(
				MathHelper.lerp((double) tickDelta, client.player.prevX, client.player.getX()),
				MathHelper.lerp((double) tickDelta, client.player.prevY, client.player.getY()),
				MathHelper.lerp((double) tickDelta, client.player.prevZ, client.player.getZ())
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

	private static boolean isInsideZoneBounds(Vec3d pos, BlockPos corner1, BlockPos corner2) {
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
		return new Box(minX, minY, minZ, maxX, maxY, maxZ).contains(pos);
	}

	private static Box getZoneBoundsBox(BlockPos corner1, BlockPos corner2) {
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
		return new Box(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static double segmentEntryFraction(Box box, Vec3d start, Vec3d end) {
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

	private record FinishZoneHit(String mapName, double fraction, Vec3d position, boolean insideAtSample) {
	}

	private boolean isServerInList(ServerList serverList, String ip) {
		for (int i = 0; i < serverList.size(); i++) {
			ServerInfo info = serverList.get(i);
			if (info.address.equals(ip)) {
				return true;
			}
		}
		return false;
	}
}
