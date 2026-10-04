package net.nerdorg.minehop.anticheat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.util.JsonStorage;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AntiCheatStorage {
    private static final String FOLDER = "MineHop_Data";
    private static final String FLAGS_PATH = FOLDER + "/anticheat_flags.json";
    private static final String EXEMPT_PATH = FOLDER + "/anticheat_exempt.json";
    private static final String SETTINGS_PATH = FOLDER + "/anticheat_settings.json";
    // Bump when the persisted anticheat shape changes; migrate in load().
    private static final int SCHEMA_VERSION = 1;

    private AntiCheatStorage() {
    }

    public static void save(MinecraftServer server, Map<UUID, AntiCheatPlayerState> states, Set<UUID> exemptList) {
        if (server == null) {
            return;
        }
        Path savePath = server.getSavePath(WorldSavePath.ROOT);
        try {
            Files.createDirectories(savePath.resolve(FOLDER));
        } catch (IOException e) {
            Minehop.LOGGER.warn("Failed to create anticheat data folder", e);
            return;
        }

        Map<String, PersistedPlayerState> persisted = new LinkedHashMap<>();
        if (states != null) {
            for (Map.Entry<UUID, AntiCheatPlayerState> entry : states.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                AntiCheatPlayerState state = entry.getValue();
                if (state.totalFlagCount() <= 0 && state.recentFlags().isEmpty()) {
                    continue;
                }
                PersistedPlayerState data = new PersistedPlayerState();
                data.lastKnownName = state.lastKnownName();
                data.totalFlagCount = state.totalFlagCount();
                data.recentFlags = new ArrayList<>(state.recentFlags());
                persisted.put(entry.getKey().toString(), data);
            }
        }

        JsonStorage.writeAtomic(savePath.resolve(FLAGS_PATH), SCHEMA_VERSION, persisted);

        List<String> exemptStrings = new ArrayList<>();
        if (exemptList != null) {
            for (UUID uuid : exemptList) {
                if (uuid != null) {
                    exemptStrings.add(uuid.toString());
                }
            }
        }
        JsonStorage.writeAtomic(savePath.resolve(EXEMPT_PATH), SCHEMA_VERSION, exemptStrings);
    }

    public static void load(MinecraftServer server, Map<UUID, AntiCheatPlayerState> states, Set<UUID> exemptList) {
        if (server == null) {
            return;
        }
        Path savePath = server.getSavePath(WorldSavePath.ROOT);
        try {
            Files.createDirectories(savePath.resolve(FOLDER));
        } catch (IOException ignored) {
        }

        try {
            Type type = new TypeToken<Map<String, PersistedPlayerState>>(){}.getType();
            Map<String, PersistedPlayerState> persisted = JsonStorage.readData(savePath.resolve(FLAGS_PATH), type);
            if (persisted != null) {
                for (Map.Entry<String, PersistedPlayerState> entry : persisted.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(entry.getKey());
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    AntiCheatPlayerState state = new AntiCheatPlayerState(uuid);
                    state.setLastKnownName(entry.getValue().lastKnownName);
                    if (entry.getValue().recentFlags != null) {
                        for (AntiCheatFlag flag : entry.getValue().recentFlags) {
                            if (flag != null) {
                                state.addFlag(flag);
                            }
                        }
                    }
                    states.put(uuid, state);
                }
            }
        } catch (Exception e) {
            Minehop.LOGGER.warn("Failed to load anticheat flag data", e);
        }

        try {
            Type type = new TypeToken<List<String>>(){}.getType();
            List<String> raw = JsonStorage.readData(savePath.resolve(EXEMPT_PATH), type);
            if (raw != null) {
                for (String s : raw) {
                    if (s == null) {
                        continue;
                    }
                    try {
                        exemptList.add(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            Minehop.LOGGER.warn("Failed to load anticheat exempt list", e);
        }
    }

    /** Admin switches that must survive restarts (e.g. lagbacks turned off while a false positive is fixed). */
    public static PersistedSettings loadSettings(MinecraftServer server) {
        if (server == null) {
            return new PersistedSettings();
        }
        try {
            PersistedSettings settings = JsonStorage.readData(
                    server.getSavePath(WorldSavePath.ROOT).resolve(SETTINGS_PATH), PersistedSettings.class);
            return settings == null ? new PersistedSettings() : settings;
        } catch (Exception e) {
            Minehop.LOGGER.warn("Failed to load anticheat settings", e);
            return new PersistedSettings();
        }
    }

    public static void saveSettings(MinecraftServer server, boolean enabled, boolean lagbacks) {
        if (server == null) {
            return;
        }
        Path savePath = server.getSavePath(WorldSavePath.ROOT);
        try {
            Files.createDirectories(savePath.resolve(FOLDER));
        } catch (IOException e) {
            Minehop.LOGGER.warn("Failed to create anticheat data folder", e);
            return;
        }
        PersistedSettings settings = new PersistedSettings();
        settings.enabled = enabled;
        settings.lagbacks = lagbacks;
        JsonStorage.writeAtomic(savePath.resolve(SETTINGS_PATH), SCHEMA_VERSION, settings);
    }

    public static final class PersistedSettings {
        public boolean enabled = true;
        public boolean lagbacks = true;
    }

    public static final class PersistedPlayerState {
        public String lastKnownName = "";
        public int totalFlagCount;
        public List<AntiCheatFlag> recentFlags = new ArrayList<>();
    }
}
