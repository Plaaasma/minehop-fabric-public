package net.nerdorg.minehop.entity.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.nerdorg.minehop.entity.custom.ReplayEntity;

// Made with Blockbench 4.9.4
// Exported for Minecraft version 1.17+ for Yarn
// Paste this class into your mod and generate all required imports
// 1.21.1: typed by the entity (no render state); renders the whole model root like 1.21.4's
// EntityModel(ModelPart root).
public class ReplayModel extends HierarchicalModel<ReplayEntity> {
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
	public ModelPart root() {
		return this.modelRoot;
	}

	@Override
	public void setupAnim(ReplayEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		// 1.21.1: HierarchicalModel doesn't reset the parts before setupAnim (1.21.4's EntityModel#setupAnim does).
		this.root().getAllParts().forEach(ModelPart::resetPose);
		this.body.visible = false;
		this.head.visible = entity.shouldRenderHead();
		// Look where the recorded player looked: head yaw relative to the body, and pitch (was always ~0 before).
		this.head.yRot = netHeadYaw * ((float) Math.PI / 180.0F);
		this.head.xRot = headPitch * ((float) Math.PI / 180.0F);
	}
}
