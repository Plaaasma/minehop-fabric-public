package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.data.LeaderboardIntegrity;
import net.nerdorg.minehop.replays.ReplayManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Admin commands for finding and removing illegitimate times (under {@code /map manage}). All
 * mutations go through {@link LeaderboardIntegrity} so leaderboards, replays and ranks stay consistent.
 */
public final class LeaderboardCommands {
    private static final int LIST_LIMIT = 25;

    private LeaderboardCommands() {
    }

    private static String actor(CommandSourceStack source) {
        return source.getTextName();
    }

    private static LeaderboardIntegrity.Target resolve(CommandSourceStack source, String input) {
        return resolve(source, input, false);
    }

    private static LeaderboardIntegrity.Target resolve(CommandSourceStack source, String input, boolean profileLookup) {
        LeaderboardIntegrity.Resolution resolution = LeaderboardIntegrity.resolve(source.getServer(), input, profileLookup);
        if (!resolution.ok()) {
            source.sendFailure(Component.literal(resolution.error()));
            return null;
        }
        return resolution.target();
    }

    /** UUID targets never touch old name-only rows; tell the admin when some exist under that name. */
    private static void noteLegacyRows(CommandSourceStack source, LeaderboardIntegrity.Target target) {
        if (target == null || !target.hasUuid() || target.name() == null || target.name().isBlank()) {
            return;
        }
        int legacy = LeaderboardIntegrity.countLegacyRows(target.name());
        if (legacy > 0) {
            source.sendSuccess(() -> Component.literal(legacy + " older row(s) saved before UUIDs were recorded also use the name '"
                    + target.name() + "' and were left alone (the name may have belonged to someone else). If they are this player's, repeat the command with "
                    + LeaderboardIntegrity.LEGACY_PREFIX + target.name()).withStyle(ChatFormatting.YELLOW), false);
        }
    }

    private static boolean mapKnown(String mapName) {
        if (DataManager.getMap(mapName) != null) {
            return true;
        }
        // Allow cleaning up times left behind by a removed map.
        if (Minehop.personalRecordList != null && Minehop.personalRecordList.stream().anyMatch(r -> r != null && mapName.equals(r.map_name))) {
            return true;
        }
        if (Minehop.recordList != null && Minehop.recordList.stream().anyMatch(r -> r != null && mapName.equals(r.map_name))) {
            return true;
        }
        return Minehop.replayList != null && Minehop.replayList.stream().anyMatch(r -> r != null && mapName.equals(r.map_name));
    }

