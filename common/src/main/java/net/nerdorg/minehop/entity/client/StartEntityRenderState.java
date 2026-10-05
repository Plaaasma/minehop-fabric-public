package net.nerdorg.minehop.entity.client;

import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;

// 1.20.1: entity renderers take the entity directly (no render states before 1.21.2); kept as the
// per-frame holder the renderer fills from the entity, so the render code stays the same.
public class StartEntityRenderState {
	public StartEntity startEntity;
}
