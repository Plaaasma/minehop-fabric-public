package net.nerdorg.minehop.entity.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.nerdorg.minehop.entity.custom.ReplayEntity;

// Made with Blockbench 4.9.4
// Exported for Minecraft version 1.17+ for Yarn
// Paste this class into your mod and generate all required imports
public class ReplayModel extends EntityModel<ReplayEntity> {
	// 1.20.1: the root part the 1.21.2+ Model base class used to hold.
	private final ModelPart modelRoot;
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart body;
	public ReplayModel(ModelPart root) {
		this.modelRoot = root;
		this.root = root.getChild("root");
		this.head = this.root.getChild("head");
		this.body = this.root.getChild("body");
	}
	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData modelPartData = modelData.getRoot();
		ModelPartData root = modelPartData.addChild("root", ModelPartBuilder.create(), ModelTransform.pivot(0.0F, 24.0F, 0.0F));

		ModelPartData head = root.addChild("head", ModelPartBuilder.create().uv(16, 20).cuboid(-4.0F, -32.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.0F)), ModelTransform.pivot(0.0F, 0.0F, 0.0F));

		ModelPartData body = root.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -24.0F, -2.0F, 8.0F, 24.0F, 4.0F, new Dilation(0.0F))
				.uv(0, 28).cuboid(-8.0F, -24.0F, -2.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.0F))
				.uv(24, 0).cuboid(4.0F, -24.0F, -2.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.0F)), ModelTransform.pivot(0.0F, 0.0F, 0.0F));
		return TexturedModelData.of(modelData, 64, 64);
	}
	@Override
	public void setAngles(ReplayEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		this.body.visible = false;
		this.head.visible = entity.shouldRenderHead();
		this.head.yaw = (float) Math.toRadians(head.yaw);
	}

	// 1.20.1: EntityModel has no root part to render implicitly (1.21.2+); render the whole tree.
	@Override
	public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
		this.modelRoot.render(matrices, vertices, light, overlay, red, green, blue, alpha);
	}
}
