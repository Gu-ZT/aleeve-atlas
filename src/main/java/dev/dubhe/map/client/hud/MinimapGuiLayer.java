package dev.dubhe.map.client.hud;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.dubhe.map.client.AleeveAtlasClientConfig;
import dev.dubhe.map.client.AtlasClientState;
import dev.dubhe.map.client.cache.MapCacheLifecycle;
import dev.dubhe.map.client.render.state.MapRenderState;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

@SuppressWarnings("unused")
public final class MinimapGuiLayer implements GuiLayer {
    private static final int BASE_CELL_COUNT = 23;
    private static final int MAX_CELL_COUNT = 63;
    private static final int CHUNK_SIZE = 16;
    private static final Map<Long, CachedColor> COLOR_CACHE = new HashMap<>();

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        MinimapHudSupport.MinimapContext context = MinimapHudSupport.captureContext(graphics);
        if (context == null) {
            return;
        }

        renderCells(
            context.minecraft(),
            graphics,
            context.mapX(),
            context.mapY(),
            context.mapSize(),
            context.rotationDeg()
        );
    }

    private static void renderCells(
        Minecraft minecraft,
        GuiGraphicsExtractor graphics,
        int mapX,
        int mapY,
        int mapSize,
        float rotationDeg
    ) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
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
        boolean circleMode = AtlasClientState.getMinimapShape() == AleeveAtlasClientConfig.MapShape.CIRCLE;
        MapClipMask clipMask = createClipMask(minecraft, mapX, mapY, mapSize, circleMode);
        GpuBufferSlice mapUniform = createClipUniform(clipMask);
        Map<Long, ChunkRenderBatch> chunkBatches = new HashMap<>();

        for (int gz = 0; gz < cellCount; gz++) {
            for (int gx = 0; gx < cellCount; gx++) {
                double sampleLocalX = (gx + 0.5D - cellCount / 2.0D) * blockStep;
                double sampleLocalZ = (gz + 0.5D - cellCount / 2.0D) * blockStep;
                int sampleX = quantizeSampleCoord(anchorX + sampleLocalX);
                int sampleZ = quantizeSampleCoord(anchorZ + sampleLocalZ);
                TileSample surfaceSample = sampleSurfaceTile(minecraft, sampleX, sampleZ, gameTime);
                TileSample northSurfaceSample = sampleSurfaceTile(minecraft, sampleX, sampleZ - northStep, gameTime);
                if (northSurfaceSample == null || surfaceSample == null) {
                    continue;
                }
                int baseColor = underground
                                ? sampleCaveColor(minecraft, sampleX, sampleZ, playerY)
                                : surfaceSample.argb();
                int color = applyNorthShade(baseColor, surfaceSample.height(), northSurfaceSample.height());

                double localLeft = -mapHalfSize + gx * cellSize + localOffsetX;
                double localTop = -mapHalfSize + gz * cellSize + localOffsetY;
                double localRight = localLeft + cellSize;
                double localBottom = localTop + cellSize;
                int chunkX = Math.floorDiv(sampleX, CHUNK_SIZE);
                int chunkZ = Math.floorDiv(sampleZ, CHUNK_SIZE);
                long chunkKey = packChunkKey(chunkX, chunkZ);
                ChunkRenderBatch batch = chunkBatches.computeIfAbsent(chunkKey, key -> new ChunkRenderBatch(chunkX, chunkZ));
                batch.cells().add(new CellRenderTask(localLeft, localTop, localRight, localBottom, color));
            }
        }

        List<ChunkRenderBatch> orderedBatches = new ArrayList<>(chunkBatches.values());
        orderedBatches.sort(Comparator.comparingInt(ChunkRenderBatch::chunkZ).thenComparingInt(ChunkRenderBatch::chunkX));
        for (ChunkRenderBatch batch : orderedBatches) {
            renderChunkBatch(graphics, batch, clipMask, mapUniform, cos, sin);
            graphics.nextStratum();
        }
    }

    private static void renderChunkBatch(
        GuiGraphicsExtractor graphics,
        ChunkRenderBatch batch,
        MapClipMask clipMask,
        @Nullable GpuBufferSlice mapUniform,
        double cos,
        double sin
    ) {
        for (CellRenderTask task : batch.cells()) {
            TileQuad quad = createTileQuad(
                clipMask.centerX(),
                clipMask.centerY(),
                task.localLeft(),
                task.localTop(),
                task.localRight(),
                task.localBottom(),
                cos,
                sin
            );
            if (quad.maxX() <= quad.minX() || quad.maxY() <= quad.minY()) {
                continue;
            }

            renderCell(graphics, quad, task.color(), clipMask, mapUniform);
        }
    }

    private static long packChunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static void renderCell(
        GuiGraphicsExtractor graphics,
        TileQuad quad,
        int color,
        MapClipMask clipMask,
        @Nullable GpuBufferSlice mapUniform
    ) {
        if (mapUniform != null) {
            pipelineUsage(graphics, quad, mapUniform, color);
            return;
        }

        renderCellFallback(graphics, quad, color, clipMask);
    }

    private static MapClipMask createClipMask(Minecraft minecraft, int mapX, int mapY, int mapSize, boolean circleMode) {
        double centerX = mapX + mapSize / 2.0D;
        double centerY = mapY + mapSize / 2.0D;
        double halfSize = mapSize / 2.0D;
        double guiScale = minecraft.getWindow().getGuiScale();
        float framebufferCenterX = (float) (centerX * guiScale);
        float framebufferCenterY = (float) (minecraft.getWindow().getHeight() - centerY * guiScale);
        float framebufferHalfSize = (float) (halfSize * guiScale);
        return new MapClipMask(
            centerX,
            centerY,
            halfSize,
            halfSize,
            halfSize,
            circleMode ? 1.0F : 0.0F,
            new Vector2f(framebufferCenterX, framebufferCenterY),
            new Vector2f(framebufferHalfSize, framebufferHalfSize),
            framebufferHalfSize
        );
    }

    private static @Nullable GpuBufferSlice createClipUniform(MapClipMask clipMask) {
        return MapRenderState.createMapUniform(
            clipMask.framebufferCenter(),
            clipMask.framebufferHalfSize(),
            clipMask.framebufferRadius(),
            clipMask.clipMode()
        );
    }

    private static int getCellCount(int mapSize) {
        int count = Math.clamp(mapSize / 2, BASE_CELL_COUNT, MAX_CELL_COUNT);
        return (count & 1) == 0 ? count + 1 : count;
    }

    private static int quantizeSampleCoord(double coord) {
        return (int) Math.floor(coord + 0.5D);
    }

    private static TileQuad createTileQuad(
        double centerX,
        double centerY,
        double localLeft,
        double localTop,
        double localRight,
        double localBottom,
        double cos,
        double sin
    ) {
        Vector2f p0 = rotateLocalPoint(centerX, centerY, localLeft, localTop, cos, sin);
        Vector2f p1 = rotateLocalPoint(centerX, centerY, localRight, localTop, cos, sin);
        Vector2f p2 = rotateLocalPoint(centerX, centerY, localRight, localBottom, cos, sin);
        Vector2f p3 = rotateLocalPoint(centerX, centerY, localLeft, localBottom, cos, sin);
        float minX = Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x));
        float minY = Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y));
        float maxX = Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x));
        float maxY = Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y));
        return new TileQuad(p0, p1, p2, p3, minX, minY, maxX, maxY);
    }

    private static Vector2f rotateLocalPoint(
        double centerX,
        double centerY,
        double localX,
        double localY,
        double cos,
        double sin
    ) {
        float x = (float) (centerX + localX * cos - localY * sin);
        float y = (float) (centerY + localX * sin + localY * cos);
        return new Vector2f(x, y);
    }

    private static void renderCellFallback(
        GuiGraphicsExtractor graphics,
        TileQuad quad,
        int color,
        MapClipMask clipMask
    ) {
        int minX = (int) Math.floor(quad.minX());
        int minY = (int) Math.floor(quad.minY());
        int maxX = (int) Math.ceil(quad.maxX());
        int maxY = (int) Math.ceil(quad.maxY());
        int baseAlpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        for (int py = minY; py < maxY; py++) {
            for (int px = minX; px < maxX; px++) {
                double sampleX = px + 0.5D;
                double sampleY = py + 0.5D;
                double quadCoverage = coverageFromSignedDistance(signedDistanceToQuad(quad, sampleX, sampleY));
                if (quadCoverage <= 0.0D) {
                    continue;
                }
                double clipCoverage = coverageFromSignedDistance(signedDistanceToClip(clipMask, sampleX, sampleY));
                double coverage = quadCoverage * clipCoverage;
                if (coverage <= 0.0D) {
                    continue;
                }
                int a = (int) Math.round(baseAlpha * coverage);
                int argb = (a << 24) | rgb;
                graphics.fill(px, py, px + 1, py + 1, argb);
            }
        }
    }

    private static double signedDistanceToQuad(TileQuad quad, double px, double py) {
        double winding = Math.signum(edgeFunction(quad.p0(), quad.p1(), quad.p2().x, quad.p2().y));
        if (winding == 0.0D) {
            winding = 1.0D;
        }

        double d0 = winding * signedEdgeDistance(quad.p0(), quad.p1(), px, py);
        double d1 = winding * signedEdgeDistance(quad.p1(), quad.p2(), px, py);
        double d2 = winding * signedEdgeDistance(quad.p2(), quad.p3(), px, py);
        double d3 = winding * signedEdgeDistance(quad.p3(), quad.p0(), px, py);
        return Math.min(Math.min(d0, d1), Math.min(d2, d3));
    }

    private static double edgeFunction(Vector2f a, Vector2f b, double px, double py) {
        double abx = b.x - a.x;
        double aby = b.y - a.y;
        double apx = px - a.x;
        double apy = py - a.y;
        return abx * apy - aby * apx;
    }

    private static double signedEdgeDistance(Vector2f a, Vector2f b, double px, double py) {
        return edgeFunction(a, b, px, py) / Math.max(Math.hypot(b.x - a.x, b.y - a.y), 1.0E-4D);
    }

    private static double signedDistanceToClip(MapClipMask clipMask, double px, double py) {
        if (clipMask.clipMode() > 0.5F) {
            return clipMask.radius() - Math.hypot(px - clipMask.centerX(), py - clipMask.centerY());
        }

        double dx = clipMask.halfWidth() - Math.abs(px - clipMask.centerX());
        double dy = clipMask.halfHeight() - Math.abs(py - clipMask.centerY());
        return Math.min(dx, dy);
    }

    private static double coverageFromSignedDistance(double signedDistance) {
        return Math.clamp(signedDistance + 0.5D, 0.0D, 1.0D);
    }

    static boolean shouldRenderCaves(Minecraft minecraft) {
        if (!AtlasClientState.isCaveMappingEnabled() || minecraft.player == null || minecraft.level == null) {
            return false;
        }
        BlockPos playerPos = minecraft.player.blockPosition();
        return !minecraft.level.canSeeSky(playerPos.above())
               && minecraft.level.getBrightness(LightLayer.SKY, playerPos.above()) < 8;
    }

    private static @Nullable TileSample sampleSurfaceTile(Minecraft minecraft, int x, int z, long gameTime) {
        if (minecraft.level == null) {
            return null;
        }
        if (minecraft.level instanceof ClientLevel clientLevel) {
            MapCacheLifecycle.SurfaceSample sample = MapCacheLifecycle.getSurfaceSample(clientLevel, x, z);
            if (sample != null) {
                int argb = 0xFF000000 | (sample.color() & 0x00FFFFFF);
                return new TileSample(argb, sample.height());
            }
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
        if (COLOR_CACHE.size() > 20000) {
            COLOR_CACHE.clear();
        }
        return new TileSample(argb, y);
    }

    private static int sampleSurfaceColor(Minecraft minecraft, int x, int z, long gameTime) {
        TileSample tileSample = sampleSurfaceTile(minecraft, x, z, gameTime);
        return tileSample == null ? 0 : tileSample.argb();
    }

    private static int sampleCaveColor(Minecraft minecraft, int x, int z, int playerY) {
        if (minecraft.level == null) {
            return 0;
        }
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
        if (minecraft.level == null) {
            return 0;
        }
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
        if (minecraft.level == null) {
            return 0;
        }
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

    private static int applyNorthShade(int argb, int y1, int y2) {
        int m = y1 < y2 ? 180 : (y1 > y2 ? 255 : 220);
        int a = (argb >>> 24) & 0xFF;
        int r = ((argb >>> 16) & 0xFF) * m / 255;
        int g = ((argb >>> 8) & 0xFF) * m / 255;
        int b = (argb & 0xFF) * m / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private record MapClipMask(
        double centerX,
        double centerY,
        double halfWidth,
        double halfHeight,
        double radius,
        float clipMode,
        Vector2f framebufferCenter,
        Vector2f framebufferHalfSize,
        float framebufferRadius
    ) {
    }

    private record TileQuad(
        Vector2f p0,
        Vector2f p1,
        Vector2f p2,
        Vector2f p3,
        float minX,
        float minY,
        float maxX,
        float maxY
    ) {
    }

    private record TileSample(int argb, int height) {
    }

    private record CachedColor(int argb, int height, long sampleTick) {
    }

    private record CellRenderTask(double localLeft, double localTop, double localRight, double localBottom, int color) {
    }

    private record ChunkRenderBatch(int chunkX, int chunkZ, List<CellRenderTask> cells) {
        private ChunkRenderBatch(int chunkX, int chunkZ) {
            this(chunkX, chunkZ, new ArrayList<>());
        }
    }

    private static void pipelineUsage(
        GuiGraphicsExtractor graphics,
        TileQuad quad,
        GpuBufferSlice mapUniform,
        int color
    ) {
        graphics.submitGuiElementRenderState(new MapRenderState(
            graphics.pose(),
            quad.p0(),
            quad.p1(),
            quad.p2(),
            quad.p3(),
            color,
            mapUniform,
            graphics.peekScissorStack()
        ));
    }
}
