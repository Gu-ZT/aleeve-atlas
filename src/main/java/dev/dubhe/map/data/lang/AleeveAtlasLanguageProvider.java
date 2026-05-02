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

        this.add("key.categories.aleeve_atlas", "Aleeve Atlas");
        this.add("key.aleeve_atlas.open_settings", "Open minimap settings");
        this.add("key.aleeve_atlas.open_waypoints", "Open waypoint manager");
        this.add("key.aleeve_atlas.open_quick_waypoint", "Quick create waypoint at camera");

        this.add("screen.aleeve_atlas.settings", "Aleeve Atlas Settings");
        this.add("screen.aleeve_atlas.settings.hint", "Press M to reopen this screen. Zoom is only configurable from this settings screen.");
        this.add("screen.aleeve_atlas.option.display", "Display: %s");
        this.add("screen.aleeve_atlas.option.shape", "Shape: %s");
        this.add("screen.aleeve_atlas.option.rotation", "Rotation: %s");
        this.add("screen.aleeve_atlas.option.zoom", "Zoom: %s");
        this.add("screen.aleeve_atlas.option.dynamic_lighting", "Dynamic Lighting: %s");
        this.add("screen.aleeve_atlas.option.cave_mapping", "Cave Mapping: %s");
        this.add("screen.aleeve_atlas.option.coordinates", "Coordinates: %s");
        this.add("screen.aleeve_atlas.option.environment", "Environment Info: %s");
        this.add("screen.aleeve_atlas.option.anchor", "Position: %s");
        this.add("screen.aleeve_atlas.option.size", "Size: %s");
        this.add("screen.aleeve_atlas.option.radar", "Radar: %s");
        this.add("screen.aleeve_atlas.option.radar_range", "Radar Range: %s");
        this.add("screen.aleeve_atlas.option.radar_hostile", "Hostiles: %s");
        this.add("screen.aleeve_atlas.option.radar_friendly", "Friendlies: %s");
        this.add("screen.aleeve_atlas.option.radar_items", "Items: %s");
        this.add("screen.aleeve_atlas.option.radar_players", "Players: %s");
        this.add("screen.aleeve_atlas.value.on", "On");
        this.add("screen.aleeve_atlas.value.off", "Off");
        this.add("screen.aleeve_atlas.value.square", "Square");
        this.add("screen.aleeve_atlas.value.circle", "Circle");
        this.add("screen.aleeve_atlas.value.top_left", "Top Left");
        this.add("screen.aleeve_atlas.value.top_right", "Top Right");
        this.add("screen.aleeve_atlas.value.bottom_left", "Bottom Left");
        this.add("screen.aleeve_atlas.value.bottom_right", "Bottom Right");
        this.add("screen.aleeve_atlas.value.small", "Small");
        this.add("screen.aleeve_atlas.value.medium", "Medium");
        this.add("screen.aleeve_atlas.value.large", "Large");

        this.add("screen.aleeve_atlas.waypoints", "Waypoint Manager");
        this.add("screen.aleeve_atlas.waypoint.list", "Waypoint List");
        this.add("screen.aleeve_atlas.waypoint.editor", "Editor");
        this.add("screen.aleeve_atlas.waypoint.name", "Name");
        this.add("screen.aleeve_atlas.waypoint.xyz", "Coordinates X / Y / Z");
        this.add("screen.aleeve_atlas.waypoint.color", "Color (#RRGGBB)");
        this.add("screen.aleeve_atlas.waypoint.previous", "Previous");
        this.add("screen.aleeve_atlas.waypoint.next", "Next");
        this.add("screen.aleeve_atlas.waypoint.use_player", "Use Player Position");
        this.add("screen.aleeve_atlas.waypoint.new", "New");
        this.add("screen.aleeve_atlas.waypoint.save", "Save");
        this.add("screen.aleeve_atlas.waypoint.delete", "Delete");
        this.add("screen.aleeve_atlas.waypoint.set_active", "Set Active");
        this.add("screen.aleeve_atlas.quick_waypoint", "Quick Waypoint");
        this.add("screen.aleeve_atlas.quick_waypoint.save", "Create Waypoint");
        this.add("screen.aleeve_atlas.quick_waypoint.default_name", "Waypoint");
    }
}
