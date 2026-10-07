package net.nerdorg.minehop.client.replay;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.ReplayEntity;

/**
 * The camera of a replay the client plays itself: a client-only entity that is never added to the level (nothing
 * ticks, renders, saves or sends it); {@link ReplayPlayback} moves it to the interpolated recorded view every rendered
 * frame and makes it the game's camera entity, so first person (and F5) work as when spectating an entity. It has the
 * player's eye heights, lowered while the recorded player was sneaking.
 */
public class ReplayCameraEntity extends ReplayEntity {
    private static final EntityDimensions STANDING = EntityDimensions.scalable(0.6F, 1.8F).withEyeHeight(1.62F);
    private static final EntityDimensions CROUCHING = EntityDimensions.scalable(0.6F, 1.5F).withEyeHeight(1.27F);

    public ReplayCameraEntity(ClientLevel level) {
        super(ModEntities.REPLAY_ENTITY.get(), level);
        this.noPhysics = true;
        this.refreshDimensions();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return pose == Pose.CROUCHING ? CROUCHING : STANDING;
    }

    /** Puts the camera at a pose (feet position, view angles), with no interpolation from the previous one. */
    void place(ReplayPlayback.ViewPose view) {
        Pose pose = view.sneaking() ? Pose.CROUCHING : Pose.STANDING;
        if (this.getPose() != pose) {
            this.setPose(pose);
            this.refreshDimensions();
        }
        this.setPos(view.x(), view.y(), view.z());
        this.xo = view.x();
        this.yo = view.y();
        this.zo = view.z();
        this.xOld = view.x();
        this.yOld = view.y();
        this.zOld = view.z();
        this.setYRot(view.yaw());
        this.setXRot(view.pitch());
        this.yRotO = view.yaw();
        this.xRotO = view.pitch();
        this.setYHeadRot(view.yaw());
        this.yHeadRotO = view.yaw();
        this.setYBodyRot(view.yaw());
        this.yBodyRotO = view.yaw();
    }
}
