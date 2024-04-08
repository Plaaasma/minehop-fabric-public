package net.nerdorg.minehop.commands;

import net.minecraft.command.argument.ArgumentTypes;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
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

        //Dev commands
        TestCommands.register();
    }
}
