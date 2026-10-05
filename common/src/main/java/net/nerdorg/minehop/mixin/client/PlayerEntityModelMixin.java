package net.nerdorg.minehop.mixin.client;


import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

// 1.21.1: PlayerModel<T extends LivingEntity> (no render state). The overwrite reproduces the vanilla 1.21.1
// layout (sleeves/pants/jacket/ear/cloak are root parts before 1.21.2), as the 1.21.4 overwrite reproduces
// vanilla 1.21.4; the custom part stays a placeholder.
@Mixin(PlayerModel.class)
public class PlayerEntityModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
    public PlayerEntityModelMixin(ModelPart modelPart) {
        super(modelPart);
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public static MeshDefinition createMesh(CubeDeformation dilation, boolean slim) {
        MeshDefinition modelData = HumanoidModel.createMesh(dilation, 0.0F);
        PartDefinition modelPartData = modelData.getRoot();
        modelPartData.addOrReplaceChild("ear", CubeListBuilder.create().texOffs(24, 0).addBox(-3.0F, -6.0F, -1.0F, 6.0F, 6.0F, 1.0F, dilation), PartPose.ZERO);
        modelPartData.addOrReplaceChild("cloak", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F, dilation, 1.0F, 0.5F), PartPose.offset(0.0F, 0.0F, 0.0F));
        float f = 0.25F;
        if (slim) {
            modelPartData.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, dilation), PartPose.offset(5.0F, 2.5F, 0.0F));
            modelPartData.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, dilation), PartPose.offset(-5.0F, 2.5F, 0.0F));
            modelPartData.addOrReplaceChild("left_sleeve", CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.offset(5.0F, 2.5F, 0.0F));
            modelPartData.addOrReplaceChild("right_sleeve", CubeListBuilder.create().texOffs(40, 32).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.offset(-5.0F, 2.5F, 0.0F));
        } else {
            modelPartData.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation), PartPose.offset(5.0F, 2.0F, 0.0F));
            modelPartData.addOrReplaceChild("left_sleeve", CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.offset(5.0F, 2.0F, 0.0F));
            modelPartData.addOrReplaceChild("right_sleeve", CubeListBuilder.create().texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.offset(-5.0F, 2.0F, 0.0F));
        }

        modelPartData.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation), PartPose.offset(1.9F, 12.0F, 0.0F));
        modelPartData.addOrReplaceChild("left_pants", CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.offset(1.9F, 12.0F, 0.0F));
        modelPartData.addOrReplaceChild("right_pants", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.offset(-1.9F, 12.0F, 0.0F));
        modelPartData.addOrReplaceChild("jacket", CubeListBuilder.create().texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.ZERO);
        // Custom stuff here

        /*ear.addChild(EntityModelPartNames.CUBE, ModelPartBuilder.create().uv(0,0)
                        .cuboid(-4.0F, -1.0F, -2.0F, 10.0F, 10.0F, 10.0F),
                ModelTransform.pivot(0.0F, 24.0F, 0.0F));*/

        return modelData;
    }
}
