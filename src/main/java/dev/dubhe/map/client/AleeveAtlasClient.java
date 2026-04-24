package dev.dubhe.map.client;

import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.hud.MinimapHudRenderer;
import dev.dubhe.map.client.input.AtlasClientInputEvents;
import dev.dubhe.map.client.input.AtlasKeyMappings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = AleeveAtlas.MOD_ID, dist = Dist.CLIENT)
public final class AleeveAtlasClient {
    public static final AleeveAtlasClientConfig CONFIG =
        ConfigManager.register(AleeveAtlas.MOD_ID, AleeveAtlasClientConfig::new);

    public AleeveAtlasClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(AtlasKeyMappings::onRegisterKeyMappings);

        NeoForge.EVENT_BUS.addListener(AtlasClientInputEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(AtlasClientInputEvents::onMouseScrolling);
        NeoForge.EVENT_BUS.addListener(MinimapHudRenderer::onRenderGuiPost);
    }
}
