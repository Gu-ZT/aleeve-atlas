package dev.dubhe.map.client;

import dev.dubhe.map.client.hud.MinimapHudRenderer;
import dev.dubhe.map.client.input.AtlasClientInputEvents;
import dev.dubhe.map.client.input.AtlasKeyMappings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

public final class AtlasClientBootstrap {
    private AtlasClientBootstrap() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(AtlasKeyMappings::onRegisterKeyMappings);

        NeoForge.EVENT_BUS.addListener(AtlasClientInputEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(AtlasClientInputEvents::onMouseScrolling);
        NeoForge.EVENT_BUS.addListener(MinimapHudRenderer::onRenderGuiPost);
    }
}

