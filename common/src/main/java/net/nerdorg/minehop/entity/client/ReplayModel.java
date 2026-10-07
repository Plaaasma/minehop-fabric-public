package net.nerdorg.minehop.entity.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

// Made with Blockbench 4.9.4
// Exported for Minecraft version 1.17+ for Yarn
// Paste this class into your mod and generate all required imports
public class ReplayModel extends EntityModel<ReplayEntityRenderState> {
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart body;
	public ReplayModel(ModelPart root) {
		super(root);
		this.root = root.getChild("root");
		this.head = this.root.getChild("head");
		this.body = this.root.getChild("body");
	}
	public static LayerDefinition getTexturedModelData() {
		MeshDefinition modelData = new MeshDefinition();
		PartDefinition modelPartData = modelData.getRoot();
		PartDefinition root = modelPartData.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		// Pivot at the neck (same place on screen as before) so the head can turn and pitch around it.
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -24.0F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -24.0F, -2.0F, 8.0F, 24.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(0, 28).addBox(-8.0F, -24.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(24, 0).addBox(4.0F, -24.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
		return LayerDefinition.create(modelData, 64, 64);
	}
	@Override
	public void setupAnim(ReplayEntityRenderState state) {
		super.setupAnim(state);
		this.body.visible = false;
		this.head.visible = state.renderHead;
		// Look where the recorded player looked: head yaw relative to the body, and pitch (was always ~0 before).
		this.head.yRot = state.yRot * ((float) Math.PI / 180.0F);
		this.head.xRot = state.xRot * ((float) Math.PI / 180.0F);
	}
}
