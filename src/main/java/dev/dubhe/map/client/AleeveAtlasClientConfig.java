package dev.dubhe.map.client;

import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import dev.dubhe.map.AleeveAtlas;
import net.neoforged.fml.config.ModConfig;

@Config(name = AleeveAtlas.MOD_ID, type = ModConfig.Type.CLIENT)
public class AleeveAtlasClientConfig {
    @Comment("Minimap's Shape")
    public MapShape mapShape = MapShape.SQUARE;

    @Comment("Should the minimap rotate with the player's perspective")
    public boolean rotation = false;

    @Comment("The scale level of the small map (1=closest, 5=farthest)")
    @BoundedDiscrete(min = 1, max = 5)
    public int zoom = 2;

    @Comment("Show the minimap or not")
    public boolean display = true;

    @Comment("Apply world light level shading to the minimap")
    public boolean dynamicLighting = true;

    @Comment("Render nearby cave surfaces when underground")
    public boolean caveMapping = true;

    @Comment("Show the player's coordinate line under the minimap")
    public boolean showCoordinates = true;

    @Comment("Show biome, dimension, time and light information under the minimap")
    public boolean showEnvironment = false;

    @Comment("Minimap anchor position on the screen")
    public MinimapAnchor minimapAnchor = MinimapAnchor.TOP_RIGHT;

    @Comment("Minimap size preset")
    public MinimapSize minimapSize = MinimapSize.MEDIUM;

    @Comment("Enable the entity radar overlay")
    public boolean radar = true;

    @Comment("Radar scan range preset")
    public RadarRange radarRange = RadarRange.R128;

    @Comment("Show hostile mobs on radar")
    public boolean radarHostile = true;

    @Comment("Show friendly mobs on radar")
    public boolean radarFriendly = true;

    @Comment("Show dropped items on radar")
    public boolean radarItems = true;

    @Comment("Show players on radar")
    public boolean radarPlayers = true;

    public enum MapShape {
        SQUARE,
        CIRCLE
    }

    public enum MinimapAnchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public enum MinimapSize {
        SMALL(76),
        MEDIUM(92),
        LARGE(124);

        private final int pixels;

        MinimapSize(int pixels) {
            this.pixels = pixels;
        }

        public int pixels() {
            return this.pixels;
        }
    }

    public enum RadarRange {
        R64(64),
        R96(96),
        R128(128),
        R192(192),
        R256(256);

        private final int blocks;

        RadarRange(int blocks) {
            this.blocks = blocks;
        }

        public int blocks() {
            return this.blocks;
        }
    }
}
