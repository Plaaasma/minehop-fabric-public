package net.nerdorg.minehop.entity.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.EntityModel;

public class EndModel extends EntityModel<EndEntityRenderState> {
	private final ModelPart bb_main;

	public EndModel(ModelPart root) {
		super(root);
		this.bb_main = root.getChild("bb_main");
	}
	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData modelPartData = modelData.getRoot();
		ModelPartData bb_main = modelPartData.addChild("bb_main", ModelPartBuilder.create().uv(2, 2).cuboid(1.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, new Dilation(0.0F)), ModelTransform.origin(0.0F, 24.0F, 0.0F));  // There was ModelTransform.pivot here.
		return TexturedModelData.of(modelData, 16, 16);
	}

	@Override
	public void setAngles(EndEntityRenderState state) {
	}

}