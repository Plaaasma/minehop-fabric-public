package net.nerdorg.minehop.entity.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public class ModModelLayers {
    public static final ModelLayerLocation GAMEMODE_ENTITY =
            new ModelLayerLocation(new ResourceLocation(Minehop.MOD_ID, "gamemode_entity"), "main");
    public static final ModelLayerLocation RESET_ENTITY =
            new ModelLayerLocation(new ResourceLocation(Minehop.MOD_ID, "reset_entity"), "main");
    public static final ModelLayerLocation START_ENTITY =
            new ModelLayerLocation(new ResourceLocation(Minehop.MOD_ID, "start_entity"), "main");
    public static final ModelLayerLocation END_ENTITY =
            new ModelLayerLocation(new ResourceLocation(Minehop.MOD_ID, "end_entity"), "main");
    public static final ModelLayerLocation REPLAY_ENTITY =
            new ModelLayerLocation(new ResourceLocation(Minehop.MOD_ID, "replay_entity"), "main");
    public static final ModelLayerLocation SURF_RAMP_ENTITY =
            new ModelLayerLocation(new ResourceLocation(Minehop.MOD_ID, "surf_ramp_entity"), "main");
    public static final ModelLayerLocation CUSTOM_MODEL =
            new ModelLayerLocation(new ResourceLocation(Minehop.MOD_ID, "custom_model"), "main");
}
