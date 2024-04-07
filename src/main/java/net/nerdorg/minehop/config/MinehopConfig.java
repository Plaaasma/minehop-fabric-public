package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "minehop")
public class MinehopConfig implements ConfigData {
    public boolean show_ssj = true;
    public boolean show_efficiency = true;
    public boolean show_current_speed = true;
    public double sv_friction = 0.35;
    public double sv_accelerate = 0.1;
    public double sv_airaccelerate = 1.0E99;
    public double sv_maxairspeed = 0.04015;
    public double speed_mul = 2.2;
    public double sv_gravity = 0.066;
    public double sv_yaw = 90;
    public String bot_token = "";
    public String record_channel = "";
}
