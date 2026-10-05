package net.nerdorg.minehop.commands;

import net.nerdorg.minehop.util.PermissionUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("minehop")
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("anticheat")
                                .requires(source -> PermissionUtil.hasLevel(source, 2))
                                .executes(AntiCheatCommands::handleHelp)
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("verbose")
                                        .executes(AntiCheatCommands::handleVerbose)
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("enable")
                                        .requires(source -> PermissionUtil.hasLevel(source, 4))
                                        .executes(context -> handleSetEnabled(context, true))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("disable")
                                        .requires(source -> PermissionUtil.hasLevel(source, 4))
                                        .executes(context -> handleSetEnabled(context, false))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("lagback")
                                        .requires(source -> PermissionUtil.hasLevel(source, 4))
                                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("on")
                                                .executes(context -> handleSetLagbacks(context, true)))
                                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("off")
                                                .executes(context -> handleSetLagbacks(context, false)))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                                        .executes(AntiCheatCommands::handleList)
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("gui")
                                        .executes(AntiCheatCommands::handleGui)
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("info")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.string())
                                                .suggests((ctx, builder) -> suggestKnownPlayers(ctx, builder))
                                                .executes(AntiCheatCommands::handleInfo)
                                        )
                                )
                                // Wiping evidence and exempting players defeat the anticheat: admins only.
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear")
                                        .requires(source -> PermissionUtil.hasLevel(source, 4))
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.string())
                                                .suggests((ctx, builder) -> suggestKnownPlayers(ctx, builder))
                                                .executes(AntiCheatCommands::handleClear)
                                        )
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("exempt")
                                        .requires(source -> PermissionUtil.hasLevel(source, 4))
                                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("add")
                                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.string())
                                                        .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                                                        .executes(context -> handleExempt(context, true))
                                                )
                                        )
                                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("remove")
                                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.string())
                                                        .suggests((ctx, builder) -> suggestOnlinePlayers(ctx, builder))
                                                        .executes(context -> handleExempt(context, false))
                                                )
                                        )
                                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                                                .executes(AntiCheatCommands::handleExemptList)
                                        )
                                )
                        )
        ));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestKnownPlayers(
            CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                builder.suggest(player.getScoreboardName(), new LiteralMessage("online"));
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
            CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                builder.suggest(player.getScoreboardName(), new LiteralMessage("online"));
            }
        }
        return builder.buildFuture();
    }

    private static int handleHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Minehop AntiCheat commands:").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("  /minehop anticheat verbose").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("  /minehop anticheat list").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("  /minehop anticheat info <player>").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("  /minehop anticheat clear <player>").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("  /minehop anticheat exempt <add|remove|list> [player]").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("  /minehop anticheat gui").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("  /minehop anticheat enable|disable (op only)").withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int handleVerbose(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Verbose toggle requires a player."));
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

    private static int handleSetEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        AntiCheatManager.setEnabled(enabled);
        context.getSource().sendSuccess(
                () -> Component.literal("AntiCheat " + (enabled ? "enabled" : "disabled") + ".").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int handleSetLagbacks(CommandContext<CommandSourceStack> context, boolean enabled) {
        AntiCheatManager.setLagbacksEnabled(enabled);
        context.getSource().sendSuccess(
                () -> Component.literal("AntiCheat lagbacks " + (enabled ? "ON" : "OFF (checks still flag)") + ".")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int handleList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<AntiCheatPlayerState> sorted = new ArrayList<>(AntiCheatManager.allStates().values());
        sorted.removeIf(state -> state == null || state.totalFlagCount() <= 0);
        sorted.sort(Comparator.comparingInt(AntiCheatPlayerState::totalFlagCount).reversed());
        if (sorted.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No players have any flags.").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendSuccess(() -> Component.literal("=== AntiCheat Flag List ===").withStyle(ChatFormatting.GOLD), false);
        int shown = 0;
        for (AntiCheatPlayerState state : sorted) {
            if (shown >= 25) {
                break;
            }
            String name = state.lastKnownName().isBlank() ? state.playerUuid().toString() : state.lastKnownName();
            int recent = state.recentFlags().size();
            int total = state.totalFlagCount();
            source.sendSuccess(() -> Component.literal(" " + name + " ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("recent=" + recent + " total=" + total).withStyle(ChatFormatting.GRAY)), false);
            shown++;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int handleInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "player");
        AntiCheatPlayerState state = findStateByName(context.getSource().getServer(), name);
        if (state == null) {
            source.sendFailure(Component.literal("No anticheat data for " + name));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("=== AntiCheat: " + state.lastKnownName() + " ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("UUID: " + state.playerUuid()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Total flags: " + state.totalFlagCount() + ", recent: " + state.recentFlags().size()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Last 8 flags:").withStyle(ChatFormatting.AQUA), false);
        List<AntiCheatFlag> recent = new ArrayList<>(state.recentFlags());
        int limit = Math.min(8, recent.size());
        for (int i = recent.size() - limit; i < recent.size(); i++) {
            AntiCheatFlag flag = recent.get(i);
            String stamp = String.format(Locale.ROOT, "[%s]", new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(flag.timestamp)));
            String line = stamp + " " + flag.checkName + " (vl=" + String.format(Locale.ROOT, "%.1f", flag.severity) + ") "
                    + flag.details + " @ " + String.format(Locale.ROOT, "%.1f,%.1f,%.1f", flag.posX, flag.posY, flag.posZ);
            source.sendSuccess(() -> Component.literal(" " + line).withStyle(ChatFormatting.YELLOW), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int handleClear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "player");
        AntiCheatPlayerState state = findStateByName(context.getSource().getServer(), name);
        if (state == null) {
            source.sendFailure(Component.literal("No anticheat data for " + name));
            return 0;
        }
        AntiCheatManager.clearFlagsFor(state.playerUuid());
        source.sendSuccess(() -> Component.literal("Cleared anticheat flags for " + state.lastKnownName() + ".").withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int handleExempt(CommandContext<CommandSourceStack> context, boolean add) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "player");
        MinecraftServer server = source.getServer();
        if (server == null) {
            return 0;
        }
        ServerPlayer target = server.getPlayerList().getPlayerByName(name);
        UUID uuid;
        String displayName;
        if (target != null) {
            uuid = target.getUUID();
            displayName = target.getScoreboardName();
        } else {
            AntiCheatPlayerState state = findStateByName(server, name);
            if (state == null) {
                source.sendFailure(Component.literal("Player not found: " + name));
                return 0;
            }
            uuid = state.playerUuid();
            displayName = state.lastKnownName();
        }
        boolean changed = add ? AntiCheatManager.addExempt(uuid) : AntiCheatManager.removeExempt(uuid);
        if (!changed) {
            source.sendSuccess(() -> Component.literal(displayName + " is already " + (add ? "exempt" : "not exempt") + ".").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendSuccess(
                () -> Component.literal((add ? "Added " : "Removed ") + displayName + (add ? " to" : " from") + " anticheat exempt list.").withStyle(ChatFormatting.GREEN),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int handleExemptList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Collection<UUID> exempt = AntiCheatManager.exemptList();
        if (exempt.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No players in exempt list.").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendSuccess(() -> Component.literal("=== AntiCheat Exempt List (" + exempt.size() + ") ===").withStyle(ChatFormatting.GOLD), false);
        for (UUID uuid : exempt) {
            String label = uuid.toString();
            if (server != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null) {
                    label = p.getScoreboardName() + " (" + uuid + ")";
                } else {
                    AntiCheatPlayerState state = AntiCheatManager.stateByUuid(uuid);
                    if (state != null && !state.lastKnownName().isBlank()) {
                        label = state.lastKnownName() + " (" + uuid + ")";
                    }
                }
            }
            String finalLabel = label;
            source.sendSuccess(() -> Component.literal(" - " + finalLabel).withStyle(ChatFormatting.YELLOW), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int handleGui(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("GUI requires a player."));
            return 0;
        }
        String json = buildAdminSnapshotJson(player.level().getServer());
        Services.NETWORK.sendToPlayer(player, new OpenAntiCheatScreenPayload(json));
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
            data.online = server != null && server.getPlayerList().getPlayer(entry.getKey()) != null;
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
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (online == null) {
                    continue;
                }
                boolean exists = false;
                for (AdminPlayerEntry existing : snapshot.players) {
                    if (online.getStringUUID().equals(existing.uuid)) {
                        existing.online = true;
                        existing.name = online.getScoreboardName();
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    AdminPlayerEntry data = new AdminPlayerEntry();
                    data.uuid = online.getStringUUID();
                    data.name = online.getScoreboardName();
                    data.online = true;
                    data.exempt = AntiCheatManager.exemptList().contains(online.getUUID());
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
            ServerPlayer player = server.getPlayerList().getPlayerByName(name);
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
        Services.NETWORK.registerServerReceiver(net.nerdorg.minehop.networking.payloads.AntiCheatActionPayload.ID, (payload, ctx) -> {
            // Throttle: ACTION_REFRESH rebuilds a full admin snapshot; don't let it be spammed.
            if (!net.nerdorg.minehop.util.PacketRateLimiter.allow(ctx.player(), net.nerdorg.minehop.networking.payloads.AntiCheatActionPayload.ID.id().toString(), 100)) {
                return;
            }
            ServerPlayer player = ctx.player();
            String action = payload.action() == null ? "" : payload.action();
            String targetUuidString = payload.targetUuid() == null ? "" : payload.targetUuid();
            ctx.server().execute(() -> handleAction(player, action, targetUuidString));
        });
    }

    private static void handleAction(ServerPlayer player, String action, String targetUuidString) {
        if (player == null || !PermissionUtil.hasLevel(player, 2)) {
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
                if (targetUuid != null && PermissionUtil.hasLevel(player, 3)) {
                    MinecraftServer server = player.level().getServer();
                    if (server != null) {
                        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
                        if (target != null) {
                            target.connection.disconnect(Component.literal("Kicked by anticheat admin."));
                        }
                    }
                }
            }
            case net.nerdorg.minehop.networking.payloads.AntiCheatActionPayload.ACTION_REFRESH -> {
            }
            default -> {
            }
        }
        Services.NETWORK.sendToPlayer(player, new OpenAntiCheatScreenPayload(buildAdminSnapshotJson(player.level().getServer())));
    }
}
