package net.nerdorg.minehop.commands;

public class CommandRegister {
    public static void register() {
        ConfigCommands.register();
        GamemodeCommands.register();
        SpawnCommands.register();
        MapUtilCommands.register();
        ZoneManagementCommands.register();
        BoostCommands.register();
        VisiblityCommands.register();
        SpectateCommands.register();
        SocialsCommands.register();
        VanillaCommands.register();
    }
}
