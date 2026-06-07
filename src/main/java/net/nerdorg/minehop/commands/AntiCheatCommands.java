package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.AntiCheatFlag;
import net.nerdorg.minehop.anticheat.AntiCheatManager;
import net.nerdorg.minehop.anticheat.AntiCheatPlayerState;
import net.nerdorg.minehop.networking.payloads.OpenAntiCheatScreenPayload;
import net.nerdorg.minehop.util.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AntiCheatCommands {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private AntiCheatCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("minehop")
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("anticheat")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(AntiCheatCommands::handleHelp)
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("verbose")
                                        .executes(AntiCheatCommands::handleVerbose)
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("enable")
                                        .requires(source -> source.hasPermissionLevel(4))
                                        .executes(context -> handleSetEnabled(context, true))
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("disable")
                                        .requires(source -> source.hasPermissionLevel(4))
                                        .executes(context -> handleSetEnabled(context, false))
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("list")
                                        .executes(AntiCheatCommands::handleList)
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("gui")
                                        .executes(AntiCheatCommands::handleGui)
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("info")
                                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("player", StringArgumentType.string())
                                                .suggests((ctx, builder) -> suggestKnownPlayers(ctx, builder))
                                                .executes(AntiCheatCommands::handleInfo)
                                        )
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("clear")
                                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("player", StringArgumentType.string())
                                                .suggests((ctx, builder) -> suggestKnownPlayers(ctx, builder))
                                                .executes(AntiCheatCommands::handleClear)
                                        )
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("exempt")
                                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("add")
                                                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("player", StringArgumentType.string())
                                                        .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                                                        .executes(context -> handleExempt(context, true))
                                                )
                                        )
                                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("remove")
                                                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("player", StringArgumentType.string())
                                                        .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                                                        .executes(context -> handleExempt(context, false))
                                                )
                                        )
                                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("list")
                                                .executes(AntiCheatCommands::handleExemptList)
                                        )
                                )
                        )
        ));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestKnownPlayers(
            CommandContext<ServerCommandSource> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server != null) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                builder.suggest(player.getNameForScoreboard(), new LiteralMessage("online"));
            }
        }
        for (AntiCheatPlayerState state : AntiCheatManager.allStates().values()) {
            if (state == null || state.lastKnownName().isBlank()) {
                continue;
            }
            builder.suggest(state.lastKnownName(), new LiteralMessage("known"));
        }
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOnlinePlayers(
            CommandContext<ServerCommandSource> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server != null) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                builder.suggest(player.getNameForScoreboard(), new LiteralMessage("online"));
            }
        }
        return builder.buildFuture();
    }

    private static int handleHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> Text.literal("Minehop AntiCheat commands:").formatted(Formatting.GOLD), false);
        source.sendFeedback(() -> Text.literal("  /minehop anticheat verbose").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("  /minehop anticheat list").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("  /minehop anticheat info <player>").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("  /minehop anticheat clear <player>").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("  /minehop anticheat exempt <add|remove|list> [player]").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("  /minehop anticheat gui").formatted(Formatting.AQUA), false);
        source.sendFeedback(() -> Text.literal("  /minehop anticheat enable|disable (op only)").formatted(Formatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int handleVerbose(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("Verbose toggle requires a player."));
            return 0;
        }
        boolean enabled = AntiCheatManager.toggleVerbose(player);
        if (enabled) {
            Logger.logSuccess(player, "AntiCheat verbose enabled. You will see flag notifications.");
        } else {
            Logger.logSuccess(player, "AntiCheat verbose disabled.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int handleSetEnabled(CommandContext<ServerCommandSource> context, boolean enabled) {
        AntiCheatManager.setEnabled(enabled);
        context.getSource().sendFeedback(
                () -> Text.literal("AntiCheat " + (enabled ? "enabled" : "disabled") + ".").formatted(enabled ? Formatting.GREEN : Formatting.RED),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int handleList(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        List<AntiCheatPlayerState> sorted = new ArrayList<>(AntiCheatManager.allStates().values());
        sorted.removeIf(state -> state == null || state.totalFlagCount() <= 0);
        sorted.sort(Comparator.comparingInt(AntiCheatPlayerState::totalFlagCount).reversed());
        if (sorted.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No players have any flags.").formatted(Formatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFeedback(() -> Text.literal("=== AntiCheat Flag List ===").formatted(Formatting.GOLD), false);
        int shown = 0;
        for (AntiCheatPlayerState state : sorted) {
            if (shown >= 25) {
                break;
            }
            String name = state.lastKnownName().isBlank() ? state.playerUuid().toString() : state.lastKnownName();
            int recent = state.recentFlags().size();
            int total = state.totalFlagCount();
            source.sendFeedback(() -> Text.literal(" " + name + " ")
                    .formatted(Formatting.YELLOW)
                    .append(Text.literal("recent=" + recent + " total=" + total).formatted(Formatting.GRAY)), false);
            shown++;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int handleInfo(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "player");
        AntiCheatPlayerState state = findStateByName(context.getSource().getServer(), name);
        if (state == null) {
            source.sendError(Text.literal("No anticheat data for " + name));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("=== AntiCheat: " + state.lastKnownName() + " ===").formatted(Formatting.GOLD), false);
        source.sendFeedback(() -> Text.literal("UUID: " + state.playerUuid()).formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("Total flags: " + state.totalFlagCount() + ", recent: " + state.recentFlags().size()).formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("Last 8 flags:").formatted(Formatting.AQUA), false);
        List<AntiCheatFlag> recent = new ArrayList<>(state.recentFlags());
        int limit = Math.min(8, recent.size());
        for (int i = recent.size() - limit; i < recent.size(); i++) {
            AntiCheatFlag flag = recent.get(i);
            String stamp = String.format(Locale.ROOT, "[%s]", new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(flag.timestamp)));
            String line = stamp + " " + flag.checkName + " (vl=" + String.format(Locale.ROOT, "%.1f", flag.severity) + ") "
                    + flag.details + " @ " + String.format(Locale.ROOT, "%.1f,%.1f,%.1f", flag.posX, flag.posY, flag.posZ);
            source.sendFeedback(() -> Text.literal(" " + line).formatted(Formatting.YELLOW), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int handleClear(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "player");
        AntiCheatPlayerState state = findStateByName(context.getSource().getServer(), name);
        if (state == null) {
            source.sendError(Text.literal("No anticheat data for " + name));
            return 0;
        }
        AntiCheatManager.clearFlagsFor(state.playerUuid());
        source.sendFeedback(() -> Text.literal("Cleared anticheat flags for " + state.lastKnownName() + ".").formatted(Formatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int handleExempt(CommandContext<ServerCommandSource> context, boolean add) {
        ServerCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "player");
        MinecraftServer server = source.getServer();
        if (server == null) {
            return 0;
        }
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(name);
        UUID uuid;
        String displayName;
        if (target != null) {
            uuid = target.getUuid();
            displayName = target.getNameForScoreboard();
        } else {
            AntiCheatPlayerState state = findStateByName(server, name);
            if (state == null) {
                source.sendError(Text.literal("Player not found: " + name));
                return 0;
            }
            uuid = state.playerUuid();
            displayName = state.lastKnownName();
        }
        boolean changed = add ? AntiCheatManager.addExempt(uuid) : AntiCheatManager.removeExempt(uuid);
        if (!changed) {
            source.sendFeedback(() -> Text.literal(displayName + " is already " + (add ? "exempt" : "not exempt") + ".").formatted(Formatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFeedback(
                () -> Text.literal((add ? "Added " : "Removed ") + displayName + (add ? " to" : " from") + " anticheat exempt list.").formatted(Formatting.GREEN),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int handleExemptList(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        MinecraftServer server = source.getServer();
        Collection<UUID> exempt = AntiCheatManager.exemptList();
        if (exempt.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No players in exempt list.").formatted(Formatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFeedback(() -> Text.literal("=== AntiCheat Exempt List (" + exempt.size() + ") ===").formatted(Formatting.GOLD), false);
        for (UUID uuid : exempt) {
            String label = uuid.toString();
            if (server != null) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                if (p != null) {
                    label = p.getNameForScoreboard() + " (" + uuid + ")";
                } else {
                    AntiCheatPlayerState state = AntiCheatManager.stateByUuid(uuid);
                    if (state != null && !state.lastKnownName().isBlank()) {
                        label = state.lastKnownName() + " (" + uuid + ")";
                    }
                }
            }
            String finalLabel = label;
            source.sendFeedback(() -> Text.literal(" - " + finalLabel).formatted(Formatting.YELLOW), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int handleGui(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("GUI requires a player."));
            return 0;
        }
        String json = buildAdminSnapshotJson(player.getServer());
        ServerPlayNetworking.send(player, new OpenAntiCheatScreenPayload(json));
        return Command.SINGLE_SUCCESS;
    }

    public static String buildAdminSnapshotJson(MinecraftServer server) {
        AdminSnapshot snapshot = new AdminSnapshot();
        snapshot.enabled = AntiCheatManager.isEnabled();
        snapshot.serverTick = AntiCheatManager.currentServerTick();
        for (Map.Entry<UUID, AntiCheatPlayerState> entry : AntiCheatManager.allStates().entrySet()) {
            AntiCheatPlayerState state = entry.getValue();
            if (state == null) {
                continue;
            }
            AdminPlayerEntry data = new AdminPlayerEntry();
            data.uuid = entry.getKey().toString();
            data.name = state.lastKnownName();
            data.online = server != null && server.getPlayerManager().getPlayer(entry.getKey()) != null;
            data.totalFlags = state.totalFlagCount();
            data.recentFlagCount = state.recentFlags().size();
            data.exempt = AntiCheatManager.exemptList().contains(entry.getKey());
            data.lagbacks = state.consecutiveLagbacks();
            for (AntiCheatFlag flag : state.recentFlags()) {
                AdminFlagEntry fe = new AdminFlagEntry();
                fe.check = flag.checkName;
                fe.timestamp = flag.timestamp;
                fe.severity = flag.severity;
                fe.details = flag.details;
                fe.x = flag.posX;
                fe.y = flag.posY;
                fe.z = flag.posZ;
                data.flags.add(fe);
            }
            snapshot.players.add(data);
        }
        if (server != null) {
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                if (online == null) {
                    continue;
                }
                boolean exists = false;
                for (AdminPlayerEntry existing : snapshot.players) {
                    if (online.getUuidAsString().equals(existing.uuid)) {
                        existing.online = true;
                        existing.name = online.getNameForScoreboard();
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    AdminPlayerEntry data = new AdminPlayerEntry();
                    data.uuid = online.getUuidAsString();
                    data.name = online.getNameForScoreboard();
                    data.online = true;
                    data.exempt = AntiCheatManager.exemptList().contains(online.getUuid());
                    snapshot.players.add(data);
                }
            }
        }
        snapshot.players.sort(Comparator
                .comparingInt((AdminPlayerEntry e) -> e.totalFlags).reversed()
                .thenComparing(e -> e.name == null ? "" : e.name, String.CASE_INSENSITIVE_ORDER));
        return GSON.toJson(snapshot);
    }

    private static AntiCheatPlayerState findStateByName(MinecraftServer server, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
            if (player != null) {
                return AntiCheatManager.stateOf(player);
            }
        }
        for (AntiCheatPlayerState state : AntiCheatManager.allStates().values()) {
            if (state != null && name.equalsIgnoreCase(state.lastKnownName())) {
                return state;
            }
        }
        return null;
    }

    public static final class AdminSnapshot {
        public boolean enabled;
        public long serverTick;
        public List<AdminPlayerEntry> players = new ArrayList<>();
    }

    public static final class AdminPlayerEntry {
        public String uuid = "";
        public String name = "";
        public boolean online;
        public boolean exempt;
        public int totalFlags;
        public int recentFlagCount;
        public int lagbacks;
        public List<AdminFlagEntry> flags = new ArrayList<>();
    }

    public static final class AdminFlagEntry {
        public String check = "";
        public long timestamp;
        public double severity;
        public String details = "";
        public double x;
        public double y;
        public double z;
    }

    public static void wireServerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(net.nerdorg.minehop.networking.payloads.AntiCheatActionPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            String action = payload.action() == null ? "" : payload.action();
            String targetUuidString = payload.targetUuid() == null ? "" : payload.targetUuid();
            ctx.server().execute(() -> handleAction(player, action, targetUuidString));
        });
    }

    private static void handleAction(ServerPlayerEntity player, String action, String targetUuidString) {
        if (player == null || !player.hasPermissionLevel(2)) {
            return;
        }
        UUID targetUuid = null;
        if (!targetUuidString.isEmpty()) {
            try {
                targetUuid = UUID.fromString(targetUuidString);
            } catch (IllegalArgumentException ignored) {
            }
        }
        switch (action) {
            case net.nerdorg.minehop.networking.payloads.AntiCheatActionPayload.ACTION_CLEAR_FLAGS -> {
                if (targetUuid != null) {
                    AntiCheatManager.clearFlagsFor(targetUuid);
                }
            }
            case net.nerdorg.minehop.networking.payloads.AntiCheatActionPayload.ACTION_EXEMPT_ADD -> {
                if (targetUuid != null) {
                    AntiCheatManager.addExempt(targetUuid);
                }
            }
            case net.nerdorg.minehop.networking.payloads.AntiCheatActionPayload.ACTION_EXEMPT_REMOVE -> {
                if (targetUuid != null) {
                    AntiCheatManager.removeExempt(targetUuid);
                }
            }
            case net.nerdorg.minehop.networking.payloads.AntiCheatActionPayload.ACTION_KICK -> {
                if (targetUuid != null && player.hasPermissionLevel(3)) {
                    MinecraftServer server = player.getServer();
                    if (server != null) {
                        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
                        if (target != null) {
                            target.networkHandler.disconnect(Text.literal("Kicked by anticheat admin."));
                        }
                    }
                }
            }
            case net.nerdorg.minehop.networking.payloads.AntiCheatActionPayload.ACTION_REFRESH -> {
            }
            default -> {
            }
        }
        ServerPlayNetworking.send(player, new OpenAntiCheatScreenPayload(buildAdminSnapshotJson(player.getServer())));
    }
}
