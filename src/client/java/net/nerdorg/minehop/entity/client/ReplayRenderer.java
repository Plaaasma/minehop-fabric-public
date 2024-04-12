package net.nerdorg.minehop.entity.client;

import com.mojang.authlib.AuthenticationService;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.entity.custom.ReplayEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.networking.ClientPacketHandler;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public class ReplayRenderer extends MobEntityRenderer<ReplayEntity, ReplayModel> {
    private static final Identifier TEXTURE = new Identifier(Minehop.MOD_ID, "textures/entity/replay_texture.png");

    public ReplayRenderer(EntityRendererFactory.Context context) {
        super(context, new ReplayModel(context.getPart(ModModelLayers.REPLAY_ENTITY)), 0.001f);
    }

    @Override
    public Identifier getTexture(ReplayEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ReplayEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return !MinehopClient.hideReplay;
    }
}
