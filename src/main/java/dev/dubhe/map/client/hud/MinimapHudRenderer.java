package dev.dubhe.map.client.hud;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.AleeveAtlasClientConfig;
import dev.dubhe.map.client.AtlasClientState;
import dev.dubhe.map.client.radar.AtlasRadar;
import dev.dubhe.map.client.waypoint.WaypointRenderer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class MinimapHudRenderer {
    private static final int CELL_COUNT = 23;
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

        GuiGraphics graphics = event.getGuiGraphics();
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

    private static void renderCells(Minecraft minecraft, GuiGraphics graphics, int mapX, int mapY, int mapSize, float rotationDeg) {
        double playerX = minecraft.player.getX();
        double playerZ = minecraft.player.getZ();
        int playerY = minecraft.player.blockPosition().getY();
        boolean underground = shouldRenderCaves(minecraft);
        float rotationRad = (float) Math.toRadians(rotationDeg);
        double cos = Math.cos(rotationRad);
        double sin = Math.sin(rotationRad);

        int cellSize = Math.max(1, mapSize / CELL_COUNT);
        int halfCells = CELL_COUNT / 2;
        int blockStep = AtlasClientState.getBlockStep();
        long gameTime = minecraft.level.getGameTime();
        double circleRadiusCells = CELL_COUNT / 2.0D;

        for (int gz = 0; gz < CELL_COUNT; gz++) {
            for (int gx = 0; gx < CELL_COUNT; gx++) {
                if (AtlasClientState.getMinimapShape() == AleeveAtlasClientConfig.MapShape.CIRCLE) {
                    double dx = (gx + 0.5D) - circleRadiusCells;
                    double dz = (gz + 0.5D) - circleRadiusCells;
                    if (dx * dx + dz * dz > circleRadiusCells * circleRadiusCells) {
                        continue;
                    }
                }

                int localX = (gx - halfCells) * blockStep;
                int localZ = (gz - halfCells) * blockStep;
                double rx = localX * cos - localZ * sin;
                double rz = localX * sin + localZ * cos;
                int sampleX = (int) Math.floor(playerX + rx);
                int sampleZ = (int) Math.floor(playerZ + rz);
                int color = underground
                    ? sampleCaveColor(minecraft, sampleX, sampleZ, playerY)
                    : sampleSurfaceColor(minecraft, sampleX, sampleZ, gameTime);

                int pixelX = mapX + gx * cellSize;
                int pixelY = mapY + gz * cellSize;
                graphics.fill(pixelX, pixelY, Math.min(mapX + mapSize, pixelX + cellSize), Math.min(mapY + mapSize, pixelY + cellSize), color);
            }
        }
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

    private static void renderPlayerMarker(GuiGraphics graphics, int mapX, int mapY, int mapSize) {
        int cx = mapX + mapSize / 2;
        int cy = mapY + mapSize / 2;
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, PLAYER_COLOR);
    }

    private static void renderHudInfo(Minecraft minecraft, GuiGraphics graphics, int x, int y) {
        BlockPos playerPos = minecraft.player.blockPosition();
        ResourceKey<?> dimensionKey = minecraft.level.dimension();
        String dimension = dimensionKey.location().toString();
        Holder<Biome> biome = minecraft.level.getBiome(playerPos);
        String biomeName = biome.unwrapKey().map(key -> key.location().toString()).orElse("minecraft:unknown");
        String facing = facingText(minecraft.player.getYRot());
        int dayTime = (int) (minecraft.level.getDayTime() % 24000L);
        int light = Math.max(minecraft.level.getBrightness(LightLayer.SKY, playerPos), minecraft.level.getBrightness(LightLayer.BLOCK, playerPos));
        int lineY = y;

        if (AtlasClientState.isCoordinateLineVisible()) {
            graphics.drawString(
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
            graphics.drawString(minecraft.font, Component.literal("Dir: " + facing + " | Zoom: " + AtlasClientState.getZoomLevel()), x, lineY, 0xFFFFFFFF, true);
            lineY += 10;
            graphics.drawString(minecraft.font, Component.literal("Biome: " + biomeName), x, lineY, 0xFFFFFFFF, false);
            lineY += 10;
            graphics.drawString(minecraft.font, Component.literal("Dim: " + dimension + " | Time: " + dayTime + " | Light: " + light), x, lineY, 0xFFFFFFFF, false);
            lineY += 10;
            String rotMode = AtlasClientState.isRotateWithPlayer() ? "FOLLOW" : "NORTH_UP";
            String cave = shouldRenderCaves(minecraft) ? "CAVE" : "SURFACE";
            graphics.drawString(
                minecraft.font,
                Component.literal("Shape: " + AtlasClientState.getMinimapShape() + " | Rot: " + rotMode + " | Radar: " + (AtlasClientState.isRadarEnabled() ? "ON" : "OFF") + " | " + cave),
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

    private static void drawCircleFilled(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int span = (int) Math.sqrt(radius * radius - y * y);
            graphics.fill(centerX - span, centerY + y, centerX + span + 1, centerY + y + 1, color);
        }
    }

    private static void drawCircleOutline(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int span = (int) Math.sqrt(radius * radius - y * y);
            graphics.fill(centerX - span, centerY + y, centerX - span + 1, centerY + y + 1, color);
            graphics.fill(centerX + span, centerY + y, centerX + span + 1, centerY + y + 1, color);
        }
    }
}