    private static int reportResult(CommandSourceStack source, String what, LeaderboardIntegrity.Report report) {
        if (report == null) {
            return 0;
        }
        if (report.isEmpty()) {
            source.sendFailure(Component.literal(what + ": nothing to remove."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(what + ": removed " + report.summary())
                .withStyle(report.saveFailed ? ChatFormatting.RED : ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    public static int invalidatePlayer(CommandContext<CommandSourceStack> context, String input, String mapName,
                                       boolean times, boolean replays) {
        CommandSourceStack source = context.getSource();
        if (mapName != null && !mapKnown(mapName)) {
            source.sendFailure(Component.literal("The map " + mapName + " does not exist and has no saved data."));
            return 0;
        }
        LeaderboardIntegrity.Target target = resolve(source, input);
        if (target == null) {
            return 0;
        }
        LeaderboardIntegrity.Report report = LeaderboardIntegrity.purgePlayer(source.getServer(), target, mapName,
                times, replays, actor(source), "");
        int result = reportResult(source, "Invalidated " + target.describe() + (mapName == null ? " on all maps" : " on " + mapName), report);
        noteLegacyRows(source, target);
        return result;
    }

    public static int invalidateMap(CommandContext<CommandSourceStack> context, String mapName, boolean times, boolean replays) {
        CommandSourceStack source = context.getSource();
        if (!mapKnown(mapName)) {
            source.sendFailure(Component.literal("The map " + mapName + " does not exist and has no saved data."));
            return 0;
        }
        LeaderboardIntegrity.Report report = LeaderboardIntegrity.purgeMap(source.getServer(), mapName, times, replays, actor(source), "");
        return reportResult(source, "Invalidated map " + mapName, report);
    }

    public static int invalidateRun(CommandContext<CommandSourceStack> context, String replayId) {
        CommandSourceStack source = context.getSource();
        StringBuilder error = new StringBuilder();
        LeaderboardIntegrity.Report report = LeaderboardIntegrity.invalidateRun(source.getServer(), replayId, actor(source), "", error);
        if (report == null) {
            source.sendFailure(Component.literal(error.toString()));
            return 0;
        }
        return reportResult(source, "Invalidated run " + replayId, report);
    }

    public static int ban(CommandContext<CommandSourceStack> context, String input, String reason) {
        CommandSourceStack source = context.getSource();
        LeaderboardIntegrity.Target target = resolve(source, input, true);
        if (target == null) {
            return 0;
        }
        if (!target.hasUuid()) {
            source.sendFailure(Component.literal(target.describe() + " has no known UUID, so a ban can't be enforced; their times are removed anyway."));
        }
        LeaderboardIntegrity.Report report = LeaderboardIntegrity.ban(source.getServer(), target, actor(source), reason);
        source.sendSuccess(() -> Component.literal("Leaderboard-banned " + target.describe() + "; removed " + report.summary())
                .withStyle(ChatFormatting.GREEN), true);
        noteLegacyRows(source, target);
        return Command.SINGLE_SUCCESS;
    }

    public static int unban(CommandContext<CommandSourceStack> context, String input) {
        CommandSourceStack source = context.getSource();
        LeaderboardIntegrity.Target target = resolve(source, input);
        if (target == null) {
            return 0;
        }
        if (!LeaderboardIntegrity.unban(source.getServer(), target, actor(source))) {
            source.sendFailure(Component.literal(target.describe() + " is not leaderboard-banned."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Removed the leaderboard ban on " + target.describe()
                + ". Their removed times are not restored.").withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    public static int listBans(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (LeaderboardIntegrity.bans().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No players are leaderboard-banned."), false);
            return Command.SINGLE_SUCCESS;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        MutableComponent text = Component.literal("Leaderboard bans:").withStyle(ChatFormatting.GOLD);
        for (LeaderboardIntegrity.BanEntry ban : LeaderboardIntegrity.bans()) {
            text.append(Component.literal("\n " + ban.name + " (" + ban.uuid + ") — " + format.format(new Date(ban.time))
                    + " by " + ban.actor + (ban.reason == null || ban.reason.isBlank() ? "" : ": " + ban.reason))
                    .withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> text, false);
        return Command.SINGLE_SUCCESS;
    }

    /** Every saved run of a player (newest first), marking PB/WR and any anticheat flags. */
    public static int history(CommandContext<CommandSourceStack> context, String input, String mapName) {
        CommandSourceStack source = context.getSource();
        LeaderboardIntegrity.Target target = resolve(source, input);
        if (target == null) {
            return 0;
        }
        List<ReplayManager.Replay> runs = new ArrayList<>();
        if (Minehop.replayList != null) {
            for (ReplayManager.Replay replay : Minehop.replayList) {
                if (replay != null && LeaderboardIntegrity.belongs(replay, target)
                        && (mapName == null || mapName.equals(replay.map_name))) {
                    runs.add(replay);
                }
            }
        }
        runs.sort(Comparator.comparingLong((ReplayManager.Replay r) -> r.saved_at).reversed());
        MutableComponent text = Component.literal("Runs by " + target.describe() + (mapName == null ? "" : " on " + mapName)
                + " (" + runs.size() + "):").withStyle(ChatFormatting.GOLD);
        for (int i = 0; i < Math.min(LIST_LIMIT, runs.size()); i++) {
            text.append(runLine(runs.get(i), true));
        }
        if (runs.size() > LIST_LIMIT) {
            text.append(Component.literal("\n ... " + (runs.size() - LIST_LIMIT) + " older run(s) not shown").withStyle(ChatFormatting.DARK_GRAY));
        }
        long pbsWithoutReplay = Minehop.personalRecordList == null ? 0 : Minehop.personalRecordList.stream()
                .filter(r -> r != null && LeaderboardIntegrity.belongs(r, target) && (mapName == null || mapName.equals(r.map_name)))
                .filter(r -> runs.stream().noneMatch(run -> run.map_name.equals(r.map_name) && Math.abs(run.time - r.time) < 1.0E-6D))
                .count();
        if (pbsWithoutReplay > 0) {
            text.append(Component.literal("\n " + pbsWithoutReplay + " PB(s) have no saved run (older data); remove those with invalidate_player.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        source.sendSuccess(() -> text, false);
        noteLegacyRows(source, target);
        return Command.SINGLE_SUCCESS;
    }

    /** Recent runs that finished with anticheat flags (newest first). */
    public static int flagged(CommandContext<CommandSourceStack> context, String mapName) {
        CommandSourceStack source = context.getSource();
        List<ReplayManager.Replay> runs = new ArrayList<>();
        if (Minehop.replayList != null) {
            for (ReplayManager.Replay replay : Minehop.replayList) {
                if (replay != null && replay.ac_flags != null && !replay.ac_flags.isBlank()
                        && (mapName == null || mapName.equals(replay.map_name))) {
                    runs.add(replay);
                }
            }
        }
        runs.sort(Comparator.comparingLong((ReplayManager.Replay r) -> r.saved_at).reversed());
        MutableComponent text = Component.literal("Flagged runs" + (mapName == null ? "" : " on " + mapName) + " (" + runs.size() + "):")
                .withStyle(ChatFormatting.GOLD);
        for (int i = 0; i < Math.min(LIST_LIMIT, runs.size()); i++) {
            text.append(runLine(runs.get(i), false));
        }
        source.sendSuccess(() -> text, false);
        return Command.SINGLE_SUCCESS;
    }

    public static int reconcileRanks(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int checked = LeaderboardIntegrity.reconcileAllRanks(source.getServer(), actor(source));
        source.sendSuccess(() -> Component.literal("Re-checked record_holder for " + checked + " player(s).").withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static Component runLine(ReplayManager.Replay run, boolean ownHistory) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        LeaderboardIntegrity.Target runner = new LeaderboardIntegrity.Target(
                run.player_uuid == null ? "" : run.player_uuid, run.player_name == null ? "" : run.player_name);
        DataManager.RecordData wr = DataManager.getRecord(run.map_name);
        boolean isWr = wr != null && LeaderboardIntegrity.belongs(wr, runner) && Math.abs(wr.time - run.time) < 1.0E-6D;
        boolean isPb = Minehop.personalRecordList != null && Minehop.personalRecordList.stream().anyMatch(pb ->
                pb != null && run.map_name.equals(pb.map_name) && LeaderboardIntegrity.belongs(pb, runner)
                        && Math.abs(pb.time - run.time) < 1.0E-6D);
        String id = run.replay_id == null ? "?" : run.replay_id.substring(0, Math.min(8, run.replay_id.length()));
        MutableComponent line = Component.literal("\n " + format.format(new Date(run.saved_at)) + " ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(run.map_name + " ").withStyle(ChatFormatting.WHITE));
        if (!ownHistory) {
            line.append(Component.literal(run.player_name + " ").withStyle(ChatFormatting.YELLOW));
        }
        line.append(Component.literal(String.format(Locale.ROOT, "%.5fs ", run.time)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("[" + id + "]").withStyle(ChatFormatting.DARK_AQUA));
        if (isWr) {
            line.append(Component.literal(" WR").withStyle(ChatFormatting.GOLD));
        } else if (isPb) {
            line.append(Component.literal(" PB").withStyle(ChatFormatting.GREEN));
        }
        if (run.ac_flags != null && !run.ac_flags.isBlank()) {
            line.append(Component.literal(" ⚠ " + run.ac_flags).withStyle(ChatFormatting.RED));
        }
        if (run.invalidated != null && !run.invalidated.isBlank()) {
            line.append(Component.literal(" [" + run.invalidated + "]").withStyle(ChatFormatting.DARK_RED));
        }
        String unavailable = ReplayManager.unavailableReason(run);
        if (!unavailable.isEmpty()) {
            line.append(Component.literal(" [no replay: " + unavailable + "]").withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }
}
