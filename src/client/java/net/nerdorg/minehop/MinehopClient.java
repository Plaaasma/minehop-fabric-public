package net.nerdorg.minehop;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.EntityType;
import net.minecraft.util.TimeSupplier;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.client.SqueedometerHud;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.client.*;
import net.nerdorg.minehop.event.KeyInputHandler;
import net.nerdorg.minehop.networking.ClientPacketHandler;

import java.util.ArrayList;
import java.util.List;

public class MinehopClient implements ClientModInitializer {
	public static SqueedometerHud squeedometerHud;

	public static int jump_count = 0;
	public static boolean jumping = false;
	public static double last_jump_speed = 0;
	public static double old_jump_speed = 0;
	public static long last_jump_time = 0;
	public static long old_jump_time = 0;
	public static double last_efficiency;

	public static boolean hideSelf = true;
	public static boolean hideOthers = false;

	public static long startTime = 0;
	public static float lastSendTime = 0;

	public static List<String> spectatorList = new ArrayList<>();

	@Override
	public void onInitializeClient() {
		ClientPacketHandler.registerReceivers();
		ConfigWrapper.loadConfig();
		squeedometerHud = new SqueedometerHud();

		KeyInputHandler.register();

		EntityRendererRegistry.register(ModEntities.RESET_ENTITY, ResetRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.RESET_ENTITY, ResetModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.START_ENTITY, StartRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.START_ENTITY, ResetModel::getTexturedModelData);
		EntityRendererRegistry.register(ModEntities.END_ENTITY, EndRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.END_ENTITY, ResetModel::getTexturedModelData);
		EntityModelLayerRegistry.registerModelLayer(ModModelLayers.CUSTOM_MODEL, CheaterPlayerModel::getTexturedModelData);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!client.isInSingleplayer()) {
				Minehop.override_config = true;
			}
			if (client.player != null) {
				if (client.options.jumpKey.isPressed()) {
					jumping = true;
				}
				else {
					jumping = false;
				}
			}
		});

		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.BOOSTER_BLOCK, RenderLayer.getTranslucent());
	}
}