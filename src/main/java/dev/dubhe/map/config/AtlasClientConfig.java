package dev.dubhe.map.config;

import com.google.gson.annotations.SerializedName;
import dev.anvilcraft.lib.v2.config.CollapsibleObject;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import net.neoforged.fml.config.ModConfig;

/// 客户端配置（`aleeve_atlas-client.toml`）。
///
/// 本类只在物理客户端加载：实例由 {@link AtlasClientConfigs#CONFIG} 持有，
/// 专用服务端不会解析本类。
@Config(name = "aleeve_atlas", type = ModConfig.Type.CLIENT)
public class AtlasClientConfig {
    @Comment("Show proximity notifications for favorite structures and followed structure types.")
    public boolean structureNotifications = true;

    @CollapsibleObject
    @Comment("Minimap settings.")
    public Minimap minimap = new Minimap();

    @CollapsibleObject
    @Comment("World map settings.")
    public WorldMap worldMap = new WorldMap();

    @CollapsibleObject
    @Comment("Shared cave mapping settings for the minimap and world map.")
    public Caves caves = new Caves();

    public static class Caves {
        @Comment("Cave mapping mode in ordinary dimensions: OFF, LAYERED, or FULL.")
        public CaveMapMode mode = CaveMapMode.LAYERED;

        @Comment("Cave mapping mode in ceiling dimensions such as the Nether: OFF, LAYERED, or FULL.")
        public CaveMapMode ceilingDimensionMode = CaveMapMode.FULL;

        @Comment("Maximum depth below the selected top Y in layered cave mode (1 ~ 64 blocks).")
        public int depth = 30;

        @Comment("Darken deeper floors in layered cave maps to make height differences legible.")
        public boolean depthShading = true;
    }

    @CollapsibleObject
    @Comment("Entity radar settings.")
    public Radar radar = new Radar();

    @CollapsibleObject
    @Comment("Waypoint settings.")
    public Waypoint waypoint = new Waypoint();

    @CollapsibleObject
    @Comment("Territory overlay settings.")
    public Territory territory = new Territory();

    @CollapsibleObject
    @Comment("Map ping settings.")
    public Ping ping = new Ping();

    public static class WorldMap {
        @Comment("Animate world map zoom. Disable to apply each wheel step immediately without continued zooming.")
        public boolean smoothZoom = true;

        @Comment("Player indicator style on the world map. Changes apply immediately.")
        public PlayerIndicatorType playerIndicator = PlayerIndicatorType.ANTIALIASED_DROPLET;

        @Comment("Corner used by the world map compass.")
        public CompassPosition compassPosition = CompassPosition.BOTTOM_RIGHT;

        @Comment("World map compass size in GUI-scaled pixels (32 ~ 192).")
        public double compassSize = 48.0;

        @Comment("Show chunk boundaries on the world map.")
        public boolean showChunkGrid = false;

        @Comment("Highlight slime chunks on the world map.")
        public boolean showSlimeChunks = false;
    }

    public static class Minimap {
        @Comment("Player indicator style on the minimap. Changes apply immediately.")
        public PlayerIndicatorType playerIndicator = PlayerIndicatorType.ANTIALIASED_DROPLET;

        @Comment("Whether the minimap HUD is enabled.")
        public boolean enabled = true;

        @Comment("Minimap outline shape.")
        public MinimapShape shape = MinimapShape.CIRCLE;

        @Comment("Map color scheme used by both the minimap and world map.")
        public MapColorScheme colorScheme = MapColorScheme.VANILLA;

        @Comment("Minimap screen size multiplier (0.5 ~ 4.0).")
        public double scale = 1.0;

        @Comment("Minimap world view zoom (1.0 ~ 4.0). Higher values show a smaller area.")
        public double zoom = 1.5;

        @Comment("Minimap screen corner.")
        public MinimapPosition position = MinimapPosition.TOP_RIGHT;

        @Comment("Custom X offset in GUI-scaled pixels, used when position is CUSTOM.")
        public int offsetX = 0;

        @Comment("Custom Y offset in GUI-scaled pixels, used when position is CUSTOM.")
        public int offsetY = 0;

        @Comment("Keep north fixed up instead of rotating with the camera.")
        public boolean lockNorth = false;

        @Comment("Show coordinates below the minimap.")
        public boolean showCoords = true;

        @Comment("Show the current biome name below the minimap.")
        public boolean showBiome = true;

        @Comment("Show the current light level below the minimap.")
        public boolean showLight = false;

        @Comment("Show the current in-game time below the minimap.")
        public boolean showTime = false;

        @Comment("Show chunk boundaries on the minimap.")
        public boolean showChunkGrid = false;

        @Comment("Highlight slime chunks on the minimap.")
        public boolean showSlimeChunks = false;

        @Comment("Adjust minimap brightness with local light level.")
        public boolean lightBrightness = false;
    }

    public static class Radar {
        @Comment("Entity radar display mode.")
        public RadarDisplayMode displayMode = RadarDisplayMode.HYBRID;

        @Comment("Fade-out grace period in seconds for occluded markers (0.5 ~ 1.5).")
        public double fadeOutSeconds = 1.0;

        @CollapsibleObject
        @Comment("Per-category radar settings.")
        public Categories categories = new Categories();

        public static class Categories {
            @Comment("Show players on the radar.")
            public boolean showPlayers = true;

            @Comment("Team player dot color as a hex string.")
            public String teamPlayerColor = "#55FF55";

            @Comment("Non-team player dot color as a hex string.")
            public String otherPlayerColor = "#FFFFFF";

            @Comment("Show hostile mobs on the radar.")
            public boolean showHostiles = true;

            @Comment("Hostile mob dot color as a hex string.")
            public String hostileColor = "#FF5555";

            @Comment("Show friendly mobs on the radar.")
            public boolean showFriendly = true;

            @Comment("Friendly mob dot color as a hex string.")
            public String friendlyColor = "#7CFC00";

            @Comment("Show tiny ambient mobs such as bats on the radar.")
            public boolean showAmbient = false;

            @Comment("Ambient mob dot color as a hex string.")
            public String ambientColor = "#B0B0B0";

            @Comment("Show dropped items on the radar.")
            public boolean showItems = true;

            @Comment("Dropped item dot color as a hex string.")
            public String itemColor = "#FF55FF";

            @Comment("Show miscellaneous entities on the radar.")
            public boolean showMisc = false;

            @Comment("Miscellaneous entity dot color as a hex string.")
            public String miscColor = "#9B59B6";
        }
    }

    public static class Waypoint {
        @Comment("Send policy for newly created waypoints.")
        public WaypointSendPolicy sendPolicy = WaypointSendPolicy.MANUAL_SYNC;

        @Comment("Receive policy for waypoints shared by teammates.")
        public WaypointReceivePolicy receivePolicy = WaypointReceivePolicy.MANUAL_ACCEPT;

        @Comment("Keep historical death waypoints.")
        public boolean keepDeathPoints = true;
    }

    public static class Territory {
        @Comment("Render territory overlays on the minimap.")
        @SerializedName("Show on Minimap")
        public boolean showOnMinimap = true;

        @Comment("Render territory names on the minimap.")
        @SerializedName("Show Label on Minimap")
        public boolean showLabelOnMinimap = false;

        @Comment("Centroid attraction weight used by the weighted anchor algorithm (0.3 ~ 0.5).")
        public double anchorWeight = 0.4;
    }

    public static class Ping {
        @Comment("Ping Wheel channel reuse strategy.")
        public PingChannelMode channelMode = PingChannelMode.AUTO;
    }
}
