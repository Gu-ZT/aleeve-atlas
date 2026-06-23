package dev.dubhe.map.client.hud;

import dev.dubhe.map.client.AtlasClientState;
import dev.dubhe.map.client.cache.MapCacheLifecycle;
import dev.dubhe.map.client.render.MinimapRenderAccumulator;
import dev.dubhe.map.client.render.state.MinimapPictureInPictureRenderState.CellData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

@SuppressWarnings("unused")
public final class MinimapGuiLayer implements GuiLayer {
    private static final int BASE_CELL_COUNT = 23;
    private static final int MAX_CELL_COUNT = 63;
    private static final Map<Long, CachedColor> COLOR_CACHE = new HashMap<>();

    private static void renderCells(
        Minecraft minecraft, GuiGraphicsExtractor graphics,
        int mapX, int mapY, int mapSize, float rotationDeg, boolean circleMode
    ) {
        if (minecraft.level == null || minecraft.player == null) return;
        MinimapRenderAccumulator.beginIfNeeded(mapX, mapY, mapX + mapSize, mapY + mapSize, graphics.peekScissorStack());

        double playerX = minecraft.player.getX();
        double playerZ = minecraft.player.getZ();
        double anchorX = Math.floor(playerX + 0.5D);
        double anchorZ = Math.floor(playerZ + 0.5D);
        double fracX = playerX - anchorX;
        double fracZ = playerZ - anchorZ;
        int playerY = minecraft.player.blockPosition().getY();
        boolean underground = shouldRenderCaves(minecraft);
        float rotationRad = (float) Math.toRadians(rotationDeg);
        double cos = Math.cos(rotationRad);
        double sin = Math.sin(rotationRad);

        int cellCount = getCellCount(mapSize);
        double blockStep = AtlasClientState.getBlockStep() * (double) BASE_CELL_COUNT / cellCount;
        int northStep = Math.max(1, (int) Math.round(blockStep));
        double cellSize = mapSize / (double) cellCount;
        double pixelsPerBlock = cellSize / blockStep;
        double localOffsetX = -fracX * pixelsPerBlock;
        double localOffsetY = -fracZ * pixelsPerBlock;
        double mapHalfSize = mapSize / 2.0D;
        long gameTime = minecraft.level.getGameTime();
        double centerX = mapX + mapSize / 2.0D;
        double centerY = mapY + mapSize / 2.0D;

        List<CellData> cells = new ArrayList<>();

        boolean expandForDiagonal = !circleMode && AtlasClientState.isRotateWithPlayer();
        int loopExtra = expandForDiagonal ? (int) Math.ceil(1.2 * cellCount * (Math.sqrt(2.0) - 1.0) / 2.0) : 0;
        double expandedRadiusSq = 2.0D * mapHalfSize * mapHalfSize * 1.2D;

        for (int gz = -loopExtra; gz < cellCount + loopExtra; gz++) {
            for (int gx = -loopExtra; gx < cellCount + loopExtra; gx++) {
                if (expandForDiagonal) {
                    double cellCenterLocalX = (gx + 0.5D - cellCount / 2.0D) * cellSize;
                    double cellCenterLocalZ = (gz + 0.5D - cellCount / 2.0D) * cellSize;
                    if (cellCenterLocalX * cellCenterLocalX + cellCenterLocalZ * cellCenterLocalZ > expandedRadiusSq) {
                        continue;
                    }
                }
                double sampleLocalX = (gx + 0.5D - cellCount / 2.0D) * blockStep;
                double sampleLocalZ = (gz + 0.5D - cellCount / 2.0D) * blockStep;
                int sampleX = quantizeSampleCoord(anchorX + sampleLocalX);
                int sampleZ = quantizeSampleCoord(anchorZ + sampleLocalZ);
                TileSample surfaceSample = sampleSurfaceTile(minecraft, sampleX, sampleZ, gameTime);
                TileSample northSurfaceSample = sampleSurfaceTile(minecraft, sampleX, sampleZ - northStep, gameTime);
                if (northSurfaceSample == null || surfaceSample == null) continue;

                int baseColor = underground
                                ? sampleCaveColor(minecraft, sampleX, sampleZ, playerY)
                                : surfaceSample.argb();
                int color = applyNorthShade(baseColor, surfaceSample.height(), northSurfaceSample.height());

                double localLeft = -mapHalfSize + gx * cellSize + localOffsetX;
                double localTop = -mapHalfSize + gz * cellSize + localOffsetY;
                double localRight = localLeft + cellSize;
                double localBottom = localTop + cellSize;

                Vector2f p0 = rotateLocalPoint(centerX, centerY, localLeft, localTop, cos, sin);
                Vector2f p1 = rotateLocalPoint(centerX, centerY, localRight, localTop, cos, sin);
                Vector2f p2 = rotateLocalPoint(centerX, centerY, localRight, localBottom, cos, sin);
                Vector2f p3 = rotateLocalPoint(centerX, centerY, localLeft, localBottom, cos, sin);

                cells.add(new CellData(p0, p1, p2, p3, color));
            }
        }

        if (!cells.isEmpty()) {
            for (CellData cell : cells) {
                MinimapRenderAccumulator.addCell(cell);
            }
        }
    }

