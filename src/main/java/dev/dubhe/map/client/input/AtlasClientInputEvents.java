package dev.dubhe.map.client.input;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.AtlasClientState;
import dev.dubhe.map.client.hud.MinimapHudRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

public final class AtlasClientInputEvents {
    private static final long DOUBLE_SCROLL_WINDOW_MS = 220L;
    private static long lastScrollMs = -1L;
    private static int lastScrollDirection = 0;

    private static boolean wasRightDown = false;
    private static boolean isRotatingDrag = false;
    private static double lastMouseGuiX = 0.0D;

    private AtlasClientInputEvents() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        while (AtlasKeyMappings.TOGGLE_MINIMAP.consumeClick()) {
            AtlasClientState.toggleMinimapVisible();
            AleeveAtlas.LOGGER.debug("Minimap visible: {}", AtlasClientState.isMinimapVisible());
        }

        while (AtlasKeyMappings.TOGGLE_ROTATION.consumeClick()) {
            AtlasClientState.toggleRotation();
            AleeveAtlas.LOGGER.debug("Rotation mode: {}", AtlasClientState.getRotationMode());
        }

        while (AtlasKeyMappings.TOGGLE_SHAPE.consumeClick()) {
            AtlasClientState.toggleMinimapShape();
            AleeveAtlas.LOGGER.debug("Minimap shape: {}", AtlasClientState.getMinimapShape());
        }

        while (AtlasKeyMappings.RESET_ZOOM.consumeClick()) {
            AtlasClientState.resetZoom();
            AleeveAtlas.LOGGER.debug("Minimap zoom reset");
        }

        handleRightDragRotation(minecraft);
    }

    public static void onMouseScrolling(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }

        if (!minecraft.options.keyShift.isDown()) {
            return;
        }

        int direction = (event.getScrollDeltaY() > 0.0D) ? 1 : (event.getScrollDeltaY() < 0.0D ? -1 : 0);
        if (direction == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean isDoubleScroll = lastScrollDirection == direction && lastScrollMs > 0L && now - lastScrollMs <= DOUBLE_SCROLL_WINDOW_MS;
        if (isDoubleScroll) {
            AtlasClientState.resetZoom();
            AleeveAtlas.LOGGER.debug("Double scroll detected, zoom reset");
            lastScrollMs = -1L;
            lastScrollDirection = 0;
        } else {
            if (direction > 0) {
                AtlasClientState.zoomIn();
            } else {
                AtlasClientState.zoomOut();
            }
            lastScrollMs = now;
            lastScrollDirection = direction;
        }

        event.setCanceled(true);
    }

    private static void handleRightDragRotation(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || !AtlasClientState.isMinimapVisible()) {
            wasRightDown = false;
            isRotatingDrag = false;
            return;
        }

        long windowHandle = minecraft.getWindow().getWindow();
        boolean rightDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        double guiX = toGuiX(minecraft);
        double guiY = toGuiY(minecraft);

        if (rightDown && !wasRightDown) {
            boolean isInsideMinimap = MinimapHudRenderer.isPointInsideMinimap(guiX, guiY, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
            isRotatingDrag = isInsideMinimap && !AtlasClientState.isRotateWithPlayer();
            lastMouseGuiX = guiX;
        } else if (rightDown && isRotatingDrag) {
            double deltaX = guiX - lastMouseGuiX;
            if (Math.abs(deltaX) >= 0.01D) {
                AtlasClientState.addManualRotationDeg((float) (deltaX * 0.7F));
                lastMouseGuiX = guiX;
            }
        } else if (!rightDown) {
            isRotatingDrag = false;
        }

        wasRightDown = rightDown;
    }

    private static double toGuiX(Minecraft minecraft) {
        return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    private static double toGuiY(Minecraft minecraft) {
        return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
    }
}

