package dev.dubhe.map.client;

import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.init.ModDynamicUniforms;
import dev.dubhe.map.client.waypoint.WaypointManager;
import lombok.Getter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ConfigureMainRenderTargetEvent;

import javax.annotation.Nullable;

@Mod(value = AleeveAtlas.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AleeveAtlasClient {
    public static final AleeveAtlasClientConfig CONFIG = ConfigManager.register(AleeveAtlas.MOD_ID, AleeveAtlasClientConfig::new);
    @Getter
    @Nullable
    private static ModDynamicUniforms modDynamicUniforms;

    public AleeveAtlasClient(IEventBus modEventBus, ModContainer modContainer) {
        WaypointManager.load();
    }


    @SubscribeEvent
    public static void init(ConfigureMainRenderTargetEvent event) {
        AleeveAtlasClient.modDynamicUniforms = new ModDynamicUniforms();
    }
}
