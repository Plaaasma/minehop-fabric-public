package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

    private static String actor(ServerCommandSource source) {
        return source.getName();
    }

    private static LeaderboardIntegrity.Target resolve(ServerCommandSource source, String input) {
        return resolve(source, input, false);
    }

    private static LeaderboardIntegrity.Target resolve(ServerCommandSource source, String input, boolean profileLookup) {
        LeaderboardIntegrity.Resolution resolution = LeaderboardIntegrity.resolve(source.getServer(), input, profileLookup);
        if (!resolution.ok()) {
            source.sendError(Text.literal(resolution.error()));
            return null;
        }
        return resolution.target();
    }

    /** UUID targets never touch old name-only rows; tell the admin when some exist under that name. */
    private static void noteLegacyRows(ServerCommandSource source, LeaderboardIntegrity.Target target) {
        if (target == null || !target.hasUuid() || target.name() == null || target.name().isBlank()) {
            return;
        }
        int legacy = LeaderboardIntegrity.countLegacyRows(target.name());
        if (legacy > 0) {
            source.sendFeedback(() -> Text.literal(legacy + " older row(s) saved before UUIDs were recorded also use the name '"
                    + target.name() + "' and were left alone (the name may have belonged to someone else). If they are this player's, repeat the command with "
                    + LeaderboardIntegrity.LEGACY_PREFIX + target.name()).formatted(Formatting.YELLOW), false);
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

    private static int reportResult(ServerCommandSource source, String what, LeaderboardIntegrity.Report report) {
        if (report == null) {
            return 0;
        }
        if (report.isEmpty()) {
            source.sendError(Text.literal(what + ": nothing to remove."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal(what + ": removed " + report.summary())
                .formatted(report.saveFailed ? Formatting.RED : Formatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    public static int invalidatePlayer(CommandContext<ServerCommandSource> context, String input, String mapName,
                                       boolean times, boolean replays) {
        ServerCommandSource source = context.getSource();
        if (mapName != null && !mapKnown(mapName)) {
            source.sendError(Text.literal("The map " + mapName + " does not exist and has no saved data."));
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

    public static int invalidateMap(CommandContext<ServerCommandSource> context, String mapName, boolean times, boolean replays) {
        ServerCommandSource source = context.getSource();
        if (!mapKnown(mapName)) {
            source.sendError(Text.literal("The map " + mapName + " does not exist and has no saved data."));
            return 0;
        }
        LeaderboardIntegrity.Report report = LeaderboardIntegrity.purgeMap(source.getServer(), mapName, times, replays, actor(source), "");
        return reportResult(source, "Invalidated map " + mapName, report);
    }

    public static int invalidateRun(CommandContext<ServerCommandSource> context, String replayId) {
        ServerCommandSource source = context.getSource();
        StringBuilder error = new StringBuilder();
        LeaderboardIntegrity.Report report = LeaderboardIntegrity.invalidateRun(source.getServer(), replayId, actor(source), "", error);
        if (report == null) {
            source.sendError(Text.literal(error.toString()));
            return 0;
        }
        return reportResult(source, "Invalidated run " + replayId, report);
    }

    public static int ban(CommandContext<ServerCommandSource> context, String input, String reason) {
        ServerCommandSource source = context.getSource();
        LeaderboardIntegrity.Target target = resolve(source, input, true);
        if (target == null) {
            return 0;
        }
        if (!target.hasUuid()) {
            source.sendError(Text.literal(target.describe() + " has no known UUID, so a ban can't be enforced; their times are removed anyway."));
        }
        LeaderboardIntegrity.Report report = LeaderboardIntegrity.ban(source.getServer(), target, actor(source), reason);
        source.sendFeedback(() -> Text.literal("Leaderboard-banned " + target.describe() + "; removed " + report.summary())
                .formatted(Formatting.GREEN), true);
        noteLegacyRows(source, target);
        return Command.SINGLE_SUCCESS;
    }

    public static int unban(CommandContext<ServerCommandSource> context, String input) {
        ServerCommandSource source = context.getSource();
        LeaderboardIntegrity.Target target = resolve(source, input);
        if (target == null) {
            return 0;
        }
        if (!LeaderboardIntegrity.unban(source.getServer(), target, actor(source))) {
            source.sendError(Text.literal(target.describe() + " is not leaderboard-banned."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Removed the leaderboard ban on " + target.describe()
                + ". Their removed times are not restored.").formatted(Formatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    public static int listBans(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (LeaderboardIntegrity.bans().isEmpty()) {
            source.sendFeedback(() -> Text.literal("No players are leaderboard-banned."), false);
            return Command.SINGLE_SUCCESS;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        MutableText text = Text.literal("Leaderboard bans:").formatted(Formatting.GOLD);
        for (LeaderboardIntegrity.BanEntry ban : LeaderboardIntegrity.bans()) {
            text.append(Text.literal("\n " + ban.name + " (" + ban.uuid + ") — " + format.format(new Date(ban.time))
                    + " by " + ban.actor + (ban.reason == null || ban.reason.isBlank() ? "" : ": " + ban.reason))
                    .formatted(Formatting.GRAY));
        }
        source.sendFeedback(() -> text, false);
        return Command.SINGLE_SUCCESS;
    }

    /** Every saved run of a player (newest first), marking PB/WR and any anticheat flags. */
    public static int history(CommandContext<ServerCommandSource> context, String input, String mapName) {
        ServerCommandSource source = context.getSource();
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
        MutableText text = Text.literal("Runs by " + target.describe() + (mapName == null ? "" : " on " + mapName)
                + " (" + runs.size() + "):").formatted(Formatting.GOLD);
        for (int i = 0; i < Math.min(LIST_LIMIT, runs.size()); i++) {
            text.append(runLine(runs.get(i), true));
        }
        if (runs.size() > LIST_LIMIT) {
            text.append(Text.literal("\n ... " + (runs.size() - LIST_LIMIT) + " older run(s) not shown").formatted(Formatting.DARK_GRAY));
        }
        long pbsWithoutReplay = Minehop.personalRecordList == null ? 0 : Minehop.personalRecordList.stream()
                .filter(r -> r != null && LeaderboardIntegrity.belongs(r, target) && (mapName == null || mapName.equals(r.map_name)))
                .filter(r -> runs.stream().noneMatch(run -> run.map_name.equals(r.map_name) && Math.abs(run.time - r.time) < 1.0E-6D))
                .count();
        if (pbsWithoutReplay > 0) {
            text.append(Text.literal("\n " + pbsWithoutReplay + " PB(s) have no saved run (older data); remove those with invalidate_player.")
                    .formatted(Formatting.DARK_GRAY));
        }
        source.sendFeedback(() -> text, false);
        noteLegacyRows(source, target);
        return Command.SINGLE_SUCCESS;
    }

    /** Recent runs that finished with anticheat flags (newest first). */
    public static int flagged(CommandContext<ServerCommandSource> context, String mapName) {
        ServerCommandSource source = context.getSource();
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
        MutableText text = Text.literal("Flagged runs" + (mapName == null ? "" : " on " + mapName) + " (" + runs.size() + "):")
                .formatted(Formatting.GOLD);
        for (int i = 0; i < Math.min(LIST_LIMIT, runs.size()); i++) {
            text.append(runLine(runs.get(i), false));
        }
        source.sendFeedback(() -> text, false);
        return Command.SINGLE_SUCCESS;
    }

    public static int reconcileRanks(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int checked = LeaderboardIntegrity.reconcileAllRanks(source.getServer(), actor(source));
        source.sendFeedback(() -> Text.literal("Re-checked record_holder for " + checked + " player(s).").formatted(Formatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static Text runLine(ReplayManager.Replay run, boolean ownHistory) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        LeaderboardIntegrity.Target runner = new LeaderboardIntegrity.Target(
                run.player_uuid == null ? "" : run.player_uuid, run.player_name == null ? "" : run.player_name);
        DataManager.RecordData wr = DataManager.getRecord(run.map_name);
        boolean isWr = wr != null && LeaderboardIntegrity.belongs(wr, runner) && Math.abs(wr.time - run.time) < 1.0E-6D;
        boolean isPb = Minehop.personalRecordList != null && Minehop.personalRecordList.stream().anyMatch(pb ->
                pb != null && run.map_name.equals(pb.map_name) && LeaderboardIntegrity.belongs(pb, runner)
                        && Math.abs(pb.time - run.time) < 1.0E-6D);
        String id = run.replay_id == null ? "?" : run.replay_id.substring(0, Math.min(8, run.replay_id.length()));
        MutableText line = Text.literal("\n " + format.format(new Date(run.saved_at)) + " ").formatted(Formatting.GRAY)
                .append(Text.literal(run.map_name + " ").formatted(Formatting.WHITE));
        if (!ownHistory) {
            line.append(Text.literal(run.player_name + " ").formatted(Formatting.YELLOW));
        }
        line.append(Text.literal(String.format(Locale.ROOT, "%.5fs ", run.time)).formatted(Formatting.AQUA))
                .append(Text.literal("[" + id + "]").formatted(Formatting.DARK_AQUA));
        if (isWr) {
            line.append(Text.literal(" WR").formatted(Formatting.GOLD));
        } else if (isPb) {
            line.append(Text.literal(" PB").formatted(Formatting.GREEN));
        }
        if (run.ac_flags != null && !run.ac_flags.isBlank()) {
            line.append(Text.literal(" ⚠ " + run.ac_flags).formatted(Formatting.RED));
        }
        return line;
    }
}
