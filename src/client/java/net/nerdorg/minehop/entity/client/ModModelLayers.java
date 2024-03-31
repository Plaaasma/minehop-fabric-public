package net.nerdorg.minehop.entity.client;

import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public class ModModelLayers {
    public static final EntityModelLayer RESET_ENTITY =
            new EntityModelLayer(new Identifier(Minehop.MOD_ID, "reset_entity"), "main");
    public static final EntityModelLayer START_ENTITY =
            new EntityModelLayer(new Identifier(Minehop.MOD_ID, "start_entity"), "main");
    public static final EntityModelLayer END_ENTITY =
            new EntityModelLayer(new Identifier(Minehop.MOD_ID, "end_entity"), "main");
    public static final EntityModelLayer CUSTOM_MODEL =
            new EntityModelLayer(new Identifier(Minehop.MOD_ID, "custom_model"), "main");
}
