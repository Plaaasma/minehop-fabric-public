package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "minehop")
public class MinehopConfig implements ConfigData {
    public boolean show_ssj = true;
    public boolean show_efficiency = true;
    public boolean show_current_speed = true;
    public boolean show_prespeed = true;
    public boolean show_gauge = true;
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int gauge_x_offset = 98;
    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    public int gauge_y_offset = 50;
    public boolean horizontal_gauge = false;
    public boolean nulls = true;
    public double sv_friction = 0.35;
    public double sv_accelerate = 0.1;
    public double sv_airaccelerate = 1.0E99;
    public double sv_maxairspeed = 0.02325;
    public double speed_mul = 2.2;
    public double sv_gravity = 0.066;
    public boolean help_command = true;
    public boolean minehop_motd = true;
    public boolean client_validation = true;
    public String bot_token = "";
    public String record_channel = "";
}
