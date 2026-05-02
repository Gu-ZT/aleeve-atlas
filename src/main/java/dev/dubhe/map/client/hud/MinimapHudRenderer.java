package dev.dubhe.map.client.hud;

import dev.dubhe.map.AleeveAtlas;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class MinimapHudRenderer {
    private MinimapHudRenderer() {
    }

    @SubscribeEvent
    public static void onGuiLayersRegister(RegisterGuiLayersEvent event) {
        event.registerAboveAll(AleeveAtlas.of("minimap_background"), new MinimapBackgroudGuiLayer());
        event.registerAbove(AleeveAtlas.of("minimap_background"), AleeveAtlas.of("minimap"), new MinimapGuiLayer());
        event.registerAbove(AleeveAtlas.of("minimap"), AleeveAtlas.of("minimap_entity_marker"), new MinimapEntityMarkerGuiLayer());
        event.registerAbove(AleeveAtlas.of("minimap_entity_marker"), AleeveAtlas.of("minimap_self_marker"), new MinimapSelfMarkerGuiLayer());
        event.registerAbove(AleeveAtlas.of("minimap_self_marker"), AleeveAtlas.of("minimap_text"), new MinimapTextGuiLayer());
    }
}
