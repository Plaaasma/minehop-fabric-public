package net.nerdorg.minehop.commands;

import net.nerdorg.minehop.Minehop;

public class CommandRegister {
    public static void register() {
        //Functional commands
        ConfigCommands.register();
        GamemodeCommands.register();
        SpawnCommands.register();
        MapUtilCommands.register();
        ZoneManagementCommands.register();
        BoostCommands.register();
        VisiblityCommands.register();
        SpectateCommands.register();
        SocialsCommands.register();
        ReplayCommands.register();
        HelpCommands.register();
        SurfStickCommands.register();
        PlotCommands.register();
        AntiCheatCommands.register();

        //Dev commands
        TestCommands.register();
    }
}
