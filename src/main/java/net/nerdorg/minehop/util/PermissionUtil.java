package net.nerdorg.minehop.util;

import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;

/**
 * 1.21.11 replaced the integer {@code hasPermissionLevel(int)} checks on command sources and players
 * with permission predicates. This keeps the old "op level >= N" semantics (0 = everyone,
 * 1 = moderators, 2 = gamemasters, 3 = admins, 4 = owners) for the existing call sites.
 */
public final class PermissionUtil {
    private PermissionUtil() {
    }

    public static boolean hasLevel(PermissionPredicate permissions, int level) {
        if (level <= 0) {
            return true;
        }
        if (permissions == null) {
            return false;
        }
        return permissions.hasPermission(new Permission.Level(PermissionLevel.fromLevel(level)));
    }

    public static boolean hasLevel(ServerCommandSource source, int level) {
        return source != null && hasLevel(source.getPermissions(), level);
    }

    public static boolean hasLevel(PlayerEntity player, int level) {
        return player != null && hasLevel(player.getPermissions(), level);
    }
}
