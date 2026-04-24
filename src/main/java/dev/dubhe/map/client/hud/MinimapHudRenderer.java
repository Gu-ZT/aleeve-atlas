package dev.dubhe.map.client.hud;

import dev.dubhe.map.client.AtlasClientState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class MinimapHudRenderer {
    private static final int MAP_SIZE_PX = 92;
    private static final int CELL_COUNT = 23;
    private static final int MARGIN = 8;
    private static final int BORDER_COLOR = 0xCC000000;
    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int PLAYER_COLOR = 0xFFFFCC00;

    private static final Map<Long, CachedColor> COLOR_CACHE = new HashMap<>();

    private MinimapHudRenderer() {
    }

    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!AtlasClientState.isMinimapVisible()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int x0 = graphics.guiWidth() - MAP_SIZE_PX - MARGIN;
        int y0 = MARGIN;
        int x1 = x0 + MAP_SIZE_PX;
        int y1 = y0 + MAP_SIZE_PX;

        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, BORDER_COLOR);
        graphics.fill(x0, y0, x1, y1, BACKGROUND_COLOR);

        renderCells(minecraft, graphics, x0, y0);
        renderPlayerMarker(graphics, x0, y0);
        renderHudInfo(minecraft, graphics, x0, y1 + 4);
    }

    private static void renderCells(Minecraft minecraft, GuiGraphics graphics, int mapX, int mapY) {
        double playerX = minecraft.player.getX();
        double playerZ = minecraft.player.getZ();
        float yawRad = (float) Math.toRadians(minecraft.player.getYRot());

        int cellSize = MAP_SIZE_PX / CELL_COUNT;
        int halfCells = CELL_COUNT / 2;
        int blockStep = AtlasClientState.getBlockStep();
        long gameTime = minecraft.level.getGameTime();

        for (int gz = 0; gz < CELL_COUNT; gz++) {
            for (int gx = 0; gx < CELL_COUNT; gx++) {
                int localX = (gx - halfCells) * blockStep;
                int localZ = (gz - halfCells) * blockStep;

                int sampleX;
                int sampleZ;
                if (AtlasClientState.isRotateWithPlayer()) {
                    double rx = localX * Math.cos(yawRad) - localZ * Math.sin(yawRad);
                    double rz = localX * Math.sin(yawRad) + localZ * Math.cos(yawRad);
                    sampleX = (int) Math.floor(playerX + rx);
                    sampleZ = (int) Math.floor(playerZ + rz);
                } else {
                    sampleX = (int) Math.floor(playerX + localX);
                    sampleZ = (int) Math.floor(playerZ + localZ);
                }

                int color = sampleTopColor(minecraft, sampleX, sampleZ, gameTime);
                int pixelX = mapX + gx * cellSize;
                int pixelY = mapY + gz * cellSize;
                graphics.fill(pixelX, pixelY, pixelX + cellSize, pixelY + cellSize, color);
            }
        }
    }

    private static int sampleTopColor(Minecraft minecraft, int x, int z, long gameTime) {
        long key = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
        CachedColor cached = COLOR_CACHE.get(key);
        if (cached != null && gameTime - cached.sampleTick < 20L) {
            return cached.argb;
        }

        int y = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        y = Math.max(y, minecraft.level.getMinY());
        BlockPos pos = new BlockPos(x, y, z);

        MapColor mapColor = minecraft.level.getBlockState(pos).getMapColor(minecraft.level, pos);
        int rgb = (mapColor == MapColor.NONE) ? biomeFallbackColor(minecraft, pos) : mapColor.col;
        int argb = 0xFF000000 | rgb;

        COLOR_CACHE.put(key, new CachedColor(argb, gameTime));
        if (COLOR_CACHE.size() > 20000) {
            COLOR_CACHE.clear();
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

    private static void renderPlayerMarker(GuiGraphics graphics, int mapX, int mapY) {
        int cellSize = MAP_SIZE_PX / CELL_COUNT;
        int cx = mapX + (CELL_COUNT / 2) * cellSize;
        int cy = mapY + (CELL_COUNT / 2) * cellSize;
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

        graphics.drawString(minecraft.font, Component.literal(String.format("X:%d Y:%d Z:%d", playerPos.getX(), playerPos.getY(), playerPos.getZ())), x, y, 0xFFFFFFFF, true);
        graphics.drawString(minecraft.font, Component.literal("Dir: " + facing + " | Zoom: " + AtlasClientState.getZoomLevel()), x, y + 10, 0xFFFFFFFF, true);
        graphics.drawString(minecraft.font, Component.literal("Biome: " + biomeName), x, y + 20, 0xFFFFFFFF, false);
        graphics.drawString(minecraft.font, Component.literal("Dim: " + dimension + " | Time: " + dayTime), x, y + 30, 0xFFFFFFFF, false);
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
}


