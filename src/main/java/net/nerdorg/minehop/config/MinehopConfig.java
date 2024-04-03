package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "minehop")
public class MinehopConfig implements ConfigData {
    public double sv_friction = 0.35;  // 0.5 default
    public double sv_accelerate = 0.1;  // 0.1 default
    public double sv_airaccelerate = 1.0E99;  // 0.2 default
    public double sv_maxairspeed = 0.04015;  // 0.1 default
    public double speed_mul = 2.2;  // 2.2 default
    public double sv_gravity = 0.066;  // 0.08 default
    public double sv_yaw = 8.9; // 10.25f default
}
