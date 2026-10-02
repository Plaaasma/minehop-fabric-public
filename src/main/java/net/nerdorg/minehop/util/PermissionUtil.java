package net.nerdorg.minehop.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.player.Player;

/**
 * 1.21.11 replaced the integer {@code hasPermissionLevel(int)} checks on command sources and players
 * with permission predicates. This keeps the old "op level >= N" semantics (0 = everyone,
 * 1 = moderators, 2 = gamemasters, 3 = admins, 4 = owners) for the existing call sites.
 */
public final class PermissionUtil {
    private PermissionUtil() {
    }

    public static boolean hasLevel(PermissionSet permissions, int level) {
        if (level <= 0) {
            return true;
        }
        if (permissions == null) {
            return false;
        }
        return permissions.hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }

    public static boolean hasLevel(CommandSourceStack source, int level) {
        return source != null && hasLevel(source.permissions(), level);
    }

    public static boolean hasLevel(Player player, int level) {
        return player != null && hasLevel(player.permissions(), level);
    }
}
