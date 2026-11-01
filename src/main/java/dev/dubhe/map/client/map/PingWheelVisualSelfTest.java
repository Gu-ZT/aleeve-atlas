package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 独立捕获轮盘画面，正常游玩不触发。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class PingWheelVisualSelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/wheel-visual-test");
    private static int ticks;
    private static boolean completed;

    private PingWheelVisualSelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (completed || System.getenv("ALEEVE_ATLAS_WHEEL_VISUAL_TEST") == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ticks++;
        if (ticks == 60) {
            AtlasWorldMap.open();
        } else if (ticks == 90) {
            if (minecraft.gui.screen() instanceof WorldMapScreen map) {
                PingWheelMenu.open(map);
            }
        } else if (ticks == 120) {
            Screenshot.grab(minecraft.gameDirectory, "atlas-wheel.png",
                    minecraft.gameRenderer.mainRenderTarget(), 1,
                    message -> LOGGER.info("Wheel screenshot: {}", message.getString()));
            if (minecraft.gui.screen() != null) {
                minecraft.gui.screen().onClose();
            }
            completed = true;
            LOGGER.info("Wheel visual verification passed");
        }
    }
}
