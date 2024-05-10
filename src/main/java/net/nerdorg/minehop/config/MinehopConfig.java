package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "minehop")
public class MinehopConfig implements ConfigData {
    public boolean nulls = true;
    @ConfigEntry.Gui.CollapsibleObject
    public JHud jHud = new JHud();
    @ConfigEntry.Gui.CollapsibleObject
    public MovementSettings movement = new MovementSettings();
    @ConfigEntry.Gui.Excluded
    public boolean help_command = false;
    @ConfigEntry.Gui.Excluded
    public boolean minehop_motd = false;
    @ConfigEntry.Gui.Excluded
    public boolean client_validation = true;
    @ConfigEntry.Gui.Excluded
    public String bot_token = "";
    @ConfigEntry.Gui.Excluded
    public String record_channel = "";

    public static class MovementSettings {
        public double sv_friction = 0.35;
        public double sv_accelerate = 0.1;
        public double sv_airaccelerate = 1.0E99;
        public double sv_maxairspeed = 0.02325;
        public double speed_mul = 2.2;
        public double sv_gravity = 0.066;
    }

    public static class JHud {
        @ConfigEntry.Gui.CollapsibleObject
        public SSJHud ssjHud = new SSJHud();
        @ConfigEntry.Gui.CollapsibleObject
        public EfficiencyHud efficiencyHud = new EfficiencyHud();
        @ConfigEntry.Gui.CollapsibleObject
        public SpeedHud speedHud = new SpeedHud();
        @ConfigEntry.Gui.CollapsibleObject
        public PrespeedHud prespeedHud = new PrespeedHud();
        @ConfigEntry.Gui.CollapsibleObject
        public GaugeHud gaugeHud = new GaugeHud();
    }

    public static class SSJHud {
        public boolean show_ssj = true;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int ssj_x_offset = 50;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int ssj_y_offset = 52;
    }

    public static class EfficiencyHud {
        public boolean show_efficiency = true;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int efficiency_x_offset = 50;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int efficiency_y_offset = 55;
    }

    public static class SpeedHud {
        public boolean show_current_speed = true;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int speed_x_offset = 1;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int speed_y_offset = 98;
    }

    public static class PrespeedHud {
        public boolean show_prespeed = true;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int prespeed_x_offset = 1;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int prespeed_y_offset = 95;
    }

    public static class GaugeHud {
        public boolean show_gauge = true;
        public boolean horizontal_gauge = false;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int gauge_x_offset = 98;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int gauge_y_offset = 50;
    }
}
