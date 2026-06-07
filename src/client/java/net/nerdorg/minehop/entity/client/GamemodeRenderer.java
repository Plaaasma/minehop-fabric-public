package net.nerdorg.minehop.entity.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.entity.custom.GamemodeEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.networking.ClientPacketHandler;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public class GamemodeRenderer extends MobEntityRenderer<GamemodeEntity, GamemodeEntityRenderState, GamemodeModel> {
    private static final Identifier TEXTURE = Identifier.of(Minehop.MOD_ID, "textures/entity/zone.png");

    public GamemodeRenderer(EntityRendererFactory.Context context) {
        super(context, new GamemodeModel(context.getPart(ModModelLayers.GAMEMODE_ENTITY)), 0.001f);
    }

    @Override
    public Identifier getTexture(GamemodeEntityRenderState state) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(GamemodeEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return true;
    }

    @Override
    public GamemodeEntityRenderState createRenderState() {
        return new GamemodeEntityRenderState();
    }

    @Override
    public void updateRenderState(GamemodeEntity gamemodeEntity, GamemodeEntityRenderState state, float tickDelta) {
        state.gamemodeEntity = gamemodeEntity;
    }
}
