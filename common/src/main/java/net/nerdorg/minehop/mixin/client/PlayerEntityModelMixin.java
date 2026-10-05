package net.nerdorg.minehop.mixin.client;


import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(PlayerModel.class)
public class PlayerEntityModelMixin extends HumanoidModel<AvatarRenderState> {
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
        float f = 0.25F;
        if (slim) {
            PartDefinition modelPartData2 = modelPartData.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, dilation), PartPose.offset(5.0F, 2.0F, 0.0F));
            PartDefinition modelPartData3 = modelPartData.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, dilation), PartPose.offset(-5.0F, 2.0F, 0.0F));
            modelPartData2.addOrReplaceChild("left_sleeve", CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.ZERO);
            modelPartData3.addOrReplaceChild("right_sleeve", CubeListBuilder.create().texOffs(40, 32).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.ZERO);
        } else {
            PartDefinition modelPartData2 = modelPartData.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation), PartPose.offset(5.0F, 2.0F, 0.0F));
            PartDefinition modelPartData3 = modelPartData.getChild("right_arm");
            modelPartData2.addOrReplaceChild("left_sleeve", CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.ZERO);
            modelPartData3.addOrReplaceChild("right_sleeve", CubeListBuilder.create().texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.ZERO);
        }

        PartDefinition modelPartData2 = modelPartData.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation), PartPose.offset(1.9F, 12.0F, 0.0F));
        PartDefinition modelPartData3 = modelPartData.getChild("right_leg");
        modelPartData2.addOrReplaceChild("left_pants", CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.ZERO);
        modelPartData3.addOrReplaceChild("right_pants", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.ZERO);
        PartDefinition modelPartData4 = modelPartData.getChild("body");
        modelPartData4.addOrReplaceChild("jacket", CubeListBuilder.create().texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, dilation.extend(0.25F)), PartPose.ZERO);
        // Custom stuff here

        /*ear.addChild(EntityModelPartNames.CUBE, ModelPartBuilder.create().uv(0,0)
                        .cuboid(-4.0F, -1.0F, -2.0F, 10.0F, 10.0F, 10.0F),
                ModelTransform.origin(0.0F, 24.0F, 0.0F));*/

        return modelData;
    }
}
