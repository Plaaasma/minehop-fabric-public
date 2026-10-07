package net.nerdorg.minehop.data;

import com.google.gson.reflect.TypeToken;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.commands.ReplayCommands;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.replays.ReplayEvents;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.JsonStorage;
import net.nerdorg.minehop.util.ZoneUtil;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The single path for removing times from the leaderboards, so every invalidation leaves PBs, world
 * records, replays, the in-world WR replay, the LuckPerms {@code record_holder} rank and connected
 * clients consistent — and leaves an audit trail.
 *
 * <p>Players are identified by UUID first. A typed name is only accepted when it maps to exactly one
 * UUID in the saved data (names get reused after renames), otherwise the admin is asked for the UUID.
 * A leaderboard ban stops a player's future runs from being recorded at all.
 */
public final class LeaderboardIntegrity {
    private static final String BANS_FILE = "MineHop_Data/minehop_leaderboard_bans.json";
    private static final String AUDIT_FILE = "MineHop_Data/minehop_invalidations.log";
    private static final int BANS_SCHEMA_VERSION = 1;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    /** Prefix that targets legacy name-only rows explicitly, e.g. {@code name:Bob}. */
    public static final String LEGACY_PREFIX = "name:";
    private static final double TIME_MATCH_EPSILON = 1.0E-6D;

    /** A leaderboard ban, keyed by UUID. */
    public static final class BanEntry {
        public String uuid = "";
        public String name = "";
        public String reason = "";
        public String actor = "";
        public long time;
    }

    private static final Map<String, BanEntry> BANS = new LinkedHashMap<>();

    /** A player identity: UUID (may be "" for legacy name-only data) plus the last known name. */
    public record Target(String uuid, String name) {
        public boolean hasUuid() {
            return this.uuid != null && !this.uuid.isBlank();
        }

        public String describe() {
            if (!hasUuid()) {
                return this.name + " (legacy name-only data)";
            }
            return (this.name == null || this.name.isBlank() ? "?" : this.name) + " (" + this.uuid + ")";
        }
    }

    public record Resolution(Target target, String error) {
        public boolean ok() {
            return this.target != null;
        }
    }

    /** What an operation removed; also the audit-log line. */
    public static final class Report {
        public final Set<String> maps = new TreeSet<>();
        public int personalRecords;
        public int worldRecords;
        public int replays;
        public final List<String> promoted = new ArrayList<>();
        public boolean saveFailed;

        public boolean isEmpty() {
            return this.personalRecords == 0 && this.worldRecords == 0 && this.replays == 0;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(this.personalRecords).append(" PB(s), ")
                    .append(this.worldRecords).append(" WR row(s), ")
                    .append(this.replays).append(" replay(s) on ")
                    .append(this.maps.size()).append(" map(s)");
            if (!this.maps.isEmpty() && this.maps.size() <= 8) {
                sb.append(" ").append(this.maps);
            }
            if (!this.promoted.isEmpty()) {
                sb.append("; new WRs: ").append(String.join(", ", this.promoted));
            }
            if (this.saveFailed) {
                sb.append(" — WARNING: a data file failed to save (see server log)");
            }
            return sb.toString();
        }
    }

    private LeaderboardIntegrity() {
    }

    public static void register() {
        Services.EVENTS.onServerStarted(LeaderboardIntegrity::loadBans);
    }

    // ------------------------------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------------------------------

    public static Resolution resolve(MinecraftServer server, String input) {
        return resolve(server, input, false);
    }

    /**
     * @param profileLookup also ask the server's profile cache for a player with no saved data (on an
     *                      online-mode server that may query Mojang; only worth it to pre-emptively ban).
     */
    public static Resolution resolve(MinecraftServer server, String input, boolean profileLookup) {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) {
            return new Resolution(null, "Player name or UUID cannot be blank.");
        }
        if (raw.regionMatches(true, 0, LEGACY_PREFIX, 0, LEGACY_PREFIX.length())) {
            // Explicit target for old rows saved before UUIDs were recorded (matched by name only).
            String name = raw.substring(LEGACY_PREFIX.length()).trim();
            if (name.isEmpty() || countLegacyRows(name) == 0) {
                return new Resolution(null, "No legacy (name-only) rows exist for '" + name + "'.");
            }
            return new Resolution(new Target("", name), null);
        }
        if (UUID_PATTERN.matcher(raw).matches()) {
            String uuid = raw.toLowerCase(Locale.ROOT);
            return new Resolution(new Target(uuid, latestNameForUuid(server, uuid)), null);
        }

