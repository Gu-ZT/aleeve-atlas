package dev.dubhe.map.data;

import dev.anvilcraft.lib.v2.config.ConfigData;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.config.AtlasClientConfig;
import dev.dubhe.map.config.AtlasServerConfig;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/// 生成 `assets/aleeve_atlas/lang/en_us.json`；`zh_cn.json` 为人工维护的源文件，两者键集必须一致。
public class AleeveAtlasLanguageProvider extends LanguageProvider {
    public AleeveAtlasLanguageProvider(PackOutput output) {
        super(output, AleeveAtlas.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("aleeve_atlas.structure.nearby", "Nearby: %s (%s, %s)");
        add("aleeve_atlas.structure.title", "Structures");
        add("aleeve_atlas.structure.all_types", "All Structure Types");
        add("aleeve_atlas.structure.all_records", "All Known");
        add("aleeve_atlas.structure.favorites_only", "Favorites");
        add("aleeve_atlas.structure.follow", "Follow Type");
        add("aleeve_atlas.structure.unfollow", "Unfollow Type");
        add("aleeve_atlas.structure.search_known", "Search Known");
        add("aleeve_atlas.structure.search_nearest", "Find Nearest");
        add("aleeve_atlas.structure.favorite", "Favorite Selected");
        add("aleeve_atlas.structure.unfavorite", "Remove Favorite");
        add("aleeve_atlas.structure.empty", "No known structures match. Explore nearby chunks or receive records from your team.");
        add("aleeve_atlas.structure.coordinates", "XYZ: %s, %s, %s");
        add("aleeve_atlas.structure.distance", "Horizontal distance: %s m");
        add("aleeve_atlas.structure.locate_tip", "Find the nearest structure from your current position, including in unexplored areas.");
        add("aleeve_atlas.structure.searching", "Searching…");
        add("aleeve_atlas.structure.status.found", "Structure found.");
        add("aleeve_atlas.structure.status.not_found", "No structure found within the vanilla search range.");
        add("aleeve_atlas.structure.status.disabled", "Unexplored search is disabled by the server or unsupported.");
        add("aleeve_atlas.structure.status.cooldown", "Please wait 10 seconds between searches.");
        add("aleeve_atlas.structure.status.busy", "The server is processing another structure search.");
        add("aleeve_atlas.structure.status.failed", "Structure search failed or timed out.");
        add("aleeve_atlas.structure.source.discovered", "Naturally discovered");
        add("aleeve_atlas.structure.source.shared", "Shared by team");
        add("aleeve_atlas.structure.source.searched", "Found by search");
        add("aleeve_atlas.structure.type.village_desert", "Desert Village");
        add("aleeve_atlas.structure.type.village_plains", "Plains Village");
        add("aleeve_atlas.structure.type.village_savanna", "Savanna Village");
        add("aleeve_atlas.structure.type.village_snowy", "Snowy Village");
        add("aleeve_atlas.structure.type.village_taiga", "Taiga Village");
        add("aleeve_atlas.structure.type.mansion", "Woodland Mansion");
        add("aleeve_atlas.structure.type.monument", "Ocean Monument");
        add("aleeve_atlas.structure.type.jungle_pyramid", "Jungle Temple");
        add("aleeve_atlas.structure.type.swamp_hut", "Swamp Hut");
        add("aleeve_atlas.structure.type.trial_chambers", "Trial Chambers");
        add("aleeve_atlas.cartography.import", "Import map");
        add("aleeve_atlas.cartography.banners_imported", "Added %s banner waypoint(s). Existing waypoints were kept.");
        add("aleeve_atlas.cartography.craft", "Create map");
        add("aleeve_atlas.cartography.cancel_selection", "Cancel selection");
        add("aleeve_atlas.cartography.confirm_title", "Create vanilla map");
        add("aleeve_atlas.cartography.lock", "Lock map (glass pane)");
        add("aleeve_atlas.cartography.bounds", "128 × 128 blocks: (%s, %s) – (%s, %s)");
        add("aleeve_atlas.cartography.cost", "Cost: 1 empty map");
        add("aleeve_atlas.cartography.cost_locked", "Cost: 1 empty map + 1 glass pane");
        add("aleeve_atlas.cartography.cost_creative", "Creative mode: no materials consumed");
        add("aleeve_atlas.cartography.selection_help", "Left click: preview · Middle drag: pan · Right click / Esc: cancel");
        add("aleeve_atlas.cartography.submitting", "Creating map…");
        add("aleeve_atlas.cartography.imported", "Map snapshot imported.");
        add("aleeve_atlas.cartography.crafted", "Map created.");
        add("aleeve_atlas.cartography.other_dimension", "Map saved in dimension %s.");
        add("aleeve_atlas.cartography.busy", "Please wait before trying again.");
        add("aleeve_atlas.cartography.changed", "The held item, dimension, or request changed. Please select again.");
        add("aleeve_atlas.cartography.missing", "The server could not find this map.");
        add("aleeve_atlas.cartography.region", "Select a complete vanilla map cell inside the world border.");
        add("aleeve_atlas.cartography.invalid", "Invalid map data.");
        add("aleeve_atlas.cartography.empty", "This area has no known map content.");
        add("aleeve_atlas.cartography.glass_missing", "Requires an ordinary glass pane in your inventory.");
        add("aleeve_atlas.cartography.save_failed", "Could not save the imported map. See the log.");
        add("aleeve_atlas.cartography.reference_height", "(reference height)");
        add("aleeve_atlas.cartography.target.red_x", "Buried treasure");
        add("aleeve_atlas.cartography.target.target_x", "Map target");
        add("aleeve_atlas.cartography.target.target_point", "Map target");
        add("aleeve_atlas.cartography.target.mansion", "Woodland mansion");
        add("aleeve_atlas.cartography.target.monument", "Ocean monument");
        add("aleeve_atlas.cartography.target.village_desert", "Desert village");
        add("aleeve_atlas.cartography.target.village_plains", "Plains village");
        add("aleeve_atlas.cartography.target.village_savanna", "Savanna village");
        add("aleeve_atlas.cartography.target.village_snowy", "Snowy village");
        add("aleeve_atlas.cartography.target.village_taiga", "Taiga village");
        add("aleeve_atlas.cartography.target.jungle_temple", "Jungle temple");
        add("aleeve_atlas.cartography.target.swamp_hut", "Swamp hut");
        add("aleeve_atlas.cartography.target.trial_chambers", "Trial chambers");
        add("key.category.aleeve_atlas.main", "Aleeve Atlas");
        add("key.aleeve_atlas.open_world_map", "Open World Map");
        add("key.aleeve_atlas.new_waypoint", "New Waypoint");
        add("key.aleeve_atlas.minimap_zoom_in", "Zoom Minimap In");
        add("key.aleeve_atlas.minimap_zoom_out", "Zoom Minimap Out");
        add("key.aleeve_atlas.focus_minimap", "Hold to Center Minimap");
        add("key.aleeve_atlas.world_ping", "Ping in World");

        add("aleeve_atlas.message.server_unsupported",
                "Server support component is not installed.");

        add("aleeve_atlas.hud.light", "Light: %s");
        add("aleeve_atlas.hud.coords", "%s, %s, %s");
        add("aleeve_atlas.hud.time", "%s:%s");
        add("aleeve_atlas.hud.biome_unknown", "?");

        add("aleeve_atlas.message.ping_received", "%s sent a ping");
        add("aleeve_atlas.message.waypoint_received", "Waypoint from %2$s accepted: %1$s");
        add("aleeve_atlas.message.waypoint_shared", "%2$s shared waypoint %1$s at %3$s, %4$s, %5$s");
        add("aleeve_atlas.message.waypoint_accept", "[Accept]");
        add("aleeve_atlas.message.waypoint_ignore", "[Ignore]");
        add("aleeve_atlas.waypoint.default_name", "Waypoint");
        add("aleeve_atlas.waypoint.death", "Death Point");
        add("aleeve_atlas.diagnostic.minimap",
                "Minimap: avg %s ms, max %s ms, over-budget %s%%, samples %s");
        add("aleeve_atlas.diagnostic.canvas",
                "World map canvas: avg %s ms, max %s ms, over-budget %s%%, samples %s");
        add("aleeve_atlas.diagnostic.ping",
                "Ping channel: mode %s, external mod %s, atlas broadcast %s");
        add("aleeve_atlas.diagnostic.team.no_team", "Team: provider %s, player has no team");
        add("aleeve_atlas.diagnostic.team", "Team: provider %s, team %s, color #%s");
        add("aleeve_atlas.message.waypoint_created", "Waypoint created at %s, %s, %s");
        add("aleeve_atlas.message.death_point_added", "Death point recorded");

        add("aleeve_atlas.message.waypoint_exported", "Exported waypoints to %s");
        add("aleeve_atlas.message.waypoint_export_failed", "Failed to export waypoints");
        add("aleeve_atlas.message.waypoint_imported",
                "Imported %s new and %s updated waypoint(s)");
        add("aleeve_atlas.message.waypoint_import_failed", "Failed to import waypoints");
        add("aleeve_atlas.message.territory_entered", "Entered %s");
        add("aleeve_atlas.message.territory_left", "Left %s");
        add("aleeve_atlas.message.territory_demo_added", "Demo territory added");
        add("aleeve_atlas.message.territory_demo_cleared", "Demo territory cleared");
        add("aleeve_atlas.territory.demo_name", "Demo Territory");
        add("aleeve_atlas.territory.demo_overlay_name", "Overlay Territory");
        add("aleeve_atlas.message.ping_channel_duplicate",
                "Ping Wheel detected while channel mode is AUTO; depending on its settings, pings may be broadcast twice.");
        add("aleeve_atlas.territory.border.solid", "Solid");
        add("aleeve_atlas.territory.border.dashed", "Dashed");
        add("aleeve_atlas.radar.category.player", "Players");
        add("aleeve_atlas.radar.category.hostile", "Hostile");
        add("aleeve_atlas.radar.category.friendly", "Friendly");
        add("aleeve_atlas.radar.category.ambient", "Ambient");
        add("aleeve_atlas.radar.category.item", "Items");
        add("aleeve_atlas.radar.category.misc", "Misc");
        add("aleeve_atlas.screen.waypoint_list", "Waypoints");
        add("aleeve_atlas.screen.waypoint_list.show", "Show");
        add("aleeve_atlas.screen.waypoint_list.hide", "Hide");
        add("aleeve_atlas.screen.waypoint_list.share", "Share");
        add("aleeve_atlas.screen.waypoint_list.share.tip", "Share this waypoint with your team");
        add("aleeve_atlas.screen.waypoint_list.public", "Public");
        add("aleeve_atlas.screen.waypoint_list.private", "Private");
        add("aleeve_atlas.screen.waypoint_list.delete", "Delete");
        add("aleeve_atlas.screen.waypoint_list.empty", "No waypoints in this dimension");
        add("aleeve_atlas.screen.waypoint_list.death", "[death]");
        add("aleeve_atlas.screen.waypoint_list.scroll_hint", "Scroll or drag the scrollbar");
        add("aleeve_atlas.screen.waypoint_list.add", "New");
        add("aleeve_atlas.screen.waypoint_list.edit", "Edit");
        add("aleeve_atlas.screen.world_map.client_config", "Client Config");
        add("aleeve_atlas.screen.waypoint_edit.create_title", "Create Waypoint");
        add("aleeve_atlas.screen.waypoint_edit.edit_title", "Edit Waypoint");
        add("aleeve_atlas.screen.waypoint_edit.name", "Name");
        add("aleeve_atlas.screen.waypoint_edit.coordinates", "Coordinates (X / Y / Z)");
        add("aleeve_atlas.screen.waypoint_edit.color", "Color");
        add("aleeve_atlas.screen.waypoint_edit.visible_distance", "Visible Distance");
        add("aleeve_atlas.screen.waypoint_edit.enabled", "Enabled");
        add("aleeve_atlas.screen.waypoint_edit.shareable", "Shareable");
        add("aleeve_atlas.screen.waypoint_edit.save", "Save");
        add("aleeve_atlas.screen.waypoint_edit.name_required", "Name cannot be empty");
        add("aleeve_atlas.screen.waypoint_edit.invalid_number",
                "Coordinates and visible distance must be integers");
        add("aleeve_atlas.screen.waypoint_edit.invalid_distance",
                "Visible distance must be -1 or between 0 and %s");
        add("aleeve_atlas.screen.waypoint_edit.invalid_color",
                "Color must use #RRGGBB format");
        add("aleeve_atlas.screen.world_map.share_team", "Share to Team");
        add("aleeve_atlas.screen.world_map.share_team.tip",
                "Share all visible waypoints with your team.");
        add("aleeve_atlas.message.waypoint_none", "No waypoints to share");
        add("aleeve_atlas.message.waypoint_shared_team", "Shared waypoint: %s");
        add("aleeve_atlas.message.waypoints_shared_team", "Shared %s visible waypoint(s) with your team");
        add("aleeve_atlas.message.slime_seed_recorded", "World seed recorded: %s");

        add("aleeve_atlas.screen.world_map.teleport", "Teleport");
        add("aleeve_atlas.screen.world_map.share_coordinates", "Send Coordinates to Chat");

        add("aleeve_atlas.screen.world_map", "World Map");
        add("aleeve_atlas.screen.world_map.hint",
                "Drag to pan, scroll to zoom, ESC or M to close");
        add("aleeve_atlas.screen.world_map.add_waypoint", "Create Waypoint");
        add("aleeve_atlas.screen.world_map.delete_waypoint", "Delete Waypoint");
        add("aleeve_atlas.screen.world_map.location",
                "Block: %s  XYZ: %s, %s, %s  Biome: %s");
        add("aleeve_atlas.screen.world_map.territory", "Territory: %s");
        add("aleeve_atlas.screen.world_map.unknown", "Unknown");
        add("aleeve_atlas.ping.type.general", "General");
        add("aleeve_atlas.ping.type.danger", "Danger");
        add("aleeve_atlas.ping.type.warning", "Warning");
        add("aleeve_atlas.ping.type.goto", "Go To");

        // 配置项翻译键由 AnvilLib 依据配置类反射生成。
        ConfigData.readConfigClass(this, AtlasClientConfig.class);
        ConfigData.readConfigClass(this, AtlasServerConfig.class);
    }
}
