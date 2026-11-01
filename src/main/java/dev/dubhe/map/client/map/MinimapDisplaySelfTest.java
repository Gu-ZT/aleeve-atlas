package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.api.ping.MinimapPingAPI;
import dev.dubhe.map.client.AtlasKeyMappings;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.CompassPosition;
import dev.dubhe.map.config.MapColorScheme;
import dev.dubhe.map.config.MinimapShape;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 小地图显示与世界内标记的实机验证，正常游玩不触发。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class MinimapDisplaySelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/minimap-display-test");
    private static int ticks;
    private static boolean completed;
    private static float previousYaw;
    private static float previousPitch;
    private static double previousMinimapScale;
    private static double previousCompassSize;
    private static CompassPosition previousCompassPosition;
    private static boolean previousSmoothZoom;
    private static boolean previousMinimapGrid;
    private static boolean previousWorldMapGrid;

    private MinimapDisplaySelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (completed || System.getenv("ALEEVE_ATLAS_MINIMAP_DISPLAY_TEST") == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ticks++;
        switch (ticks) {
            case 60 -> prepare(minecraft, player);
            case 65 -> AtlasClientConfigs.CONFIG.minimap.shape = MinimapShape.SQUARE;
            case 70 -> {
                screenshot(minecraft, "atlas-minimap-square.png");
                AtlasClientConfigs.CONFIG.minimap.shape = MinimapShape.CIRCLE;
            }
            case 90 -> KeyMapping.click(AtlasKeyMappings.MINIMAP_ZOOM_IN.getDefaultKey());
            case 110 -> verifyZoom(minecraft);
            case 120 -> AtlasKeyMappings.FOCUS_MINIMAP.setDown(true);
            case 140 -> verifyFocused(minecraft);
            case 160 -> {
                AtlasClientConfigs.CONFIG.worldMap.compassPosition = CompassPosition.TOP_LEFT;
                AtlasClientConfigs.CONFIG.worldMap.compassSize = 192.0;
                AtlasWorldMap.open();
            }
            case 170 -> screenshot(minecraft, "atlas-world-compass-large-top-left.png");
            case 175 -> AtlasClientConfigs.CONFIG.worldMap.compassPosition = CompassPosition.BOTTOM_RIGHT;
            case 180 -> {
                screenshot(minecraft, "atlas-world-compass.png");
                AtlasClientConfigs.CONFIG.worldMap.smoothZoom = false;
                zoomOut(minecraft, 12);
            }
            case 195 -> {
                screenshot(minecraft, "atlas-world-zoomed-out.png");
                zoomOut(minecraft, 20);
            }
            case 210 -> {
                screenshot(minecraft, "atlas-world-minimum-zoom.png");
                minecraft.gui.setScreen(null);
                lookNorth(player);
            }
            case 230 -> finish(minecraft, player);
            default -> {
            }
        }
    }

    private static void prepare(Minecraft minecraft, LocalPlayer player) {
        previousYaw = player.getYRot();
        previousPitch = player.getXRot();
        previousMinimapScale = AtlasClientConfigs.CONFIG.minimap.scale;
        previousCompassSize = AtlasClientConfigs.CONFIG.worldMap.compassSize;
        previousCompassPosition = AtlasClientConfigs.CONFIG.worldMap.compassPosition;
        previousSmoothZoom = AtlasClientConfigs.CONFIG.worldMap.smoothZoom;
        previousMinimapGrid = AtlasClientConfigs.CONFIG.minimap.showChunkGrid;
        previousWorldMapGrid = AtlasClientConfigs.CONFIG.worldMap.showChunkGrid;
        AtlasClientConfigs.CONFIG.minimap.showChunkGrid = true;
        AtlasClientConfigs.CONFIG.worldMap.showChunkGrid = true;
        AtlasClientConfigs.CONFIG.minimap.scale = 1.25;
        setRotation(player, 65.0F, 0.0F);
        AtlasClientConfigs.CONFIG.minimap.lockNorth = false;
        AtlasClientConfigs.CONFIG.minimap.shape = MinimapShape.CIRCLE;
        AtlasClientConfigs.CONFIG.minimap.colorScheme = MapColorScheme.VANILLA;
        AtlasClientConfigs.CONFIG.minimap.zoom = MinimapLayout.DEFAULT_ZOOM;
        MinimapPingAPI.setWorldPingsEnabled(false);
        require(!MinimapPingAPI.isWorldPingsEnabled(), "World ping API disable");
        MinimapPingAPI.setWorldPingsEnabled(true);
        require(MinimapPingAPI.isWorldPingsEnabled(), "World ping API enable");
        screenshot(minecraft, "atlas-minimap-rotating.png");
    }

    private static void verifyZoom(Minecraft minecraft) {
        require(AtlasClientConfigs.CONFIG.minimap.zoom > MinimapLayout.DEFAULT_ZOOM,
                "Zoom key must increase world view zoom");
        screenshot(minecraft, "atlas-minimap-zoomed.png");
    }

    private static void verifyFocused(Minecraft minecraft) {
        MinimapLayout layout = MinimapLayout.of(AtlasClientConfigs.CONFIG, minecraft);
        require(layout != null, "Focused minimap layout");
        int expectedSize = Math.min(minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight());
        require(layout.size() == expectedSize, "Focused minimap must fill the screen short side");
        screenshot(minecraft, "atlas-minimap-focused.png");
        AtlasKeyMappings.FOCUS_MINIMAP.setDown(false);
    }

    private static void lookNorth(LocalPlayer player) {
        Vec3 target = new Vec3(player.getX() + 0.5, player.getY() + 1.2, player.getZ() - 19.5);
        Vec3 delta = target.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        setRotation(player, yaw, pitch);
    }

    private static void finish(Minecraft minecraft, LocalPlayer player) {
        screenshot(minecraft, "atlas-world-waypoint-look.png");
        setRotation(player, previousYaw, previousPitch);
        AtlasClientConfigs.CONFIG.minimap.zoom = MinimapLayout.DEFAULT_ZOOM;
        AtlasClientConfigs.CONFIG.minimap.scale = previousMinimapScale;
        AtlasClientConfigs.CONFIG.worldMap.compassSize = previousCompassSize;
        AtlasClientConfigs.CONFIG.worldMap.compassPosition = previousCompassPosition;
        AtlasClientConfigs.CONFIG.worldMap.smoothZoom = previousSmoothZoom;
        AtlasClientConfigs.CONFIG.minimap.showChunkGrid = previousMinimapGrid;
        AtlasClientConfigs.CONFIG.worldMap.showChunkGrid = previousWorldMapGrid;
        completed = true;
        LOGGER.info("Minimap display and world marker verification passed");
    }

    private static void setRotation(LocalPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    private static void zoomOut(Minecraft minecraft, int steps) {
        WorldMapScreen screen = (WorldMapScreen) minecraft.gui.screen();
        for (int step = 0; step < steps; step++) {
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1);
        }
    }

    private static void screenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.gameRenderer.mainRenderTarget(), 1,
                message -> LOGGER.info("Display screenshot: {}", message.getString()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
