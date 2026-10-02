package net.nerdorg.minehop.entity.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.nerdorg.minehop.entity.custom.EndEntity;

// 1.21.1: models are typed by the entity and render a single root part (SinglePartEntityModel is the
// pre-1.21.2 equivalent of EntityModel(ModelPart root), which renders the whole root).
public class EndModel extends SinglePartEntityModel<EndEntity> {
	private final ModelPart root;
	private final ModelPart bb_main;
	public EndModel(ModelPart root) {
		this.root = root;
		this.bb_main = root.getChild("bb_main");
	}
	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData modelPartData = modelData.getRoot();
		ModelPartData bb_main = modelPartData.addChild("bb_main", ModelPartBuilder.create().uv(2, 2).cuboid(1.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, new Dilation(0.0F)), ModelTransform.pivot(0.0F, 24.0F, 0.0F));
		return TexturedModelData.of(modelData, 16, 16);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(EndEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
	}
}
