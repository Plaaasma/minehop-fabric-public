package net.nerdorg.minehop.entity.client;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;

public class SurfRampModel extends EntityModel<SurfRampEntity> {
    // 1.20.1: the root part the 1.21.2+ Model base class used to hold.
    private final ModelPart modelRoot;
    private final ModelPart root;

    public SurfRampModel(ModelPart root) {
        this.modelRoot = root;
        this.root = root;
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();
        root.addChild("bb_main", ModelPartBuilder.create(), ModelTransform.NONE);
        return TexturedModelData.of(modelData, 16, 16);
    }

    @Override
    public void setAngles(SurfRampEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
    }

    // 1.20.1: EntityModel has no root part to render implicitly (1.21.2+); render the whole tree.
    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        this.modelRoot.render(matrices, vertices, light, overlay, red, green, blue, alpha);
    }
}
