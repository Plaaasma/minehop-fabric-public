package net.nerdorg.minehop.entity.custom;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.data.DataManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The start and end zones currently loaded, so a player's position can be checked against them once per client tick
 * (RunClock) without scanning every entity of the level. A zone registers itself whenever it ticks; a removed zone
 * (killed, or unloaded with its chunk) drops out on the next lookup. A zone that doesn't tick has no player near it.
 *
 * <p>Server thread only.
 */
public final class RunZones {
    private static final Set<StartEntity> START_ZONES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<EndEntity> END_ZONES = Collections.newSetFromMap(new IdentityHashMap<>());

    private RunZones() {
    }

    static void register(StartEntity zone) {
        START_ZONES.add(zone);
    }

    static void register(EndEntity zone) {
        END_ZONES.add(zone);
    }

    public static void clear() {
        START_ZONES.clear();
        END_ZONES.clear();
    }

    /** The first start zone of an existing map in {@code level} that contains {@code pos}, or null. */
    public static StartEntity startZoneAt(ServerLevel level, Vec3 pos) {
        for (Iterator<StartEntity> it = START_ZONES.iterator(); it.hasNext(); ) {
            StartEntity zone = it.next();
            if (zone.isRemoved()) {
                it.remove();
                continue;
            }
            if (zone.level() != level) {
                continue;
            }
            AABB box = zone.runBounds();
            if (box != null && box.contains(pos) && DataManager.getMap(zone.getPairedMap()) != null) {
                return zone;
            }
        }
        return null;
    }

    /** The boxes of the loaded end zones of {@code mapName} in {@code level}. */
    public static List<AABB> endZones(ServerLevel level, String mapName) {
        List<AABB> boxes = new ArrayList<>(2);
        if (mapName == null) {
            return boxes;
        }
        for (Iterator<EndEntity> it = END_ZONES.iterator(); it.hasNext(); ) {
            EndEntity zone = it.next();
            if (zone.isRemoved()) {
                it.remove();
                continue;
            }
            if (zone.level() != level || !mapName.equals(zone.getPairedMap())) {
                continue;
            }
            AABB box = zone.runBounds();
            if (box != null) {
                boxes.add(box);
            }
        }
        return boxes;
    }

    /** Loaded zone counts, for diagnostics: {start, end}. */
    public static int[] counts() {
        return new int[]{START_ZONES.size(), END_ZONES.size()};
    }
}
