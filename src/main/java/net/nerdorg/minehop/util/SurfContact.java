package net.nerdorg.minehop.util;

import net.minecraft.util.math.Vec3d;

public record SurfContact(Vec3d normal, double surfaceY, boolean hardEndpoint) {
}
