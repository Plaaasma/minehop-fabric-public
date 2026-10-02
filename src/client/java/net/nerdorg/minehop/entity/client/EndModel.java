package net.nerdorg.minehop.entity.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.nerdorg.minehop.entity.custom.EndEntity;

public class EndModel extends EntityModel<EndEntity> {
	// 1.20.1: the root part the 1.21.2+ Model base class used to hold.
	private final ModelPart modelRoot;
	private final ModelPart bb_main;

	public EndModel(ModelPart root) {
		this.modelRoot = root;
		this.bb_main = root.getChild("bb_main");
	}
	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData modelPartData = modelData.getRoot();
		ModelPartData bb_main = modelPartData.addChild("bb_main", ModelPartBuilder.create().uv(2, 2).cuboid(1.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, new Dilation(0.0F)), ModelTransform.pivot(0.0F, 24.0F, 0.0F));
		return TexturedModelData.of(modelData, 16, 16);
	}

	@Override
	public void setAngles(EndEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
	}

	// 1.20.1: EntityModel has no root part to render implicitly (1.21.2+); render the whole tree.
	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
		this.modelRoot.render(matrices, vertices, light, overlay, red, green, blue, alpha);
	}
}
