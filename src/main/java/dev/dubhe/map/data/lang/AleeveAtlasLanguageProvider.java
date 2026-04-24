package dev.dubhe.map.data.lang;

import dev.anvilcraft.lib.v2.config.ConfigData;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.AleeveAtlasClientConfig;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class AleeveAtlasLanguageProvider extends LanguageProvider {
    public AleeveAtlasLanguageProvider(PackOutput output) {
        super(output, AleeveAtlas.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        ConfigData.readConfigClass(this, AleeveAtlasClientConfig.class);

        add("key.categories.aleeve_atlas", "Aleeve Atlas");
        add("key.aleeve_atlas.open_settings", "Open minimap settings");
        add("key.aleeve_atlas.open_waypoints", "Open waypoint manager");
        add("key.aleeve_atlas.open_quick_waypoint", "Quick create waypoint at camera");

        add("screen.aleeve_atlas.settings", "Aleeve Atlas Settings");
        add("screen.aleeve_atlas.settings.hint", "Press M to reopen this screen. Zoom is only configurable from this settings screen.");
        add("screen.aleeve_atlas.option.display", "Display: %s");
        add("screen.aleeve_atlas.option.shape", "Shape: %s");
        add("screen.aleeve_atlas.option.rotation", "Rotation: %s");
        add("screen.aleeve_atlas.option.zoom", "Zoom: %s");
        add("screen.aleeve_atlas.option.dynamic_lighting", "Dynamic Lighting: %s");
        add("screen.aleeve_atlas.option.cave_mapping", "Cave Mapping: %s");
        add("screen.aleeve_atlas.option.coordinates", "Coordinates: %s");
        add("screen.aleeve_atlas.option.environment", "Environment Info: %s");
        add("screen.aleeve_atlas.option.anchor", "Position: %s");
        add("screen.aleeve_atlas.option.size", "Size: %s");
        add("screen.aleeve_atlas.option.radar", "Radar: %s");
        add("screen.aleeve_atlas.option.radar_range", "Radar Range: %s");
        add("screen.aleeve_atlas.option.radar_hostile", "Hostiles: %s");
        add("screen.aleeve_atlas.option.radar_friendly", "Friendlies: %s");
        add("screen.aleeve_atlas.option.radar_items", "Items: %s");
        add("screen.aleeve_atlas.option.radar_players", "Players: %s");
        add("screen.aleeve_atlas.value.on", "On");
        add("screen.aleeve_atlas.value.off", "Off");
        add("screen.aleeve_atlas.value.square", "Square");
        add("screen.aleeve_atlas.value.circle", "Circle");
        add("screen.aleeve_atlas.value.top_left", "Top Left");
        add("screen.aleeve_atlas.value.top_right", "Top Right");
        add("screen.aleeve_atlas.value.bottom_left", "Bottom Left");
        add("screen.aleeve_atlas.value.bottom_right", "Bottom Right");
        add("screen.aleeve_atlas.value.small", "Small");
        add("screen.aleeve_atlas.value.medium", "Medium");
        add("screen.aleeve_atlas.value.large", "Large");

        add("screen.aleeve_atlas.waypoints", "Waypoint Manager");
        add("screen.aleeve_atlas.waypoint.list", "Waypoint List");
        add("screen.aleeve_atlas.waypoint.editor", "Editor");
        add("screen.aleeve_atlas.waypoint.name", "Name");
        add("screen.aleeve_atlas.waypoint.xyz", "Coordinates X / Y / Z");
        add("screen.aleeve_atlas.waypoint.color", "Color (#RRGGBB)");
        add("screen.aleeve_atlas.waypoint.previous", "Previous");
        add("screen.aleeve_atlas.waypoint.next", "Next");
        add("screen.aleeve_atlas.waypoint.use_player", "Use Player Position");
        add("screen.aleeve_atlas.waypoint.new", "New");
        add("screen.aleeve_atlas.waypoint.save", "Save");
        add("screen.aleeve_atlas.waypoint.delete", "Delete");
        add("screen.aleeve_atlas.waypoint.set_active", "Set Active");
        add("screen.aleeve_atlas.quick_waypoint", "Quick Waypoint");
        add("screen.aleeve_atlas.quick_waypoint.save", "Create Waypoint");
        add("screen.aleeve_atlas.quick_waypoint.default_name", "Waypoint");
    }
}
