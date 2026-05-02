package dev.dubhe.map.client.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.dubhe.map.AleeveAtlas;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;

public final class WaypointManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve(AleeveAtlas.MOD_ID + "-waypoints.json");
    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static String activeWaypointId;

    private WaypointManager() {
    }

    public static void load() {
        WAYPOINTS.clear();
        activeWaypointId = null;
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            StoredWaypoints stored = GSON.fromJson(reader, StoredWaypoints.class);
            if (stored != null && stored.waypoints != null) {
                WAYPOINTS.addAll(stored.waypoints);
                activeWaypointId = stored.activeWaypointId;
            }
        } catch (IOException exception) {
            AleeveAtlas.LOGGER.warn("Failed to load waypoints from {}", PATH, exception);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                StoredWaypoints stored = new StoredWaypoints();
                stored.activeWaypointId = activeWaypointId;
                stored.waypoints = new ArrayList<>(WAYPOINTS);
                GSON.toJson(stored, writer);
            }
        } catch (IOException exception) {
            AleeveAtlas.LOGGER.warn("Failed to save waypoints to {}", PATH, exception);
        }
    }

    public static List<Waypoint> getWaypoints() {
        return WAYPOINTS;
    }

    public static Optional<Waypoint> getActiveWaypoint() {
        return WAYPOINTS.stream().filter(waypoint -> Objects.equals(waypoint.id, activeWaypointId)).findFirst();
    }

    public static Optional<Waypoint> getActiveWaypoint(ResourceKey<Level> dimension) {
        String key = dimension.identifier().toString();
        return getActiveWaypoint().filter(waypoint -> waypoint.enabled && Objects.equals(waypoint.dimension, key));
    }

    public static List<Waypoint> getWaypoints(ResourceKey<Level> dimension) {
        String key = dimension.identifier().toString();
        return WAYPOINTS.stream()
            .filter(waypoint -> waypoint.enabled && Objects.equals(waypoint.dimension, key))
            .sorted(Comparator.comparing(waypoint -> waypoint.name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public static void upsert(Waypoint waypoint) {
        for (int i = 0; i < WAYPOINTS.size(); i++) {
            if (Objects.equals(WAYPOINTS.get(i).id, waypoint.id)) {
                WAYPOINTS.set(i, waypoint.copy());
                save();
                return;
            }
        }
        WAYPOINTS.add(waypoint.copy());
        save();
    }

    public static void delete(String waypointId) {
        WAYPOINTS.removeIf(waypoint -> Objects.equals(waypoint.id, waypointId));
        if (Objects.equals(activeWaypointId, waypointId)) {
            activeWaypointId = null;
        }
        save();
    }

    public static void setActive(String waypointId) {
        activeWaypointId = waypointId;
        save();
    }

    public static Waypoint createAtPlayer() {
        Waypoint waypoint = new Waypoint();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.level != null) {
            BlockPos pos = minecraft.player.blockPosition();
            waypoint.name = "Waypoint " + (WAYPOINTS.size() + 1);
            waypoint.dimension = minecraft.level.dimension().identifier().toString();
            waypoint.x = pos.getX();
            waypoint.y = pos.getY();
            waypoint.z = pos.getZ();
        }
        return waypoint;
    }

    public static Waypoint createAtCamera() {
        Waypoint waypoint = createAtPlayer();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return waypoint;
        }
        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().position();
        waypoint.dimension = minecraft.level.dimension().identifier().toString();
        waypoint.x = (int) Math.floor(cameraPos.x);
        waypoint.y = (int) Math.floor(cameraPos.y);
        waypoint.z = (int) Math.floor(cameraPos.z);
        return waypoint;
    }

    private static class StoredWaypoints {
        private String activeWaypointId;
        private List<Waypoint> waypoints = new ArrayList<>();
    }
}

