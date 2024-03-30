package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "minehop")
public class MinehopConfig implements ConfigData {
    public double sv_friction = 0.5;  // 0.5 default
    public double sv_accelerate = 0.1;  // 0.1 default
    public double sv_airaccelerate = 0.2;  // 0.2 default
    public double sv_maxairspeed = 0.1;  // 0.1 default
    public double speed_mul = 2.2;  // 2.2 default
    public double sv_gravity = 0.08;  // 0.08 default
}