    private static int getCellCount(int mapSize) {
        int count = Math.clamp(mapSize / 2, BASE_CELL_COUNT, MAX_CELL_COUNT);
        return (count & 1) == 0 ? count + 1 : count;
    }

    private static int quantizeSampleCoord(double coord) {
        return (int) Math.floor(coord + 0.5D);
    }

    private static Vector2f rotateLocalPoint(
        double centerX, double centerY, double localX, double localY,
        double cos, double sin
    ) {
        float x = (float) (centerX + localX * cos - localY * sin);
        float y = (float) (centerY + localX * sin + localY * cos);
        return new Vector2f(x, y);
    }

    static boolean shouldRenderCaves(Minecraft minecraft) {
        if (!AtlasClientState.isCaveMappingEnabled() || minecraft.player == null || minecraft.level == null) return false;
        BlockPos playerPos = minecraft.player.blockPosition();
        return !minecraft.level.canSeeSky(playerPos.above())
               && minecraft.level.getBrightness(LightLayer.SKY, playerPos.above()) < 8;
    }

    private static @Nullable TileSample sampleSurfaceTile(Minecraft minecraft, int x, int z, long gameTime) {
        if (minecraft.level == null) return null;
        if (minecraft.level instanceof ClientLevel clientLevel) {
            MapCacheLifecycle.SurfaceSample sample = MapCacheLifecycle.getSurfaceSample(clientLevel, x, z);
            if (sample != null) return new TileSample(0xFF000000 | (sample.color() & 0x00FFFFFF), sample.height());
        }
        long key = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
        CachedColor cached = COLOR_CACHE.get(key);
        if (cached != null && gameTime - cached.sampleTick < 20L) {
            return new TileSample(cached.argb, cached.height);
        }

        int y = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        y = Math.max(y, minecraft.level.getMinY());
        BlockPos pos = new BlockPos(x, y, z);
        int argb = resolveMapColor(minecraft, pos);
        COLOR_CACHE.put(key, new CachedColor(argb, y, gameTime));
        if (COLOR_CACHE.size() > 20000) COLOR_CACHE.clear();
        return new TileSample(argb, y);
    }

    private static int sampleSurfaceColor(Minecraft minecraft, int x, int z, long gameTime) {
        TileSample tileSample = sampleSurfaceTile(minecraft, x, z, gameTime);
        return tileSample == null ? 0 : tileSample.argb();
    }

    private static int sampleCaveColor(Minecraft minecraft, int x, int z, int playerY) {
        if (minecraft.level == null) return 0;
        int minY = minecraft.level.getMinY();
        int maxY = Math.min(minecraft.level.getMaxY() - 1, playerY + 8);
        int floorY = Math.max(minY, playerY - 24);
        for (int y = maxY; y >= floorY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = minecraft.level.getBlockState(pos);
            if (state.isAir()) continue;
            if (minecraft.level.getBlockState(pos.above()).isAir()) {
                return darken(resolveMapColor(minecraft, pos), 0.72F);
            }
        }
        return darken(sampleSurfaceColor(minecraft, x, z, minecraft.level.getGameTime()), 0.7F);
    }

    private static int resolveMapColor(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) return 0;
        MapColor mapColor = minecraft.level.getBlockState(pos).getMapColor(minecraft.level, pos);
        int rgb = mapColor == MapColor.NONE ? biomeFallbackColor(minecraft, pos) : mapColor.col;
        int argb = 0xFF000000 | rgb;
        if (AtlasClientState.isDynamicLightingEnabled()) {
            int sky = minecraft.level.getBrightness(LightLayer.SKY, pos.above());
            int block = minecraft.level.getBrightness(LightLayer.BLOCK, pos.above());
            return darken(argb, 0.35F + 0.65F * (Math.max(sky, block) / 15.0F));
        }
        return argb;
    }

    private static int biomeFallbackColor(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) return 0;
        Holder<Biome> biome = minecraft.level.getBiome(pos);
        float temperature = biome.value().getBaseTemperature();
        if (temperature < 0.15F) return 0xA7D7FF;
        if (temperature < 0.9F) return 0x66B85E;
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

    private static int applyNorthShade(int argb, int y1, int y2) {
        int m = y1 < y2 ? 180 : (y1 > y2 ? 255 : 220);
        int a = (argb >>> 24) & 0xFF;
        int r = ((argb >>> 16) & 0xFF) * m / 255;
        int g = ((argb >>> 8) & 0xFF) * m / 255;
        int b = (argb & 0xFF) * m / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        MinimapHudSupport.MinimapContext context = MinimapHudSupport.captureContext(graphics);
        if (context == null) {
            return;
        }

        renderCells(
            context.minecraft(), graphics, context.mapX(), context.mapY(),
            context.mapSize(), context.rotationDeg(), context.circleMode()
        );
        // Flush PIP state here so the PIP texture quad renders below markers (z-order)
        MinimapRenderAccumulator.flush(graphics);
    }

    private record TileSample(int argb, int height) {
    }

    private record CachedColor(int argb, int height, long sampleTick) {
    }
}
