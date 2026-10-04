package net.nerdorg.minehop.entity.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.nerdorg.minehop.entity.custom.ReplayEntity;

public class ReplayEntityRenderState extends LivingEntityRenderState {
    public ReplayEntity replayEntity;
    public boolean renderHead = true;
}