        // Every UUID that has used this name in the saved data (plus the online player / ban list).
        Map<String, Integer> uuidHits = new LinkedHashMap<>();
        String legacyName = null;
        for (DataManager.RecordData record : allRecordRows()) {
            if (record.name != null && record.name.equalsIgnoreCase(raw)) {
                if (record.uuid == null || record.uuid.isBlank()) {
                    legacyName = record.name;
                } else {
                    uuidHits.merge(record.uuid.toLowerCase(Locale.ROOT), 1, Integer::sum);
                }
            }
        }
        if (Minehop.replayList != null) {
            for (ReplayManager.Replay replay : Minehop.replayList) {
                if (replay != null && replay.player_name != null && replay.player_name.equalsIgnoreCase(raw)) {
                    if (replay.player_uuid == null || replay.player_uuid.isBlank()) {
                        legacyName = replay.player_name;
                    } else {
                        uuidHits.merge(replay.player_uuid.toLowerCase(Locale.ROOT), 1, Integer::sum);
                    }
                }
            }
        }
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(raw);
            if (online != null) {
                uuidHits.merge(online.getStringUUID().toLowerCase(Locale.ROOT), 0, Integer::sum);
            }
        }
        for (BanEntry ban : BANS.values()) {
            if (ban.name != null && ban.name.equalsIgnoreCase(raw)) {
                uuidHits.merge(ban.uuid, 0, Integer::sum);
            }
        }

        if (uuidHits.size() > 1) {
            StringBuilder sb = new StringBuilder("The name '").append(raw)
                    .append("' has been used by more than one player — use the UUID instead:");
            for (Map.Entry<String, Integer> hit : uuidHits.entrySet()) {
                sb.append("\n  ").append(hit.getKey()).append(" (").append(hit.getValue()).append(" saved row(s))");
            }
            return new Resolution(null, sb.toString());
        }
        if (uuidHits.size() == 1) {
            String uuid = uuidHits.keySet().iterator().next();
            String name = latestNameForUuid(server, uuid);
            return new Resolution(new Target(uuid, name.isBlank() ? raw : name), null);
        }
        if (legacyName != null) {
            return new Resolution(new Target("", legacyName), null);
        }
        if (profileLookup && server != null && server.getProfileCache() != null) {
            Optional<com.mojang.authlib.GameProfile> profile = server.getProfileCache().get(raw);
            if (profile.isPresent() && profile.get().getId() != null) {
                return new Resolution(new Target(profile.get().getId().toString(), profile.get().getName()), null);
            }
        }
        return new Resolution(null, "No saved times, runs or bans exist for a player named '" + raw + "'.");
    }

    private static String latestNameForUuid(MinecraftServer server, String uuid) {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getStringUUID().equalsIgnoreCase(uuid)) {
                    return player.getScoreboardName();
                }
            }
        }
        long newest = Long.MIN_VALUE;
        String name = "";
        if (Minehop.replayList != null) {
            for (ReplayManager.Replay replay : Minehop.replayList) {
                if (replay != null && uuid.equalsIgnoreCase(replay.player_uuid) && replay.saved_at > newest
                        && replay.player_name != null) {
                    newest = replay.saved_at;
                    name = replay.player_name;
                }
            }
        }
        if (!name.isBlank()) {
            return name;
        }
        for (DataManager.RecordData record : allRecordRows()) {
            if (uuid.equalsIgnoreCase(record.uuid) && record.name != null) {
                return record.name;
            }
        }
        BanEntry ban = BANS.get(uuid);
        return ban == null ? "" : ban.name;
    }

    /**
     * A UUID target owns only rows carrying that UUID. Legacy rows (saved before UUIDs were recorded)
     * are matched only by an explicit name-only target: the same name may have belonged to someone else.
     */
    public static boolean belongs(DataManager.RecordData record, Target target) {
        if (record == null || target == null) {
            return false;
        }
        boolean rowHasUuid = record.uuid != null && !record.uuid.isBlank();
        if (target.hasUuid()) {
            return rowHasUuid && record.uuid.equalsIgnoreCase(target.uuid());
        }
        return !rowHasUuid && target.name() != null && !target.name().isBlank() && target.name().equalsIgnoreCase(record.name);
    }

    public static boolean belongs(ReplayManager.Replay replay, Target target) {
        if (replay == null || target == null) {
            return false;
        }
        boolean rowHasUuid = replay.player_uuid != null && !replay.player_uuid.isBlank();
        if (target.hasUuid()) {
            return rowHasUuid && replay.player_uuid.equalsIgnoreCase(target.uuid());
        }
        return !rowHasUuid && target.name() != null && !target.name().isBlank() && target.name().equalsIgnoreCase(replay.player_name);
    }

    /** Legacy (name-only) PB/WR rows and replays saved under this name. */
    public static int countLegacyRows(String name) {
        if (name == null || name.isBlank()) {
            return 0;
        }
        Target legacy = new Target("", name);
        int count = 0;
        for (DataManager.RecordData record : allRecordRows()) {
            if (belongs(record, legacy)) {
                count++;
            }
        }
        if (Minehop.replayList != null) {
            for (ReplayManager.Replay replay : Minehop.replayList) {
                if (belongs(replay, legacy)) {
                    count++;
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------------------------------------
    // Invalidation
    // ------------------------------------------------------------------------------------------

    /** Removes a player's PBs and WR rows and their replays — on one map, or everywhere if null. */
    public static Report purgePlayer(MinecraftServer server, Target target, String mapFilter, boolean includeReplays,
                                     String actor, String reason) {
        return purgePlayer(server, target, mapFilter, true, includeReplays, actor, reason);
    }

    public static Report purgePlayer(MinecraftServer server, Target target, String mapFilter, boolean includeTimes,
                                     boolean includeReplays, String actor, String reason) {
        Report report = new Report();
        Map<String, DataManager.RecordData> oldWorldRecords = new LinkedHashMap<>();
        Set<String> wrMapsLost = new LinkedHashSet<>();

        if (includeTimes) {
            for (DataManager.RecordData record : allRecordRows()) {
                if (belongs(record, target) && matchesMap(record.map_name, mapFilter)) {
                    report.maps.add(record.map_name);
                }
            }
        }
        if (includeReplays && Minehop.replayList != null) {
            for (ReplayManager.Replay replay : Minehop.replayList) {
                if (belongs(replay, target) && matchesMap(replay.map_name, mapFilter)) {
                    report.maps.add(replay.map_name);
                }
            }
        }
        for (String map : report.maps) {
            oldWorldRecords.put(map, DataManager.getRecord(map));
        }

        if (includeTimes && Minehop.personalRecordList != null) {
            Iterator<DataManager.RecordData> it = Minehop.personalRecordList.iterator();
            while (it.hasNext()) {
                DataManager.RecordData record = it.next();
                if (belongs(record, target) && matchesMap(record.map_name, mapFilter)) {
                    it.remove();
                    report.personalRecords++;
                }
            }
        }
        if (includeTimes && Minehop.recordList != null) {
            Iterator<DataManager.RecordData> it = Minehop.recordList.iterator();
            while (it.hasNext()) {
                DataManager.RecordData record = it.next();
                if (belongs(record, target) && matchesMap(record.map_name, mapFilter)) {
                    it.remove();
                    report.worldRecords++;
                    wrMapsLost.add(record.map_name);
                }
            }
        }
        if (includeReplays && Minehop.replayList != null) {
            Iterator<ReplayManager.Replay> it = Minehop.replayList.iterator();
            while (it.hasNext()) {
                ReplayManager.Replay replay = it.next();
                if (belongs(replay, target) && matchesMap(replay.map_name, mapFilter)) {
                    it.remove();
                    report.replays++;
                }
            }
        }

        // Only maps whose WR belonged to the target need a new WR: the fastest remaining PB.
        for (String map : wrMapsLost) {
            promoteNextWorldRecord(map, report);
        }

        if (includeTimes) {
            clearLiveRunState(server, target);
        }
        finish(server, report, oldWorldRecords, includeReplays,
                "purge " + (includeTimes ? "times" : "") + (includeTimes && includeReplays ? "+" : "")
                        + (includeReplays ? "replays" : "") + " of " + target.describe()
                        + (mapFilter == null ? " (all maps)" : " on " + mapFilter), actor, reason);
        return report;
    }

    /** Removes every time (and optionally replay) on a map. */
    public static Report purgeMap(MinecraftServer server, String mapName, boolean times, boolean replays,
                                  String actor, String reason) {
        Report report = new Report();
        report.maps.add(mapName);
        Map<String, DataManager.RecordData> oldWorldRecords = new LinkedHashMap<>();
        oldWorldRecords.put(mapName, DataManager.getRecord(mapName));
        if (times) {
            if (Minehop.personalRecordList != null) {
                int before = Minehop.personalRecordList.size();
                Minehop.personalRecordList.removeIf(r -> r != null && mapName.equals(r.map_name));
                report.personalRecords = before - Minehop.personalRecordList.size();
            }
            if (Minehop.recordList != null) {
                int before = Minehop.recordList.size();
                Minehop.recordList.removeIf(r -> r != null && mapName.equals(r.map_name));
                report.worldRecords = before - Minehop.recordList.size();
            }
        }
        if (replays) {
            report.replays = ReplayManager.deleteReplaysForMap(mapName);
        }
        finish(server, report, oldWorldRecords, replays,
                "purge map " + mapName + (times ? " times" : "") + (replays ? " replays" : ""), actor, reason);
        return report;
    }

    /**
     * Invalidates one run (by replay id, or a unique prefix of at least 8 characters). If that run was
     * the player's PB, their best remaining run on the map becomes the PB; if it was the WR, the next
     * fastest PB is promoted.
     */
    public static Report invalidateRun(MinecraftServer server, String replayIdOrPrefix, String actor, String reason,
                                       StringBuilder error) {
        String key = replayIdOrPrefix == null ? "" : replayIdOrPrefix.trim().toLowerCase(Locale.ROOT);
        if (key.length() < 8) {
            error.append("Give at least the first 8 characters of the replay id (see /map manage history).");
            return null;
        }
        List<ReplayManager.Replay> matches = new ArrayList<>();
        if (Minehop.replayList != null) {
            for (ReplayManager.Replay replay : Minehop.replayList) {
                if (replay != null && replay.replay_id != null && replay.replay_id.toLowerCase(Locale.ROOT).startsWith(key)) {
                    matches.add(replay);
                }
            }
        }
        if (matches.isEmpty()) {
            error.append("No saved run has an id starting with '").append(key).append("'.");
            return null;
        }
        if (matches.size() > 1) {
            error.append("That id prefix matches ").append(matches.size()).append(" runs; give more characters.");
            return null;
        }
        ReplayManager.Replay run = matches.get(0);
        Target target = new Target(run.player_uuid == null ? "" : run.player_uuid.toLowerCase(Locale.ROOT),
                run.player_name == null ? "" : run.player_name);
        String map = run.map_name;
        Report report = new Report();
        report.maps.add(map);
        Map<String, DataManager.RecordData> oldWorldRecords = new LinkedHashMap<>();
        DataManager.RecordData oldWr = DataManager.getRecord(map);
        oldWorldRecords.put(map, oldWr);

        Minehop.replayList.remove(run);
        report.replays = 1;

        DataManager.RecordData pb = findRow(Minehop.personalRecordList, map, target);
        if (pb != null && timesMatch(pb.time, run.time)) {
            Minehop.personalRecordList.removeIf(r -> r != null && map.equals(r.map_name) && belongs(r, target));
            report.personalRecords = 1;
            ReplayManager.Replay nextBest = bestRemainingRun(map, target);
            if (nextBest != null) {
                DataManager.upsertPersonalRecord(nextBest.player_name,
                        nextBest.player_uuid == null || nextBest.player_uuid.isBlank() ? target.uuid() : nextBest.player_uuid,
                        map, nextBest.time, nextBest.ac_flags);
            }
        }
        if (oldWr != null && belongs(oldWr, target) && timesMatch(oldWr.time, run.time)) {
            Minehop.recordList.removeIf(r -> r != null && map.equals(r.map_name));
            report.worldRecords = 1;
            promoteNextWorldRecord(map, report);
        }

        finish(server, report, oldWorldRecords, true,
                "invalidate run " + run.replay_id + " by " + target.describe() + " on " + map
                        + String.format(Locale.ROOT, " (%.5fs)", run.time), actor, reason);
        return report;
    }

    private static void promoteNextWorldRecord(String map, Report report) {
        if (DataManager.getMap(map) == null) {
            return; // map was removed; leave it without a WR
        }
        DataManager.RecordData best = null;
        if (Minehop.personalRecordList != null) {
            for (DataManager.RecordData pb : Minehop.personalRecordList) {
                if (pb != null && map.equals(pb.map_name) && (best == null || pb.time < best.time)) {
                    best = pb;
                }
            }
        }
        if (Minehop.recordList != null) {
            Minehop.recordList.removeIf(r -> r != null && map.equals(r.map_name));
        }
        if (best != null) {
            DataManager.upsertRecord(best.name, best.uuid, map, best.time, best.ac_flags);
            report.promoted.add(map + "=" + best.name + String.format(Locale.ROOT, " %.3fs", best.time));
        }
    }

    private static ReplayManager.Replay bestRemainingRun(String map, Target target) {
        ReplayManager.Replay best = null;
        if (Minehop.replayList != null) {
            for (ReplayManager.Replay replay : Minehop.replayList) {
                if (replay != null && map.equals(replay.map_name) && belongs(replay, target)
                        && Double.isFinite(replay.time) && replay.time > 0.0D
                        && (best == null || replay.time < best.time)) {
                    best = replay;
                }
            }
        }
        return best;
    }

    private static void finish(MinecraftServer server, Report report, Map<String, DataManager.RecordData> oldWorldRecords,
                               boolean replaysTouched, String action, String actor, String reason) {
        ServerLevel world = server == null ? null : server.overworld();
        if (world != null) {
            report.saveFailed |= !DataManager.saveDataChecked(world, DataManager.pbListLocation, Minehop.personalRecordList);
            report.saveFailed |= !DataManager.saveDataChecked(world, DataManager.recordsListLocation, Minehop.recordList);
            if (replaysTouched) {
                // Background write (the store is large); failures are logged and retried by ReplayManager.
                ReplayManager.saveRecordReplaysAsync(world, Minehop.replayList);
            }
        }
        // Rank: everyone who held or now holds a WR on an affected map.
        List<Target> people = new ArrayList<>();
        for (Map.Entry<String, DataManager.RecordData> entry : oldWorldRecords.entrySet()) {
            DataManager.RecordData before = entry.getValue();
            DataManager.RecordData after = DataManager.getRecord(entry.getKey());
            if (before != null) {
                people.add(new Target(before.uuid == null ? "" : before.uuid, before.name));
            }
            if (after != null) {
                people.add(new Target(after.uuid == null ? "" : after.uuid, after.name));
            }
        }
        reconcileRanks(server, people);
        for (String map : report.maps) {
            refreshWorldRecordReplay(server, map);
        }
        syncClients(server);
        audit(server, actor, action, reason, report.summary());
    }

    // ------------------------------------------------------------------------------------------
    // record_holder rank
    // ------------------------------------------------------------------------------------------

    /** True if the player holds a WR on a real (existing, non-plot) map. */
    public static boolean holdsCompetitiveRecord(Target person) {
        if (person == null || Minehop.recordList == null) {
            return false;
        }
        for (DataManager.RecordData record : Minehop.recordList) {
            if (record == null || !belongs(record, person)) {
                continue;
            }
            DataManager.MapData map = DataManager.getMap(record.map_name);
            if (map != null && !map.userMap) {
                return true;
            }
        }
        return false;
    }

    /** Grants or revokes {@code record_holder} so it matches the current records for each person. */
    public static void reconcileRanks(MinecraftServer server, Collection<Target> people) {
        if (server == null || people == null) {
            return;
        }
        Set<String> done = new LinkedHashSet<>();
        for (Target person : people) {
            // By UUID only: LuckPerms resolves a name to whoever owns it NOW, which for an old
            // name-only record may be a different player.
            if (person == null || !person.hasUuid() || !UUID_PATTERN.matcher(person.uuid()).matches()) {
                continue;
            }
            String subject = person.uuid();
            if (!done.add(subject.toLowerCase(Locale.ROOT))) {
                continue;
            }
            boolean holds = holdsCompetitiveRecord(person);
            runConsoleCommand(server, "lp user " + subject + " parent " + (holds ? "add" : "remove") + " record_holder");
        }
    }

    /** Recomputes the rank for everyone who has any saved time. Returns how many players were checked. */
    public static int reconcileAllRanks(MinecraftServer server, String actor) {
        Map<String, Target> people = new LinkedHashMap<>();
        for (DataManager.RecordData record : allRecordRows()) {
            String key = record.uuid != null && !record.uuid.isBlank()
                    ? record.uuid.toLowerCase(Locale.ROOT)
                    : "name:" + (record.name == null ? "" : record.name.toLowerCase(Locale.ROOT));
            people.putIfAbsent(key, new Target(record.uuid == null ? "" : record.uuid, record.name));
        }
        reconcileRanks(server, people.values());
        audit(server, actor, "reconcile record_holder ranks", "", people.size() + " player(s) checked");
        return people.size();
    }

    // ------------------------------------------------------------------------------------------
    // Leaderboard bans
    // ------------------------------------------------------------------------------------------

    public static boolean isBanned(ServerPlayer player) {
        return player != null && BANS.containsKey(player.getStringUUID().toLowerCase(Locale.ROOT));
    }

    public static boolean isBanned(Target target) {
        return target != null && target.hasUuid() && BANS.containsKey(target.uuid().toLowerCase(Locale.ROOT));
    }

    public static Collection<BanEntry> bans() {
        return BANS.values();
    }

    /** Bans a player from the leaderboards and removes all their times and replays. */
    public static Report ban(MinecraftServer server, Target target, String actor, String reason) {
        if (target.hasUuid()) {
            BanEntry entry = new BanEntry();
            entry.uuid = target.uuid().toLowerCase(Locale.ROOT);
            entry.name = target.name() == null ? "" : target.name();
            entry.reason = reason == null ? "" : reason;
            entry.actor = actor == null ? "" : actor;
            entry.time = System.currentTimeMillis();
            BANS.put(entry.uuid, entry);
            saveBans(server);
        }
        audit(server, actor, "leaderboard ban " + target.describe(), reason, target.hasUuid() ? "banned" : "no UUID: times purged only");
        return purgePlayer(server, target, null, true, actor, reason);
    }

    public static boolean unban(MinecraftServer server, Target target, String actor) {
        if (!target.hasUuid()) {
            return false;
        }
        BanEntry removed = BANS.remove(target.uuid().toLowerCase(Locale.ROOT));
        if (removed != null) {
            saveBans(server);
            audit(server, actor, "leaderboard unban " + target.describe(), "", "");
        }
        return removed != null;
    }

    private static void loadBans(MinecraftServer server) {
        BANS.clear();
        Type type = new TypeToken<List<BanEntry>>() {}.getType();
        List<BanEntry> loaded = JsonStorage.readData(server.getWorldPath(LevelResource.ROOT).resolve(BANS_FILE), type);
        if (loaded != null) {
            for (BanEntry entry : loaded) {
                if (entry != null && entry.uuid != null && UUID_PATTERN.matcher(entry.uuid).matches()) {
                    BANS.put(entry.uuid.toLowerCase(Locale.ROOT), entry);
                }
            }
        }
    }

    private static void saveBans(MinecraftServer server) {
        if (server == null) {
            return;
        }
        JsonStorage.writeAtomic(server.getWorldPath(LevelResource.ROOT).resolve(BANS_FILE), BANS_SCHEMA_VERSION,
                new ArrayList<>(BANS.values()));
    }

    // ------------------------------------------------------------------------------------------
    // Side effects
    // ------------------------------------------------------------------------------------------

    /** Points the in-world WR replay at the current WR, or removes it (ejecting spectators) if none. */
    public static void refreshWorldRecordReplay(MinecraftServer server, String map) {
        if (server == null || map == null) {
            return;
        }
        if (ReplayManager.getReplay(map) != null && DataManager.getMap(map) != null) {
            ReplayCommands.ensureWorldRecordReplayEntity(server, map);
            return;
        }
        List<String> ejected = ReplayCommands.removeWorldRecordReplayEntities(server, map);
        for (String spectatorName : ejected) {
            ejectSpectator(server, server.getPlayerList().getPlayerByName(spectatorName));
        }
    }

    private static void ejectSpectator(MinecraftServer server, ServerPlayer spectator) {
        if (spectator == null) {
            return;
        }
        spectator.setCamera(spectator);
        PacketHandler.clearReplayPath(spectator);
        if (spectator.isSpectator()) {
            DataManager.MapData spawn = DataManager.getMap("spawn");
            ServerLevel world = server.overworld();
            if (spawn != null && spawn.worldKey != null) {
                for (ServerLevel candidate : server.getAllLevels()) {
                    if (candidate.dimension().toString().equals(spawn.worldKey)) {
                        world = candidate;
                        break;
                    }
                }
            }
            if (spawn != null && world != null) {
                ZoneUtil.teleportTo(spectator, ZoneUtil.makeTeleportTarget(world, new Vec3(spawn.x, spawn.y, spawn.z),
                        (float) spawn.yrot, (float) spawn.xrot));
            }
            spectator.setGameMode(GameType.ADVENTURE);
        }
        SpectateCommands.spectatorList.values().forEach(list -> list.remove(spectator.getScoreboardName()));
    }

    private static void clearLiveRunState(MinecraftServer server, Target target) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean same = target.hasUuid()
                    ? player.getStringUUID().equalsIgnoreCase(target.uuid())
                    : player.getScoreboardName().equalsIgnoreCase(target.name());
            if (same) {
                String name = player.getScoreboardName();
                Minehop.timerManager.remove(name);
                Minehop.finishTimeManager.remove(name);
                Minehop.runSignatureManager.remove(name);
                ReplayEvents.replayEntryMap.remove(name);
            }
        }
    }

    private static void syncClients(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketHandler.sendRecords(player);
            PacketHandler.sendPersonalRecords(player);
        }
    }

    private static void runConsoleCommand(MinecraftServer server, String command) {
        server.getCommands().performCommand(
                server.getCommands().getDispatcher().parse(command, server.createCommandSourceStack()), command);
    }

    /** Appends one line to the invalidation audit log and mirrors it to the server console. */
    public static void audit(MinecraftServer server, String actor, String action, String reason, String result) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        String line = stamp + " | " + (actor == null || actor.isBlank() ? "console" : actor) + " | " + action
                + (reason == null || reason.isBlank() ? "" : " | reason: " + reason)
                + (result == null || result.isBlank() ? "" : " | " + result);
        Minehop.LOGGER.info("[LEADERBOARD] {}", line);
        if (server == null) {
            return;
        }
        Path file = server.getWorldPath(LevelResource.ROOT).resolve(AUDIT_FILE);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Minehop.LOGGER.warn("Failed to append to {}", AUDIT_FILE, e);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private static List<DataManager.RecordData> allRecordRows() {
        List<DataManager.RecordData> rows = new ArrayList<>();
        if (Minehop.personalRecordList != null) {
            for (DataManager.RecordData r : Minehop.personalRecordList) {
                if (r != null) {
                    rows.add(r);
                }
            }
        }
        if (Minehop.recordList != null) {
            for (DataManager.RecordData r : Minehop.recordList) {
                if (r != null) {
                    rows.add(r);
                }
            }
        }
        return rows;
    }

    private static DataManager.RecordData findRow(List<DataManager.RecordData> rows, String map, Target target) {
        if (rows == null) {
            return null;
        }
        DataManager.RecordData best = null;
        for (DataManager.RecordData row : rows) {
            if (row != null && map.equals(row.map_name) && belongs(row, target) && (best == null || row.time < best.time)) {
                best = row;
            }
        }
        return best;
    }

    private static boolean matchesMap(String map, String filter) {
        return map != null && (filter == null || filter.equals(map));
    }

    static boolean timesMatch(double a, double b) {
        return Math.abs(a - b) < TIME_MATCH_EPSILON;
    }
}
