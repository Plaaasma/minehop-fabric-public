package net.nerdorg.minehop.entity.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class SurfRampModel extends EntityModel<SurfRampEntityRenderState> {
    private final ModelPart root;

    public SurfRampModel(ModelPart root) {
        super(root);
        this.root = root;
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild("bb_main", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(modelData, 16, 16);
    }

    @Override
    public void setupAnim(SurfRampEntityRenderState state) {
    }
}
