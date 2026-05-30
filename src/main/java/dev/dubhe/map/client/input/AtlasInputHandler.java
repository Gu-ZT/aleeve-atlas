package dev.dubhe.map.client.input;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.screen.QuickWaypointScreen;
import dev.dubhe.map.client.screen.WaypointListScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasInputHandler {
    private AtlasInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (AtlasKeyMappings.OPEN_SETTINGS.consumeClick()) {
            //noinspection DataFlowIssue
            ModList.get()
                .getModContainerById(AleeveAtlas.MOD_ID)
                .ifPresent(container -> minecraft.setScreen(new ConfigurationScreen(container, minecraft.screen)));
        }
        if (AtlasKeyMappings.WAYPOINT_HOTKEYS_ENABLED) {
            while (AtlasKeyMappings.OPEN_WAYPOINTS.consumeClick()) {
                minecraft.setScreen(new WaypointListScreen(minecraft.screen));
            }
            while (AtlasKeyMappings.OPEN_QUICK_WAYPOINT.consumeClick()) {
                minecraft.setScreen(new QuickWaypointScreen(minecraft.screen));
            }
        }

        // Built-in config screens may leave the cursor ungrabbed on close in-game.
        if (minecraft.screen == null && minecraft.player != null && !minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.grabMouse();
        }
    }
}
