package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "minehop")
public class MinehopConfig implements ConfigData {
    public boolean enabled = true;
    public boolean fall_damage = false;
    public boolean hideSelf = false;
    public boolean hideOthers = false;
    public boolean hideReplay = false;
    public boolean nulls = true;
    public boolean censor_user_map_descriptions = true;
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
        public double sv_friction = 4.0;
        public double sv_accelerate = 5.0;
        public double sv_airaccelerate = 1000.0;
        public double sv_maxairspeed = 30.0;
        public double sv_jump_impulse = 290.0;
        public double speed_mul = 3.25;
        public double sv_gravity = 800.0;
        public double sv_stopspeed = 75.0;
        public double speed_coefficient = 0.9;
        public boolean auto_step_up = false;
        public boolean css_crouch_jump = true;
        public boolean disable_sprint = false;
        @ConfigEntry.Gui.Excluded
        public boolean source_units_migrated = true;
        @ConfigEntry.Gui.Excluded
        public int source_movement_version = 12;
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
        @ConfigEntry.Gui.CollapsibleObject
        public TimerHud timerHud = new TimerHud();
    }

    public static class SSJHud {
        public boolean show_ssj = true;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int ssj_x_offset = 50;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int ssj_y_offset = 52;
        @ConfigEntry.Gui.Tooltip
        public double ssj_scale = 1.0;
    }

    public static class EfficiencyHud {
        public boolean show_efficiency = true;
        public boolean vertical_efficiency = false;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int efficiency_x_offset = 50;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int efficiency_y_offset = 91;
        @ConfigEntry.Gui.Tooltip
        public double efficiency_scale = 1.0;
    }

    public static class SpeedHud {
        public boolean show_current_speed = true;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int speed_x_offset = 50;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int speed_y_offset = 81;
        @ConfigEntry.Gui.Tooltip
        public double speed_scale = 1.1;
    }

    public static class PrespeedHud {
        public boolean show_prespeed = false;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int prespeed_x_offset = 1;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int prespeed_y_offset = 95;
        @ConfigEntry.Gui.Tooltip
        public double prespeed_scale = 1.0;
    }

    public static class GaugeHud {
        public boolean show_gauge = true;
        public boolean horizontal_gauge = true;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int gauge_x_offset = 50;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int gauge_y_offset = 87;
        @ConfigEntry.Gui.Tooltip
        public double gauge_scale = 1.0;
    }

    public static class TimerHud {
        public boolean show_timer = true;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int timer_x_offset = 50;
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int timer_y_offset = 5;
        @ConfigEntry.Gui.Tooltip
        public double timer_scale = 1.0;
    }
}
