package net.nerdorg.minehop.util;

import net.minecraft.world.phys.Vec3;

public record SurfContact(Vec3 normal, double surfaceY, boolean hardEndpoint) {
}
