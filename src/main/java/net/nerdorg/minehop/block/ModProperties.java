package net.nerdorg.minehop.block;

import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;

public class ModProperties {
    public static final IntProperty RAMP_HEIGHT;

    static {
        RAMP_HEIGHT = IntProperty.of("height", 0, 8);
    }
}
