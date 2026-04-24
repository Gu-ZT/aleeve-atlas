package dev.dubhe.map.client;

import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.waypoint.WaypointManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = AleeveAtlas.MOD_ID, dist = Dist.CLIENT)
public final class AleeveAtlasClient {
    public static final AleeveAtlasClientConfig CONFIG = ConfigManager.register(AleeveAtlas.MOD_ID, AleeveAtlasClientConfig::new);

    public AleeveAtlasClient(IEventBus modEventBus, ModContainer modContainer) {
        WaypointManager.load();
    }
}
