package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.ping.ClientPings;
import dev.dubhe.map.ping.MapPingData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 世界内中键短按、长按和轮盘选择的实机验证。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class WorldPingSelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/world-ping-test");
    private static int ticks;
    private static boolean selecting;
    private static boolean selectionAttempted;
    private static boolean selectionVerified;
    private static boolean completed;

    private WorldPingSelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (completed || System.getenv("ALEEVE_ATLAS_WORLD_PING_TEST") == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ticks++;
        if (ticks == 60) {
            ClientPings.clear();
            pressMouse(minecraft, GLFW.GLFW_PRESS);
        } else if (ticks == 63) {
            pressMouse(minecraft, GLFW.GLFW_RELEASE);
        } else if (ticks == 70) {
            require(hasPing(MapPingData.PingIcon.GENERAL), "Short middle click must create GENERAL");
            screenshot(minecraft, "atlas-world-ping.png");
            pressMouse(minecraft, GLFW.GLFW_PRESS);
        } else if (ticks >= 80 && !selectionAttempted) {
            if (minecraft.screen instanceof PingWheelMenu) {
                if (!selecting) {
                    screenshot(minecraft, "atlas-world-ping-wheel.png");
                    selecting = true;
                }
            } else if (ticks >= 120) {
                require(false, "World ping wheel did not open: "
                        + (minecraft.screen == null ? "null" : minecraft.screen.getClass().getName()));
            }
        } else if (selectionAttempted && !selectionVerified) {
            selectionVerified = true;
            require(selectionAttempted, "World ping wheel selection must be attempted");
            boolean dangerCreated = hasPing(MapPingData.PingIcon.DANGER);
            require(minecraft.screen == null, "World ping wheel must close after selection: "
                    + (minecraft.screen == null ? "null" : minecraft.screen.getClass().getName())
                    + ", danger=" + dangerCreated);
            require(dangerCreated,
                    "World ping wheel must create the selected DANGER ping");
            pressMouse(minecraft, GLFW.GLFW_PRESS);
        } else if (ticks == 96) {
            AtlasWorldMap.open();
        } else if (ticks == 102) {
            require(minecraft.screen instanceof WorldMapScreen,
                    "Opening another screen must cancel the world ping hold");
            int pingCount = ClientPings.active().size();
            InputEvent.MouseButton.Pre releaseEvent = new InputEvent.MouseButton.Pre(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, GLFW.GLFW_RELEASE, 0);
            WorldPingController.onMouseInput(releaseEvent);
            require(!releaseEvent.isCanceled(), "Canceled hold must not consume map input");
            require(ClientPings.active().size() == pingCount,
                    "Canceled hold must not create a ping on release");
            minecraft.screen.onClose();
            completed = true;
            LOGGER.info("World ping input, wheel selection and hold cancellation passed");
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Pre event) {
        if (!selecting || !(event.getScreen() instanceof PingWheelMenu wheel)) {
            return;
        }
        selecting = false;
        double x = wheel.width / 2.0;
        double y = wheel.height / 2.0 - Math.min(wheel.width, wheel.height) * 0.25;
        wheel.render(event.getGuiGraphics(), (int) x, (int) y, event.getPartialTick());
        pressMouse(Minecraft.getInstance(), GLFW.GLFW_RELEASE);
        selectionAttempted = true;
    }

    private static void pressMouse(Minecraft minecraft, int action) {
        InputEvent.MouseButton.Pre event = new InputEvent.MouseButton.Pre(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, action, 0);
        WorldPingController.onMouseInput(event);
        require(!event.isCanceled(), "World ping must preserve vanilla middle-click behavior");
    }

    private static boolean hasPing(MapPingData.PingIcon icon) {
        return ClientPings.active().stream().anyMatch(ping -> ping.icon() == icon);
    }

    private static void screenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> LOGGER.info("World ping screenshot: {}", message.getString()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
