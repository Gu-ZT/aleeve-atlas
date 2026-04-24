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

    public enum MapShape {
        SQUARE,
        CIRCLE
    }
}
