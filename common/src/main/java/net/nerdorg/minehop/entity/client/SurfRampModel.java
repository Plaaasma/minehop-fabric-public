package net.nerdorg.minehop.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;

public class SurfRampModel extends EntityModel<SurfRampEntity> {
    // 1.20.1: the root part the 1.21.2+ Model base class used to hold.
    private final ModelPart modelRoot;
    private final ModelPart root;

    public SurfRampModel(ModelPart root) {
        this.modelRoot = root;
        this.root = root;
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild("bb_main", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(modelData, 16, 16);
    }

    @Override
    public void setupAnim(SurfRampEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
    }

    // 1.20.1: EntityModel has no root part to render implicitly (1.21.2+); render the whole tree.
    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        this.modelRoot.render(matrices, vertices, light, overlay, red, green, blue, alpha);
    }
}
