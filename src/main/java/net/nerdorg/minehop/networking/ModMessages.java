package net.nerdorg.minehop.networking;

import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public class ModMessages {
    public static final Identifier CONFIG_SYNC_ID = new Identifier(Minehop.MOD_ID, "config");
    public static final Identifier ZONE_SYNC_ID = new Identifier(Minehop.MOD_ID, "zone");
    public static final Identifier SELF_V_TOGGLE = new Identifier(Minehop.MOD_ID, "self_v_toggle");
    public static final Identifier OTHER_V_TOGGLE = new Identifier(Minehop.MOD_ID, "other_v_toggle");
    public static final Identifier MAP_FINISH = new Identifier(Minehop.MOD_ID, "map_finish");
    public static final Identifier SEND_TIME = new Identifier(Minehop.MOD_ID, "send_time");
    public static final Identifier SEND_EFFICIENCY = new Identifier(Minehop.MOD_ID, "send_efficiency");
    public static final Identifier DO_SPECTATE = new Identifier(Minehop.MOD_ID, "do_spectate");
    public static final Identifier SEND_SPECTATORS = new Identifier(Minehop.MOD_ID, "send_spectators");
    public static final Identifier CLIENT_SPEC_EFFICIENCY = new Identifier(Minehop.MOD_ID, "client_spec_efficiency");
    public static final Identifier SERVER_SPEC_EFFICIENCY = new Identifier(Minehop.MOD_ID, "server_spec_efficiency");
    public static final Identifier ANTI_CHEAT_CHECK = new Identifier(Minehop.MOD_ID, "anti_cheat_check");
}
