package dev.dubhe.map.client.input;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.AtlasClientState;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

public final class AtlasClientInputEvents {
    private static long lastScrollMs = 0L;

    private AtlasClientInputEvents() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (AtlasKeyMappings.TOGGLE_MINIMAP.consumeClick()) {
            AtlasClientState.toggleMinimapVisible();
            AleeveAtlas.LOGGER.debug("Minimap visible: {}", AtlasClientState.isMinimapVisible());
        }

        while (AtlasKeyMappings.TOGGLE_ROTATION.consumeClick()) {
            AtlasClientState.toggleRotation();
            AleeveAtlas.LOGGER.debug("Rotate with player: {}", AtlasClientState.isRotateWithPlayer());
        }

        while (AtlasKeyMappings.RESET_ZOOM.consumeClick()) {
            AtlasClientState.resetZoom();
            AleeveAtlas.LOGGER.debug("Minimap zoom reset");
        }
    }

    public static void onMouseScrolling(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        if (!minecraft.options.keyShift.isDown()) {
            return;
        }

        if (event.getScrollDeltaY() > 0.0D) {
            AtlasClientState.zoomIn();
        } else if (event.getScrollDeltaY() < 0.0D) {
            AtlasClientState.zoomOut();
        }

        long now = System.currentTimeMillis();
        if (now - lastScrollMs <= 250L) {
            AtlasClientState.resetZoom();
        }
        lastScrollMs = now;

        event.setCanceled(true);
    }
}

