package net.nerdorg.minehop.client.replay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.client.ClientVisibility;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.entity.client.ModModelLayers;
import net.nerdorg.minehop.entity.client.ReplayEntityRenderState;
import net.nerdorg.minehop.entity.client.ReplayModel;
import net.nerdorg.minehop.platform.ClientServices;

/**
 * Draws the ghosts of client playback with the replay ghost's own model and texture (ReplayModel, as ReplayRenderer
 * draws the server's ghost entities, same pose: body and head turned to the recorded yaw, head pitched): the watched
 * replay when the camera isn't looking through it (third person), and the race ghost, translucent. Drawn after the
 * entities, at the pose of this rendered frame.
 */
public final class ReplayGhostRenderer {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "textures/entity/replay_texture.png");
    private static final float EYE_HEIGHT = 1.62F;

    private static EntityModelSet bakedFrom;
    private static ReplayModel model;
    private static final ReplayEntityRenderState STATE = new ReplayEntityRenderState();

    private ReplayGhostRenderer() {
    }

    public static void register() {
        ClientServices.CLIENT.onWorldRenderAfterEntities(context -> {
            PoseStack poses = context.matrixStack();
            MultiBufferSource buffers = context.consumers();
            Camera camera = context.camera();
            Minecraft mc = Minecraft.getInstance();
            if (poses == null || buffers == null || camera == null || mc.level == null) {
                return;
            }
            ReplayPlayback.Watch watch = ReplayPlayback.current();
            if (watch != null && watch.pose != null && (camera.isDetached() || !ReplayPlayback.cameraIsReplay())
                    && !ClientVisibility.hideReplay()) {
                draw(mc, poses, buffers, camera, watch.pose, 0xFFFFFFFF, false);
            }
            ReplayPlayback.ViewPose race = RaceGhost.currentPose();
            if (race != null) {
                int opacity = ConfigWrapper.config == null || ConfigWrapper.config.replay == null ? 50 : ConfigWrapper.config.replay.race_ghost_opacity;
                int alpha = Math.max(10, Math.min(100, opacity)) * 255 / 100;
                draw(mc, poses, buffers, camera, race, (alpha << 24) | 0xFFFFFF, true);
            }
        });
    }

    private static ReplayModel model(Minecraft mc) {
        EntityModelSet models = mc.getEntityModels();
        if (model == null || bakedFrom != models) {
            model = new ReplayModel(models.bakeLayer(ModModelLayers.REPLAY_ENTITY));
            bakedFrom = models;
        }
        return model;
    }

    private static void draw(Minecraft mc, PoseStack poses, MultiBufferSource buffers, Camera camera, ReplayPlayback.ViewPose pose,
                             int color, boolean translucent) {
        Vec3 cameraPos = camera.position();
        double dx = pose.x() - cameraPos.x;
        double dy = pose.y() - cameraPos.y;
        double dz = pose.z() - cameraPos.z;
        if (dx * dx + dy * dy + dz * dz > 256.0D * 256.0D) {
            return;
        }
        ReplayModel ghost = model(mc);
        int light = LevelRenderer.getLightCoords(mc.level, BlockPos.containing(pose.x(), pose.y() + EYE_HEIGHT, pose.z()));
        poses.pushPose();
        poses.translate(dx, dy, dz);
        // LivingEntityRenderer#render: body turned to the yaw, flipped, model space.
        poses.mulPose(Axis.YP.rotationDegrees(180.0F - pose.yaw()));
        poses.scale(-1.0F, -1.0F, 1.0F);
        poses.translate(0.0F, -1.501F, 0.0F);
        STATE.yRot = 0.0F;
        STATE.xRot = pose.pitch();
        STATE.renderHead = true;
        ghost.setupAnim(STATE);
        RenderType type = translucent ? RenderTypes.entityTranslucent(TEXTURE) : ghost.renderType(TEXTURE);
        ghost.renderToBuffer(poses, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY, color);
        poses.popPose();
    }
}
