package dev.dubhe.map.client.hud;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.AleeveAtlasClientConfig;
import dev.dubhe.map.client.AtlasClientState;
import dev.dubhe.map.client.radar.AtlasRadar;
import dev.dubhe.map.client.render.state.MapRenderState;
import dev.dubhe.map.client.waypoint.WaypointRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Vector2f;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class MinimapHudRenderer {
    private static final int BASE_CELL_COUNT = 23;
    private static final int MAX_CELL_COUNT = 63;
    private static final int MARGIN = 8;
    private static final int BORDER_COLOR = 0xCC000000;
    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int PLAYER_COLOR = 0xFFFFCC00;
    private static final Map<Long, CachedColor> COLOR_CACHE = new HashMap<>();

    private MinimapHudRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!AtlasClientState.isMinimapVisible()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }

        // Do not render the HUD minimap while any full-screen UI is open,
        // otherwise it can conflict with screen background blur strata.
        if (minecraft.screen != null) {
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int mapSize = AtlasClientState.getMinimapSizePx();
        int x0 = getMinimapLeft(graphics.guiWidth());
        int y0 = getMinimapTop(graphics.guiHeight());
        int x1 = x0 + mapSize;
        int y1 = y0 + mapSize;

        if (AtlasClientState.getMinimapShape() == AleeveAtlasClientConfig.MapShape.CIRCLE) {
            drawCircleFilled(graphics, x0 + mapSize / 2, y0 + mapSize / 2, mapSize / 2, BACKGROUND_COLOR);
            drawCircleOutline(graphics, x0 + mapSize / 2, y0 + mapSize / 2, mapSize / 2, BORDER_COLOR);
        } else {
            graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, BORDER_COLOR);
            graphics.fill(x0, y0, x1, y1, BACKGROUND_COLOR);
        }

        float rotationDeg = getEffectiveRotationDegrees(minecraft);
        renderCells(minecraft, graphics, x0, y0, mapSize, rotationDeg);
        AtlasRadar.render(minecraft, graphics, x0, y0, mapSize, rotationDeg);
        WaypointRenderer.renderMinimap(minecraft, graphics, x0, y0, mapSize, rotationDeg);
        renderPlayerMarker(graphics, x0, y0, mapSize);
        renderHudInfo(minecraft, graphics, x0, y1 + 4);
    }

    public static float getEffectiveRotationDegrees(Minecraft minecraft) {
        float rotationDeg = AtlasClientState.getManualRotationDeg();
        if (AtlasClientState.isRotateWithPlayer() && minecraft.player != null) {
            rotationDeg += minecraft.player.getYRot() + 180.0F;
        }
        return rotationDeg;
    }

    private static void renderCells(
        Minecraft minecraft,
        GuiGraphicsExtractor graphics,
        int mapX,
        int mapY,
        int mapSize,
        float rotationDeg
    ) {
        double playerX = minecraft.player.getX();
        double playerZ = minecraft.player.getZ();
        int playerY = minecraft.player.blockPosition().getY();
        boolean underground = shouldRenderCaves(minecraft);
        float rotationRad = (float) Math.toRadians(rotationDeg);
        double cos = Math.cos(rotationRad);
        double sin = Math.sin(rotationRad);

        int cellCount = getCellCount(mapSize);
        int halfCells = cellCount / 2;
        double blockStep = AtlasClientState.getBlockStep() * (double) BASE_CELL_COUNT / cellCount;
        long gameTime = minecraft.level.getGameTime();
        boolean circleMode = AtlasClientState.getMinimapShape() == AleeveAtlasClientConfig.MapShape.CIRCLE;
        double circleCx = mapX + mapSize / 2.0D;
        double circleCy = mapY + mapSize / 2.0D;
        double circleRadius = mapSize / 2.0D;
        GpuBufferSlice mapUniform = circleMode ? createMapUniform(minecraft, circleCx, circleCy, circleRadius) : null;

        for (int gz = 0; gz < cellCount; gz++) {
            for (int gx = 0; gx < cellCount; gx++) {
                double localX = (gx - halfCells) * blockStep;
                double localZ = (gz - halfCells) * blockStep;
                double rx = localX * cos - localZ * sin;
                double rz = localX * sin + localZ * cos;
                int sampleX = (int) Math.floor(playerX + rx);
                int sampleZ = (int) Math.floor(playerZ + rz);
                int color = underground
                            ? sampleCaveColor(minecraft, sampleX, sampleZ, playerY)
                            : sampleSurfaceColor(minecraft, sampleX, sampleZ, gameTime);

                int pixelX0 = mapX + gx * mapSize / cellCount;
                int pixelY0 = mapY + gz * mapSize / cellCount;
                int pixelX1 = mapX + (gx + 1) * mapSize / cellCount;
                int pixelY1 = mapY + (gz + 1) * mapSize / cellCount;
                if (pixelX1 <= pixelX0 || pixelY1 <= pixelY0) {
                    continue;
                }

                renderCell(graphics, pixelX0, pixelY0, pixelX1, pixelY1, color, circleMode, circleCx, circleCy, circleRadius, mapUniform);
            }
        }
    }

    private static void renderCell(
        GuiGraphicsExtractor graphics,
        int x0,
        int y0,
        int x1,
        int y1,
        int color,
        boolean circleMode,
        double cx,
        double cy,
        double radius,
        GpuBufferSlice mapUniform
    ) {
        if (!circleMode) {
            graphics.fill(x0, y0, x1, y1, color);
            return;
        }

        if (mapUniform != null) {
            pipelineUsage(
                graphics,
                new Vector2f(x0, y0),
                new Vector2f(x1, y1),
                mapUniform,
                color
            );
            return;
        }

        renderCellClipped(graphics, x0, y0, x1, y1, color, true, cx, cy, radius);
    }

    private static GpuBufferSlice createMapUniform(Minecraft minecraft, double circleCx, double circleCy, double circleRadius) {
        double guiScale = minecraft.getWindow().getGuiScale();
        float framebufferCenterX = (float) (circleCx * guiScale);
        float framebufferCenterY = (float) (minecraft.getWindow().getHeight() - circleCy * guiScale);
        float framebufferRadius = (float) (circleRadius * guiScale);
        return MapRenderState.createMapUniform(new Vector2f(framebufferCenterX, framebufferCenterY), framebufferRadius);
    }

    private static int getCellCount(int mapSize) {
        int count = Math.clamp(mapSize / 2, BASE_CELL_COUNT, MAX_CELL_COUNT);
        return (count & 1) == 0 ? count + 1 : count;
    }

    private static void renderCellClipped(
        GuiGraphicsExtractor graphics,
        int x0,
        int y0,
        int x1,
        int y1,
        int color,
        boolean circleMode,
        double cx,
        double cy,
        double radius
    ) {
        if (!circleMode) {
            graphics.fill(x0, y0, x1, y1, color);
            return;
        }

        double innerRadius = Math.max(0.0D, radius - 1.0D);
        double outerRadius = radius + 1.0D;
        double nearDist = distanceToRect(x0, y0, x1, y1, cx, cy);
        if (nearDist >= outerRadius) {
            return;
        }

        double farDist = maxCornerDistance(x0, y0, x1, y1, cx, cy);
        if (farDist <= innerRadius) {
            graphics.fill(x0, y0, x1, y1, color);
            return;
        }

        int baseAlpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) {
                double dx = (px + 0.5D) - cx;
                double dy = (py + 0.5D) - cy;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double coverage = Math.clamp(radius + 0.5D - dist, 0.0D, 1.0D);
                if (coverage <= 0.0D) {
                    continue;
                }
                int a = (int) Math.round(baseAlpha * coverage);
                int argb = (a << 24) | rgb;
                graphics.fill(px, py, px + 1, py + 1, argb);
            }
        }
    }

    private static double distanceToRect(int x0, int y0, int x1, int y1, double px, double py) {
        double clampedX = Math.clamp(px, x0, x1 - 1.0D);
        double clampedY = Math.clamp(py, y0, y1 - 1.0D);
        double dx = px - clampedX;
        double dy = py - clampedY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double maxCornerDistance(int x0, int y0, int x1, int y1, double px, double py) {
        double d0 = cornerDistance(x0, y0, px, py);
        double d1 = cornerDistance(x1 - 1.0D, y0, px, py);
        double d2 = cornerDistance(x0, y1 - 1.0D, px, py);
        double d3 = cornerDistance(x1 - 1.0D, y1 - 1.0D, px, py);
        return Math.max(Math.max(d0, d1), Math.max(d2, d3));
    }

    private static double cornerDistance(double x, double y, double px, double py) {
        double dx = x - px;
        double dy = y - py;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static boolean shouldRenderCaves(Minecraft minecraft) {
        if (!AtlasClientState.isCaveMappingEnabled() || minecraft.player == null || minecraft.level == null) {
            return false;
        }
        BlockPos playerPos = minecraft.player.blockPosition();
        return !minecraft.level.canSeeSky(playerPos.above())
               && minecraft.level.getBrightness(LightLayer.SKY, playerPos.above()) < 8;
    }

    private static int sampleSurfaceColor(Minecraft minecraft, int x, int z, long gameTime) {
        long key = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
        CachedColor cached = COLOR_CACHE.get(key);
        if (cached != null && gameTime - cached.sampleTick < 20L) {
            return cached.argb;
        }

        int y = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        y = Math.max(y, minecraft.level.getMinY());
        BlockPos pos = new BlockPos(x, y, z);
        int argb = resolveMapColor(minecraft, pos);
        COLOR_CACHE.put(key, new CachedColor(argb, gameTime));
        if (COLOR_CACHE.size() > 20000) {
            COLOR_CACHE.clear();
        }
        return argb;
    }

    private static int sampleCaveColor(Minecraft minecraft, int x, int z, int playerY) {
        int minY = minecraft.level.getMinY();
        int maxY = Math.min(minecraft.level.getMaxY() - 1, playerY + 8);
        int floorY = Math.max(minY, playerY - 24);
        for (int y = maxY; y >= floorY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = minecraft.level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (minecraft.level.getBlockState(pos.above()).isAir()) {
                return darken(resolveMapColor(minecraft, pos), 0.72F);
            }
        }
        return darken(sampleSurfaceColor(minecraft, x, z, minecraft.level.getGameTime()), 0.7F);
    }

    private static int resolveMapColor(Minecraft minecraft, BlockPos pos) {
        MapColor mapColor = minecraft.level.getBlockState(pos).getMapColor(minecraft.level, pos);
        int rgb = mapColor == MapColor.NONE ? biomeFallbackColor(minecraft, pos) : mapColor.col;
        int argb = 0xFF000000 | rgb;
        if (AtlasClientState.isDynamicLightingEnabled()) {
            int sky = minecraft.level.getBrightness(LightLayer.SKY, pos.above());
            int block = minecraft.level.getBrightness(LightLayer.BLOCK, pos.above());
            float factor = 0.35F + 0.65F * (Math.max(sky, block) / 15.0F);
            return darken(argb, factor);
        }
        return argb;
    }

    private static int biomeFallbackColor(Minecraft minecraft, BlockPos pos) {
        Holder<Biome> biome = minecraft.level.getBiome(pos);
        float temperature = biome.value().getBaseTemperature();
        if (temperature < 0.15F) {
            return 0xA7D7FF;
        }
        if (temperature < 0.9F) {
            return 0x66B85E;
        }
        return 0xD6C47A;
    }

    private static int darken(int argb, float factor) {
        factor = Math.clamp(factor, 0.0F, 1.0F);
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >>> 16) & 0xFF) * factor);
        int g = (int) (((argb >>> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void renderPlayerMarker(GuiGraphicsExtractor graphics, int mapX, int mapY, int mapSize) {
        int cx = mapX + mapSize / 2;
        int cy = mapY + mapSize / 2;
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, PLAYER_COLOR);
    }

    private static void renderHudInfo(Minecraft minecraft, GuiGraphicsExtractor graphics, int x, int y) {
        BlockPos playerPos = minecraft.player.blockPosition();
        ResourceKey<?> dimensionKey = minecraft.level.dimension();
        String dimension = dimensionKey.identifier().toString();
        Holder<Biome> biome = minecraft.level.getBiome(playerPos);
        String biomeName = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("minecraft:unknown");
        String facing = facingText(minecraft.player.getYRot());
        int dayTime = (int) (minecraft.level.getOverworldClockTime() % 24000L);
        int light = Math.max(
            minecraft.level.getBrightness(LightLayer.SKY, playerPos),
            minecraft.level.getBrightness(LightLayer.BLOCK, playerPos)
        );
        int lineY = y;

        if (AtlasClientState.isCoordinateLineVisible()) {
            graphics.text(
                minecraft.font,
                Component.literal(String.format(Locale.ROOT, "X:%d Y:%d Z:%d", playerPos.getX(), playerPos.getY(), playerPos.getZ())),
                x,
                lineY,
                0xFFFFFFFF,
                true
            );
            lineY += 10;
        }

        if (AtlasClientState.isEnvironmentLineVisible()) {
            graphics.text(
                minecraft.font,
                Component.literal("Dir: " + facing + " | Zoom: " + AtlasClientState.getZoomLevel()),
                x,
                lineY,
                0xFFFFFFFF,
                true
            );
            lineY += 10;
            graphics.text(minecraft.font, Component.literal("Biome: " + biomeName), x, lineY, 0xFFFFFFFF, false);
            lineY += 10;
            graphics.text(
                minecraft.font,
                Component.literal("Dim: " + dimension + " | Time: " + dayTime + " | Light: " + light),
                x,
                lineY,
                0xFFFFFFFF,
                false
            );
            lineY += 10;
            String rotMode = AtlasClientState.isRotateWithPlayer() ? "FOLLOW" : "NORTH_UP";
            String cave = shouldRenderCaves(minecraft) ? "CAVE" : "SURFACE";
            graphics.text(
                minecraft.font,
                Component.literal("Shape: " + AtlasClientState.getMinimapShape() + " | Rot: " + rotMode + " | Radar: " + (
                    AtlasClientState.isRadarEnabled()
                    ? "ON"
                    : "OFF"
                ) + " | " + cave),
                x,
                lineY,
                0xFFFFFFFF,
                false
            );
        }
    }

    private static String facingText(float yaw) {
        float normalized = (yaw % 360.0F + 360.0F) % 360.0F;
        if (normalized >= 45.0F && normalized < 135.0F) {
            return "W";
        }
        if (normalized >= 135.0F && normalized < 225.0F) {
            return "N";
        }
        if (normalized >= 225.0F && normalized < 315.0F) {
            return "E";
        }
        return "S";
    }

    private record CachedColor(int argb, long sampleTick) {
    }

    public static int getMinimapLeft(int guiWidth) {
        int size = AtlasClientState.getMinimapSizePx();
        return switch (AtlasClientState.getMinimapAnchor()) {
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> guiWidth - size - MARGIN;
        };
    }

    public static int getMinimapTop(int guiHeight) {
        int size = AtlasClientState.getMinimapSizePx();
        return switch (AtlasClientState.getMinimapAnchor()) {
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> guiHeight - size - MARGIN - 44;
        };
    }

    public static boolean isPointInsideMinimap(double guiX, double guiY, int guiWidth, int guiHeight) {
        int size = AtlasClientState.getMinimapSizePx();
        int left = getMinimapLeft(guiWidth);
        int top = getMinimapTop(guiHeight);
        int right = left + size;
        int bottom = top + size;
        if (guiX < left || guiX >= right || guiY < top || guiY >= bottom) {
            return false;
        }
        if (AtlasClientState.getMinimapShape() == AleeveAtlasClientConfig.MapShape.SQUARE) {
            return true;
        }
        double cx = left + size / 2.0D;
        double cy = top + size / 2.0D;
        double dx = guiX - cx;
        double dy = guiY - cy;
        double radius = size / 2.0D;
        return dx * dx + dy * dy <= radius * radius;
    }

    private static void drawCircleFilled(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int span = (int) Math.sqrt(radius * radius - y * y);
            graphics.fill(centerX - span, centerY + y, centerX + span + 1, centerY + y + 1, color);
        }
    }

    private static void drawCircleOutline(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int span = (int) Math.sqrt(radius * radius - y * y);
            graphics.fill(centerX - span, centerY + y, centerX - span + 1, centerY + y + 1, color);
            graphics.fill(centerX + span, centerY + y, centerX + span + 1, centerY + y + 1, color);
        }
    }

    private static void pipelineUsage(
        GuiGraphicsExtractor graphics,
        Vector2f start,
        Vector2f end,
        GpuBufferSlice mapUniform,
        int color
    ) {
        graphics.submitGuiElementRenderState(new MapRenderState(
            graphics.pose(),
            start,
            end,
            color,
            mapUniform,
            graphics.peekScissorStack()
        ));
    }
}


